import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.file.TomlFileReader
import kotlinx.serialization.serializer
import kotlin.experimental.ExperimentalNativeApi

/*
*/
fun main(args: Array<String>) {
    // Treat both arguments on the CLI and lines of stdin as inputs.
    val executable = args.getOrNull(0)
    val inputArgs = args.slice(1..<args.size) + stdinLines()

    // Discern the directory containing this program.
    val dir = File(executable ?: "").directoryPath

    // Load the configuration from the TOML file.
    val config = TomlFileReader(
        inputConfig = TomlInputConfig(
            ignoreUnknownNames = true,
        )
    ).decodeFromFile<JaunchConfig>(serializer(), "$dir/jaunch.toml")

    // TODO: Get the filename portion of `executable`, strip `.exe` suffix if present, then load that `.toml` as well,
    //  combining it with the general-purpose `jaunch.toml`. Probably want to prepend, not append, but is it OK?

    // Parse the configuration's declared Jaunch options.
    //
    // For each option, we have a string of the form:
    //   --alpha,...,--beta=assignment|Description of what this option does.
    //
    // We need to parse out the help text, the actual flags list, and whether the flags expect an assignment value.

    val supportedOptions = mutableMapOf<String, JaunchOption>()
    for (optionLine in config.supportedOptions) {
        val (flagsString, help) = optionLine.split("|", limit = 2)
        val equals = flagsString.indexOf("=")
        val flagsList = if (equals < 0) flagsString else flagsString.substring(0, equals)
        val assignment = if (equals < 0) null else flagsString.substring(equals + 1)
        val flags = flagsList.split(",")
        val option = JaunchOption(flags.toTypedArray(), assignment, help)
        for (flag in flags) supportedOptions[flag] = option
    }

    // The set of active hints, for activation of configuration elements.
    val hints = mutableSetOf<String>()

    // Parse the input arguments.
    var i = 0
    while (i < inputArgs.size) {
        val arg = inputArgs[i++]
        if (arg in supportedOptions) {
            val option = supportedOptions[arg]
            val param = if (option?.assignment == null || i >= inputArgs.size) null else inputArgs[i++]
            // START HERE: Keep track of these in a good data structure.
        }
    }

    // TODO: Compute platform hints.
    // Kotlin knows these operating systems:
    //   UNKNOWN, MACOSX, IOS, LINUX, WINDOWS, ANDROID, WASM, TVOS, WATCHOS
    hints.add("OS:${osName()}")
    // Kotlin knows these CPU architectures:
    //   UNKNOWN, ARM32, ARM64, X86, X64, MIPS32, MIPSEL32, WASM32
    hints.add("ARCH:${cpuArch()}")

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
    val jvmArgs = arrayOf(
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
    // TODO: use config.mainArgs
    val mainArgs = arrayOf(
        "-DtestFooMain=testBarMain",
    )

    // Emit final configuration.
    println(libjvmPath)
    println(jvmArgs.size)
    for (jvmArg in jvmArgs) println(jvmArg)
    println(mainClassName.replace(".", "/"))
    println(mainArgs.size)
    for (mainArg in mainArgs) println(mainArg)
}

@OptIn(ExperimentalNativeApi::class)
fun osName(): String {
    return Platform.osFamily.name
}

@OptIn(ExperimentalNativeApi::class)
fun cpuArch(): String {
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
