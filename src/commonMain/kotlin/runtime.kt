// Base class for runtime environment configuration state.

class ProgramArgs {
    val runtime = mutableListOf<String>()
    val main = mutableListOf<String>()
    val ambiguous = mutableListOf<String>()
}

typealias DirectivesMap = Map<String, (ProgramArgs) -> Unit>

abstract class RuntimeConfig(
    val prefix: String,
    val directive: String,
    val recognizedArgs: Array<String>
) {
    val runtimeArgs = mutableListOf<String>()
    val mainArgs = mutableListOf<String>()
    var mainProgram: String? = null

    /** Dictionary of supported directives and their associated implementations. */
    abstract val supportedDirectives: DirectivesMap

    /**
     * Configures the runtime according to the given [JaunchConfig] and friends.
     *
     * It is up to each runtime what "configuring" it means, but typically it involves
     * searching for an installation on the system matching the configuration constraints.
     *
     * When configuration is complete, the [runtimeArgs], [mainArgs], and [mainProgram]
     * should be populated as appropriate for the runtime to emit its final launch
     * configuration via the [launch] method.
     */
    abstract fun configure(
        configDir: File,
        config: JaunchConfig,
        hints: MutableSet<String>,
        vars: MutableMap<String, String>
    )

    /** Gets the launch directive block for this runtime configuration. */
    abstract fun launch(args: ProgramArgs): List<String>

    /** @return true iff the given argument matches one of the [recognizedArgs]. */
    fun recognizes(arg: String): Boolean {
        return arg in recognizedArgs
    }

    /**
     * Attempt to execute the given directive.
     * @param directive The directive to maybe execute.
     * @param args The arguments passed by the user, which the directive might wish to examine.
     * @return true iff the directive was successfully executed.
     */
    fun tryDirective(directive: String, args: ProgramArgs): Boolean {
        val doDirective = supportedDirectives[directive] ?: return false
        debug("$prefix: executing directive: $directive")
        doDirective(args)
        return true
    }
}
