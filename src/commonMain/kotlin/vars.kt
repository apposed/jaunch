class Vars(appDir: File, configDir: File, exeFile: File?) {
    private val vars = mutableMapOf<String, Any>()

    init {
        vars["app-dir"] = appDir.path
        vars["config-dir"] = configDir.path
        if (exeFile?.exists == true) vars["executable"] = exeFile.path
    }

    fun calculate(items: Array<String>, hints: Set<String>): List<String> {
        return items.mapNotNull { it.evaluate(hints) }.filter { it.isNotEmpty() }
    }

    fun interpolateInto(args: MutableList<String>) {
        val noo = mutableListOf<String>()
        for (arg in args) {
            // Check for the special `@{...}` syntax referencing a list.
            // In this case, we want to replace this item with the elements of
            // the list, rather than stringifying the list object itself.
            if (arg.startsWith("@{") && arg.endsWith("}")) {
                // interpolate list
                val name = arg.substring(2, arg.length - 1)
                val list = vars[name]
                if (list is Iterable<*>) {
                    noo += list.map { it.toString() }
                    continue
                }
            }
            noo += interpolate(arg)
        }
        args.clear()
        args += noo
    }

    operator fun get(key: String): Any? { return vars[key] }
    operator fun set(varName: String, value: Any) { vars[varName] = value }
    operator fun plusAssign(items: Map<String, Any>) { vars += items }

    private fun String.evaluate(hints: Set<String>): String? {
        val tokens = split('|')
        val rules = tokens.subList(0, tokens.lastIndex)
        val value = tokens.last()

        // Check that all rules apply.
        for (rule in rules) {
            val negation = rule.startsWith('!')
            val hint = if (negation) rule.substring(1) else rule
            if (hint in hints == negation) return null
        }

        // Line matches all rules. Populate variable values and return the result.
        return interpolate(value)
    }

    /** Replaces `${var}` expressions with values from the vars map. */
    private fun interpolate(s: String): String = buildString {
        var pos = 0
        while (true) {
            // Find the next variable expression.
            val start = s.indexOf("\${", pos)
            val end = if (start < 0) -1 else s.indexOf('}', start + 2)
            if (start < 0 || end < 0) {
                // No more variable expressions found; append remaining string.
                append(s.substring(pos))
                break
            }

            // Add the text before the expression.
            append(s.substring(pos, start))

            // Evaluate the expression and add it to the result.
            // If the variable name is missing from the map, check for an environment variable.
            // If no environment variable either, then just leave the expression alone.
            val name = s.substring(start + 2, end)
            append(vars[name] ?: getenv(name) ?: s.substring(start, end + 1))

            // Advance the position beyond the variable expression.
            pos = end + 1
        }
    }
}
