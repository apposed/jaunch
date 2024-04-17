// Functions for working with version strings.

val JAUNCH_VERSION = "{{{VERSION}}}"
val JAUNCH_BUILD = "{{{GIT-HASH}}}"

fun versionOutOfBounds(version: String, min: String?, max: String?): Boolean {
    return compareVersions(version, min) < 0 || compareVersions(version, max) > 0
}

fun compareVersions(v1: String, v2: String?): Int {
    if (v2 == null) return 0 // Hacky but effective.

    // Extract the list of digits for each version.
    val digits1 = versionDigits(v1)
    val digits2 = versionDigits(v2)

    // Compare digit by digit.
    return digits1.zip(digits2).map { (e1, e2) -> e1.compareTo(e2) }.firstOrNull { it != 0 } ?: 0
}

fun versionDigits(v: String): List<Int> {
    // NB: Strip leading 1. prefix, as described in the jaunch.toml
    // documentation's section on java-version-min & java-version-max.
    // This is a Java-specific HACK, but won't impact Python because it's already at 3.x.
    val vv = if (v.startsWith("1.")) v.substring(2) else v
    return Regex("\\d+").findAll(vv).map { it.value.toInt() }.toList()
}
