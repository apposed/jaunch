Tests for app defined in repl.toml

Pre-requisites:
1. run `make clean demo` in the root directory
2. Ensure a suitable JVM is installed on the system
3. Ensure python 3.8+ is available (system or by running in conda env)

Setup:

  $ . "$TESTDIR/common.include"
  $ cd "$TESTDIR/../demo"

Test help text

  $ ./repl --help
  Usage: repl* [<Runtime options>.. --] [<main arguments>..] (glob)
  
  REPL launcher (Jaunch v* / * / *) (glob)
  Runtime options are passed to the runtime platform (JVM or Python),
  main arguments to the launched program (REPL).
  
  In addition, the following options are supported:
  --python
                      Launch the Python REPL
  --jshell
                      Launch the jshell REPL
  --java-home <path>
                      specify Java installation path explicitly
  --print-class-path, --print-classpath
                      print runtime classpath elements
  --print-java-home
                      print path to the selected Java
  --print-java-info
                      print information about the selected Java
  --heap, --mem, --memory <amount>
                      set Java's heap size to <amount> (e.g. 512M or 64%)
  --class-path, --classpath, -classpath, --cp, -cp <path>
                      append <path> to the class path
  --ext <path>
                      set Java's extension directory to <path>
  --debugger <port>[,suspend]
                      start Java in a mode so an IDE/debugger can attach to it
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

Hard to test REPLs in an automated way so we just make sure that the launcher
will try to launch each repl appropriately
  $ ./repl --python --dry-run
  \[DRY-RUN\] .*python3? -i (re)

  $ ./repl --jshell --dry-run
  [DRY-RUN] /*/bin/java jdk.internal.jshell.tool.JShellToolProvider (glob)
