This document describes how maintainers *make a release of Jaunch*.

* To *use Jaunch as your application launcher*, see [SETUP.md](SETUP.md).
* To *build Jaunch from source*, see [BUILD.md](BUILD.md).
* To *play with Jaunch's demo apps*, see [EXAMPLES.md](EXAMPLES.md).

## Steps to release

1. On a Linux x64 system with push rights to Jaunch, clone the repository and jump in:
   ```shell
   git clone git@github.com:apposed/jaunch
   cd jaunch
   ```

2. Run the `bin/release.sh` script and follow instructions.

3. Transfer the `jaunch-x.y.z.zip` release archive to a macOS computer configured for code-signing. Unpack it, sign the `Jaunch.app`
