Integration test for several runtime launch directives in sequence.
Pre-requisites: run `make clean demo` in the root directory

Setup:

  $ . "$TESTDIR/common.include"
  $ cd "$TESTDIR/../demo"
  $ cp -rp jaunch jaunch.original
  $ rm jaunch/jaunch*
  $ cp ../test/sequence.sh jaunch/jaunch

Test complex sequence of hardcoded directives involving Java AWT:

  $ ./hi
  Hello, 1-JVM-main!
  Hello, 2-PYTHON-main!
  Error creating Java Virtual Machine
  [3]

Cleanup:

  $ rm -rf jaunch
  $ mv jaunch.original jaunch
