import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.file.TomlFileReader
import kotlinx.serialization.serializer

fun readConfig(tomlPath: String): JaunchConfig {
    val tomlFile = File(tomlPath)
    if (!tomlFile.exists) return JaunchConfig()
    return TomlFileReader(
        inputConfig = TomlInputConfig(
            ignoreUnknownNames = true,
        )
    ).decodeFromFile(serializer(), tomlPath)
}

fun readReleaseInfo(rootDir: File): Map<String, String>? {
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

    val releaseFile = File("$rootDir/release")
    if (!releaseFile.isFile) return null
    // TODO: Instead of failing immediately when the release file is missing, we should try a couple of
    //  heuristics to glean the desired information of Java vendor/distro and version; see above comment.
    val lines = releaseFile.readLines()

    val info = mutableMapOf<String, String>()
    for (line in lines) {
        val trimmed = line.trim()
        val equals = trimmed.indexOf("=\"")
        if (equals < 0 || !trimmed.endsWith("\"")) {
            // We are looking for lines of the form:
            //   KEY="VALUE"
            // and skipping (for now) lines not conforming to this pattern.
            // These release files sometimes have lines in other forms, such as JSON-style map data structures.
            // But we do not need to parse them, because we only care about two specific key/value string pairs.
            continue
        }
        val key = trimmed.substring(0, equals)
        val value = trimmed.substring(equals + 2, trimmed.lastIndex)
        info[key] = value
    }
    return info
}
