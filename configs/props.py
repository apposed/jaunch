#!/usr/bin/env python
"""
Python property extractor for Jaunch.

Outputs configuration values that Jaunch uses to reason about the Python
installation, including the Python shared library path and other metadata.
"""

import sys
import sysconfig

from pathlib import Path


def guess_libpython_path():
    """Tries to discern the path to the Python shared library."""
    if hasattr(sys, "dllhandle"):
        # Windows: use sys.dllhandle + GetModuleFileName.
        import ctypes
        from ctypes.wintypes import HMODULE, LPWSTR, DWORD
        GetModuleFileNameW = ctypes.windll.kernel32.GetModuleFileNameW
        GetModuleFileNameW.argtypes = [HMODULE, LPWSTR, DWORD]
        GetModuleFileNameW.restype = DWORD
        buf = ctypes.create_unicode_buffer(260)
        GetModuleFileNameW(sys.dllhandle, buf, 260)
        return str(buf.value)
    else:
        # POSIX: use sysconfig metadata.
        libdir = sysconfig.get_config_var("LIBDIR")
        ldlibrary = sysconfig.get_config_var("LDLIBRARY")

        if "Python.framework" in str(ldlibrary):
            # Homebrew/framework: use PYTHONFRAMEWORKPREFIX.
            framework_prefix = sysconfig.get_config_var("PYTHONFRAMEWORKPREFIX")
            if framework_prefix:
                return str(Path(framework_prefix, ldlibrary))
            # Fallback: try to construct from prefix.
            prefix = sysconfig.get_config_var("prefix")
            if prefix and "Frameworks/Python.framework" in prefix:
                base = prefix.split("/Frameworks/Python.framework")[0]
                return str(Path(f"{base}/Frameworks", ldlibrary))

        elif libdir and ldlibrary:
            p = Path(libdir, ldlibrary)
            if p.suffix == ".a":
                # HACK: Work around Conda envs reporting
                # the static lib over the shared lib.
                for ext in [".dylib", ".so"]:
                    candidate = p.with_suffix(ext)
                    if candidate.exists():
                        return str(candidate)
            return str(p)

        return None


props = {
    "jaunch.libpython_path": guess_libpython_path(),
    "sys.executable": sys.executable,
    "sys.version": sys.version,
    "sys.prefix": sys.prefix,
}
props.update({f"paths.{k}": v for k, v in sysconfig.get_paths().items()})
props.update({f"cvars.{k}": v for k, v in sysconfig.get_config_vars().items()})

if len(sys.argv) > 1:
    # Generalize machine-specific paths.
    prefix = sys.prefix
    home = str(Path().home())
    version = props["cvars.py_version"]
    vshort = props["cvars.py_version_short"]
    vnodot = props["cvars.py_version_nodot"]
    for k, v in props.items():
        if not isinstance(v, str): continue
        v = v.replace(prefix, "${PREFIX}")
        v = v.replace(home, "${HOME}")
        if not k.startswith("cvars.py_version"):
            if version: v = v.replace(version, "${VERSION}")
            if vshort: v = v.replace(vshort, "${SPEC}")
            if vnodot: v = v.replace(vnodot, "${NODOT}")
        props[k] = v

for k, v in props.items():
    print(f"{k}={v}")
