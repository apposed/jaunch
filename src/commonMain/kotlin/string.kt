// Functions for parsing strings.

fun linesToMap(lines: Iterable<String>, delimiter: String, stripQuotes: Boolean = false): Map<String, String> {
    return lines.map { it.trim() }.filter { it.indexOf(delimiter) >= 0 }.associate {
        var (k, v) = it.split(delimiter, limit=2)
        if (stripQuotes && v.startsWith("\"") && v.endsWith("\""))
            v = v.substring(1, v.lastIndex)
        Pair(k, v)
    }
}

fun linesToMapOfLists(lines: Iterable<String>): Map<String, List<String>> {
    return linesToMap(lines, ":").map { (k, v) -> Pair(k, v.split(",")) }.toMap()
}

fun extractMatches(pattern: String, s: String): List<String> {
    return Regex(pattern).findAll(s).map { it.value }.toList()
}

infix fun String.bisect(delimiter: Char): Pair<String, String?> {
    val index = indexOf(delimiter)
    return if (index < 0) this to null else substring(0, index) to substring(index + 1)
}
