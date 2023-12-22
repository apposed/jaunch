import kotlin.experimental.ExperimentalNativeApi

typealias JaunchOptions = Map<String, JaunchOption>

@OptIn(ExperimentalNativeApi::class)
private val osName = Platform.osFamily.name

@OptIn(ExperimentalNativeApi::class)
private val cpuArch = Platform.cpuArchitecture.name

fun main(args: Array<String>) {
    // Treat both lines of stdin and arguments on the CLI as inputs.
    // Normally, there will only be lines of stdin, not command line arguments,
    // but it's perhaps convenient for testing to be able to pass args on the CLI, too.
    val stdinArgs = stdinLines()
    val executable = stdinArgs.getOrNull(0)
    val inputArgs = stdinArgs.slice(1..<stdinArgs.size) + args
    debug("inputArgs -> ", inputArgs)

    // Discern the directory containing this program.
    val exeFile = executable?.let(::File)
    val exeDir = exeFile?.directoryPath ?: "."
    debug("exeDir -> ", exeDir)

    // Load the configuration from the TOML files.
    var config = readConfig("$exeDir/jaunch.toml")
    if (exeFile != null) {
        // Parse and merge the app-specific TOML file as well.
        config += readConfig("${exeFile.withoutSuffix}.toml")
    }

    val programName = config.programName ?: executable?.let(::File)?.name?.let(::File)?.withoutSuffix ?: "Jaunch"
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
        "OS:$osName",
        // Kotlin knows these CPU architectures:
        //   UNKNOWN, ARM32, ARM64, X86, X64, MIPS32, MIPSEL32, WASM32
        "ARCH:$cpuArch"
    )

    // Declare a set to store option parameter values.
    // It will be populated at argument parsing time.
    val vars = mutableMapOf<String, String>()

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
        if (arg == "--") {
            if (i != divider) error("Divider symbol (--) may only be given once")
        }
        else if (arg in supportedOptions) {
            // The argument is declared in Jaunch's configuration. Deal with it appropriately.
            val option: JaunchOption = supportedOptions[arg]!!
            if (option.assignment != null) {
                // option with value assignment
                if (i >= inputArgs.size) error("No value given for argument $arg")
                val value = inputArgs[i++]
                // TODO: What if --arg=value is passed multiple times?
                //  Should we save all values in a list? Or overwrite?

                // Normalize the argument to the primary flag on the comma-separated list.
                // Then strip leading dashes: --class-path becomes class-path, -v becomes v, etc.
                var varName = option.flags.first()
                while (varName.startsWith("-")) varName = varName.substring(1)

                // Store assignment value as a variable named after the normalized argument.
                // E.g. if the matching supported option is `--heap,--mem=<max>|Maximum heap size`,
                // and `--mem 52g` is given, it will store `"52g"` for the variable `heap`.
                vars[varName] = value
            }
            hints += arg
        }
        else {
            // The argument is not a Jaunch one. Pass it through directly.
            if (divider < 0) {
                // No dash-dash divider was given, so we need to guess: is this a JVM arg, or a main arg?
                (if (config.recognizes(arg)) jvmArgs else mainArgs) += arg
            }
            else if (i < divider) {
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
    for (modeLine in config.modes) {
        val mode = modeLine.evaluate(hints, vars) ?: continue
        if (mode.startsWith("!")) {
            // Negated mode expression: remove the mode hint.
            hints.remove(mode.substring(1))
        }
        else hints.add(mode)
    }

    debug()
    debug("Modes applied:")
    debug("* hints -> ", hints)

    // Discern directives to perform.
    val directives = mutableSetOf<String>()
    for (directiveLine in config.directives) {
        val directive = directiveLine.evaluate(hints, vars) ?: continue
        directives.add(directive)
    }
    val nativeDirective = if (directives.isEmpty()) "LAUNCH" else "CANCEL"

    debug()
    debug("Directives parsed:")
    debug("* directives -> ", directives)
    debug("* nativeDirective -> ", nativeDirective)

    // Execute the help directive.
    if ("help" in directives) {
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

    // Discover Java.
    var libjvmPath: String? = null
    var jvmRootPath: String? = null
    debug()
    debug("Discovering Java installations...")
    for (rootPathLine in config.rootPaths) {
        val path = rootPathLine.evaluate(hints, vars) ?: continue
        val rootDir = File(path)

        // Validate the Java installation. It needs to meet the configuration constraints.
        validateRootDir(rootDir) || continue

        // Root directory is looking good! Now let's find the libjvm.
        for (libjvmSuffixLine in config.libjvmSuffixes) {
            val libjvmSuffix = libjvmSuffixLine.evaluate(hints, vars) ?: continue
            val libjvmFile = File("$path/$libjvmSuffix")
            if (!libjvmFile.isFile) continue
            libjvmPath = libjvmFile.path
            jvmRootPath = path
            debug("libjvmPath -> ", libjvmPath)
            debug("jvmRootPath -> ", jvmRootPath)
            break
        }
        if (libjvmPath != null) break
        debug("...but no JVM library was found in this root directory.")
    }
    if (libjvmPath == null || jvmRootPath == null) {
        error("No Java installation found.")
    }

    if ("print-java-home" in directives) {
        printlnErr(jvmRootPath)
    }

    // Calculate classpath.
    val classpath = mutableListOf<String>()
    for (classpathLine in config.classpath) {
        val value = classpathLine.evaluate(hints, vars) ?: continue

        if (value.endsWith("/*")) {
            // Add all JAR files and directories to the classpath.
            val valueWithoutGlob = value.substring(0, value.length - 2)
            for (file in File(valueWithoutGlob).listFiles()) {
                if (file.isDirectory || file.suffix == "jar") classpath += file.path
            }
        }
        else {
            classpath += value
        }
    }
    debugList("Classpath calculated:", classpath)

    if ("print-class-path" in directives) {
        classpath.forEach { printlnErr(it) }
    }

    // Calculate JVM arguments.
    for (argLine in config.jvmArgs) {
        val arg = argLine.evaluate(hints, vars) ?: continue
        jvmArgs += arg
    }
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
    var mainClassName: String? = null
    for (mainClassLine in config.mainClasses) {
        mainClassName = mainClassLine.evaluate(hints, vars) ?: continue
        break
    }
    debug("mainClassName -> ", mainClassName ?: "<null>")
    if (mainClassName == null) {
        error("No matching main class name")
    }

    // Calculate main args.
    for (argLine in config.mainArgs) {
        mainArgs += argLine.evaluate(hints, vars) ?: continue
    }
    debugList("Main arguments calculated:", mainArgs)

    if ("dry-run" in directives) {
        val lib = libjvmPath.lastIndexOf("/lib/")
        val dirPath = if (lib >= 0) libjvmPath.substring(0, lib) else jvmRootPath
        val javaBinary = File("$dirPath/bin/java")
        val javaCommand = if (javaBinary.isFile) javaBinary.path else "java"
        printlnErr(buildString {
            append(javaCommand)
            jvmArgs.forEach { append(" $it") }
            append(" $mainClassName")
            mainArgs.forEach { append(" $it") }
        })
    }

    debug("Emitting final configuration to stdout...")

    // Emit final configuration.
    println(nativeDirective)
    println(libjvmPath)
    println(jvmArgs.size)
    for (jvmArg in jvmArgs) println(jvmArg)
    println(mainClassName.replace(".", "/"))
    println(mainArgs.size)
    for (mainArg in mainArgs) println(mainArg)
}

private fun validateRootDir(rootDir: File): Boolean {
    debug("Examining candidate rootDir: ", rootDir)
    if (!rootDir.isDirectory) {
        debug("Not a directory")
        return false
    }

    // ~~~~~~~~~ 👯 REGEX TIME 👯 ~~~~~~~~~

    val sep = "(-|_|\\.|)"
    val versionPattern = "((8u)?([0-9]+)([\\.+_][0-9]+)*(-b[0-9]+|-ca)?)?"
    val prefixes = listOf("jdk", "java", "openjdk", "").joinToString("|")
    val flavors = listOf(
        "adopt",
        "amazon-corretto",
        "graalvm-ce",
        "graalvm-community-openjdk",
        "graalvm-jdk",
        "jbrsdk",
        "oracle",
        "zulu",
        ""
    ).joinToString("|")

    val featuresPattern = listOf("-crac", "-jdk", "-jre", "-fx", "-java").joinToString("|")
    val oses = listOf("linux", "macosx", "").joinToString("|")
    val variants = listOf("musl", "openjdk", "").joinToString("|")
    val arches = listOf("amd64", "x64", "x86", "").joinToString("|")
    val suffixes = listOf("-jre", "-lite", "").joinToString("|")
    val pattern =
        "($prefixes)$sep" +
                "($flavors)$sep" +
                versionPattern +
                "(($featuresPattern)*)" +
                versionPattern + sep +
                "($oses)$sep" +
                "($variants)$sep" +
                "($arches)" +
                versionPattern +
                "($suffixes)"

    val matchGroups = Regex(pattern).matchEntire(rootDir.name)?.groupValues
    val missing = "<null>"
    val entire   = matchGroups?.get( 0) ?: missing
    val prefix   = matchGroups?.get( 1) ?: missing
    val sep1     = matchGroups?.get( 2) ?: missing
    val flavor   = matchGroups?.get( 3) ?: missing
    val sep2     = matchGroups?.get( 4) ?: missing
    val v1       = matchGroups?.get( 5) ?: missing // and 6, 7, 8, 9
    val features = matchGroups?.get(10) ?: missing // and 11
    val v2       = matchGroups?.get(12) ?: missing // and 13, 14, 15, 16
    val sep3     = matchGroups?.get(17) ?: missing
    val os       = matchGroups?.get(18) ?: missing
    val sep4     = matchGroups?.get(19) ?: missing
    val variant  = matchGroups?.get(20) ?: missing
    val sep5     = matchGroups?.get(21) ?: missing
    val arch     = matchGroups?.get(22) ?: missing
    val v3       = matchGroups?.get(23) ?: missing // and 24, 25, 26, 27
    val suffix   = matchGroups?.get(28) ?: missing

    debug("Directory name parsed:")
    debug("entire   = ", entire)
    debug("prefix   = ", prefix)
    debug("sep1     = ", sep1)
    debug("flavor   = ", flavor)
    debug("sep2     = ", sep2)
    debug("v1       = ", v1)
    debug("features = ", features)
    debug("v2       = ", v2)
    debug("sep3     = ", sep3)
    debug("os       = ", os)
    debug("sep4     = ", sep4)
    debug("variant  = ", variant)
    debug("sep5     = ", sep5)
    debug("arch     = ", arch)
    debug("v3       = ", v3)
    debug("suffix   = ", suffix)

    val info = readReleaseInfo(rootDir)
    val vendor = info?.get("IMPLEMENTOR")
    val version = info?.get("JAVA_VERSION")
    debug("Java vendor -> ", vendor ?: "<null>")
    debug("Java version -> ", version ?: "<null>")

    // TODO: parse out majorVersion from version, then compare to versionMin/versionMax.
    //  If not within the constraints, continue.
    //  Add allowedVendors/blockedVendors lists to the TOML schema, and check it here.

    return true
}

private fun calculateMaxHeap(maxHeap: String?): String? {
    if (maxHeap?.endsWith("%") != true) return maxHeap

    // Compute percentage of total available memory.
    val percent = maxHeap.substring(0, maxHeap.lastIndex).toDoubleOrNull() // Double or nothing! XD
    if (percent == null || percent <= 0) {
        warn("Ignoring invalid max-heap value '", maxHeap, "'")
        return null
    }
    val memInfo = memInfo()
    if (memInfo.total == null) {
        warn("Cannot determine total memory -- ignoring max-heap value '", maxHeap, "'")
        return null
    }
    val kbValue = (percent * memInfo.total!! / 100).toInt()
    if (kbValue <= 9999) {
        debug("Calculated maxHeap of ", kbValue, " KB")
        return "${kbValue}k"
    }
    val mbValue = kbValue / 1024
    if (mbValue <= 9999) {
        debug("Calculated maxHeap of ", mbValue, " MB")
        return "${mbValue}m"
    }
    val gbValue = mbValue / 1024
    debug("Calculated maxHeap of ", gbValue, " GB")
    return "${gbValue}g"
}

private infix fun String.bisect(delimiter: Char): Pair<String, String?> {
    val index = indexOf(delimiter)
    return if (index < 0) Pair(this, null) else Pair(substring(0, index), substring(index + 1))
}

private fun String.evaluate(hints: Set<String>, vars: Map<String, String>): String? {
    val tokens = split('|')
    //assert(tokens.isNotEmpty())
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
