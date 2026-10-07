package dev.pocketpython.ide;

import android.app.Service;
import android.content.Intent;
import android.os.*;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import java.util.concurrent.LinkedBlockingQueue;

public class PythonService extends Service {
    public static final int RUN=1, INPUT=2, STOP=3, OUTPUT=4, WAITING=5, DONE=6, EOF=7;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Messenger client;
    private boolean launched = false;
    private final LinkedBlockingQueue<String> input = new LinkedBlockingQueue<>();
    private volatile boolean eof = false;
    private final StringBuilder out = new StringBuilder();
    private final StringBuilder err = new StringBuilder();
    private int total = 0;
    private boolean truncated = false;
    private final Runnable drain = new Runnable() {
        @Override public void run() { flush(); main.postDelayed(this, 60); }
    };
    private final Messenger messenger = new Messenger(new Handler(Looper.getMainLooper()) {
        @Override public void handleMessage(Message msg) {
            if (msg.what == RUN && !launched) {
                launched = true;
                client = msg.replyTo;
                Bundle data = msg.getData();
                main.post(drain);
                new Thread(() -> {
                    try {
                        if (!Python.isStarted()) Python.start(new AndroidPlatform(PythonService.this));
                        Python.getInstance().getModule("runner").callAttr("run",
                            data.getString("source"), data.getString("name"),
                            data.getString("directory"), PythonService.this);
                    } catch (Throwable error) {
                        write("Interpreter error: " + error + "\n", "stderr");
                        finished(1, 0);
                    }
                }, "python-script").start();
            } else if (msg.what == INPUT) {
                input.offer(msg.getData().getString("text", ""));
            } else if (msg.what == EOF) {
                eof = true;
                input.offer("");
            } else if (msg.what == STOP) {
                android.os.Process.killProcess(android.os.Process.myPid());
            }
        }
    });

    @Override public IBinder onBind(Intent intent) { return messenger.getBinder(); }
    public synchronized void write(String text, String kind) {
        if (truncated) return;
        int remaining = 250000 - total;
        String accepted = text.length() > remaining ? text.substring(0, remaining) : text;
        ("stderr".equals(kind) ? err : out).append(accepted);
        total += accepted.length();
        if (total >= 250000) {
            truncated = true;
            err.append("\n[Output limit reached. The program is still running; use Stop if needed.]\n");
        }
    }
    private void send(int what, Bundle data) {
        try { Message message = Message.obtain(null, what); message.setData(data); client.send(message); }
        catch (Exception ignored) { }
    }
    public synchronized void flush() {
        drainBuffer(out, "stdout");
        drainBuffer(err, "stderr");
    }
    private void drainBuffer(StringBuilder buffer, String kind) {
        while (buffer.length() > 0) {
            int count = Math.min(8000, buffer.length());
            Bundle data = new Bundle();
            data.putString("text", buffer.substring(0, count));
            data.putString("kind", kind);
            buffer.delete(0, count);
            send(OUTPUT, data);
        }
    }
    public String readLine() throws InterruptedException {
        flush();
        if (eof) return null;
        send(WAITING, new Bundle());
        String line = input.take();
        return eof ? null : line;
    }
    public void finished(int code, double seconds) {
        flush();
        Bundle data = new Bundle();
        data.putInt("code", code);
        data.putDouble("seconds", seconds);
        send(DONE, data);
        main.post(() -> {
            main.removeCallbacks(drain);
            main.postDelayed(() -> android.os.Process.killProcess(android.os.Process.myPid()), 180);
        });
    }
    @Override public boolean onUnbind(Intent intent) {
        android.os.Process.killProcess(android.os.Process.myPid());
        return false;
    }
}
