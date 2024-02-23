import platform.posix.exit
import kotlin.math.min

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
        // Special variable containing path to the application.
        "app-dir" to appDir.path,
    )
    if (exeFile?.exists == true) vars["executable"] = exeFile.path

    // Define the list of supported runtimes, for use as keys to data structures.
    // Must match the launch directives expected by the C-based native launcher.
    val recognizedArgs = mapOf(
        "PYTHON" to config.pythonRecognizedArgs,
        "JVM" to config.jvmRecognizedArgs,
    )
    val runtimes = recognizedArgs.keys

    // Declare the authoritative lists of runtime arguments and main arguments,
    // one pair of variables for each supported runtime.
    // At the end of the configuration process, we will emit these to stdout.
    val runtimeArgs = mutableMapOf<String, MutableList<String>>()
    val mainArgs = mutableMapOf<String, MutableList<String>>()
    for (runtime in runtimes) {
        runtimeArgs[runtime] = mutableListOf()
        mainArgs[runtime] = mutableListOf()
    }

    // Parse the configurator's input arguments.
    //
    // An input argument matching one of Jaunch's supported options becomes an active hint.
    // So e.g. `--foo` would be added to the hints set as `--foo`.
    //
    // Matching input arguments that take a parameter not only add the option as a hint,
    // but also add the parameter value to the vars map.
    // So e.g. `--sigmas=5` would add the `--sigma` hint, and add `"5"` as the value for the `sigma` variable.
    // Variable values will be interpolated into argument strings later in the configuration process.
    //
    // Non-matching input arguments will be passed through directly, either to the runtime or to the main program.
    // The exact behavior will depend on whether the `--` separator was provided.
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
                    (if (config.recognizes(argKey, recognizedArgs[r]!!)) runtimeArgs[r]!! else mainArgs[r]!!) += arg
                }
            }
            else if (i <= divider) {
                // This argument is before the dash-dash divider, so must be treated as a runtime arg.
                for (r in runtimes) runtimeArgs[r]!! += arg
            }
            else {
                // This argument is after the dash-dash divider, so we treat it as a main arg.
                for (r in runtimes) mainArgs[r]!! += arg
            }
        }
    }

    debug()
    debug("Input arguments parsed:")
    debug("* hints -> ", hints)
    debug("* vars -> ", vars)
    for (r in runtimes) {
        debug("* $r.runtimeArgs -> ", runtimeArgs[r]!!)
        debug("* $r.mainArgs -> ", mainArgs[r]!!)
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

    // TODO -- How to find Python stuff?
    //
    // # TODO: Polish this list.
    // pythonRootPaths = [
    //     '~/miniforge3/envs/*',
    //     '~/mambaforge/envs/*',
    //     '${app-dir}/python',
    //     '${app-dir}/lib/runtime',
    // ]
    //
    // # TODO: Verify this list for all platforms.
    // pythonLibSuffixes = [
    //     'LINUX:lib/libpython3.so',
    //     'MACOSX:lib/libpython3.dylib',
    //     'WINDOWS:lib\libpython3.dll',
    // ]
    //
    // For system Python on Linux:
    //     $ find /usr/lib -name 'libpython*'                                                                                                                                                        #  0 {2024-02-22 13:35:10}
    //     /usr/lib/x86_64-linux-gnu/libpeas-1.0/loaders/libpython3loader.so
    //     /usr/lib/x86_64-linux-gnu/libpython3.11.so.1
    //     /usr/lib/x86_64-linux-gnu/libpython3.11.so.1.0
    //     /usr/lib/python3.11/config-3.11-x86_64-linux-gnu/libpython3.11.so
    //     /usr/lib/libreoffice/program/libpythonloaderlo.so
    //
    // val sitePackagesPath = "$pythonRootPath/lib/python$pythonVersion/site-packages"
    // Two possible formats:
    // - "$sitePackagesPath/*.dist-info/METADATA"
    // - "$sitePackagesPath/*.egg-info/PKG-INFO"
    // Extract info from these, perhaps.
    // But the naming is also very structured: $packageName-$packageVersion.(dist|egg)-info
    // So we could probably avoid even reading the metadata files, in favor of dir existence.

    // START HERE -- This begins the search for Java.
    // But we only do this if the directive is going to be JVM.
    // Except... for the Java-via-Python thing, we *do* need to find a
    // libjvm and pass it as one of the arguments to the Python program! Oof!

    // Calculate all the places to search for Java.
    val jvmRootPaths = calculate(config.jvmRootPaths, hints, vars).
        flatMap { glob(it) }.filter { File(it).isDirectory }.toSet()

    debug()
    debug("Root paths to search for Java:")
    jvmRootPaths.forEach { debug("* ", it) }

    // Calculate all the places to look for the JVM library.
    val libjvmSuffixes = calculate(config.jvmLibSuffixes, hints, vars)

    debug()
    debug("Suffixes to check for libjvm:")
    libjvmSuffixes.forEach { debug("* ", it) }

    // Calculate Java distro and version constraints.
    val allowWeirdJvms = config.jvmAllowWeirdRuntimes ?: false
    val distrosAllowed = calculate(config.jvmDistrosAllowed, hints, vars)
    val distrosBlocked = calculate(config.jvmDistrosBlocked, hints, vars)
    val osAliases = calculate(config.osAliases, hints, vars)
    val archAliases = calculate(config.archAliases, hints, vars)
    val constraints = JvmConstraints(configDir, libjvmSuffixes,
        allowWeirdJvms, config.jvmVersionMin, config.jvmVersionMax,
        distrosAllowed, distrosBlocked, osAliases, archAliases)

    // Discover Java.
    debug()
    debug("Discovering Java installations...")
    var java: JavaInstallation? = null
    for (jvmPath in jvmRootPaths) {
        debug("Analyzing candidate JVM directory: '", jvmPath, "'")
        val javaCandidate = JavaInstallation(jvmPath, constraints)
        if (javaCandidate.conforms) {
            // Installation looks good! Moving on.
            java = javaCandidate
            break
        }
    }
    if (java == null) error("No Java installation found.")
    debug("* jvmRootPath -> ", java.rootPath)
    debug("* libjvmPath -> ", java.libjvmPath ?: "<null>")
    debug("* binJava -> ", java.binJava ?: "<null>")

    // Apply JAVA: hints.
    val mv = java.majorVersion
    if (mv != null) {
        hints += "JAVA:$mv"
        // If Java version is OVER 9000, something went wrong in the parsing.
        // Let's not explode the hints set with too many bogus values.
        for (v in 0..min(mv, 9000)) hints += "JAVA:$v+"
    }
    debug("* hints -> ", hints)

    // Calculate classpath.
    val rawClasspath = calculate(config.jvmClasspath, hints, vars)
    debugList("Classpath to calculate:", rawClasspath)
    val classpath = rawClasspath.flatMap { glob(it) }
    debugList("Classpath calculated:", classpath)

    // Calculate JVM arguments.
    jvmRuntimeArgs += calculate(config.jvmRuntimeArgs, hints, vars)
    debugList("JVM arguments calculated:", jvmRuntimeArgs)

    // Append or amend argument declaring classpath elements.
    if (classpath.isNotEmpty()) {
        val classpathString = classpath.joinToString(COLON)
        val cpIndex = jvmRuntimeArgs.indexOfFirst { it.startsWith("-Djava.class.path=") }
        if (cpIndex >= 0) {
            // Append to existing `-Djava.class.path` argument.
            jvmRuntimeArgs[cpIndex] += "$COLON$classpathString"
            debug("Extended classpath arg: ${jvmRuntimeArgs[cpIndex]}")
        }
        else {
            // No `-Djava.class.path` argument, so we add one.
            jvmRuntimeArgs += "-Djava.class.path=$classpathString"
            debug("Added classpath arg: ${jvmRuntimeArgs.last()}")
        }
    }

    // If not already declared, calculate and declare the max heap size.
    val mxIndex = jvmRuntimeArgs.indexOfFirst { it.startsWith("-Xmx") }
    if (mxIndex < 0) {
        val maxHeap = calculateMaxHeap(config.jvmMaxHeap)
        jvmRuntimeArgs += "-Xmx${maxHeap}"
        debug("Added maxHeap arg: ${jvmRuntimeArgs.last()}")
    }

    // Calculate main class.
    debug()
    debug("Calculating main class name...")
    val mainClassNames = calculate(config.jvmMainClass, hints, vars)
    val mainClassName = if (mainClassNames.isEmpty()) null else mainClassNames.first()
    debug("mainClassName -> ", mainClassName ?: "<null>")
    if (mainClassName == null)
        error("No matching main class name")

    // Calculate main args.
    jvmMainArgs += calculate(config.jvmMainArgs, hints, vars)
    debugList("Main arguments calculated:", jvmMainArgs)

    // Discern directives to perform.
    val directives = calculate(config.directives, hints, vars).toSet()

    debug()
    debug("Directives parsed:")
    debug("* directives -> ", directives)

    // Execute directives
    var launchDirective = "CANCEL"
    for (directive in directives) {
        when (directive) {
            "help" -> help(executable, programName, supportedOptions)
            "print-java-home" -> printlnErr(java.rootPath)
            "print-java-info" -> printlnErr(java.toString())
            "print-class-path" -> classpath.forEach { printlnErr(it) }
            "dry-run" -> dryRun(java, jvmRuntimeArgs, mainClassName, jvmMainArgs)
            else -> {
                if (directive in runtimes) launchDirective = directive
                else error("Invalid directive: $directive")
            }
        }
    }

    val chosenRuntimeArgs = runtimeArgs.get(launchDirective) ?: emptyList()
    val chosenMainArgs = mainArgs.get(launchDirective) ?: emptyList()

    // Validate runtime args.
    if (config.allowUnrecognizedArgs != true && launchDirective in recognizedArgs) {
        // Check that the computed runtime args are all valid.
        val chosenRecognizedArgs = recognizedArgs[launchDirective]!!
        for (arg in chosenRuntimeArgs) {
            if (!config.recognizes(arg, chosenRecognizedArgs)) {
                error("Unrecognized $launchDirective argument: $arg")
            }
        }
    }

    // Emit final configuration.
    debug()
    debug("Emitting final configuration to stdout...")
    println(launchDirective)
    if (launchDirective == "CANCEL") return
    println(java.libjvmPath!!)
    println(chosenRuntimeArgs.size)
    for (arg in chosenRuntimeArgs) println(arg)
    println(mainClassName.replace(".", "/"))
    println(chosenMainArgs.size)
    for (mainArg in chosenMainArgs) println(mainArg)
}

// -- Helper functions --

private fun calculateMaxHeap(maxHeap: String?): String? {
    if (maxHeap?.endsWith("%") != true) return maxHeap

    // Compute percentage of total available memory.
    val percent = maxHeap.substring(0, maxHeap.lastIndex).toDoubleOrNull() // Double or nothing! XD
    if (percent == null || percent <= 0) {
        warn("Ignoring invalid max-heap value '", maxHeap, "'")
        return null
    }

    debug()
    debug("Calculating max heap (", maxHeap, ")...")
    val memInfo = memInfo()
    if (memInfo.total == null) {
        warn("Cannot determine total memory -- ignoring max-heap value '", maxHeap, "'")
        return null
    }
    else debug("System reported memTotal of ", memInfo.total.toString())

    val kbValue = (percent * memInfo.total!! / 100 / 1024).toInt()
    if (kbValue <= 9999) return "${kbValue}k"
    val mbValue = kbValue / 1024
    if (mbValue <= 9999) return "${mbValue}m"
    val gbValue = mbValue / 1024
    return "${gbValue}g"
}

private fun calculate(items: Array<String>, hints: Set<String>, vars: Map<String, String>): List<String> {
    return items.mapNotNull { it.evaluate(hints, vars) }.filter { it.isNotEmpty() }
}

// -- String extensions --

private fun String.evaluate(hints: Set<String>, vars: Map<String, String>): String? {
    val tokens = split('|')
    val rules = tokens.subList(0, tokens.lastIndex)
    val value = tokens.last()

    // Check that all rules apply.
    for (rule in rules) {
        val negation = rule.startsWith('!')
        val hint = if (negation) rule.substring(1) else rule
        if (hint in hints == negation) return null
    }

    // Line matches all rules. Populate variable values and return the result.
    return value interpolate vars
}

private infix fun String.bisect(delimiter: Char): Pair<String, String?> {
    val index = indexOf(delimiter)
    return if (index < 0) Pair(this, null) else Pair(substring(0, index), substring(index + 1))
}

/** Replaces `${var}` expressions with values from a vars map. */
private infix fun String.interpolate(vars: Map<String, String>): String = buildString {
    val s = this@interpolate
    var pos = 0
    while (true) {
        // Find the next variable expression.
        val start = s.indexOf("\${", pos)
        val end = if (start < 0) -1 else s.indexOf('}', start + 2)
        if (start < 0 || end < 0) {
            // No more variable expressions found; append remaining string.
            append(s.substring(pos))
            break
        }

        // Add the text before the expression.
        append(s.substring(pos, start))

        // Evaluate the expression and add it to the result.
        // If the variable name is missing from the map, check for an environment variable.
        // If no environment variable either, then just leave the expression alone.
        val name = s.substring(start + 2, end)
        append(vars[name] ?: getenv(name) ?: s.substring(start, end + 1))

        // Advance the position beyond the variable expression.
        pos = end + 1
    }
}

// -- Directives --

private fun help(executable: String?, programName: String, supportedOptions: JaunchOptions) {
    // TODO: Glean Jaunch version and build hash somehow.
    val exeName = executable ?: "jaunch"

    printlnErr("Usage: $exeName [<Java options>.. --] [<main arguments>..]")
    printlnErr()
    printlnErr("$programName launcher (Jaunch v$JAUNCH_VERSION / $JAUNCH_BUILD / $BUILD_TARGET)")
    printlnErr("Java options are passed to the Java Runtime,")
    printlnErr("main arguments to the launched program ($programName).")
    printlnErr()
    printlnErr("In addition, the following options are supported:")
    val optionsUnique = linkedSetOf(*supportedOptions.values.toTypedArray())
    optionsUnique.forEach { printlnErr(it.help()) }
}

private fun dryRun(
    java: JavaInstallation,
    jvmArgs: MutableList<String>,
    mainClassName: String?,
    mainArgs: MutableList<String>
) {
    printlnErr(buildString {
        append(java.binJava ?: "java")
        jvmArgs.forEach { append(" $it") }
        append(" $mainClassName")
        mainArgs.forEach { append(" $it") }
    })
}
