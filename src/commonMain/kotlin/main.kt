import platform.posix.exit

@Suppress("ArrayInDataClass")
data class JaunchOption(
    val flags: Array<String>,
    val assignment: String?,
    val help: String?,
) {
    fun help(): String = buildString {
        val indent = "$NL                    "
        append(flags.joinToString(", "))
        assignment?.let { append(" $it") }
        help?.let { append("$indent${it.replace("\n", indent)}") }
    }
}

typealias JaunchOptions = Map<String, JaunchOption>

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        // Display usage message.
        printlnErr("""
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

            You can learn similar information using Jaunch's --dry-run option:

                fizzbuzz --heap 2g --debugger 8000 --dry-run

            For more details, check out the jaunch.toml file. Happy Jaunching!
        """.trimIndent())
        exit(1)
    }

    // If a sole `-` argument was given on the CLI, read the arguments from stdin.
    val theArgs = if (args.size == 1 && args[0] == "-") stdinLines() else args

    // The first argument is the path to the calling executable.
    val executable = theArgs.getOrNull(0)

    // Subsequent arguments were specified by the user.
    val inputArgs = theArgs.slice(1..<theArgs.size)

    // Enable debug mode when --debug flag is present.
    debugMode = inputArgs.contains("--debug")

    debug("executable -> ", executable ?: "<null>")
    debug("inputArgs -> ", inputArgs)

    // Discern the application base directory.
    val exeFile = executable?.let(::File) // The native launcher program.
    // Check for native launcher in Contents/MacOS directory.
    // If so, treat the app directory as two directories higher up.
    // We do it this way, rather than OS_NAME == "MACOSX", so that the native
    // launcher also works on macOS when placed directly in the app directory.
    val exeDir = exeFile?.dir ?: File(".")
    val appDir = if (exeDir.name == "MacOS" && exeDir.dir.name == "Contents") exeDir.dir.dir else exeDir
    debug("appDir -> ", appDir)

    // Find the configuration directory.
    // NB: This list should match the JAUNCH_SEARCH_PATHS array in jaunch.c.
    val configDirs = listOf(
        appDir / "jaunch",
        appDir / ".jaunch",
        appDir / "config" / "jaunch",
        appDir / ".config" / "jaunch"
    )
    val configDir = configDirs.find { it.isDirectory } ?:
        error("Jaunch config directory not found. Please place config in one of: $configDirs")
    debug("configDir -> ", configDir)

    // Make a list of relevant configuration files to read.
    val osName = OS_NAME.lowercase()
    val cpuArch = CPU_ARCH.lowercase()
    var config = JaunchConfig()
    val configFiles = mutableListOf(
        configDir / "jaunch.toml",
        configDir / "jaunch-$osName.toml",
        configDir / "jaunch-$osName-$cpuArch.toml",
    )
    if (exeFile != null) {
        // Include the app-specific config file(s) as well.
        val index = configFiles.size
        var fileName = exeFile.base.name
        while (true) {
            configFiles.add(index, configDir / "$fileName.toml")
            val dash = fileName.lastIndexOf("-")
            if (dash < 0) break
            fileName = fileName.substring(0, dash)
        }
    }
    // Read and merge all the config files.
    for (configFile in configFiles) config += readConfig(configFile)

    val programName = config.programName ?: exeFile?.base?.name ?: "Jaunch"
    debug("programName -> ", programName)

    // Parse the configuration's declared Jaunch options.
    //
    // For each option, we have a string of the form:
    //   --alpha,...,--omega=assignment|Description of what this option does.
    //
    // We need to parse out the help text, the actual flags list, and whether the flags expect an assignment value.
    debug()
    debug("Parsing supported options...")
    val supportedOptions: JaunchOptions = buildMap {
        for (optionLine in config.supportedOptions) {
            val (optionString, help) = optionLine bisect '|'
            val (flagsString, assignment) = optionString bisect '='
            val flags = flagsString.split(',')
            val option = JaunchOption(flags.toTypedArray(), assignment, help)
            debug("* ", option)
            for (flag in flags) this[flag] = option
        }
    }

    // Declare a set of active hints, for activation of configuration elements.
    // Initially populated with hints for the current operating system and CPU architecture,
    // but it will grow over the course of the configuration process below.
    val hints = mutableSetOf(
        // Kotlin knows these operating systems:
        //   UNKNOWN, MACOSX, IOS, LINUX, WINDOWS, ANDROID, WASM, TVOS, WATCHOS
        "OS:$OS_NAME",
        // Kotlin knows these CPU architectures:
        //   UNKNOWN, ARM32, ARM64, X86, X64, MIPS32, MIPSEL32, WASM32
        "ARCH:$CPU_ARCH"
    )

    // Declare a set to store option parameter values.
    // It will be populated at argument parsing time.
    val vars = mutableMapOf(
        // Special variable containing application directory path.
        "app-dir" to appDir.path,
        // Special variable containing config directory path.
        "config-dir" to configDir.path,
    )
    if (exeFile?.exists == true) vars["executable"] = exeFile.path

    // Define the list of supported runtimes.
    val runtimes = listOf(
        JvmRuntimeConfig(config.jvmRecognizedArgs),
    )

    // Parse the configurator's input arguments.
    //
    // An input argument matching one of Jaunch's supported options becomes an active hint.
    // So e.g. `--foo` would be added to the hints set as `--foo`.
    //
    // A matching input argument that takes a parameter not only adds the option as a hint,
    // but also adds the parameter value to the vars map.
    // So e.g. `--sigmas=5` would add the `--sigma` hint, and add `"5"` as the value for the `sigma` variable.
    // Variable values will be interpolated into argument strings later in the configuration process.
    //
    // Non-matching input arguments will be passed through directly, either to the runtime or to the main program.
    // The exact behavior will depend on whether the `--` separator was provided in the input argument list.
    val divider = inputArgs.indexOf("--")
    var i = 0
    while (i < inputArgs.size) {
        val arg = inputArgs[i++]

        // Check for the --option=value kind of argument.
        val equals = arg.indexOf("=")
        val argKey = if (equals >= 0) arg.substring(0, equals) else arg
        val argVal = if (equals >= 0) arg.substring(equals + 1) else null

        if (argKey == "--") {
            if (argVal != null) error("Divider symbol (--) does not accept a parameter")
            if (i - 1 != divider) error("Divider symbol (--) may only be given once")
        }
        else if ((divider < 0 || i <= divider) && argKey in supportedOptions) {
            // The argument is declared in Jaunch's configuration. Deal with it appropriately.
            val option: JaunchOption = supportedOptions[argKey]!!
            if (option.assignment == null) {
                // standalone option
                if (argVal != null) error("Option $argKey does not accept a parameter")
            }
            else {
                // option with value assignment
                val v = argVal ?: if (i < inputArgs.size) inputArgs[i++] else
                    error("No parameter value given for argument $argKey")

                // Normalize the argument to the primary flag on the comma-separated list.
                // Then strip leading dashes: --class-path becomes class-path, -v becomes v, etc.
                var varName = option.flags.first()
                while (varName.startsWith("-")) varName = varName.substring(1)

                // Store assignment value as a variable named after the normalized argument.
                // E.g. if the matching supported option is `--heap,--mem=<max>|Maximum heap size`,
                // and `--mem 52g` is given, it will store `"52g"` for the variable `heap`.
                vars[varName] = v
            }
            hints += argKey
        }
        else {
            // The argument is not a Jaunch one. Pass it through directly.
            if (divider < 0) {
                // No dash-dash divider was given, so we need to guess: is this a runtime arg, or a main arg?
                for (r in runtimes) {
                    (if (config.recognizes(argKey, r.recognizedArgs)) r.runtimeArgs else r.mainArgs) += arg
                }
            }
            else if (i <= divider) {
                // This argument is before the dash-dash divider, so must be treated as a runtime arg.
                for (r in runtimes) r.runtimeArgs += arg
            }
            else {
                // This argument is after the dash-dash divider, so we treat it as a main arg.
                for (r in runtimes) r.mainArgs += arg
            }
        }
    }

    debug()
    debug("Input arguments parsed:")
    debug("* hints -> ", hints)
    debug("* vars -> ", vars)
    for (r in runtimes) {
        debug("* ${r.name}.runtimeArgs -> ", r.runtimeArgs)
        debug("* ${r.name}.mainArgs -> ", r.mainArgs)
    }

    // Apply mode hints.
    for (mode in calculate(config.modes, hints, vars)) {
        if (mode.startsWith("!")) {
            // Negated mode expression: remove the mode hint.
            hints -= mode.substring(1)
        }
        else hints += mode
    }
    debug()
    debug("Modes applied:")
    debug("* hints -> ", hints)

    // Discover and configure runtime installations.
    for (r in runtimes) r.configure(configDir, config, hints, vars)

    // Discern directives to perform.
    val directives = calculate(config.directives, hints, vars).toSet()
    val launchDirectives = listOf(if (directives.isEmpty()) "JVM" else "STOP")

    debug()
    debug("Directives parsed:")
    debug("* directives -> ", directives)
    debug("* launchDirectives -> ", launchDirectives)

    val runtime: JvmRuntimeConfig = runtimes[0] // TODO: Actually decide based on directives.

    // Execute configurator-side directives.
    for (directive in directives) {
        when (directive) {
            "help" -> help(executable, programName, supportedOptions)
            "print-runtime-home" -> printlnErr(runtime.home())
            "print-runtime-info" -> printlnErr(runtime.info())
            "print-class-path" -> printlnErr(runtime.classpath())
            "dry-run" -> printlnErr(runtime.dryRun())
            else -> error("Invalid directive: $directive")
        }
    }

    if (launchDirectives[0] == "STOP") {
        println("STOP")
        println(0)
        return
    }

    val chosenRecognizedArgs = runtime.recognizedArgs
    val chosenRuntimeArgs = runtime.runtimeArgs

    // Validate runtime args.
    if (config.allowUnrecognizedArgs != true) {
        // Check that the computed runtime args are all valid.
        for (arg in chosenRuntimeArgs) {
            if (!config.recognizes(arg, chosenRecognizedArgs)) {
                error("Unrecognized ${runtime.name} argument: $arg")
            }
        }
    }

    // Emit final configuration.
    debug()
    debug("Emitting final configuration to stdout...")
    println(runtime.nativeConfig())
}

// -- Helper functions --

private infix fun String.bisect(delimiter: Char): Pair<String, String?> {
    val index = indexOf(delimiter)
    return if (index < 0) Pair(this, null) else Pair(substring(0, index), substring(index + 1))
}

// -- Directives --

private fun help(executable: String?, programName: String, supportedOptions: JaunchOptions) {
    val exeName = executable ?: "jaunch"
    printlnErr("Usage: $exeName [<Runtime options>.. --] [<main arguments>..]")
    printlnErr()
    printlnErr("$programName launcher (Jaunch v$JAUNCH_VERSION / $JAUNCH_BUILD / $BUILD_TARGET)")
    printlnErr("Runtime options are passed to the Java Runtime,")
    printlnErr("main arguments to the launched program ($programName).")
    printlnErr()
    printlnErr("In addition, the following options are supported:")
    val optionsUnique = linkedSetOf(*supportedOptions.values.toTypedArray())
    optionsUnique.forEach { printlnErr(it.help()) }
}
