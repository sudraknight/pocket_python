"""One script per Android service process; the editor lives in another process."""
import builtins
import io
import linecache
import os
import sys
import time
import traceback


class Output(io.TextIOBase):
    def __init__(self, bridge, kind):
        self.bridge = bridge
        self.kind = kind

    @property
    def encoding(self):
        return 'utf-8'

    def writable(self):
        return True

    def isatty(self):
        return False

    def write(self, value):
        value = str(value)
        self.bridge.write(value, self.kind)
        return len(value)

    def flush(self):
        self.bridge.flush()


class Input(io.TextIOBase):
    def __init__(self, bridge):
        self.bridge = bridge
        self.pending = ''

    @property
    def encoding(self):
        return 'utf-8'

    def readable(self):
        return True

    def readline(self, size=-1):
        if not self.pending:
            value = self.bridge.readLine()
            if value is None:
                return ''
            self.pending = str(value) + '\n'
        if size < 0:
            value, self.pending = self.pending, ''
        else:
            value, self.pending = self.pending[:size], self.pending[size:]
        return value

    def read(self, size=-1):
        result = ''
        while size < 0 or len(result) < size:
            part = self.readline(-1 if size < 0 else size-len(result))
            if not part:
                break
            result += part
        return result


def run(source, name, directory, bridge):
    previous = sys.stdout, sys.stderr, sys.stdin, sys.argv, list(sys.path), os.getcwd()
    started = time.monotonic()
    code = 0
    filename = os.path.join(directory, name)
    try:
        os.chdir(directory)
        sys.path.insert(0, directory)
        sys.argv = [filename]
        sys.stdout = Output(bridge, 'stdout')
        sys.stderr = Output(bridge, 'stderr')
        sys.stdin = Input(bridge)
        linecache.cache[filename] = (len(source), None, source.splitlines(True), filename)
        namespace = {'__name__': '__main__', '__file__': filename,
                     '__package__': None, '__builtins__': builtins}
        exec(compile(source, filename, 'exec'), namespace, namespace)
    except SystemExit as error:
        code = error.code if isinstance(error.code, int) else (0 if error.code is None else 1)
        if error.code is not None and not isinstance(error.code, int):
            print(error.code, file=sys.stderr)
    except BaseException as error:
        code = 1
        traceback.print_exception(type(error), error, error.__traceback__.tb_next)
    finally:
        bridge.flush()
        sys.stdout, sys.stderr, sys.stdin, sys.argv, old_path, old_cwd = previous
        sys.path[:] = old_path
        os.chdir(old_cwd)
        bridge.finished(code, time.monotonic() - started)
