## Using Jaunch as your application launcher

1. Use `make dist` to [build from source](BUILD.md).

2. Copy the `dist/launcher`/`dist\launcher.exe` native launcher to your
   application distribution root, renaming it to match your desired naming.
   For example, if your application is called Fizzbuzz, you might name it
   `fizzbuzz`/`fizzbuzz.exe`.

3. Create a directory beneath your application root, into which your application's
   Jaunch TOML configuration will be stored. This configuration directory can be
   named `jaunch`, `.jaunch`, `config/jaunch`, or `.config/jaunch`, depending on
   your requirements and taste. Or you can customize the allowed configuration
   directory names by editing the `JAUNCH_SEARCH_PATHS` list in
   [jaunch.c](src/c/jaunch.c) and matching `configDirs` list in
   [main.kt](src/commonMain/kotlin/main.kt).

4. Copy the TOML files from the `dist/jaunch` folder to your application
   distribution's configuration folder, as created in (3).

5. Rename the `launcher.toml` file to match your launcher name from (2)
   (e.g. `fizzbuzz.toml`).

6. Edit the renamed TOML file's contents to set the parameters for your
   application. See the comments in `common.toml` for detailed guidance.
   If you don't need the JVM, you can remove the JVM-specific parts.
   If you don't need Python, you can remove the Python-specific parts.
