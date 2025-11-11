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

/**
 * Parses a package constraint string.
 * Supports a pip-style syntax:
 * - "numpy" (any version)
 * - "numpy==1.24.0" (exact version)
 * - "numpy>=1.20.0" (minimum version)
 * - "numpy<=2.0.0" (maximum version)
 * - "numpy>=1.20.0,<2.0.0" (combined constraints)
 */
data class PackageConstraint(
    val name: String,
    val specs: List<Pair<String, String>> // operator to version
) {
    /** Checks if a package version satisfies this constraint. */
    fun isSatisfiedBy(pkgVersion: String): Boolean {
        // If no version specs, any version is acceptable.
        if (specs.isEmpty()) return true

        // Check each spec.
        for ((operator, requiredVersion) in specs) {
            val cmp = compareVersions(pkgVersion, requiredVersion)
            val satisfied = when (operator) {
                "==", "=" -> cmp == 0
                ">=" -> cmp >= 0
                "<=" -> cmp <= 0
                ">" -> cmp > 0
                "<" -> cmp < 0
                "!=" -> cmp != 0
                else -> false
            }
            if (!satisfied) return false
        }

        return true
    }

    override fun toString(): String {
        return specs.joinToString(", ") { "${it.first}${it.second}" }
    }
}

fun packageConstraint(constraint: String): PackageConstraint {
    val trimmed = constraint.trim()

    // Match package name and version specs.
    val pattern = Regex("^([a-zA-Z0-9_-]+)(.*)$")
    val match = pattern.find(trimmed) ?: return PackageConstraint(trimmed, emptyList())

    val name = match.groupValues[1]
    val specsString = match.groupValues[2].trim()

    if (specsString.isEmpty()) {
        return PackageConstraint(name, emptyList())
    }

    // Parse version specs (e.g., ">=1.20.0,<2.0.0").
    val specs = mutableListOf<Pair<String, String>>()
    val specPattern = Regex("(==|=|>=|<=|>|<|!=)\\s*([^,]+)")

    for (specMatch in specPattern.findAll(specsString)) {
        val operator = specMatch.groupValues[1]
        val version = specMatch.groupValues[2].trim()
        specs.add(Pair(operator, version))
    }

    return PackageConstraint(name, specs)
}
