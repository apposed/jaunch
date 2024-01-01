import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.file.TomlFileReader
import kotlinx.serialization.serializer

fun readConfig(tomlPath: String): JaunchConfig {
    debug("Reading config file: $tomlPath");
    val tomlFile = File(tomlPath)
    if (!tomlFile.exists) return JaunchConfig()
    return TomlFileReader(
        inputConfig = TomlInputConfig(
            ignoreUnknownNames = true,
        )
    ).decodeFromFile(serializer(), tomlPath)
}

val BUFFER_SIZE = 65536
