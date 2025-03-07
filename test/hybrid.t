Setup:

  $ cd "$TESTDIR/../demo"
  $ cp "$TESTDIR/hybrid.toml" ./jaunch
  $ cp hi-linux-x64 hybrid-linux-x64

Tests:
  $ ./hybrid-linux-x64
  [DRY-RUN] *python /*/demo/main-script.py /*/libjvm.so -Djava.class.path=/*/demo/lib/jython-*.jar:/*/demo/lib/parsington-*.jar -Xmx57m -- org.apposed.jaunch.MainJavaProgram jvm-main-arg-1 jvm-main-arg-2 (glob)
