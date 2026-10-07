import importlib.util
from pathlib import Path
import tempfile
import unittest

path = Path(__file__).resolve().parents[1] / 'app/src/main/python/runner.py'
spec = importlib.util.spec_from_file_location('runner', path)
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)

class Bridge:
    def __init__(self, lines=()):
        self.output = []
        self.lines = iter(lines)
        self.exit = None
    def write(self, text, kind): self.output.append((kind, text))
    def flush(self): pass
    def readLine(self): return next(self.lines, None)
    def finished(self, code, seconds): self.exit = code
    @property
    def text(self): return ''.join(text for kind,text in self.output)

class RunnerTests(unittest.TestCase):
    def setUp(self):
        self.directory = Path(tempfile.gettempdir())/'runner-tests'
        self.directory.mkdir(exist_ok=True)
    def run_script(self, source, lines=()):
        bridge = Bridge(lines)
        runner.run(source, 'test.py', str(self.directory), bridge)
        return bridge
    def test_functions_stdlib(self):
        b=self.run_script('from math import sqrt\ndef f(n): return sqrt(n)\nprint(f(144))')
        self.assertEqual(b.text,'12.0\n'); self.assertEqual(b.exit,0)
    def test_input_and_prompt(self):
        b=self.run_script('name=input("Name? ")\nprint("Hello",name)', ['Ada'])
        self.assertEqual(b.text,'Name? Hello Ada\n');self.assertEqual(b.exit,0)
    def test_file_io_and_local_import(self):
        (self.directory/'helper_module.py').write_text('answer = 42\n')
        b=self.run_script('import helper_module\nfrom pathlib import Path\nPath("result.txt").write_text(str(helper_module.answer))\nprint(Path("result.txt").read_text())')
        self.assertEqual(b.text,'42\n');self.assertEqual(b.exit,0)
    def test_runtime_traceback_has_user_line(self):
        b=self.run_script('x = 1\nprint(1 / 0)')
        self.assertEqual(b.exit,1);self.assertIn('ZeroDivisionError',b.text);self.assertIn('line 2',b.text)
    def test_syntax_error(self):
        b=self.run_script('def broken(:\n pass')
        self.assertEqual(b.exit,1);self.assertIn('SyntaxError',b.text)
    def test_exit_codes(self):
        self.assertEqual(self.run_script('raise SystemExit(7)').exit,7)
        self.assertEqual(self.run_script('raise SystemExit()').exit,0)
        self.assertIn('goodbye',self.run_script('raise SystemExit("goodbye")').text)
    def test_stdin_eof(self):
        b=self.run_script('import sys\nprint(repr(sys.stdin.read()))',['one','two'])
        self.assertEqual(b.text,"'one\\ntwo\\n'\n")
    def test_globals_reset(self):
        self.run_script('a = 5')
        self.assertEqual(self.run_script('print("a" in globals())').text,'False\n')

unittest.main(verbosity=2)
