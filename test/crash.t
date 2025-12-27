Tests for crash handling (SIGABRT from native libraries)

Pre-requisites:
1. run `make clean demo` in the root directory
2. Ensure python 3.8+ is available (system or by running in conda env)

Setup:

  $ . "$TESTDIR/common.include"
  $ cd "$TESTDIR/../demo"

Test that normal Python execution works

  $ ./paunch -c "print('hello')"
  hello

Test that Python abort() triggers SIGABRT handling

We simulate a native library calling abort() via ctypes. This should:
1. Print an error message to stderr
2. Exit with code 20 (ERROR_RUNTIME_CRASH)

  $ ./paunch --headless -c "import ctypes; ctypes.CDLL(None).abort()" 2>&1
  [ERROR] Runtime execution aborted unexpectedly!
  [20]

Test that normal exit() still works

  $ ./paunch -c "import sys; sys.exit(42)"
  [42]

Test that normal exceptions still produce Python output

The grep exclusion works around output differences
between Python 3.13+ and earlier versions:

  $ ./paunch --headless -c "raise ValueError('test error')" 2>&1 | grep -v 'raise ValueError'
  Traceback (most recent call last):
    File "<string>", line 1, in <module>
  ValueError: test error
  [ERROR] Failed to run Python script: 1
