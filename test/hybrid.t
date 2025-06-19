Setup:

  $ . "$TESTDIR/common.include"
  $ cd "$TESTDIR/../demo"
  $ cp "$TESTDIR/hybrid.toml" ./jaunch
  $ cp hi-$os-$arch hybrid-$os-$arch

Tests:
  $ ./hybrid-$os-$arch
  \[DRY-RUN\] .*/python[^ /]* .*/demo/main-script.py .*/(libjvm.so|libjli.dylib|jvm.dll) -Djava.class.path=[^ :]*/demo/lib/jython-[^ :]*.jar:[^ :]*/demo/lib/parsington-[^ :]*.jar -Xmx57m -- org.apposed.jaunch.MainJavaProgram jvm-main-arg-1 jvm-main-arg-2 (re)
