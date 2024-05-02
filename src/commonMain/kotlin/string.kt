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

