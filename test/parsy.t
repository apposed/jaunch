Tests for parsy
Pre-requisites:
1. run `make clean demo` in the root directory
2. Ensure a suitable JVM is installed on the system

Setup:

  $ cd "$TESTDIR/../demo"

Test that the correct Java program actually runs.

  $ echo 1+2 | ./parsy | head -n1
  * 3 : java.lang.Integer (glob)

Test command line argument combinations.

  $ ./parsy --help
  Usage: parsy* [<Runtime options>.. --] [<main arguments>..] (glob)
  
  Parsy launcher (Jaunch v* / * / *) (glob)
  Runtime options are passed to the runtime platform (JVM or Python),
  main arguments to the launched program (Parsy).
  
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

  $ ./parsy --print-java-home
  /* (glob)

This is highly variable with java version & build
  $ ./parsy --print-java-info 2>&1 | wc
  * (glob)

  $ ./parsy --dry-run --print-app-dir
  --- Application Directory ---
  /*/demo (glob)
  
  [DRY-RUN] /*/bin/java -Djava.class.path=/* -Xmx64m org.scijava.parsington.Main (glob)

  $ ./parsy --dry-run --system
  [DRY-RUN] /*/bin/java -Djava.class.path=/* -Xmx64m org.scijava.parsington.Main (glob)

  $ ./parsy --dry-run --java-home /usr/lib/jvm/default-java
  [DRY-RUN] /*/bin/java -Djava.class.path=/* -Xmx64m org.scijava.parsington.Main (glob)

  $ ./parsy --dry-run --headless
  [DRY-RUN] /*/bin/java -Djava.awt.headless=true -Dapple.awt.UIElement=true -Djava.class.path=/* -Xmx64m org.scijava.parsington.Main (glob)

  $ ./parsy --dry-run --class-path /tmp/lions.jar:/tmp/tigers.jar
  [DRY-RUN] /*/bin/java -Djava.class.path=/*:/tmp/lions.jar:/tmp/tigers.jar -Xmx64m org.scijava.parsington.Main (glob)

  $ ./parsy --dry-run --debugger 8765,suspend
  [DRY-RUN] /*/bin/java -agentlib:jdwp=transport=dt_socket,server=y,address=localhost:8765,suspend -Djava.class.path=/* -Xmx64m org.scijava.parsington.Main (glob)

  $ ./parsy --dry-run --system --java-home /usr/lib/jvm/default-java
  [DRY-RUN] /*/bin/java -Djava.class.path=/* -Xmx64m org.scijava.parsington.Main (glob)

  $ ./parsy --dry-run --system --headless
  [DRY-RUN] /*/bin/java -Djava.awt.headless=true -Dapple.awt.UIElement=true -Djava.class.path=/* -Xmx64m org.scijava.parsington.Main (glob)

  $ ./parsy --dry-run --system --heap 58m
  [DRY-RUN] /*/bin/java -Xmx58m -Djava.class.path=/* org.scijava.parsington.Main (glob)

  $ ./parsy --dry-run --system --class-path /tmp/lions.jar:/tmp/tigers.jar
  [DRY-RUN] /*/bin/java -Djava.class.path=/*:/tmp/lions.jar:/tmp/tigers.jar -Xmx64m org.scijava.parsington.Main (glob)

  $ ./parsy --dry-run --system --jar-path /tmp/jars:/tmp/other-jars
  [DRY-RUN] /*/bin/java -Djava.class.path=/* -Xmx64m org.scijava.parsington.Main --jar-path /tmp/jars:/tmp/other-jars (glob)

  $ ./parsy --dry-run --system --debugger 8765,suspend
  [DRY-RUN] /*/bin/java -agentlib:jdwp=transport=dt_socket,server=y,address=localhost:8765,suspend -Djava.class.path=/* -Xmx64m org.scijava.parsington.Main (glob)

  $ ./parsy --dry-run --system
  [DRY-RUN] /*/bin/java -Djava.class.path=/* -Xmx64m org.scijava.parsington.Main (glob)

  $ ./parsy --dry-run --java-home /usr/lib/jvm/default-java --headless
  [DRY-RUN] /*/bin/java -Djava.awt.headless=true -Dapple.awt.UIElement=true -Djava.class.path=/* -Xmx64m org.scijava.parsington.Main (glob)

  $ ./parsy --dry-run --java-home /usr/lib/jvm/default-java --heap 58m
  [DRY-RUN] /*/bin/java -Xmx58m -Djava.class.path=/* org.scijava.parsington.Main (glob)

  $ ./parsy --dry-run --java-home /usr/lib/jvm/default-java --class-path /tmp/lions.jar:/tmp/tigers.jar
  [DRY-RUN] /*/bin/java -Djava.class.path=/*:/tmp/lions.jar:/tmp/tigers.jar -Xmx64m org.scijava.parsington.Main (glob)

  $ ./parsy --dry-run --java-home /usr/lib/jvm/default-java --jar-path /tmp/jars:/tmp/other-jars
  [DRY-RUN] /*/bin/java -Djava.class.path=/* -Xmx64m org.scijava.parsington.Main --jar-path /tmp/jars:/tmp/other-jars (glob)

  $ ./parsy --dry-run --java-home /usr/lib/jvm/default-java --debugger 8765,suspend
  [DRY-RUN] /*/bin/java -agentlib:jdwp=transport=dt_socket,server=y,address=localhost:8765,suspend -Djava.class.path=/* -Xmx64m org.scijava.parsington.Main (glob)

  $ ./parsy --dry-run --java-home /usr/lib/jvm/default-java
  [DRY-RUN] /*/bin/java -Djava.class.path=/* -Xmx64m org.scijava.parsington.Main (glob)

  $ ./parsy --dry-run --headless --heap 58m
  [DRY-RUN] /*/bin/java -Djava.awt.headless=true -Dapple.awt.UIElement=true -Xmx58m -Djava.class.path=/* org.scijava.parsington.Main (glob)

  $ ./parsy --dry-run --headless --class-path /tmp/lions.jar:/tmp/tigers.jar
  [DRY-RUN] /*/bin/java -Djava.awt.headless=true -Dapple.awt.UIElement=true -Djava.class.path=/* -Xmx64m org.scijava.parsington.Main (glob)

  $ ./parsy --dry-run --headless --jar-path /tmp/jars:/tmp/other-jars
  [DRY-RUN] /*/bin/java -Djava.awt.headless=true -Dapple.awt.UIElement=true -Djava.class.path=/* -Xmx64m org.scijava.parsington.Main --jar-path /tmp/jars:/tmp/other-jars (glob)

  $ ./parsy --dry-run --headless --debugger 8765,suspend
  [DRY-RUN] /*/bin/java -Djava.awt.headless=true -Dapple.awt.UIElement=true -agentlib:jdwp=transport=dt_socket,server=y,address=localhost:8765,suspend -Djava.class.path=/* -Xmx64m org.scijava.parsington.Main (glob)

  $ ./parsy --dry-run --headless
  [DRY-RUN] /*/bin/java -Djava.awt.headless=true -Dapple.awt.UIElement=true -Djava.class.path=/* -Xmx64m org.scijava.parsington.Main (glob)

  $ ./parsy --dry-run --heap 58m --class-path /tmp/lions.jar:/tmp/tigers.jar
  [DRY-RUN] /*/bin/java -Xmx58m -Djava.class.path=/* org.scijava.parsington.Main (glob)

  $ ./parsy --dry-run --heap 58m --jar-path /tmp/jars:/tmp/other-jars
  [DRY-RUN] /*/bin/java -Xmx58m -Djava.class.path=/* org.scijava.parsington.Main --jar-path /tmp/jars:/tmp/other-jars (glob)

  $ ./parsy --dry-run --heap 58m --debugger 8765,suspend
  [DRY-RUN] /*/bin/java -Xmx58m -agentlib:jdwp=transport=dt_socket,server=y,address=localhost:8765,suspend -Djava.class.path=/* org.scijava.parsington.Main (glob)

  $ ./parsy --dry-run --heap 58m
  [DRY-RUN] /*/bin/java -Xmx58m -Djava.class.path=/* org.scijava.parsington.Main (glob)

  $ ./parsy --dry-run --class-path /tmp/lions.jar:/tmp/tigers.jar --jar-path /tmp/jars:/tmp/other-jars
  [DRY-RUN] /*/bin/java -Djava.class.path=/* -Xmx64m org.scijava.parsington.Main --jar-path /tmp/jars:/tmp/other-jars (glob)

  $ ./parsy --dry-run --class-path /tmp/lions.jar:/tmp/tigers.jar --debugger 8765,suspend
  [DRY-RUN] /*/bin/java -agentlib:jdwp=transport=dt_socket,server=y,address=localhost:8765,suspend -Djava.class.path=/*:/tmp/lions.jar:/tmp/tigers.jar -Xmx64m org.scijava.parsington.Main (glob)

  $ ./parsy --dry-run --class-path /tmp/lions.jar:/tmp/tigers.jar
  [DRY-RUN] /*/bin/java -Djava.class.path=/*:/tmp/lions.jar:/tmp/tigers.jar -Xmx64m org.scijava.parsington.Main (glob)

  $ ./parsy --dry-run --jar-path /tmp/jars:/tmp/other-jars --debugger 8765,suspend
  [DRY-RUN] /*/bin/java -agentlib:jdwp=transport=dt_socket,server=y,address=localhost:8765,suspend -Djava.class.path=/* -Xmx64m org.scijava.parsington.Main --jar-path /tmp/jars:/tmp/other-jars (glob)

  $ ./parsy --dry-run --jar-path /tmp/jars:/tmp/other-jars
  [DRY-RUN] /*/bin/java -Djava.class.path=/* -Xmx64m org.scijava.parsington.Main --jar-path /tmp/jars:/tmp/other-jars (glob)

  $ ./parsy --dry-run --debugger 8765,suspend
  [DRY-RUN] /*/bin/java -agentlib:jdwp=transport=dt_socket,server=y,address=localhost:8765,suspend -Djava.class.path=/* -Xmx64m org.scijava.parsington.Main (glob)
