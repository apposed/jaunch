import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.file.TomlFileReader
import kotlinx.serialization.serializer

fun readConfig(tomlFile: File): JaunchConfig {
    debug("Reading config file: $tomlFile");
    if (!tomlFile.exists) return JaunchConfig()
    return TomlFileReader(
        inputConfig = TomlInputConfig(
            ignoreUnknownNames = true,
        )
    ).decodeFromFile(serializer(), tomlFile.path)
}

const val BUFFER_SIZE = 65536
