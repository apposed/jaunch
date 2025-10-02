General tests for Java configuration, using the 'hi' Java program
Pre-requisites:
1. run `make clean demo` in the root directory
2. Ensure a suitable JVM is installed on the system

Setup:

  $ . "$TESTDIR/common.include"
  $ cd "$TESTDIR/../demo"

Tests:
  $ ./jaunch/jaunch-$os-$arch hi --print-java-home
  /* (glob)
  ABORT

This is highly variable with java version & build
  $ ./jaunch/jaunch-$os-$arch hi --print-java-info 2> /dev/null
  ABORT

Memory tests: Verify that all the aliases pass the heap size
  $ ./jaunch/jaunch-$os-$arch hi --print-class-path
  */jaunch/demo (glob)
  ABORT

  $ ./jaunch/jaunch-$os-$arch hi --heap 2g
  JVM
  5
  .*/(libjvm.so|libjli.dylib|jvm.dll) (re)
  2
  -Xmx2g
  -Djava.class.path=/* (glob)
  HelloWorld

  $ ./jaunch/jaunch-$os-$arch hi --mem 512m
  JVM
  5
  .*/(libjvm.so|libjli.dylib|jvm.dll) (re)
  2
  -Xmx512m
  -Djava.class.path=/* (glob)
  HelloWorld

  $ ./jaunch/jaunch-$os-$arch hi --memory 1g
  JVM
  5
  .*/(libjvm.so|libjli.dylib|jvm.dll) (re)
  2
  -Xmx1g
  -Djava.class.path=/* (glob)
  HelloWorld

Memory tests: Try running the app using percents with each alias
  $ ./hi --mem 50%
  Hello from Java!

  $ ./hi --memory 25%
  Hello from Java!

  $ ./hi --heap 73%
  Hello from Java!

Memory tests: Run the app to verify the invalid heap size
  $ ./hi --memory -2g
  Invalid maximum heap size: -Xmx-2g
  [ERROR] Failed to create the Java Virtual Machine
  [3]

Memory tests: use a .cfg file to override memory settings
  $ echo "cfg.max-heap = '50%'" >> jaunch/hi.toml
  $ echo "jvm.max-heap = '\${cfg.max-heap}'" >> jaunch/hi.toml
  $ ./hi --dry-run
  [DRY-RUN] /*/bin/java -Djava.class.path=/*/demo -Xmx* HelloWorld (glob)
  $ echo "max-heap=2g" > ./jaunch/hi.cfg
  $ ./hi --dry-run
  [DRY-RUN] /*/bin/java -Djava.class.path=/*/demo -Xmx2g HelloWorld (glob)
  $ echo "max-heap=4g" > ./jaunch/hi.cfg
  $ ./hi --dry-run
  [DRY-RUN] /*/bin/java -Djava.class.path=/*/demo -Xmx4g HelloWorld (glob)
  $ rm ./jaunch/hi.cfg
  $ rm ./jaunch/hi.toml
  $ cp ../configs/hi.toml ./jaunch/

Classpath tests: move our .class file and verify we broke things
  $ mv HelloWorld.class ..
  $ ./hi
  [ERROR] Failed to locate class HelloWorld
  [4]

Make sure each alias works now
  $ ./hi --class-path ..
  Hello from Java!
  $ ./hi --classpath ..
  Hello from Java!
  $ ./hi --cp ..
  Hello from Java!
  $ ./hi -classpath ..
  Hello from Java!
  $ ./hi -cp ..
  Hello from Java!

Move the .class file back
  $ mv ../HelloWorld.class .

  $ ./jaunch/jaunch-$os-$arch hi --ext . --dry-run
  [DRY-RUN] /*/bin/java -Djava.ext.dirs=. -Djava.class.path=/*/demo HelloWorld (glob)
  ABORT

Verify --headless would pass the correct flags
  $ ./jaunch/jaunch-$os-$arch hi --headless --dry-run
  [DRY-RUN] /*java -Djava.awt.headless=true -Dapple.awt.UIElement=true -Djava.class.path=/*/demo HelloWorld (glob)
  ABORT

Verify --debugger would pass the correct flags
  $ ./jaunch/jaunch-$os-$arch hi --debugger 8000 --dry-run
  [DRY-RUN] /*/bin/java -agentlib:jdwp=transport=dt_socket,server=y,address=localhost:8000 -Djava.class.path=/*/demo HelloWorld (glob)
  ABORT

For testing --java-home, we use an invalid path and just verify it's in the search space
  $ ./jaunch/jaunch-$os-$arch hi --java-home . --debug 2>&1 | grep -A1 "Root paths to search for Java"
  [DEBUG] Root paths to search for Java:
  [DEBUG] * .

Should also work with jvm-dir in hi.cfg
  $ echo "jvm-dir=." > ./jaunch/hi.cfg
  $ ./jaunch/jaunch-$os-$arch hi --debug 2>&1 | grep -A1 "Root paths to search for Java"
  [DEBUG] Root paths to search for Java:
  [DEBUG] * .
  $ rm ./jaunch/hi.cfg

System flag testing: make a fake "bundled" java directory and ensure it's
on the search path
  $  mkdir -p ./java/$os-$arch/fakejdk
  $ ./hi --debug 2>&1 | grep fakejdk
  \[DEBUG\] \* .*/java/[^/]*/fakejdk (re)
  \[DEBUG\] Analyzing candidate JVM directory: '.*/java/[^/]*/fakejdk' (re)
Running again with --system should skip this directory for searching
  $ ./hi --system --debug 2>&1 | grep fakejdk
  [1]

Cleanup:
  $ rm -rf ./java
