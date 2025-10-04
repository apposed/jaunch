This folder contains property dumps from Java and Python installations of
various sorts. Naming scheme: `<runtime>-<os>-<arch>-<flavor>-<version>.txt`
Contents are the results of that runtime's property dumping mechanism:

* For Java: `java Props5 x`
* For Python: `python props.py x`

In all properties, certain substring occurrences are replaced with placeholders:

* `${PREFIX}`: the environment's base directory / installation prefix
* `${PWD}`: the current working directory
* `${HOME}`: the user's home directory (e.g. `/Users/chuckles`)
* `${USER}`: the user's username (e.g. `chuckles`)
* `${VERSION}`: the runtime's full version (e.g. `3.13.7` or `11.0.28`)
* `${SPEC}`: the runtime's specification version (e.g. `3.13` for Python, `21` for Java)
* `${NODOT}`: the runtime's no-dot version (Python only; e.g. `313`)

Finally, for consistency, the output is sorted with `LC_ALL=C sort`.

The goal of this postprocessing is to make diffs between the files less noisy.
