import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.file.TomlFileReader
import kotlinx.serialization.serializer
import kotlin.experimental.ExperimentalNativeApi

fun main(args: Array<String>) {
    // Treat both lines of stdin and arguments on the CLI as inputs.
    val stdinArgs = stdinLines()
    val executable = stdinArgs.getOrNull(0)
    val inputArgs = stdinArgs.slice(1..<stdinArgs.size) + args

    // Discern the directory containing this program.
    val exeFile = if (executable == null) null else File(executable)
    val dir = exeFile?.directoryPath ?: "."

    // Load the configuration from the TOML files.
    var config = readConfig("$dir/jaunch.toml")
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

    val supportedOptions = mutableMapOf<String, JaunchOption>()
    for (optionLine in config.supportedOptions) {
        val (optionString, help) = partition(optionLine, "|")
        val (flagsString, assignment) = partition(optionString, "=")
        val flags = flagsString.split(",")
        val option = JaunchOption(flags.toTypedArray(), assignment, help)
        for (flag in flags) supportedOptions[flag] = option
    }

    // The set of active hints, for activation of configuration elements.
    // Initially populated with hints for the current operating system and CPU architecture.
    val hints = mutableSetOf(
        // Kotlin knows these operating systems:
        //   UNKNOWN, MACOSX, IOS, LINUX, WINDOWS, ANDROID, WASM, TVOS, WATCHOS
        "OS:${osName()}",
        // Kotlin knows these CPU architectures:
        //   UNKNOWN, ARM32, ARM64, X86, X64, MIPS32, MIPSEL32, WASM32
        "ARCH:${cpuArch()}"
    )

    val jvmArgs = mutableListOf<String>()
    val mainArgs = mutableListOf<String>()
    val vars = mutableMapOf<String, String>()

    // Parse the input arguments. Each input argument becomes an active hint.
    var i = 0
    var afterDivider = false
    while (i < inputArgs.size) {
        val arg = inputArgs[i++]
        if (arg == "--") {
            if (afterDivider) error("Divider symbol (--) may only be given once")
            afterDivider = true
        }
        if (arg in supportedOptions) {
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
            // TODO: There is a third case: no divider is ever declared through the input args.
            //  In that case, we should make a best guess on a per-arg basis whether it's for JVM or main.
            //  We can use config.recognizedJvmArgs (after stripping [:/=].*) and config.allowUnrecognizedJvmArgs.
            if (afterDivider) mainArgs += arg
            else jvmArgs += arg
        }
    }

    // Apply mode hints.
    // TODO: config.modes

    // If a directive is active, do it instead of discovering and launching Java.
    // TODO: config.directives
    //  This needs further thought; it's not clear to me when each directive should
    //  be applied. I don't actually think it's consistent. The dry-run directive for
    //  example should fully parse everything and actually discover the Java, but not
    //  actually launch it -- just emit the arguments that would be passed.
    //  Whereas the help directive should emit the help immediately and exit.

    // Discover Java.
    // TODO: config.rootPaths, config.libjvmSuffixes, config.versionMin, config.versionMax
    val jdkDir = getenv("JAVA_HOME")
    if (jdkDir == null || !File(jdkDir).isDirectory) {
        error("No Java installation found.")
    }
    val libjvmPath = "$jdkDir/lib/server/libjvm.so"

    // TODO: print-java-home directive here.

    // Calculate classpath.
    // TODO: config.classpath
    val fijiDir = getenv("FIJI_HOME") ?: (getenv("HOME") + "/Applications/Fiji.app")
    if (!File(fijiDir).isDirectory) {
        error("No Fiji installation found.")
    }
    val classpath = findJarsAndPlugins(fijiDir)

    // Calculate JVM arguments.
    // TODO: config.jvmArgs, config.maxHeap, config.splashImage
    //  And mix in classpath elements from above, if any.
    val kbMemAvailable = getMemAvailable()
    val mbToUse = 3 * kbMemAvailable / 4 / 1024
    jvmArgs += arrayOf(
        "-Xmx${mbToUse}m",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED",
        "-Djava.class.path=$classpath",
        "-DtestFooJVM=testBarJVM",
    )

    // Calculate main class.
    // TODO: use config.mainClasses
    val mainClassName = "sc.fiji.Main"

    // Calculate main args.
    for (argLine in config.mainArgs) {
        val tokens = argLine.split("|")
        val rules = tokens.slice(0..<tokens.size-1)
        val arg = tokens.last()
        if (rulesApply(rules, hints)) {
            mainArgs.add(interpolate(arg, vars))
        }
    }

    // Emit final configuration.
    println(libjvmPath)
    println(jvmArgs.size)
    for (jvmArg in jvmArgs) println(jvmArg)
    println(mainClassName.replace(".", "/"))
    println(mainArgs.size)
    for (mainArg in mainArgs) println(mainArg)
}

private fun readConfig(tomlPath: String): JaunchConfig {
    val tomlFile = File(tomlPath)
    if (!tomlFile.exists) return JaunchConfig()
    return TomlFileReader(
        inputConfig = TomlInputConfig(
            ignoreUnknownNames = true,
        )
    ).decodeFromFile<JaunchConfig>(serializer(), tomlPath)
}

private fun partition(s: String, delimiter: String): Pair<String, String?> {
    val index = s.indexOf(delimiter)
    return if (index < 0) Pair(s, null) else Pair(s.substring(0, index), s.substring(index + 1))
}

private fun rulesApply(rules: List<String>, hints: Set<String>): Boolean {
    for (rule in rules) {
        val negation = rule.startsWith("!")
        val hint = if (negation) rule.substring(1) else rule
        if (negation && hint in hints) return false
        if (!negation && hint !in hints) return false
    }
    return true
}

/** Replaces ${var} expressions with values from a vars map. */
private fun interpolate(s: String, vars: Map<String, String>): String {
    val result = StringBuilder()
    var pos = 0
    while (true) {
        // Find the next variable expression.
        val start = s.indexOf("\${", pos)
        val end = if (start < 0) -1 else s.indexOf("}", start + 2)
        if (start < 0 || end < 0) {
            // No more variable expressions found; append remaining string.
            result.append(s.substring(pos))
            break
        }

        // Add the text before the variable.
        result.append(s.substring(pos, start))

        // Evaluate the expression and add it to the result.
        // If the variable is missing from the map, just leave the expression alone.
        val name = s.substring(start + 2, end)
        val value = vars.getOrElse(name) { s.substring(start, end) }
        result.append(value)

        // Advance the position beyond the variable expression.
        pos = end + 1
    }
    return result.toString()
}

@OptIn(ExperimentalNativeApi::class)
private fun osName(): String {
    return Platform.osFamily.name
}

@OptIn(ExperimentalNativeApi::class)
private fun cpuArch(): String {
    return Platform.cpuArchitecture.name
}

private fun findJarsAndPlugins(fijiDir: String): String {
    val jarsDir = File("$fijiDir/jars")
    val bioFormatsDir = File("$fijiDir/jars/bio-formats")
    val pluginsDir = File("$fijiDir/plugins")

    val jarFiles = jarsDir.listFiles() + bioFormatsDir.listFiles() + pluginsDir.listFiles()
    return jarFiles.filter { it.isFile && it.absolutePath.endsWith(".jar") }.joinToString(":") { it.absolutePath }
}

private fun getMemAvailable(): Int {
    val kbMemAvailable = 20454432
    /*
    try {
        val memInfo = processFileToString("/proc/meminfo")
        val kbMemAvailable = memInfo.lines().firstOrNull { it.startsWith("MemAvailable:") }
            ?.substringAfter(":").trim()?.filter { it.isDigit() }?.toIntOrNull()

        return kbMemAvailable ?: 0
    } catch (e: Exception) {
        e.printStackTrace()
        return 0
    }
    */
    return kbMemAvailable
}
