// Functions for parsing strings.

fun linesToMap(lines: Iterable<String>, delimiter: String, stripQuotes: Boolean = false): Map<String, String> {
    return lines.map { it.trim() }.filter { it.indexOf(delimiter) >= 0 }.associate {
        var (k, v) = it.split(delimiter, limit=2)
        if (stripQuotes && v.startsWith("\"") && v.endsWith("\""))
            v = v.substring(1, v.lastIndex)
        Pair(k, v)
    }
}

fun extractMatches(pattern: String, s: String): List<String> {
    return Regex(pattern).findAll(s).map { it.value }.toList()
}

fun calculate(items: Array<String>, hints: Set<String>, vars: Map<String, String>): List<String> {
    return items.mapNotNull { it.evaluate(hints, vars) }.filter { it.isNotEmpty() }
}

private fun String.evaluate(hints: Set<String>, vars: Map<String, String>): String? {
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
    return value interpolate vars
}

/** Replaces `${var}` expressions with values from a vars map. */
private infix fun String.interpolate(vars: Map<String, String>): String = buildString {
    val s = this@interpolate
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
