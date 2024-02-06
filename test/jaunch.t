Setup:

  $ cd "$TESTDIR/../app"

Now test stuff.

  $ ./jaunch/jaunch-linux-x64
  Hello! You have found the Jaunch configurator.
  Your curiosity is an asset. :-)
  
  This program is intended to be called internally by Jaunch's native
  launcher executable. Normally, you do not need to run it yourself.
  
  However, if you wish, you can test its behavior by passing command line
  arguments in the same manner as you would to Jaunch's native launcher,
  prepended by the name of the native launcher executable.
  
  For example, if your native launcher is called fizzbuzz, you could try:
  
      jaunch fizzbuzz --heap 2g --debugger 8000
  
  and watch how Jaunch transforms the arguments.
  
  You can learn similar information using Jaunch's --dry-run option:
  
      fizzbuzz --heap 2g --debugger 8000 --dry-run
  
  For more details, check out the jaunch.toml file. Happy Jaunching!
  [1]

  $ jaunch/jaunch-linux-x64 parsy a b c
  LAUNCH
  /*/libjvm.so (glob)
  2
  -Djava.class.path=/*/lib/parsington-3.1.0.jar* (glob)
  -Xmx128m
  org/scijava/parsington/Main
  3
  a
  b
  c
