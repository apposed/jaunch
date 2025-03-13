// Logic for discovery and inspection of Java Virtual Machine (JVM) installations.

import kotlin.math.min

data class JvmConstraints(
    val configDir: File,
    val libSuffixes: List<String>,
    val allowWeirdRuntimes: Boolean,
    val versionMin: String?,
    val versionMax: String?,
    val distrosAllowed: List<String>,
    val distrosBlocked: List<String>,
    val osAliases: List<String>,
    val archAliases: List<String>,
)

class JvmRuntimeConfig(recognizedArgs: Array<String>) :
    RuntimeConfig("jvm", "JVM", recognizedArgs)
{
    private var java: JavaInstallation? = null
    private var defaultClasspath: List<String> = emptyList()
    private var defaultMaxHeap: String? = null

    override val supportedDirectives: DirectivesMap = mutableMapOf(
        "print-class-path" to { args -> printlnErr(classpath(args) ?: "<none>") },
        "print-java-home" to { _ -> printlnErr(javaHome()) },
        "print-java-info" to { _ -> printlnErr(javaInfo()) },
    )

    override fun configure(
        configDir: File,
        config: JaunchConfig,
        hints: MutableSet<String>,
        vars: Vars
    ) {
        // Calculate all the places to search for Java.
        val jvmRootPaths = vars.calculate(config.jvmRootPaths, hints)
                .flatMap { glob(it) }
                .filter { File(it).isDirectory }
                .toSet()

        debug()
        debug("Root paths to search for Java:")
        jvmRootPaths.forEach { debug("* ", it) }

        // Calculate all the places to look for the JVM library.
        val libjvmSuffixes = vars.calculate(config.jvmLibSuffixes, hints)

        debug()
        debug("Suffixes to check for libjvm:")
        libjvmSuffixes.forEach { debug("* ", it) }

        // Calculate Java distro and version constraints.
        val allowWeirdJvms = config.jvmAllowWeirdRuntimes ?: false
        val distrosAllowed = vars.calculate(config.jvmDistrosAllowed, hints)
        val distrosBlocked = vars.calculate(config.jvmDistrosBlocked, hints)
        val osAliases = vars.calculate(config.osAliases, hints)
        val archAliases = vars.calculate(config.archAliases, hints)
        val constraints = JvmConstraints(
            configDir, libjvmSuffixes,
            allowWeirdJvms, config.jvmVersionMin, config.jvmVersionMax,
            distrosAllowed, distrosBlocked, osAliases, archAliases
        )

        // Discover Java.
        debug()
        debug("Discovering Java installations...")
        var java: JavaInstallation? = null
        for (jvmPath in jvmRootPaths) {
            debug("Analyzing candidate JVM directory: '", jvmPath, "'")
            val javaCandidate = JavaInstallation(jvmPath, constraints)
            if (javaCandidate.conforms) {
                // Installation looks good! Moving on.
                java = javaCandidate
                break
            }
        }
        if (java == null) {
            debug("No Java installation found.")
            return
        }
        debug("Successfully discovered Java installation:")
        debug("* rootPath -> ", java.rootPath)
        debug("* libjvmPath -> ", java.libjvmPath ?: "<null>")
        debug("* binJava -> ", java.binJava ?: "<null>")

        // Apply JAVA: hints.
        val mv = java.majorVersion
        if (mv != null) {
            hints += "JAVA:$mv"
            // If Java version is OVER 9000, something went wrong in the parsing.
            // Let's not explode the hints set with too many bogus values.
            for (v in 0..min(mv, 9000)) hints += "JAVA:$v+"
        }
        debug("* hints -> ", hints)

        // Calculate classpath.
        defaultClasspath = vars.calculate(config.jvmClasspath, hints)
        debugList("Default classpath:", defaultClasspath)

        // Save the default max heap value.
        defaultMaxHeap = vars.calculate(config.jvmMaxHeap, hints)
        debug("Default max heap: $defaultMaxHeap")

        // Calculate JVM arguments.
        runtimeArgs += vars.calculate(config.jvmRuntimeArgs, hints)
        debugList("JVM arguments calculated:", runtimeArgs)

        // Calculate main class.
        debug()
        debug("Calculating main class name...")
        val mainClassNames = vars.calculate(config.jvmMainClass, hints)
        mainProgram = mainClassNames.firstOrNull()
        debug("mainProgram -> ", mainProgram ?: "<null>")

        // Calculate main args.
        mainArgs += vars.calculate(config.jvmMainArgs, hints)
        debugList("Main arguments calculated:", mainArgs)

        this.java = java
    }

    override fun injectInto(vars: Vars) {
        maybeAssign(vars, "mainClass", mainProgram)
        maybeAssign(vars, "rootPath", java?.rootPath)
        maybeAssign(vars, "libjvmPath", java?.libjvmPath)
        maybeAssign(vars, "binJava", java?.binJava)
        maybeAssign(vars, "version", java?.version)
        maybeAssign(vars, "distro", java?.distro)
        maybeAssign(vars, "osName", java?.osName)
        maybeAssign(vars, "cpuArch", java?.cpuArch)
    }

    override fun tweakArgs(args: MutableList<String>) {
        // Append or amend argument declaring classpath elements.
        val classpath = defaultClasspath.flatMap { glob(it) }.distinct()
        debugList("Classpath finalized:", classpath)
        if (classpath.isNotEmpty()) {
            val classpathString = classpath.joinToString(COLON)
            val cpIndex = args.indexOfFirst { it.startsWith("-Djava.class.path=") }
            if (cpIndex >= 0) {
                // Append to existing `-Djava.class.path` argument.
                args[cpIndex] += "$COLON$classpathString"
                debug("Extended classpath arg: ${args[cpIndex]}")
            } else {
                // No `-Djava.class.path` argument, so we add one.
                args += "-Djava.class.path=$classpathString"
                debug("Added classpath arg: ${args.last()}")
            }
        }

        debug()
        debug("Finalizing max heap settings...")

        // Add a max heap argument if appropriate.
        val mxIndex = args.indexOfFirst { it.startsWith("-Xmx") }
        if (mxIndex < 0 && defaultMaxHeap != null) {
            // No `-Xmx` argument, so we add one.
            args += "-Xmx$defaultMaxHeap"
            debug("Added maxHeap arg: ${args.last()}")
        }

        // Squash multiple memory arguments.
        val argCountBefore = args.size
        squashExtraArgs(args, "-Xms")
        squashExtraArgs(args, "-Xmx")
        val squashedCount = args.size - argCountBefore
        if (squashedCount > 0) debug("Squashed $squashedCount args")

        // Expand % signs in memory-related arguments.
        for (prefix in listOf("-Xms", "-Xmx")) {
            for ((i, v) in args.withIndex()) {
                if (!v.startsWith(prefix) || '%' !in v) continue
                val memPercent = args[i].substring(prefix.length)
                val mem = calculateMemory(memPercent)
                val expanded = "$prefix$mem"
                debug("Expanding % in JVM runtime arg: ${args[i]} -> $expanded")
                args[i] = expanded
            }
        }
    }

    override fun launch(args: ProgramArgs): List<String> {
        val libjvmPath = java?.libjvmPath ?: fail("No matching Java installations found.")
        val mainClass = mainProgram ?: fail("No Java main program specified.")

        dryRun(buildString {
            append(java?.binJava ?: "java")
            args.runtime.forEach { append(" $it") }
            append(" $mainProgram")
            args.main.forEach { append(" $it") }
        })

        return buildList {
            add(libjvmPath)
            add(args.runtime.size.toString())
            addAll(args.runtime)
            add(mainClass.replace(".", "/"))
            addAll(args.main)
        }
    }

    // -- Directive handlers --

    fun classpath(args: ProgramArgs, divider: String = NL): String? {
        val prefix = "-Djava.class.path="
        val classpathArg = args.runtime.firstOrNull { it.startsWith(prefix) } ?: return null
        return classpathArg.substring(prefix.length).replace(COLON, divider)
    }

    fun javaHome(): String {
        return java?.rootPath ?: fail("No matching Java installations found.")
    }

    fun javaInfo(): String {
        return java?.toString() ?: fail("No matching Java installations found.")
    }
}

/**
 * A Java installation, rooted at a particular directory.
 *
 * This class contains heuristics for discerning the Java installation's
 * version, distribution, operating system, and CPU architecture.
 *
 * The heuristics consist of three increasingly aggressive techniques:
 *
 * 1. Look for keyword tokens in the JVM directory name. Fast but fragile.
 *
 * 2. Look inside the `$rootPath/release` file for useful keys such as
 *    IMPLEMENTOR, IMPLEMENTOR_VERSION, JAVA_VERSION, OS_NAME, and OS_ARCH.
 *    This is more reliable than examining the JVM directory name, but can still fail:
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
 * TODO: Actually cache the metadata. ;-)
 */
class JavaInstallation(
    val rootPath: String,
    val constraints: JvmConstraints,
) {
    val libjvmPath: String? by lazy { findLibjvm() }
    val binJava: String? by lazy { findBinJava() }
    val version: String? by lazy { guessJavaVersion() }
    val distro: String? by lazy { guessDistribution() }
    val osName: String? by lazy { guessOperatingSystemName() }
    val cpuArch: String? by lazy { guessCpuArchitecture() }
    val releaseInfo: Map<String, String>? by lazy { readReleaseInfo() }
    val sysProps: Map<String, String>? by lazy { askJavaForSystemProperties() }
    val conforms: Boolean by lazy { checkConstraints() }

    /**
     * Gets the major version digit (i.e. "Java product version") of the Java installation.
     * For versions 1.8 and prior, the second digit is returned:
     *
     * * 1.0.x -> 0
     * * 1.1.x -> 1
     * * 1.2.x -> 2
     * * ...
     * * 1.8.x -> 8
     * * 9.x.y -> 9
     * * 10.x.y -> 10
     * * 11.x.y -> 11
     * * etc.
     */
    val majorVersion: Int?
        get() {
            val digits = versionDigits(version ?: return null)
            if (digits.isEmpty()) return null
            var major = digits[0]
            if (major == 1 && digits.size > 1) major = digits[1] // e.g. 1.8 -> 8
            return major
        }

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
        return constraints.libSuffixes.map { File("$rootPath$SLASH$it") }.firstOrNull { it.exists }?.path
    }

    private fun findBinJava(): String? {
        val extension = if (OS_NAME == "WINDOWS") ".exe" else ""
        for (candidate in arrayOf("bin", "jre${SLASH}bin")) {
            val javaFile = File("$rootPath$SLASH$candidate${SLASH}java$extension")
            if (javaFile.exists) return javaFile.path
        }
        return null
    }

    private fun guessJavaVersion(): String? {
        return guess("Java version") {
            extractJavaVersion(File(rootPath).name) ?:
            releaseInfo?.get("JAVA_VERSION") ?:
            sysProps?.get("java.version")
        }
    }

    private fun guessDistribution(): String? {
        return guess("Java distribution") {
            val distroMap = aliasMap(constraints.distrosAllowed + constraints.distrosBlocked)
            guessDistro(distroMap, File(rootPath).name) ?:
            guessDistro(distroMap, releaseInfo?.get("IMPLEMENTOR_VERSION")) ?:
            guessDistro(distroMap, releaseInfo?.get("IMPLEMENTOR")) ?:
            guessDistro(distroMap, sysProps?.get("java.vendor.version")) ?:
            guessDistro(distroMap, sysProps?.get("java.vendor")) ?: sysProps?.get("java.vendor")
        }
    }

    private fun guessOperatingSystemName(): String? {
        return guess("OS name") {
            guessField(constraints.osAliases, "OS_NAME", "os.name")
        }
    }

    private fun guessCpuArchitecture(): String? {
        return guess("CPU architecture") {
            guessField(constraints.archAliases, "OS_ARCH", "os.arch")
        }
    }

    private fun guess(label: String, doGuess: () -> String?): String? {
        debug("Guessing $label...")
        val result = doGuess()
        debug("-> $label: $result")
        return result
    }

    /** Reads metadata from the `release` file. */
    private fun readReleaseInfo(): Map<String, String>? {
        debug("Reading release file...")
        val releaseFile = File("$rootPath/release")
        if (!releaseFile.exists) return null
        return linesToMap(releaseFile.lines(), "=", stripQuotes=true)
    }

    /** Calls `java Props` to harvest system properties from the horse's mouth. */
    private fun askJavaForSystemProperties(): Map<String, String>? {
        val javaExe = binJava
        if (javaExe == null) {
            debug("Java executable does not exist")
            return null
        }
        debug("Invoking `\"", javaExe, "\" Props`...")
        // NB: Temporarily change the current working directory to the one containing
        // the Props.class helper program. This lets us invoke the java executable
        // in a simpler way, without needing to pass something like `-cp $configDir`,
        // which creates more quoting complexity, especially on Windows.
        val cwd = getcwd()
        setcwd(constraints.configDir.path)
        val stdout = execute("\"$javaExe\" Props") ?: return null
        setcwd(cwd)
        return linesToMap(stdout, "=")
    }

    private fun checkConstraints(): Boolean {
        val strict = !constraints.allowWeirdRuntimes

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
                if (versionOutOfBounds(version!!, constraints.versionMin, constraints.versionMax))
                    return fail("Version '$version' is outside specified bounds " +
                            "[${constraints.versionMin}, ${constraints.versionMax}].")
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
        val jvmDirName = File(rootPath).name.lowercase()
        val aliasMap = aliasMap(aliasLines)

        val alias =
            // Look for an alias embedded in the JVM directory name.
            aliasMap.values.flatten().firstOrNull { jvmDirName.contains(it) } ?:
            // Look for the field in the release file.
            releaseInfo?.get(releaseField)?.lowercase() ?:
            // Extract the field from Java's system properties.
            sysProps?.get(propsField)?.lowercase() ?: return null

        // Find the canonical name of the extracted value.
        return aliasMap.entries.firstOrNull { (_, aliases) -> aliases.contains(alias) }?.key ?: alias
    }

    private fun aliasMap(aliasLines: Iterable<String>): Map<String, List<String>> {
        return linesToMap(aliasLines, ":").map { (k, v) -> Pair(k, v.split(",")) }.toMap()
    }

    private fun fail(vararg args: Any): Boolean { debug(*args); return false }
}

internal fun extractJavaVersion(root: String): String? {
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

private fun cleanupVersion(v: String): String {
    // Prepend `1.` as appropriate.
    return v.replace(Regex("^[2345678](\\D|$)"), "1.$0")
    // TODO: Should also remove `1.` from strings like `1.11.0`...
}

private fun calculateMemory(mem: String?): String? {
    if (mem?.endsWith("%") != true) return mem

    // Compute percentage of total available memory.
    val percent = mem.substring(0, mem.lastIndex).toDoubleOrNull() // Double or nothing! XD
    if (percent == null || percent <= 0) {
        warn("Ignoring invalid memory value '", mem, "'")
        return null
    }

    debug()
    debug("Calculating memory (", mem, ")...")
    val memInfo = memInfo()
    if (memInfo.total == null) {
        warn("Cannot determine total memory -- ignoring memory value '", mem, "'")
        return null
    }
    else debug("System reported memTotal of ", memInfo.total.toString())

    val kbValue = (percent * memInfo.total!! / 100 / 1024).toInt()
    if (kbValue <= 9999) return "${kbValue}k"
    val mbValue = kbValue / 1024
    if (mbValue <= 9999) return "${mbValue}m"
    val gbValue = mbValue / 1024
    return "${gbValue}g"
}

private fun squashExtraArgs(list: MutableList<String>, prefix: String) {
    val last = list.indexOfLast { it.startsWith(prefix) }
    var i = 0
    list.removeAll { i++ != last && it.startsWith(prefix) }
}
