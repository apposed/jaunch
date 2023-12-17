import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.file.TomlFileReader
import kotlinx.serialization.serializer

/*
== DESIGN GOALS ==

Support for launching *your* JVM-based application.
- jaunch.exe is the Kotlin program. It does not need to be modified.
- The native launcher (built from jaunch.c) should be named whatever you want. E.g. fiji.exe.
- fiji.toml is the configuration that jaunch.exe uses to decide how to behave.
  - When fiji.exe invokes jaunch.exe, it passes `fiji` (can I do this from cross-platform C?) to jaunch.exe.
- In this way, there can be multiple different launchers that all lean on the same jaunch.exe.
  - (Or, if this turns out to be "hard", we can just have jaunch.toml.)

Discover available Javas from:
- Subfolders of the application (i.e. bundled Java).
- Known OS-specific system installation locations.
  - /usr/libexec/java_home (macOS)
  - /usr/lib/update-java-alternatives (Linux)
  - Windows registry?
- Known tool-specific installation locations.
  - sdkman
  - install-jdk
  - cjdk
  - conda (base only?)
  - brew
  - scoop
This can be done in general by having a hardcoded list of directories in the default CFG content.
- Q: Should the directory list be platform-specific?
  - A: Probably want to have common dirs list, plus additions per platform.
       Can make each platform its own section with the same schema as the base one.

... more to come ...
*/
fun main(args: Array<String>) {
    val config = TomlFileReader(
        inputConfig = TomlInputConfig(
            ignoreUnknownNames = true,
        )
    ).decodeFromFile<Config>(serializer(), "jaunch.toml")
    println(config)

    val jdkDir = getenv("JAVA_HOME")

    if (jdkDir == null || !File(jdkDir).isDirectory) {
        error("No Java installation found.")
    }

    val fijiDir = getenv("FIJI_HOME") ?: (getenv("HOME") + "/Applications/Fiji.app")

    if (!File(fijiDir).isDirectory) {
        error("No Fiji installation found.")
    }

    val classpath = findJarsAndPlugins(fijiDir)

    val kbMemAvailable = getMemAvailable()
    val mbToUse = 3 * kbMemAvailable / 4 / 1024

    val libjvmPath = "$jdkDir/lib/server/libjvm.so"
    val jvmArgs = arrayOf(
        "-Xmx${mbToUse}m",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED",
        "-Djava.class.path=$classpath",
        "-DtestFooJVM=testBarJVM",
    )
    val mainClassName = "sc.fiji.Main"
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
