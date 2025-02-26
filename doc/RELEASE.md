This document describes how maintainers *make a release of Jaunch*.

* To *use Jaunch as your application launcher*, see [SETUP.md](SETUP.md).
* To *build Jaunch from source*, see [BUILD.md](BUILD.md).
* To *play with Jaunch's demo apps*, see [EXAMPLES.md](EXAMPLES.md).

## Steps to release

On a Linux x64 system with push rights to Jaunch, clone the repository and jump in:
```shell
git clone git@github.com:apposed/jaunch
cd jaunch
bin/release.sh
```

The script will walk you through the entire release process.

Note that there are several manual steps which the script will prompt you to
perform, including code-signing on macOS and Windows machines. Configuring
these platforms for code-signing requires time (approximately several days)
and money (approximately $150 or €150), so please go through this setup process
beforehand and verify that all works before engaging the `release.sh` sequence.

Alternately, you can skip the code-signing step (just press ENTER right away)
and the final release archive will simply contain unsigned binaries. But in
that case, Jaunch will not work immediately on fresh macOS or Windows systems.
