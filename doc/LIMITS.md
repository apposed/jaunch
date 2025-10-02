## Limitations

### Runtimes

**Python:**
* Jaunch requires at least Python 3.8, because it uses the Python [Stable ABI]'s [`Py_BytesMain`] function, which was added in Python 3.8.

**Java:**
* Jaunch has been designed for, and tested with, OpenJDK 8, 11, 17, 21, and 25.
* It might work with earlier versions of Java, but it hasn't been tested.
* Jaunch has been tested with a variety of OpenJDK flavors; see `jvm.distros-allowed` in [jvm.toml](../configs/jvm.toml) for a list.

### Platforms

Jaunch is designed to work with arm64 and x86-64 CPU architectures.

The minimum operating system versions are:
* [Linux](LINUX.md) with glibc 2.34
* [macOS](MACOS.md) 11 "Big Sur"
* [Windows](WINDOWS.md) 10

Here are some Linux distributions that use glibc 2.34 or later:

| Distro         | Release       |
|----------------|---------------|
| Red Hat Family |               |
|----------------|---------------|
| RHEL           | 9.0           |
| CentOS         | Stream 9      |
| Rocky Linux    | 9.0           |
| AlmaLinux      | 9.0           |
| Fedora         | 35            |
|----------------|---------------|
| Debian Family  |               |
|----------------|---------------|
| Ubuntu         | 22.04 LTS     |
| Debian         | 12 "Bookworm" |
| Linux Mint     | 21            |
|----------------|---------------|
| SUSE Family    |               |
|----------------|---------------|
| openSUSE       | Leap 15.5     |
| SLES           | 15 SP5        |

See the operating system documentation links above for additional quirks and limitations relating to each specific platform.

------------------------------------------------------------------------------

[Stable ABI]: https://docs.python.org/3/c-api/stable.html#stable-abi
[`Py_BytesMain`]: https://docs.python.org/3/c-api/veryhigh.html#c.Py_BytesMain
