// Logic for discovery and inspection of Python runtime installations.

class PythonRuntimeConfig(recognizedArgs: Array<String>) :
    RuntimeConfig("python", "LIBPYTHON", recognizedArgs)
{
    var python: PythonInstallation? = null

    override fun configure(config: JaunchConfig, hints: MutableSet<String>, vars: MutableMap<String, String>) {
        TODO("Not yet implemented")
        // TODO -- How to find Python stuff?
        //
        // # TODO: Polish this list.
        // pythonRootPaths = [
        //     '~/miniforge3/envs/*',
        //     '~/mambaforge/envs/*',
        //     '${app-dir}/python',
        //     '${app-dir}/lib/runtime',
        // ]
        //
        // # TODO: Verify this list for all platforms.
        // pythonLibSuffixes = [
        //     'LINUX:lib/libpython3.so',
        //     'MACOSX:lib/libpython3.dylib',
        //     'WINDOWS:lib\libpython3.dll',
        // ]
        //
        // For system Python on Linux:
        //     $ find /usr/lib -name 'libpython*'                                                                                                                                                        #  0 {2024-02-22 13:35:10}
        //     /usr/lib/x86_64-linux-gnu/libpeas-1.0/loaders/libpython3loader.so
        //     /usr/lib/x86_64-linux-gnu/libpython3.11.so.1
        //     /usr/lib/x86_64-linux-gnu/libpython3.11.so.1.0
        //     /usr/lib/python3.11/config-3.11-x86_64-linux-gnu/libpython3.11.so
        //     /usr/lib/libreoffice/program/libpythonloaderlo.so
        //
        // val sitePackagesPath = "$pythonRootPath/lib/python$pythonVersion/site-packages"
        // Two possible formats:
        // - "$sitePackagesPath/*.dist-info/METADATA"
        // - "$sitePackagesPath/*.egg-info/PKG-INFO"
        // Extract info from these, perhaps.
        // But the naming is also very structured: $packageName-$packageVersion.(dist|egg)-info
        // So we could probably avoid even reading the metadata files, in favor of dir existence.
    }

    override fun dryRun(): String {
        TODO("Not yet implemented")
    }

    override fun info(): String {
        TODO("Not yet implemented")
    }
}

class PythonInstallation(
    val rootPath: String,
    val constraints: PythonConstraints,
) {
    val libpythonPath: String? by lazy { findLibPython() }
    val binPython: String? by lazy { findBinPython() }
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
            "libpython: $libpythonPath",
            "version: $version",
            "OS name: $osName",
            "CPU arch: $cpuArch",
        ).joinToString(NL)
    }

    // -- Lazy evaluation functions --

    private fun findLibPython(): String? {
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
        return linesToMap(releaseFile.lines(), "=", stripQuotes=true)
    }

    /** Calls `java Props` to harvest system properties from the horse's mouth. */
    private fun askJavaForSystemProperties(): Map<String, String>? {
        val javaExe = binPython
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

        // Ensure libpython is present.
        if (libpythonPath == null) return fail("No Python library found.")

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
