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

    // TODO - make it so that the "else" case for directives is an error message,
    //  which will be shown in a dialog box on a best effort basis on the C side.
    //  Weeelll... do we want to complexify the C code? Probably not,
    //  UNLESS we fail to run the jaunch configurator at all...

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

    // Load the configuration from the TOML file(s).
    var config = readConfig(configDir / "jaunch.toml")
    config += readConfig(configDir / "jaunch-$$OS_NAME.toml")
    config += readConfig(configDir / "jaunch-$$OS_NAME-$CPU_ARCH.toml")
    if (exeFile != null) {
        // Parse and merge the app-specific TOML file(s) as well.
        var fileName = exeFile.base.name
        while (true) {
            config += readConfig(configDir / "$fileName.toml")
            val dash = fileName.lastIndexOf("-")
            if (dash < 0) break
            fileName = fileName.substring(0, dash)
        }
    }

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

    // Declare the authoritative lists of JVM arguments and main arguments.
    // At the end of the configuration process, we will emit these to stdout.
    val jvmArgs = mutableListOf<String>()
    val mainArgs = mutableListOf<String>()

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
    // Non-matching input arguments will be passed through directly,
    // either to the JVM or to the main method on the Java side.
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
                // No dash-dash divider was given, so we need to guess: is this a JVM arg, or a main arg?
                (if (config.recognizes(argKey)) jvmArgs else mainArgs) += arg
            }
            else if (i <= divider) {
                // This argument is before the dash-dash divider, so must be treated as a JVM arg.
                // But we only allow it through if it's a recognized JVM argument, or the
                // allow-unrecognized-jvm-args configuration flag is set to true.
                if (config.allowUnrecognizedJvmArgs != true && !config.recognizes(arg)) {
                    error("Unrecognized JVM argument: $arg")
                }
                jvmArgs += arg
            }
            else {
                // This argument is after the dash-dash divider, so we treat it as a main arg.
                mainArgs += arg
            }
        }
    }

    debug()
    debug("Input arguments parsed:")
    debug("* hints -> ", hints)
    debug("* vars -> ", vars)
    debug("* jvmArgs -> ", jvmArgs)
    debug("* mainArgs -> ", mainArgs)

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

    // Discern directives to perform.
    val directives = calculate(config.directives, hints, vars).toSet()
    val nativeDirective = if (directives.isEmpty()) "LAUNCH" else "CANCEL"

    debug()
    debug("Directives parsed:")
    debug("* directives -> ", directives)
    debug("* nativeDirective -> ", nativeDirective)

    // Execute the help directive.
    if ("help" in directives) help(executable, programName, supportedOptions)

    // Calculate all the places to search for Java.
    val jvmRootPaths = calculate(config.jvmRootPaths, hints, vars).
        flatMap { glob(it) }.filter { File(it).isDirectory }.toSet()

    debug()
    debug("Root paths to search for Java:")
    jvmRootPaths.forEach { debug("* ", it) }

    // Calculate all the places to look for the JVM library.
    val libjvmSuffixes = calculate(config.libjvmSuffixes, hints, vars)

    debug()
    debug("Suffixes to check for libjvm:")
    libjvmSuffixes.forEach { debug("* ", it) }

    // Calculate Java distro and version constraints.
    val allowWeirdJvms = config.allowWeirdJvms ?: false
    val distrosAllowed = calculate(config.javaDistrosAllowed, hints, vars)
    val distrosBlocked = calculate(config.javaDistrosBlocked, hints, vars)
    val osAliases = calculate(config.osAliases, hints, vars)
    val archAliases = calculate(config.archAliases, hints, vars)
    val constraints = JavaConstraints(configDir, libjvmSuffixes,
        allowWeirdJvms, config.javaVersionMin, config.javaVersionMax,
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

    // Execute the print-java-home and/or print-java-info directive.
    if ("print-java-home" in directives) printlnErr(java.rootPath)
    if ("print-java-info" in directives) printlnErr(java.toString())

    // Calculate classpath.
    val rawClasspath = calculate(config.classpath, hints, vars)
    debugList("Classpath to calculate:", rawClasspath)
    val classpath = rawClasspath.flatMap { glob(it) }
    debugList("Classpath calculated:", classpath)

    // Execute the print-class-path directive.
    if ("print-class-path" in directives) classpath.forEach { printlnErr(it) }

    // Calculate JVM arguments.
    jvmArgs += calculate(config.jvmArgs, hints, vars)
    debugList("JVM arguments calculated:", jvmArgs)

    // Append or amend argument declaring classpath elements.
    if (classpath.isNotEmpty()) {
        val classpathString = classpath.joinToString(COLON)
        val cpIndex = jvmArgs.indexOfFirst { it.startsWith("-Djava.class.path=") }
        if (cpIndex >= 0) {
            // Append to existing `-Djava.class.path` argument.
            jvmArgs[cpIndex] += "$COLON$classpathString"
            debug("Extended classpath arg: ${jvmArgs[cpIndex]}")
        }
        else {
            // No `-Djava.class.path` argument, so we add one.
            jvmArgs += "-Djava.class.path=$classpathString"
            debug("Added classpath arg: ${jvmArgs.last()}")
        }
    }

    // If not already declared, calculate and declare the max heap size.
    val mxIndex = jvmArgs.indexOfFirst { it.startsWith("-Xmx") }
    if (mxIndex < 0) {
        val maxHeap = calculateMaxHeap(config.maxHeap)
        jvmArgs += "-Xmx${maxHeap}"
        debug("Added maxHeap arg: ${jvmArgs.last()}")
    }

    // Calculate main class.
    debug()
    debug("Calculating main class name...")
    val mainClassNames = calculate(config.mainClasses, hints, vars)
    val mainClassName = if (mainClassNames.isEmpty()) null else mainClassNames.first()
    debug("mainClassName -> ", mainClassName ?: "<null>")
    if (mainClassName == null)
        error("No matching main class name")

    // Calculate main args.
    mainArgs += calculate(config.mainArgs, hints, vars)
    debugList("Main arguments calculated:", mainArgs)

    // Execute the dry-run directive.
    if ("dry-run" in directives) dryRun(java, jvmArgs, mainClassName, mainArgs)

    // Emit final configuration.
    debug()
    debug("Emitting final configuration to stdout...")
    println(nativeDirective)
    println(java.libjvmPath!!)
    println(jvmArgs.size)
    for (jvmArg in jvmArgs) println(jvmArg)
    println(mainClassName.replace(".", "/"))
    println(mainArgs.size)
    for (mainArg in mainArgs) println(mainArg)
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
    val jaunchVersion = "???"
    val jaunchBuild = "???"
    val exeName = executable ?: "jaunch"

    printlnErr("Usage: $exeName [<Java options>.. --] [<main arguments>..]")
    printlnErr()
    printlnErr("$programName launcher (Jaunch v$jaunchVersion / build $jaunchBuild)")
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
