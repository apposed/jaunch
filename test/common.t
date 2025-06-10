NB: general tests for common configuration, using 'hello' 
Pre-requisites: run `make clean demo` in the root directory

Setup:

  $ cd "$TESTDIR/../demo"

Test: Help text with --help
  $ ./jaunch/jaunch-linux-x64 hello --help
  Usage: hello [<Runtime options>.. --] [<main arguments>..]
  
  Hello launcher (Jaunch v* / * / *) (glob)
  Runtime options are passed to the runtime platform (JVM or Python),
  main arguments to the launched program (Hello).
  
  In addition, the following options are supported:
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
  ABORT
--End of expected output--

Test: Help text with -h
  $ ./jaunch/jaunch-linux-x64 hello -h
  Usage: hello [<Runtime options>.. --] [<main arguments>..]
  
  Hello launcher (Jaunch v* / * / *) (glob)
  Runtime options are passed to the runtime platform (JVM or Python),
  main arguments to the launched program (Hello).
  
  In addition, the following options are supported:
  --java-home <path>
                      specify Java installation path explicitly
  --print-class-path, --print-classpath
                      print runtime classpath elements
  --print-java-home
                      print path to the selected Java
  --print-java-info
                      print information about the selected Java
  --headless
                      run in text mode
  --heap, --mem, --memory <amount>
                      set Java's heap size to <amount> (e.g. 512M or 64%)
  --class-path, --classpath, -classpath, --cp, -cp <path>
                      append <path> to the class path
  --ext <path>
                      set Java's extension directory to <path>
  --debugger <port>[,suspend]
                      start Java in a mode so an IDE/debugger can attach to it
  --help, -h
                      show this help
  --dry-run
                      show the command line, but do not run anything
  --debug
                      verbose output
  --print-app-dir
                      print directory where the application is located
  --print-config-dir
                      print directory where the configuration files are located
  --system
                      do not try to run bundled runtime
  ABORT
--End of expected output--

Test: Debug output
  $ ./jaunch/jaunch-linux-x64 hello --debug 2>&1 | grep "JAUNCH CONFIGURATION COMPLETE"
  [DEBUG] | JAUNCH CONFIGURATION COMPLETE |

  $ ls hello.log
  hello.log

Test: dry-run
  $ ./jaunch/jaunch-linux-x64 hello --dry-run
  [DRY-RUN] /*/bin/java -Djava.class.path=/*/demo HelloSwing (glob)
  ABORT

Test: print-app-dir
  $ ./jaunch/jaunch-linux-x64 hello --print-app-dir
  --- Application Directory ---
  /*/demo (glob)
  
  JVM
  4
  /*/libjvm.so (glob)
  1
  -Djava.class.path=/*/demo (glob)
  HelloSwing

Test: print-config-dir
  $ ./jaunch/jaunch-linux-x64 hello --print-config-dir
  --- Configuration Directory ---
  /home/*/demo/jaunch (glob)
  
  JVM
  4
  /*/libjvm.so (glob)
  1
  -Djava.class.path=/*/demo (glob)
  HelloSwing

Cleanup:
  $ rm *.log
