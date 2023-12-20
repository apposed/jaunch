import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.file.TomlFileReader
import kotlinx.serialization.serializer
import kotlin.experimental.ExperimentalNativeApi

typealias JaunchOptions = Map<String, JaunchOption>

fun main(args: Array<String>) {
    // Treat both lines of stdin and arguments on the CLI as inputs.
    // Normally, there will only be lines of stdin, not command line arguments,
    // but it's perhaps convenient for testing to be able to pass args on the CLI, too.
    val stdinArgs = stdinLines()
    val executable = stdinArgs.getOrNull(0)
    val inputArgs = stdinArgs.slice(1..<stdinArgs.size) + args

    // Discern the directory containing this program.
    val exeFile = executable?.let(::File)
    val exeDir = exeFile?.directoryPath ?: "."

    // Load the configuration from the TOML files.
    var config = readConfig("$exeDir/jaunch.toml")
    if (exeFile != null) {
        // Parse and merge the app-specific TOML file as well.
        config += readConfig("${exeFile.withoutSuffix}.toml")
    }

    // Parse the configuration's declared Jaunch options.
    //
    // For each option, we have a string of the form:
    //   --alpha,...,--omega=assignment|Description of what this option does.
    //
    // We need to parse out the help text, the actual flags list, and whether the flags expect an assignment value.
    val supportedOptions: JaunchOptions = buildMap {
        for (optionLine in config.supportedOptions) {
            val (optionString, help) = optionLine bisect '|'
            val (flagsString, assignment) = optionString bisect '='
            val flags = flagsString.split(',')
            val option = JaunchOption(flags.toTypedArray(), assignment, help)
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
                vars[arg] = value
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

    // Apply mode hints.
    for (modeLine in config.modes) {
        val mode = modeLine.evaluate(hints, vars) ?: continue
        if (mode.startsWith("!")) {
            // Negated mode expression: remove the mode hint.
            hints.remove(mode.substring(1))
        }
        else hints.add(mode)
    }

    // If a directive is active, do it instead of discovering and launching Java.
    // TODO: config.directives
    //  This needs further thought; it's not clear to me when each directive should
    //  be applied. I don't actually think it's consistent. The dry-run directive for
    //  example should fully parse everything and actually discover the Java, but not
    //  actually launch it -- just emit the arguments that would be passed.
    //  Whereas the help directive should emit the help immediately and exit.

    // Discover Java.
    var libjvmPath: String? = null
    for (rootPathLine in config.rootPaths) {
        val path = rootPathLine.evaluate(hints, vars) ?: continue
        val rootDir = File(path)
        if (!rootDir.isDirectory) continue

        // We found an actual directory. Now we check it for libjvm.
        for (libjvmSuffixLine in config.libjvmSuffixes) {
            val suffix = libjvmSuffixLine.evaluate(hints, vars) ?: continue
            val libjvmFile = File("$path/$suffix")
            if (!libjvmFile.isFile) continue

            // Found a libjvm. So now we validate the Java installation.
            // It needs to conform to the configuration's version constraints.
            val info = releaseInfo(rootDir) ?: continue
            val vendor = info["IMPLEMENTOR"]
            val version = info["JAVA_VERSION"]
            // TODO: parse out majorVersion from version, then compare to versionMin/versionMax.
            //  If not within the constraints, continue.
            //  Add allowedVendors/blockedVendors lists to the TOML schema, and check it here.

            // All constraints passed -- select this Java installation!
            libjvmPath = libjvmFile.path
            break
        }
        if (libjvmPath != null) break
    }
    if (libjvmPath == null) {
        error("No Java installation found.")
    }

    // TODO: print-java-home directive.

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

    // TODO: print-class-path directive.

    // Calculate max heap.
    val maxHeap = config.maxHeap ?: "1g" // TODO

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
        jvmArgs += "-Djava.class.path=${classpath.joinToString(colon)}"
    }
    jvmArgs += "-Xmx${maxHeap}"

    // Calculate main class.
    var mainClassName: String? = null
    for (candidateLine in config.mainClassCandidates) {
        val candidate = candidateLine.evaluate(hints, vars) ?: continue
        mainClassName = candidate
        break
    }
    if (mainClassName == null) {
        error("No matching main class name")
    }

    // Calculate main args.
    for (argLine in config.mainArgs) {
        val arg = argLine.evaluate(hints, vars) ?: continue
        mainArgs += arg
    }

    // TODO: dry-run directive.

    // Emit final configuration.
    println(libjvmPath)
    println(jvmArgs.size)
    for (jvmArg in jvmArgs) println(jvmArg)
    println(mainClassName.replace(".", "/"))
    println(mainArgs.size)
    for (mainArg in mainArgs) println(mainArg)
}

private fun releaseInfo(rootDir: File): Map<String, String>? {
    // Discern the Java version and vendor. We need to extract them
    // from the Java installation as inexpensively as possibly.
    //
    // Approaches:
    // A) Parse the directory name.
    //    - Fast but fragile.
    // B) Look inside the <dir>/release file for IMPLEMENTOR and JAVA_VERSION.
    //    - Some flavors (JBRSDK 8, Corretto 8) may be missing this file.
    //    - Some flavors (JBRSDK 8, macOS Adopt 8, macOS Zulu 8) do not have IMPLEMENTOR.
    //    - And some other flavors (JBRSDK 11, macOS Adopt 9) put "N/A" for IMPLEMENTOR.
    //    - Can also look at OS_ARCH and OS_NAME if we want to glean those things.
    //      All release files I possess appear to include these two OS lines.
    // C) Call `java SysProps` to run a class to print System.getProperties() to stdout.
    //    - Slow but reliable.
    //    - Ideally would avoid doing this if we already know the os/arch is wrong.
    //
    // After succeeding at identifying a Java installation, we can cache the results.

    val releaseFile = File("${rootDir.path}/release")
    if (!releaseFile.isFile) return null
    // TODO: Instead of failing immediately when the release file is missing, we should try a couple of
    //  heuristics to glean the desired information of Java vendor/distro and version; see above comment.
    val lines = releaseFile.readLines()
    val info = mutableMapOf<String, String>()
    for (line in lines) {
        val equals = line.indexOf("=\"")
        if (equals < 0 || !line.endsWith("\"")) {
            // We are looking for lines of the form:
            //   KEY="VALUE"
            // and skipping (for now) lines not conforming to this pattern.
            // These release files sometimes have lines in other forms, such as JSON-style map data structures.
            // But we do not need to parse them, because we only care about two specific key/value string pairs.
            continue
        }
        val key = line.substring(0, equals)
        val value = line.substring(equals + 2)
        info[key] = value
    }
    return info
}

private fun readConfig(tomlPath: String): JaunchConfig {
    val tomlFile = File(tomlPath)
    if (!tomlFile.exists) return JaunchConfig()
    return TomlFileReader(
        inputConfig = TomlInputConfig(
            ignoreUnknownNames = true,
        )
    ).decodeFromFile(serializer(), tomlPath)
}

private infix fun String.bisect(delimiter: Char): Pair<String, String?> {
    val index = indexOf(delimiter)
    return if (index < 0) Pair(this, null) else Pair(substring(0, index), substring(index + 1))
}

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

/** Replaces ${var} expressions with values from a vars map. */
private infix fun String.interpolate(vars: Map<String, String>): String = buildString {
    var pos = 0
    while (true) {
        // Find the next variable expression.
        val start = indexOf("\${", pos)
        val end = if (start < 0) -1 else indexOf('}', start + 2)
        if (start < 0 || end < 0) {
            // No more variable expressions found; append remaining string.
            append(substring(pos))
            break
        }

        // Add the text before the expression.
        append(substring(pos, start))

        // Evaluate the expression and add it to the result.
        // If the variable name is missing from the map, check for an environment variable.
        // If no environment variable either, then just leave the expression alone.
        val name = substring(start + 2, end)
        append(vars[name] ?: getenv(name) ?: substring(start, end))

        // Advance the position beyond the variable expression.
        pos = end + 1
    }
}

@OptIn(ExperimentalNativeApi::class)
private val osName = Platform.osFamily.name

@OptIn(ExperimentalNativeApi::class)
private val cpuArch = Platform.cpuArchitecture.name
