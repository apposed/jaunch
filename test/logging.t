Tests for logging launch details to a file
Pre-requisites: run `make clean demo` in the root directory

Setup:

  $ . "$TESTDIR/common.include"
  $ cd "$TESTDIR/../demo"

Test logging

  $ ./hi --debug 2>/dev/null
  Hello from Java!
  $ test "$(cat hi.log | wc -l)" -gt 100 && echo log-populated
  log-populated
  $ head -n15 hi.log
  [DEBUG] 
  [DEBUG] /--------------------------------------\
  [DEBUG] | PROCEEDING WITH JAUNCH CONFIGURATION |
  [DEBUG] \--------------------------------------/
  [DEBUG] executable -> */demo*/hi-* (glob)
  [DEBUG] internalFlags -> {target-arch=*} (glob)
  [DEBUG] inputArgs -> [--debug]
  [DEBUG] appDir -> */demo (glob)
  [DEBUG] configDir -> */demo/jaunch (glob)
  [DEBUG] Looking for config file: */demo/jaunch/hi-*-*.toml (glob)
  [DEBUG] Looking for config file: */demo/jaunch/hi-*.toml (glob)
  [DEBUG] Looking for config file: */demo/jaunch/hi.toml (glob)
  [DEBUG] Reading config file: */demo/jaunch/hi.toml (glob)
  [DEBUG] Reading config file: */demo/jaunch/jvm.toml (glob)
  [DEBUG] Reading config file: */demo/jaunch/common.toml (glob)
