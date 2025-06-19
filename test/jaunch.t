General tests for Jaunch configurator
Pre-requisites: run `make clean demo` in the root directory

Setup:

  $ . "$TESTDIR/common.include"
  $ cd "$TESTDIR/../demo"

Test 1: help text

  $ ./jaunch/jaunch-$os-$arch
  
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
  If the behavior is not what you expect, try using the --debug flag:
  
      jaunch fizzbuzz --heap 2g --debugger 8000 --debug
  
  You can also see similar information using Jaunch's --dry-run option:
  
      fizzbuzz --heap 2g --debugger 8000 --dry-run
  
  For more details, check out the nearby TOML files. Happy Jaunching!
  
  [1]
--End Test 1 expected output--

Test 2: using jaunch to launch an absent application

  $ ./jaunch/jaunch-$os-$arch missing a b c
  ERROR
  2
  20
  No config file found for missing
  [20]
--End of Test 2 expected output--

Test 3: use jaunch configurator manually

  $ ./jaunch/jaunch-$os-$arch parsy a b c
  JVM
  8
  .*/(libjvm.so|libjli.dylib|jvm.dll) (re)
  2
  -Djava.class.path=* (glob)
  -Xmx64m
  org/scijava/parsington/Main
  a
  b
  c
--End of Test 3 expected output--
