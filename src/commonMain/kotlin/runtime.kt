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
        vars: Vars
    )

    /** Populate variables with information about this runtime. */
    abstract fun injectInto(vars: Vars)

    /** Get the launch directive block for this runtime configuration. */
    abstract fun launch(args: ProgramArgs): List<String>

    /**
     * Perform any runtime-specific argument processing here.
     */
    abstract fun processArgs(args: MutableList<String>)

    /**
     * Check whether the given argument matches one of the [recognizedArgs].
     *
     * - For a non-matching argument, returns 0.
     * - For a standalone matching argument, returns 1.
     * - For an argument that takes additional space-separated arguments as parameters,
     *   returns 1 + the number of parameters. For example, the argument `-c` will return 2 when
     *   queried against a runtime like Python which has `-c cmd` on its [recognizedArgs] list.
     *
     * @return a non-negative integer as described above.
     */
    fun recognizes(arg: String): Int {
        return recognizedArgs.map {
            val tokens = it.split(" ")
            val number = tokens.size
            if (number > 0 && argMatches(tokens[0], arg)) number else 0
        }.firstOrNull { it > 0 } ?: 0
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

    /** Helper method for [injectInto]. */
    protected fun maybeAssign(vars: Vars, key: String, value: Any?) {
        if (value != null) vars["$prefix.$key"] = value
    }

    private fun argMatches(expected: String, actual: String): Boolean {
        return when {
            expected.isEmpty() -> false
            expected.endsWith("*") -> {
                val prefix = expected.substring(0, expected.length - 1)
                actual.startsWith(prefix)
            }
            else -> expected == actual
        }
    }
}
