package dev.pocketpython.ide;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.util.AtomicFile;
import android.view.*;
import android.webkit.*;
import android.widget.LinearLayout;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public class MainActivity extends Activity {
    private WebView web;
    private File workspace;
    private Messenger python;
    private boolean bound, running;
    private String pendingSource, pendingName, exportText;
    private final Handler main = new Handler(Looper.getMainLooper());
    private static final int IMPORT=30, EXPORT=31;
    private final Messenger receiver = new Messenger(new Handler(Looper.getMainLooper()) {
        @Override public void handleMessage(Message msg) {
            Bundle data = msg.getData();
            if (msg.what == PythonService.OUTPUT) {
                event("output", obj("text", data.getString("text"), "kind", data.getString("kind")));
            } else if (msg.what == PythonService.WAITING) {
                event("waiting", new JSONObject());
            } else if (msg.what == PythonService.DONE) {
                event("done", obj("code", data.getInt("code"), "seconds", data.getDouble("seconds")));
                running = false;
                disconnect();
            }
        }
    });
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            python = new Messenger(binder);
            if (!running) { disconnect(); return; }
            Bundle data = new Bundle();
            data.putString("source", pendingSource);
            data.putString("name", pendingName);
            data.putString("directory", workspace.getAbsolutePath());
            send(PythonService.RUN, data);
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            python = null;
            if (running) event("crashed", obj("text", "Python process ended unexpectedly."));
            running = false;
            disconnect();
        }
        @Override public void onNullBinding(ComponentName name) {
            event("crashed", obj("text", "Could not start the Python service."));
            running = false;
            disconnect();
        }
    };

    static JSONObject obj(Object... pairs) {
        JSONObject object = new JSONObject();
        try { for (int i=0; i<pairs.length; i+=2) object.put((String)pairs[i], pairs[i+1]); }
        catch (JSONException ignored) { }
        return object;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        workspace = new File(getFilesDir(), "scripts");
        workspace.mkdirs();
        if (!getPreferences(0).getBoolean("initialized", false)) {
            try (InputStream stream = getAssets().open("welcome.py")) {
                saveFile("welcome.py", new String(readBytes(stream, 1200000), StandardCharsets.UTF_8));
                getPreferences(0).edit().putBoolean("initialized", true).apply();
            } catch (Exception ignored) { }
        }
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        LinearLayout root = new LinearLayout(this);
        root.setBackgroundColor(Color.rgb(32,33,36));
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                android.graphics.Insets ime = insets.getInsets(WindowInsets.Type.ime());
                view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
                return WindowInsets.CONSUMED;
            });
        }
        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(32,33,36));
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("https".equals(uri.getScheme()) && "app.pocketpython.local".equals(uri.getHost())) {
                    String file = uri.getPath().substring(1);
                    if (file.contains("..")) return emptyResponse();
                    if (file.isEmpty()) file = "index.html";
                    String mime = file.endsWith(".css") ? "text/css" : file.endsWith(".js") ? "application/javascript" : file.endsWith(".html") ? "text/html" : "text/plain";
                    try { return new WebResourceResponse(mime, "UTF-8", getAssets().open(file)); }
                    catch (IOException ignored) { return emptyResponse(); }
                }
                return emptyResponse();
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return true;
            }
        });
        web.addJavascriptInterface(new Bridge(), "Native");
        root.addView(web, new LinearLayout.LayoutParams(-1, -1));
        setContentView(root);
        web.loadUrl("https://app.pocketpython.local/index.html");
    }
    private WebResourceResponse emptyResponse() {
        return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
    }
    private byte[] readBytes(InputStream stream, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = stream.read(buffer)) != -1) {
            if (output.size() + count > limit) throw new IOException("File too large.");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }
    private File safeFile(String name) throws IOException {
        if (name == null || !name.matches("[A-Za-z0-9_][A-Za-z0-9_. -]{0,95}\\.py"))
            throw new IOException("Use a .py filename with letters, numbers, spaces, underscores or hyphens.");
        File file = new File(workspace, name);
        if (!file.getCanonicalFile().getParentFile().equals(workspace.getCanonicalFile()))
            throw new IOException("Invalid filename.");
        return file;
    }
    private synchronized void saveFile(String name, String text) throws IOException {
        if (text.length() > 300000) throw new IOException("This editor supports scripts up to 300,000 characters.");
        AtomicFile file = new AtomicFile(safeFile(name));
        FileOutputStream stream = null;
        try {
            stream = file.startWrite();
            stream.write(text.getBytes(StandardCharsets.UTF_8));
            file.finishWrite(stream);
        } catch (IOException error) { if (stream != null) file.failWrite(stream); throw error; }
    }
    private void event(String name, JSONObject data) {
        main.post(() -> { if (web != null) web.evaluateJavascript("window.onNativeEvent(" + JSONObject.quote(name) + "," + data + ")", null); });
    }
    private void send(int what, Bundle data) {
        try {
            Message msg = Message.obtain(null, what);
            msg.setData(data); msg.replyTo = receiver;
            python.send(msg);
        } catch (Exception error) {
            event("crashed", obj("text", "Could not communicate with Python."));
            running = false; disconnect();
        }
    }
    private void disconnect() {
        if (bound) { try { unbindService(connection); } catch (Exception ignored) { } }
        bound = false; python = null;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    public class Bridge {
        @JavascriptInterface public String list() {
            JSONArray result = new JSONArray();
            File[] files = workspace.listFiles((dir, name) -> name.endsWith(".py"));
            if (files != null) {
                Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
                for (File file : files) result.put(file.getName());
            }
            return result.toString();
        }
        @JavascriptInterface public String read(String name) {
            try {
                File file = safeFile(name);
                if (file.length() > 1200000) throw new IOException("File is too large for the editor.");
                return obj("ok", true, "text", new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)).toString();
            } catch (Exception error) { return obj("ok", false, "error", error.getMessage()).toString(); }
        }
        @JavascriptInterface public String save(String name, String text) {
            try { saveFile(name, text); return obj("ok", true).toString(); }
            catch (Exception error) { return obj("ok", false, "error", error.getMessage()).toString(); }
        }
        @JavascriptInterface public String create(String name) {
            try {
                if (safeFile(name).exists()) throw new IOException("That file already exists.");
                saveFile(name, ""); return obj("ok", true).toString();
            } catch (Exception error) { return obj("ok", false, "error", error.getMessage()).toString(); }
        }
        @JavascriptInterface public String rename(String oldName, String name) {
            try {
                File dest = safeFile(name);
                if (dest.exists()) throw new IOException("That file already exists.");
                if (!safeFile(oldName).renameTo(dest)) throw new IOException("Could not rename file.");
                return obj("ok", true).toString();
            } catch (Exception error) { return obj("ok", false, "error", error.getMessage()).toString(); }
        }
        @JavascriptInterface public String delete(String name) {
            try {
                if (!safeFile(name).delete()) throw new IOException("Could not delete file.");
                return obj("ok", true).toString();
            } catch (Exception error) { return obj("ok", false, "error", error.getMessage()).toString(); }
        }
        @JavascriptInterface public void run(String name, String source) {
            main.post(() -> {
                if (running) return;
                try { saveFile(name, source); }
                catch (Exception error) { event("crashed", obj("text", error.getMessage())); return; }
                pendingName = name; pendingSource = source; running = true;
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                try {
                    bound = bindService(new Intent(MainActivity.this, PythonService.class), connection, BIND_AUTO_CREATE);
                    if (!bound) throw new IOException("Could not start Python.");
                } catch (Exception error) {
                    running = false; disconnect(); event("crashed", obj("text", error.getMessage()));
                }
            });
        }
        @JavascriptInterface public void stop() {
            main.post(() -> {
                if (python != null) send(PythonService.STOP, new Bundle());
                running = false; disconnect(); event("stopped", new JSONObject());
            });
        }
        @JavascriptInterface public void input(String text) {
            main.post(() -> { if (python != null) { Bundle data = new Bundle(); data.putString("text", text); send(PythonService.INPUT, data); } });
        }
        @JavascriptInterface public void eof() {
            main.post(() -> { if (python != null) send(PythonService.EOF, new Bundle()); });
        }
        @JavascriptInterface public void importFile() {
            main.post(() -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(intent, IMPORT);
            });
        }
        @JavascriptInterface public void exportFile(String name, String text) {
            main.post(() -> {
                exportText = text;
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/x-python").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, name);
                startActivityForResult(intent, EXPORT);
            });
        }
        @JavascriptInterface public void copy(String text) {
            main.post(() -> ((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Python output", text)));
        }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        try {
            Uri uri = data.getData();
            if (request == IMPORT) {
                String name = "imported.py";
                try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        if (col >= 0) name = cursor.getString(col);
                    }
                }
                name = name.replaceAll("[^A-Za-z0-9_. -]", "_");
                if (!name.matches("^[A-Za-z0-9_].*")) name = "imported_" + name;
                if (!name.endsWith(".py")) name += ".py";
                if (name.length() > 80) name = name.substring(0, 76) + ".py";
                String stem = name.substring(0, name.length()-3);
                int number = 2;
                while (safeFile(name).exists()) name = stem + "_" + number++ + ".py";
                try (InputStream stream = getContentResolver().openInputStream(uri)) {
                    byte[] bytes = readBytes(stream, 1200000);
                    saveFile(name, new String(bytes, StandardCharsets.UTF_8));
                }
                event("imported", obj("name", name));
            } else if (request == EXPORT && exportText != null) {
                try (OutputStream stream = getContentResolver().openOutputStream(uri, "wt")) {
                    stream.write(exportText.getBytes(StandardCharsets.UTF_8));
                }
                event("notice", obj("text", "File exported to your chosen folder."));
                exportText = null;
            }
        } catch (Exception error) { event("notice", obj("text", "File operation failed: " + error.getMessage())); }
    }
    @Override protected void onPause() {
        super.onPause();
        if (web != null) web.evaluateJavascript("window.saveCurrent && window.saveCurrent()", null);
    }
    @Override protected void onDestroy() {
        running = false; disconnect();
        if (web != null) { web.removeJavascriptInterface("Native"); web.destroy(); web = null; }
        super.onDestroy();
    }
    @Override public void onBackPressed() {
        web.evaluateJavascript("window.handleBack()", handled -> {
            if (!"true".equals(handled)) {
                if (running) new AlertDialog.Builder(this).setMessage("Stop the program and leave Pocket Python?")
                    .setNegativeButton("Keep coding", null).setPositiveButton("Leave", (d,w) -> finish()).show();
                else super.onBackPressed();
            }
        });
    }
}
