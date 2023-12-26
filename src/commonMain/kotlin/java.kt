data class JavaConstraints(
    val libjvmSuffixes: List<String>,
    val allowWeirdJvms: Boolean,
    val versionMin: String?,
    val versionMax: String?,
    val distrosAllowed: List<String>,
    val distrosBlocked: List<String>,
    val osAliases: List<String>,
    val archAliases: List<String>,
)

/**
 * A Java installation, rooted at a particular directory.
 *
 * This class contains heuristics for discerning the Java installation's
 * version, distribution, operating system, and CPU architecture.
 *
 * The heuristics consist of three increasingly aggressive techniques:
 *
 * 1. Look for keyword tokens in the root directory name. Fast but fragile.
 *
 * 2. Look inside the `$rootPath/release` file for useful keys such as
 *    IMPLEMENTOR, IMPLEMENTOR_VERSION, JAVA_VERSION, OS_NAME, and OS_ARCH.
 *    This is more reliable than examining the root directory name, but can still fail:
 *    - Some flavors (JBRSDK 8, Corretto 8) may be missing this file.
 *    - Some flavors (JBRSDK 8, macOS Adopt 8, macOS Zulu 8) do not have IMPLEMENTOR.
 *    - Some other flavors (JBRSDK 11.0.6, macOS Adopt 9) put "N/A" for IMPLEMENTOR.
 *    - OS_ARCH and OS_NAME at least are reliably present in all known distributions.
 *
 * 3. Invoke `$rootPath/bin/java Props` to run a simple class that prints
 *    a table of `System.getProperties()` to stdout. Slow but reliable.
 *
 * We looked into faster ways than launching Java to extract the properties, but
 * the way they are embedded in the Java installation varies greatly. In older versions
 * of Java, they can be found in META-INF/MANIFEST.MF inside rt.jar (or classes.jar),
 * but in later versions of Java they are embedded in a compressed file lib/modules.
 * Since Jaunch's primary purpose is to *launch Java*, it seems OK for the Jaunch
 * configuration tool to launch Java as needed to extract Java system properties.
 *
 * The logic is coded so that less expensive techniques are tried first, and metadata
 * is cached, so that more expensive techniques only trigger when necessary.
 */
class JavaInstallation(
    val rootPath: String,
    val constraints: JavaConstraints,
) {
    val libjvmPath: String? by lazy { findLibjvm() }
    val version: String? by lazy { guessJavaVersion() }
    val distro: String? by lazy { guessDistribution() }
    val osName: String? by lazy { guessOperatingSystemName() }
    val cpuArch: String? by lazy { guessCpuArchitecture() }
    val releaseInfo: Map<String, String>? by lazy { readReleaseInfo() }
    val sysProps: Map<String, String>? by lazy { askJavaForSystemProperties() }
    val conforms: Boolean by lazy { checkConstraints() }

    override fun toString(): String {
        return listOf(
            "root: $rootPath",
            "libjvm: $libjvmPath",
            "version: $version",
            "distro: $distro",
            "OS name: $osName",
            "CPU arch: $cpuArch",
            "release file:${bulletList(releaseInfo)}",
            "system properties:${bulletList(sysProps)}",
        ).joinToString(NL)
    }

    // -- Lazy evaluation functions --

    private fun findLibjvm(): String? {
        return constraints.libjvmSuffixes.map { File("$rootPath/$it") }.firstOrNull { it.exists }?.path
    }

    private fun guessJavaVersion(): String? {
        debug("Guessing Java version...")
        return extractJavaVersion(File(rootPath).name) ?:
            releaseInfo?.get("JAVA_VERSION") ?: sysProps?.get("java.version")
    }

    private fun guessDistribution(): String? {
        debug("Guessing Java distribution...")

        val distroMap = aliasMap(constraints.distrosAllowed + constraints.distrosBlocked)
        return guessDistro(distroMap, File(rootPath).name) ?:
            guessDistro(distroMap, releaseInfo?.get("IMPLEMENTOR_VERSION")) ?:
            guessDistro(distroMap, releaseInfo?.get("IMPLEMENTOR")) ?:
            guessDistro(distroMap, sysProps?.get("java.vendor.version")) ?:
            guessDistro(distroMap, sysProps?.get("java.vendor")) ?:
            sysProps?.get("java.vendor")
    }

    private fun guessOperatingSystemName(): String? {
        debug("Guessing OS name...")
        return guessField(constraints.osAliases, "OS_NAME", "os.name")
    }

    private fun guessCpuArchitecture(): String? {
        debug("Guessing CPU architecture...")
        return guessField(constraints.archAliases, "OS_ARCH", "os.arch")
    }

    /** Reads metadata from the `release` file. */
    private fun readReleaseInfo(): Map<String, String>? {
        debug("Reading release file...")

        val releaseFile = File("$rootPath/release")
        if (!releaseFile.exists) return null
        return linesToMap(releaseFile.readLines(), "=", stripQuotes=true)
    }

    /** Calls `bin/java Props` to harvest system properties from the horse's mouth. */
    private fun askJavaForSystemProperties(): Map<String, String>? {
        debug("Invoking `\"", rootPath, SLASH, "bin", SLASH, "java\" Props`...")

        val binJava = File("$rootPath/bin/java")
        if (!binJava.exists) return null
        val stdout = execute("$binJava Props") ?: return null
        return linesToMap(stdout, "=")
    }

    private fun checkConstraints(): Boolean {
        val strict = !constraints.allowWeirdJvms

        // Ensure libjvm is present.
        if (libjvmPath == null) return fail("No JVM library found.")

        // Check OS name and CPU architecture constraints.
        if (osName == null && strict)
            return fail("Unknown operating system, and weird JVMs are disallowed.")
        if (osName != null && osName != OS_NAME)
            return fail("Operating system '$osName' does not match current platform $OS_NAME")
        if (cpuArch == null && strict)
            return fail("Unknown CPU architecture, and weird JVMs are disallowed.")
        if (cpuArch != null && cpuArch != CPU_ARCH)
            return fail("CPU architecture '$cpuArch' does not match current architecture $CPU_ARCH")

        // Check Java version constraints.
        if (constraints.versionMin != null || constraints.versionMax != null) {
            if (version == null && strict)
                return fail("Version constraints exist, but version is unknown and weird JVMs are disallowed.")
            if (version != null) {
                if (versionOutOfBounds(version!!, constraints.versionMin, constraints.versionMin))
                    return fail("Version '$version' is outside specified bounds " +
                            "[${constraints.versionMin}, ${constraints.versionMin}].")
            }
        }

        // Check Java distro constraints.
        if (constraints.distrosBlocked.any { it.startsWith("$distro:") })
            return fail("Distro '$distro' is on the blocklist.")
        if (strict && constraints.distrosAllowed.none { it.startsWith("$distro:") })
            return fail("Distro '$distro' is not on the allowlist, and weird JVMs are disallowed.")

        // All checks passed!
        return true
    }

    // -- Helper methods --

    private fun bulletList(map: Map<String, String>?, bullet: String = "* "): String {
        return when {
            map == null -> " <none>"
            map.isEmpty() -> " <empty>"
            else -> "$NL$bullet" + map.entries.joinToString("$NL$bullet")
        }
    }

    private fun guessDistro(distroMap: Map<String, List<String>>, s: String?): String? {
        if (s == null) return null
        val slow = s.lowercase()
        return distroMap.entries.firstOrNull { (_, aliases) -> aliases.any { slow.contains(it) } }?.key
    }

    private fun guessField(aliasLines: Iterable<String>, releaseField: String, propsField: String): String? {
        val rootDirName = File(rootPath).name.lowercase()
        val aliasMap = aliasMap(aliasLines)

        val alias =
            // Look for an alias embedded in the root directory name.
            aliasMap.values.flatten().firstOrNull { rootDirName.contains(it) } ?:
            // Look for the field in the release file.
            releaseInfo?.get(releaseField)?.lowercase() ?:
            // Extract the field from Java's system properties.
            sysProps?.get(propsField)?.lowercase() ?: return null

        // Find the canonical name of the extracted value.
        return aliasMap.entries.firstOrNull { (_, aliases) -> aliases.contains(alias) }?.key ?: alias
    }

    private fun linesToMap(lines: Iterable<String>, delimiter: String, stripQuotes: Boolean = false): Map<String, String> {
        return lines.map { it.trim() }.filter { it.indexOf(delimiter) >= 0 }.associate {
            var (k, v) = it.split(delimiter, limit=2)
            if (stripQuotes && v.startsWith("\"") && v.endsWith("\""))
                v = v.substring(1, v.lastIndex)
            Pair(k, v)
        }
    }

    private fun aliasMap(aliasLines: Iterable<String>): Map<String, List<String>> {
        return linesToMap(aliasLines, ":").map { (k, v) -> Pair(k, v.split(",")) }.toMap()
    }

    private fun fail(vararg args: Any): Boolean { debug(*args); return false }
}

fun extractJavaVersion(root: String): String? {
    // Many CPU architecture tokens are hard to distinguish from version digits.
    // So first, do some preprocessing to remove such tokens from the root path string.
    val confusingPatterns = arrayOf(
        "aarch(32|64)",
        "amd(32|64)",
        "i[3456]86",
        "x86[-_](32|64)",
        "x(64|86)",
    )
    val confusingStuff = Regex("(${confusingPatterns.joinToString("|")})")
    val rootString = root.replace(confusingStuff, "").lowercase()
    debug("* rootString -> ", rootString)

    // Now, extract version strings from the preprocessed root path string.
    val versionPattern = "\\d+([-+_.]\\d+)*"

    // First, find the most obvious '8u' syntax, which needs to take precedence.
    val versions8u = extractMatches("8u\\d+", rootString).
        map { it.replace("8u", "1.8.0_") }
    debug("* versions8u -> ", versions8u)
    if (versions8u.isNotEmpty()) return versions8u[0]

    // Next, look for version strings with a telltale prefix like "java" or "jdk".
    //
    // Why is "hotspot" on the list, you might wonder?
    // It's Eclipse Temurin's fault, because it uses root folders like
    // 'OpenJDK21U-jdk_x64_linux_hotspot_21.0.1_12', and we don't want the
    // 'OpenJDK21U' part to take precedence over the later '21.0.1_12'.
    val prefixPattern = "(java|jdk|hotspot)[-_]?"
    val versionsPrefixed = extractMatches("$prefixPattern$versionPattern", rootString).
        map { it.replace(Regex("^$prefixPattern"), "") }.map(::cleanupVersion)
    debug("* versionsPrefixed -> ", versionsPrefixed)
    if (versionsPrefixed.isNotEmpty()) return versionsPrefixed[0]

    // Finally, look for unprefixed version strings.
    val versionsBare = extractMatches(versionPattern, rootString).map(::cleanupVersion)
    debug("* versionsBare -> ", versionsBare)
    if (versionsBare.isNotEmpty()) return versionsBare[0]

    return null
}

fun extractMatches(pattern: String, s: String): List<String> {
    return Regex(pattern).findAll(s).map { it.value }.toList()
}

fun cleanupVersion(v: String): String {
    // Prepend `1.` as appropriate.
    return v.replace(Regex("^[2345678](\\D|$)"), "1.$0")
    // TODO: Should also remove `1.` from strings like `1.11.0`...
}

fun versionOutOfBounds(version: String, min: String?, max: String?): Boolean {
    return compareVersions(version, min) >= 0 && compareVersions(version, max) <= 0
}

fun compareVersions(v1: String, v2: String?): Int {
    if (v2 == null) return 0 // Hacky but effective.

    // Extract the list of digits for each version.
    val re = Regex("\\d+")
    val digits1 = re.findAll(v1).map { it.value.toLong() }.toList()
    val digits2 = re.findAll(v2).map { it.value.toLong() }.toList()

    // Compare digit by digit.
    return digits1.zip(digits2).map { (e1, e2) -> e1.compareTo(e2) }.firstOrNull { it != 0 } ?: 0
}
