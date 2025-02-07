On Windows, in Git Bash, the Jython console autodetection does not work. It behaves in file-reading mode unless `-i` is passed. Not an issue with Jaunch, since the same happens when launched via `java`.

On Windows, the repl app hangs after `exit()`/`quit()`; see repl-windows-hang.txt for thread dump.

On Windows, the dist folder's `launcher.bat` and `launcher.sh` scripts do not work out of the box, due to the `-console.exe` / `-gui.exe` dichotomy. (Maybe this is OK though...)
