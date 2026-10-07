# Pocket Python

A standalone Android Python editor with a dark grey theme, syntax highlighting,
local files, and a terminal beneath the editor. Designed for touch use on phones
including the Motorola Edge 60 Pro. This is an independent app, not Microsoft VS Code.

## Install after building an APK

Copy the built APK to the phone and open it in Files. If Android prompts,
allow that file manager to install apps from this source, then choose Install.
Open **Pocket Python** and tap **Run** on the included `welcome.py`.

The APK includes its Python interpreter and editor assets. No account, server,
subscription, or separate Python installation is needed. Network requests made
by your Python programs still need an internet connection.

## Use

- **Folder icon:** open the workspace, create a file, or import a `.py` file.
- **Plus:** create a new Python file.
- **Save icon:** save immediately. Changes also save automatically after typing.
- **Run / Stop:** execute the current file or stop its Python process.
- **Terminal:** view output and tracebacks; type a reply when `input()` asks.
- **EOF:** signal the end of standard input.
- **Divider:** drag to adjust terminal height.
- **Three dots:** find, undo/redo, rename, export, delete, and library help.
- **Aa:** cycle the editor font size.
- **Coding keys:** indent, unindent, brackets, quotes, arrows, and suggestions.

Scripts live in the app's private workspace and survive reopening the app.
Use **Export file** to save copies in Documents or another chosen location.
Uninstalling the app or clearing its data removes its private workspace.
Imported files are copied into the workspace; their originals are unchanged.
Name collisions during import receive a numbered suffix.

Every run starts a fresh Python process. Other `.py` files in the workspace can
be imported as modules. Relative file I/O uses that workspace as the working
directory. Keep the app open while running programs. This version is not a
background execution service.

## Python support

- CPython 3.13, including normal Python language features and built-in functions.
- Android-compatible standard-library modules, including math, random, datetime,
  json, re, pathlib, statistics, sqlite3, asyncio, urllib and many more.
- NumPy, Requests, SymPy, and Pillow (`PIL`).
- Keywords, strings, numbers, comments, definitions and built-ins are highlighted.
  Suggestions include built-ins, keywords, and identifiers in the current file.

It is not possible to bundle every Python package, and desktop-only APIs cannot
all work on Android. Tkinter, Turtle, curses, readline, certain Unix account
modules, and most multiprocessing APIs are unsupported by the embedded runtime.
Third-party native packages must have compatible Android builds. Packages are
bundled when building the APK; this version has no on-device pip installer.
The terminal is a text input/output console, not an Android shell or an
interactive Python REPL. Desktop GUI windows and VS Code extensions are not included.
Text `stdin`, `stdout`, and `stderr` are supported; binary `.buffer` access on these
terminal streams is not implemented. Scripts are limited to 300,000 characters,
and displayed output is limited to 250,000 characters per run.

Runtime details: https://chaquo.com/chaquopy/doc/current/android.html

## Build the source

Use JDK 21, Android SDK Platform 35, Gradle 9.1.0, and a local Python 3.13.
The project uses Android Gradle Plugin 9.0.1 and Chaquopy 17.0.0. Open this folder
in a compatible Android Studio release, set the SDK path in `local.properties`,
and set `POCKET_BUILD_PYTHON` if Python 3.13 is not found automatically.

```powershell
$env:POCKET_BUILD_PYTHON = 'C:/path/to/python313/python.exe'
gradle assembleDebug
```

The result is `app/build/outputs/apk/debug/app-debug.apk`.
For a release build, use `assembleRelease`, then sign the APK with your key.
The debug APK is automatically signed for installation and is not a Play Store
release. For a release build, use your own signing key and keep it for updates.

The included GitHub Actions workflow can also build a debug APK. Place the
contents of this folder at the root of your own GitHub repository and run
**Build Android APK** from its Actions tab. Download the resulting
`PocketPython-debug-apk` artifact, then copy the APK to your phone.

The app targets Android 15 (API 35), requires Android 8.0 or newer, and includes
ARM64 and x86-64 native libraries. ARM64 is the intended phone build.

## Verification

The Python runner has desktop tests for language execution, imports, file I/O,
interactive text input, EOF, exit codes, and error reporting. Editor tests use a
browser with a mocked Android bridge to check file flows and mobile layouts.
These checks do not replace installing and testing on a physical Android phone.

## Components and notices

- CodeMirror 5.65.20 — MIT; license in `app/src/main/assets/vendor`.
- Chaquopy 17.0.0 — MIT; https://github.com/chaquo/chaquopy
- CPython 3.13 — Python Software Foundation license; https://docs.python.org/3/license.html
- NumPy — BSD-3-Clause and bundled native-library notices; https://numpy.org
- Requests — Apache-2.0; https://requests.readthedocs.io
- SymPy — BSD-3-Clause; https://www.sympy.org
- Pillow — HPND and bundled-library notices; https://python-pillow.org

The Python distributions include their own metadata and license files. Additional
notices extracted during packaging are included alongside this source.
