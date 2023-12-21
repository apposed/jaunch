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
        if (!rootDir.isDirectory) continue
        debug("Examining candidate rootDir: ", rootDir)

        // We found an actual directory. Now we check it for libjvm.
        for (libjvmSuffixLine in config.libjvmSuffixes) {
            val suffix = libjvmSuffixLine.evaluate(hints, vars) ?: continue
            val libjvmFile = File("$path/$suffix")
            if (!libjvmFile.isFile) continue
            debug("Examining candidate libjvm: ", libjvmFile)

            // Found a libjvm. So now we validate the Java installation.
            // It needs to conform to the configuration's version constraints.
            // TODO: Fix the readReleaseInfo function not to crash.
            //val info = readReleaseInfo(rootDir) ?: continue
            //val vendor = info["IMPLEMENTOR"]
            //val version = info["JAVA_VERSION"]
            // TODO: parse out majorVersion from version, then compare to versionMin/versionMax.
            //  If not within the constraints, continue.
            //  Add allowedVendors/blockedVendors lists to the TOML schema, and check it here.

            // All constraints passed -- select this Java installation!
            libjvmPath = libjvmFile.path
            jvmRootPath = path
            debug("libjvmPath -> ", libjvmPath)
            debug("jvmRootPath -> ", jvmRootPath)
            break
        }
        if (libjvmPath != null) break
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

    // Calculate max heap.
    val maxHeap = config.maxHeap ?: "1g" // TODO
    debug()
    debug("maxHeap -> $maxHeap")

    // Calculate JVM arguments.
    for (argLine in config.jvmArgs) {
        val arg = argLine.evaluate(hints, vars) ?: continue
        jvmArgs += arg
    }
    // TODO: Consider the best ordering for these elements.
    //  Maybe they should be prepended, in case the user overrode them.
    //  (With java arguments, later ones trump/replace earlier ones.)
    // TODO: If jvmArgs already have a `-Djava.class.path`, should we
    //  skip appending the classpath? Or append to that same arg?
    //  Similar question for `-Xmx`: leave it off if it was already given?
    if (classpath.isNotEmpty()) {
        jvmArgs += "-Djava.class.path=${classpath.joinToString(COLON)}"
    }
    jvmArgs += "-Xmx${maxHeap}"
    debugList("JVM arguments calculated:", jvmArgs)

    // Calculate main class.
    var mainClassName: String? = null
    for (mainClassLine in config.mainClasses) {
        mainClassName = mainClassLine.evaluate(hints, vars) ?: continue
        break
    }
    debug()
    debug("mainClassName -> ", mainClassName ?: "<null>")
    if (mainClassName == null) {
        error("No matching main class name")
    }

    // Calculate main args.
    for (argLine in config.mainArgs) {
        val arg = argLine.evaluate(hints, vars) ?: continue
        mainArgs += arg
    }
    debugList("Main arguments calculated:", mainArgs)

    if ("dry-run" in directives) {
        val lib = libjvmPath.lastIndexOf("/lib/")
        val dirPath = if (lib >= 0) libjvmPath.substring(0, lib) else jvmRootPath
        val javaBinary = File("$dirPath/bin/java")
        val javaCommand = if (javaBinary.isFile) javaBinary.path else "java"
        printlnErr(buildString {
            append(javaCommand)
            jvmArgs.forEach { append(it) }
            append(mainClassName)
            mainArgs.forEach { append(it) }
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
