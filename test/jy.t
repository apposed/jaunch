Setup:

  $ cd "$TESTDIR/../app"

Test that the correct Java program actually runs.

  $ ./jy-linux-x64 -c 'print(1+2)'
  3

Test system property assignment.

  $ ./jy-linux-x64 -Dcake=chocolate -c 'from java.lang import System; print(System.getProperty("cake"))'
  chocolate

Test divider symbol handling.

  $ ./jy-linux-x64 -- --dry-run 2>&1
  Unknown option: -- or '--dry-run'
  usage: jython [option] ... [-c cmd | -m mod | file | -] [arg] ...
  Try 'jython -h' for more information.
  [1]

  $ ./jy-linux-x64 --dry-run --
  /*/bin/java -Dpython.import.site=false -Dpython.cachedir.skip=true -Dpython.console.encoding=UTF-8 -Djava.class.path=/*/lib/jython-2.7.3.jar* -Xmx* org.python.util.jython (glob)

  $ ./jy-linux-x64 --dry-run -Dfoo=before -- -Dfoo=after
  /*/bin/java -Dfoo=before -Dpython.import.site=false -Dpython.cachedir.skip=true -Dpython.console.encoding=UTF-8 -Djava.class.path=/*/lib/jython-2.7.3.jar* -Xmx* org.python.util.jython -Dfoo=after (glob)

  $ ./jy-linux-x64 -Dfoo=before -- -Dfoo=after -c 'from java.lang import System; print(System.getProperty("foo"))'
  after

  $ ./jy-linux-x64 bad -- good 2>&1 | head -n1
  kotlin.IllegalStateException: Unrecognized JVM argument: bad

Test command line argument combinations.

  $ ./jy-linux-x64 --help
  Usage: ./jy-linux-x64 [<Java options>.. --] [<main arguments>..]
  
  Jy launcher (Jaunch v* / * / *) (glob)
  Java options are passed to the Java Runtime,
  main arguments to the launched program (Jy).
  
  In addition, the following options are supported:
  --help, -h
                      show this help
  --dry-run
                      show the command line, but do not run anything
  --info
                      informational output
  --debug
                      verbose output
  --system
                      do not try to run bundled Java
  --java-home <path>
                      specify JAVA_HOME explicitly
  --print-java-home
                      print path to the selected Java
  --print-java-info
                      print information about the selected Java
  --print-app-dir
                      print directory where the application is located
  --headless
                      run in text mode
  --heap, --mem, --memory <amount>
                      set Java's heap size to <amount> (e.g. 512M)
  --class-path, --classpath, -classpath, --cp, -cp <path>
                      append <path> to the class path
  --jar-path, --jarpath, -jarpath <path>
                      append .jar files in <path> to the class path
  --ext <path>
                      set Java's extension directory to <path>
  --debugger <port>[,suspend]
                      start Java in a mode so an IDE/debugger can attach to it

  $ ./jy-linux-x64 --print-java-home
  /* (glob)

  $ ./jy-linux-x64 --print-java-info 2>&1 | grep -v '^\* \(IMPLEMENTOR\|java\.\|jdk\.\|sun\.\|user\.\)' | LC_ALL=C sort
  \* JAVA_VERSION=* (glob)
  * OS_ARCH=amd64
  * OS_NAME=Linux
  \* OS_VERSION=* (glob)
  \* SOURCE=* (glob)
  * awt.toolkit=sun.awt.X11.XToolkit
  * file.encoding.pkg=sun.io
  \* file.encoding=* (glob)
  * file.separator=/
  * line.separator=
  * os.arch=amd64
  * os.name=Linux
  \* os.version=* (glob)
  * path.separator=:
  CPU arch: X64
  OS name: LINUX
  distro: * (glob)
  libjvm: /*/libjvm.so (glob)
  release file:
  root: /* (glob)
  system properties:
  version: * (glob)

  $ ./jy-linux-x64 --dry-run --print-app-dir
  /*/bin/java -Dpython.import.site=false -Dpython.cachedir.skip=true -Dpython.console.encoding=UTF-8 -Djava.class.path=/*/lib/jython-2.7.3.jar* -Xmx* org.python.util.jython (glob)

  $ ./jy-linux-x64 --dry-run --system
  /*/bin/java -Dpython.import.site=false -Dpython.cachedir.skip=true -Dpython.console.encoding=UTF-8 -Djava.class.path=/*/lib/jython-2.7.3.jar* -Xmx* org.python.util.jython (glob)

  $ ./jy-linux-x64 --dry-run --java-home /usr/lib/jvm/default-java
  /*/bin/java -Dpython.import.site=false -Dpython.cachedir.skip=true -Dpython.console.encoding=UTF-8 -Djava.class.path=/*/lib/jython-2.7.3.jar* -Xmx* org.python.util.jython (glob)

  $ ./jy-linux-x64 --dry-run --headless
  /*/bin/java -Dpython.import.site=false -Dpython.cachedir.skip=true -Dpython.console.encoding=UTF-8 -Djava.awt.headless=true -Dapple.awt.UIElement=true -Djava.class.path=/*/lib/jython-2.7.3.jar* -Xmx* org.python.util.jython (glob)

  $ ./jy-linux-x64 --dry-run --heap 58m
  /*/bin/java -Dpython.import.site=false -Dpython.cachedir.skip=true -Dpython.console.encoding=UTF-8 -Xmx58m -Djava.class.path=/*/lib/jython-2.7.3.jar* org.python.util.jython (glob)

  $ ./jy-linux-x64 --dry-run --class-path /tmp/lions.jar:/tmp/tigers.jar
  /*/bin/java -Dpython.import.site=false -Dpython.cachedir.skip=true -Dpython.console.encoding=UTF-8 -Djava.class.path=/*/lib/jython-2.7.3.jar* -Xmx* org.python.util.jython (glob)

  $ ./jy-linux-x64 --dry-run --jar-path /tmp/jars:/tmp/other-jars
  /*/bin/java -Dpython.import.site=false -Dpython.cachedir.skip=true -Dpython.console.encoding=UTF-8 -Djava.class.path=/*/lib/jython-2.7.3.jar* -Xmx* org.python.util.jython (glob)

  $ ./jy-linux-x64 --dry-run --debugger 8765,suspend
  /*/bin/java -Dpython.import.site=false -Dpython.cachedir.skip=true -Dpython.console.encoding=UTF-8 -agentlib:jdwp=transport=dt_socket,server=y,address=localhost:8765,suspend -Djava.class.path=/*/lib/jython-2.7.3.jar* -Xmx* org.python.util.jython (glob)

  $ ./jy-linux-x64 --dry-run
  /*/bin/java -Dpython.import.site=false -Dpython.cachedir.skip=true -Dpython.console.encoding=UTF-8 -Djava.class.path=/*/lib/jython-2.7.3.jar* -Xmx* org.python.util.jython (glob)

  $ ./jy-linux-x64 --dry-run --system --java-home /usr/lib/jvm/default-java
  /*/bin/java -Dpython.import.site=false -Dpython.cachedir.skip=true -Dpython.console.encoding=UTF-8 -Djava.class.path=/*/lib/jython-2.7.3.jar* -Xmx* org.python.util.jython (glob)

  $ ./jy-linux-x64 --dry-run --system --headless
  /*/bin/java -Dpython.import.site=false -Dpython.cachedir.skip=true -Dpython.console.encoding=UTF-8 -Djava.awt.headless=true -Dapple.awt.UIElement=true -Djava.class.path=/*/lib/jython-2.7.3.jar* -Xmx* org.python.util.jython (glob)

  $ ./jy-linux-x64 --dry-run --system --heap 58m
  /*/bin/java -Dpython.import.site=false -Dpython.cachedir.skip=true -Dpython.console.encoding=UTF-8 -Xmx58m -Djava.class.path=/*/lib/jython-2.7.3.jar* org.python.util.jython (glob)

  $ ./jy-linux-x64 --dry-run --system --class-path /tmp/lions.jar:/tmp/tigers.jar
  /*/bin/java -Dpython.import.site=false -Dpython.cachedir.skip=true -Dpython.console.encoding=UTF-8 -Djava.class.path=/*/lib/jython-2.7.3.jar* -Xmx* org.python.util.jython (glob)

  $ ./jy-linux-x64 --dry-run --system --jar-path /tmp/jars:/tmp/other-jars
  /*/bin/java -Dpython.import.site=false -Dpython.cachedir.skip=true -Dpython.console.encoding=UTF-8 -Djava.class.path=/*/lib/jython-2.7.3.jar* -Xmx* org.python.util.jython (glob)

  $ ./jy-linux-x64 --dry-run --system --debugger 8765,suspend
  /*/bin/java -Dpython.import.site=false -Dpython.cachedir.skip=true -Dpython.console.encoding=UTF-8 -agentlib:jdwp=transport=dt_socket,server=y,address=localhost:8765,suspend -Djava.class.path=/*/lib/jython-2.7.3.jar* -Xmx* org.python.util.jython (glob)

  $ ./jy-linux-x64 --dry-run --system
  /*/bin/java -Dpython.import.site=false -Dpython.cachedir.skip=true -Dpython.console.encoding=UTF-8 -Djava.class.path=/*/lib/jython-2.7.3.jar* -Xmx* org.python.util.jython (glob)

  $ ./jy-linux-x64 --dry-run --java-home /usr/lib/jvm/default-java --headless
  /usr/lib/jvm/default-java/bin/java -Dpython.import.site=false -Dpython.cachedir.skip=true -Dpython.console.encoding=UTF-8 -Djava.awt.headless=true -Dapple.awt.UIElement=true -Djava.class.path=/*/lib/jython-2.7.3.jar* -Xmx* org.python.util.jython (glob)

  $ ./jy-linux-x64 --dry-run --java-home /usr/lib/jvm/default-java --heap 58m
  /usr/lib/jvm/default-java/bin/java -Dpython.import.site=false -Dpython.cachedir.skip=true -Dpython.console.encoding=UTF-8 -Xmx58m -Djava.class.path=/*/lib/jython-2.7.3.jar* org.python.util.jython (glob)

  $ ./jy-linux-x64 --dry-run --java-home /usr/lib/jvm/default-java --class-path /tmp/lions.jar:/tmp/tigers.jar
  /usr/lib/jvm/default-java/bin/java -Dpython.import.site=false -Dpython.cachedir.skip=true -Dpython.console.encoding=UTF-8 -Djava.class.path=/*/lib/jython-2.7.3.jar* -Xmx* org.python.util.jython (glob)

  $ ./jy-linux-x64 --dry-run --java-home /usr/lib/jvm/default-java --jar-path /tmp/jars:/tmp/other-jars
  /usr/lib/jvm/default-java/bin/java -Dpython.import.site=false -Dpython.cachedir.skip=true -Dpython.console.encoding=UTF-8 -Djava.class.path=/*/lib/jython-2.7.3.jar* -Xmx* org.python.util.jython (glob)

  $ ./jy-linux-x64 --dry-run --java-home /usr/lib/jvm/default-java --debugger 8765,suspend
  /usr/lib/jvm/default-java/bin/java -Dpython.import.site=false -Dpython.cachedir.skip=true -Dpython.console.encoding=UTF-8 -agentlib:jdwp=transport=dt_socket,server=y,address=localhost:8765,suspend -Djava.class.path=/*/lib/jython-2.7.3.jar* -Xmx* org.python.util.jython (glob)

  $ ./jy-linux-x64 --dry-run --java-home /usr/lib/jvm/default-java
  /usr/lib/jvm/default-java/bin/java -Dpython.import.site=false -Dpython.cachedir.skip=true -Dpython.console.encoding=UTF-8 -Djava.class.path=/*/lib/jython-2.7.3.jar* -Xmx* org.python.util.jython (glob)

  $ ./jy-linux-x64 --dry-run --headless --heap 58m
  /*/bin/java -Dpython.import.site=false -Dpython.cachedir.skip=true -Dpython.console.encoding=UTF-8 -Djava.awt.headless=true -Dapple.awt.UIElement=true -Xmx58m -Djava.class.path=/*/lib/jython-2.7.3.jar* org.python.util.jython (glob)

  $ ./jy-linux-x64 --dry-run --headless --class-path /tmp/lions.jar:/tmp/tigers.jar
  /*/bin/java -Dpython.import.site=false -Dpython.cachedir.skip=true -Dpython.console.encoding=UTF-8 -Djava.awt.headless=true -Dapple.awt.UIElement=true -Djava.class.path=/*/lib/jython-2.7.3.jar* -Xmx* org.python.util.jython (glob)

  $ ./jy-linux-x64 --dry-run --headless --jar-path /tmp/jars:/tmp/other-jars
  /*/bin/java -Dpython.import.site=false -Dpython.cachedir.skip=true -Dpython.console.encoding=UTF-8 -Djava.awt.headless=true -Dapple.awt.UIElement=true -Djava.class.path=/*/lib/jython-2.7.3.jar* -Xmx* org.python.util.jython (glob)

  $ ./jy-linux-x64 --dry-run --headless --debugger 8765,suspend
  /*/bin/java -Dpython.import.site=false -Dpython.cachedir.skip=true -Dpython.console.encoding=UTF-8 -Djava.awt.headless=true -Dapple.awt.UIElement=true -agentlib:jdwp=transport=dt_socket,server=y,address=localhost:8765,suspend -Djava.class.path=/*/lib/jython-2.7.3.jar* -Xmx* org.python.util.jython (glob)

  $ ./jy-linux-x64 --dry-run --headless
  /*/bin/java -Dpython.import.site=false -Dpython.cachedir.skip=true -Dpython.console.encoding=UTF-8 -Djava.awt.headless=true -Dapple.awt.UIElement=true -Djava.class.path=/*/lib/jython-2.7.3.jar* -Xmx* org.python.util.jython (glob)

  $ ./jy-linux-x64 --dry-run --heap 58m --class-path /tmp/lions.jar:/tmp/tigers.jar
  /*/bin/java -Dpython.import.site=false -Dpython.cachedir.skip=true -Dpython.console.encoding=UTF-8 -Xmx58m -Djava.class.path=/*/lib/jython-2.7.3.jar* org.python.util.jython (glob)

  $ ./jy-linux-x64 --dry-run --heap 58m --jar-path /tmp/jars:/tmp/other-jars
  /*/bin/java -Dpython.import.site=false -Dpython.cachedir.skip=true -Dpython.console.encoding=UTF-8 -Xmx58m -Djava.class.path=/*/lib/jython-2.7.3.jar* org.python.util.jython (glob)

  $ ./jy-linux-x64 --dry-run --heap 58m --debugger 8765,suspend
  /*/bin/java -Dpython.import.site=false -Dpython.cachedir.skip=true -Dpython.console.encoding=UTF-8 -Xmx58m -agentlib:jdwp=transport=dt_socket,server=y,address=localhost:8765,suspend -Djava.class.path=/*/lib/jython-2.7.3.jar* org.python.util.jython (glob)

  $ ./jy-linux-x64 --dry-run --heap 58m
  /*/bin/java -Dpython.import.site=false -Dpython.cachedir.skip=true -Dpython.console.encoding=UTF-8 -Xmx58m -Djava.class.path=/*/lib/jython-2.7.3.jar* org.python.util.jython (glob)

  $ ./jy-linux-x64 --dry-run --class-path /tmp/lions.jar:/tmp/tigers.jar --jar-path /tmp/jars:/tmp/other-jars
  /*/bin/java -Dpython.import.site=false -Dpython.cachedir.skip=true -Dpython.console.encoding=UTF-8 -Djava.class.path=/*/lib/jython-2.7.3.jar* -Xmx* org.python.util.jython (glob)

  $ ./jy-linux-x64 --dry-run --class-path /tmp/lions.jar:/tmp/tigers.jar --debugger 8765,suspend
  /*/bin/java -Dpython.import.site=false -Dpython.cachedir.skip=true -Dpython.console.encoding=UTF-8 -agentlib:jdwp=transport=dt_socket,server=y,address=localhost:8765,suspend -Djava.class.path=/*/lib/jython-2.7.3.jar* -Xmx* org.python.util.jython (glob)

  $ ./jy-linux-x64 --dry-run --class-path /tmp/lions.jar:/tmp/tigers.jar
  /*/bin/java -Dpython.import.site=false -Dpython.cachedir.skip=true -Dpython.console.encoding=UTF-8 -Djava.class.path=/*/lib/jython-2.7.3.jar* -Xmx* org.python.util.jython (glob)

  $ ./jy-linux-x64 --dry-run --jar-path /tmp/jars:/tmp/other-jars --debugger 8765,suspend
  /*/bin/java -Dpython.import.site=false -Dpython.cachedir.skip=true -Dpython.console.encoding=UTF-8 -agentlib:jdwp=transport=dt_socket,server=y,address=localhost:8765,suspend -Djava.class.path=/*/lib/jython-2.7.3.jar* -Xmx* org.python.util.jython (glob)

  $ ./jy-linux-x64 --dry-run --jar-path /tmp/jars:/tmp/other-jars
  /*/bin/java -Dpython.import.site=false -Dpython.cachedir.skip=true -Dpython.console.encoding=UTF-8 -Djava.class.path=/*/lib/jython-2.7.3.jar* -Xmx* org.python.util.jython (glob)

  $ ./jy-linux-x64 --dry-run --debugger 8765,suspend
  /*/bin/java -Dpython.import.site=false -Dpython.cachedir.skip=true -Dpython.console.encoding=UTF-8 -agentlib:jdwp=transport=dt_socket,server=y,address=localhost:8765,suspend -Djava.class.path=/*/lib/jython-2.7.3.jar* -Xmx* org.python.util.jython (glob)

