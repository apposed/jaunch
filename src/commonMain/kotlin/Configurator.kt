import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.file.TomlFileReader
import kotlinx.serialization.serializer

/*
*/
fun main(args: Array<String>) {
    /*
    val config = TomlFileReader(
        inputConfig = TomlInputConfig(
            ignoreUnknownNames = true,
        )
    ).decodeFromFile<Config>(serializer(), "jaunch.toml")
    println(config)
    */

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
