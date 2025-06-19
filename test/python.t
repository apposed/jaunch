General tests for python configuration, using 'paunch'
Pre-requisites:
1. run `make clean demo` in the root directory
2. Ensure python 3.8+ is available (system or by running in conda env)

Setup:

  $ . "$TESTDIR/common.include"
  $ cd "$TESTDIR/../demo"

Tests:

  $ ./paunch --help
  Usage: paunch* [<Runtime options>.. --] [<main arguments>..] (glob)
  
  Paunch launcher (Jaunch v* / * / *) (glob)
  Runtime options are passed to the runtime platform (JVM or Python),
  main arguments to the launched program (Paunch).
  
  In addition, the following options are supported:
  --python-home <path>
                      specify PYTHON_HOME explicitly
  --print-python-home
                      print path to the selected Python
  --print-python-info
                      print information about the selected Python
  --help, -h
                      show this help
  --dry-run
                      show the command line, but do not run anything
  --debug
                      verbose output
  --headless
                      run in text mode (without any GUI)
  --print-app-dir
                      print directory where the application is located
  --print-config-dir
                      print directory where the configuration files are located
  --system
                      do not try to run bundled runtime

  $ ./paunch -c "print(1+2)"
  3

  $ ./paunch --print-python-home
  /* (glob)

  $ ./paunch --print-python-info 2>&1 | head -n4
  root: /* (glob)
  libPython: .*(libpython[0-9\.]*\.so|libpython[0-9\.]*\.dylib|python[0-9\.]*\.dll) (re)
  version: * (glob)
  packages:* (glob)

  $ ./paunch --debug --python-home . 2>&1 | grep -m 1 -A1 "Analyzing candidate Python"
  [DEBUG] Analyzing candidate Python directory: '.'
  [DEBUG] No Python library found.
