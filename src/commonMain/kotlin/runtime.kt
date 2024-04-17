// Base class for runtime environment configuration state.

abstract class RuntimeConfig(
    val name: String,
    val directive: String,
    val recognizedArgs: Array<String>
) {
    val runtimeArgs = mutableListOf<String>()
    val mainArgs = mutableListOf<String>()
    var mainProgram: String? = null

    abstract fun configure(configDir: File, config: JaunchConfig, hints: MutableSet<String>, vars: MutableMap<String, String>)
    abstract fun nativeConfig(): String
    abstract fun home(): String
    abstract fun info(): String
    abstract fun dryRun(): String
}
