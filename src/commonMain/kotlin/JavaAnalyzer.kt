class JavaAnalyzer(
    private val libjvmSuffixes: List<String>,
) {
    /**
     * Gets info about the Java installation rooted at the given directory.
     *
     * Returns null if the directory does not look like the root of a Java installation.
     */
    fun info(rootPath: String): JavaInfo {
        // Find the libjvm.
        var libjvmPath: String? = null
        for (libjvmSuffix in libjvmSuffixes) {
            val libjvmFile = File("$rootPath/$libjvmSuffix")
            if (!libjvmFile.isFile) continue
            libjvmPath = libjvmFile.path
            break
        }

        // Squeeze sweet metadata out of the Java installation's root path name.
        // ~~~~~~~~~ 👯 REGEX TIME 👯 ~~~~~~~~~
        // TODO: Change the approach to avoid so much hardcoding.
        // should probably back off on the mega-regex, in favor of smaller regexes that match more flexibly.
        // Then the order of metadata fields in the directory prefix doesn't have to align, and the v1/v2/v3
        // nonsense could go away (although for graalvm, version extraction could still be tricky).
        // May want to have a list of regexes covering all observed cases thus far...

        val sep = "(-|_|\\.|)"
        val versionPattern = "((8u)?([0-9]+)([\\.+_][0-9]+)*(-b[0-9]+|-ca)?)?"
        val prefixes = listOf("jdk", "java", "openjdk", "").joinToString("|")
        val distros = listOf(
            "adopt",
            "dragonwell",
            "corretto", "amazon-corretto",
            "zulu",
            "jbrsdk",
            "openlogic-openjdk",
            "graalvm-ce", "graalvm-community-openjdk",
            "graalvm-jdk",
            "oracle",
            "sapmachine-jdk",
            "TencentKona",
            ""
        ).joinToString("|")

        val featuresPattern = listOf("-crac", "-jdk", "-jre", "-fx", "-java").joinToString("|")
        val oses = listOf("linux", "macos", "macosx", "win", "windows", "").joinToString("|")
        val variants = listOf("musl", "openjdk", "").joinToString("|")
        val arches = listOf("amd64", "x64", "x86", "").joinToString("|")
        val suffixes = listOf("-jre", "-lite", "").joinToString("|")
        val pattern =
            "($prefixes)$sep" +
            "($distros)$sep" +
            versionPattern +
            "(($featuresPattern)*)" +
            versionPattern + sep +
            "($oses)$sep" +
            "($variants)$sep" +
            "($arches)" +
            versionPattern +
            "($suffixes)"

        val rootDir = File(rootPath)
        val matchGroups = Regex(pattern).matchEntire(rootDir.name)?.groupValues
        val missing = "<null>"
        val entire   = matchGroups?.get( 0)
        val prefix   = matchGroups?.get( 1)
        val sep1     = matchGroups?.get( 2)
        val distro   = matchGroups?.get( 3)
        val sep2     = matchGroups?.get( 4)
        val v1       = matchGroups?.get( 5) // and 6, 7, 8, 9
        val features = matchGroups?.get(10) // and 11
        val v2       = matchGroups?.get(12) // and 13, 14, 15, 16
        val sep3     = matchGroups?.get(17)
        val os       = matchGroups?.get(18)
        val sep4     = matchGroups?.get(19)
        val variant  = matchGroups?.get(20)
        val sep5     = matchGroups?.get(21)
        val arch     = matchGroups?.get(22)
        val v3       = matchGroups?.get(23) // and 24, 25, 26, 27
        val suffix   = matchGroups?.get(28)

        debug("Directory name parsed:")
        debug("entire   = ", entire ?: missing)
        debug("prefix   = ", prefix ?: missing)
        debug("sep1     = ", sep1 ?: missing)
        debug("distro   = ", distro ?: missing)
        debug("sep2     = ", sep2 ?: missing)
        debug("v1       = ", v1 ?: missing)
        debug("features = ", features ?: missing)
        debug("v2       = ", v2 ?: missing)
        debug("sep3     = ", sep3 ?: missing)
        debug("os       = ", os ?: missing)
        debug("sep4     = ", sep4 ?: missing)
        debug("variant  = ", variant ?: missing)
        debug("sep5     = ", sep5 ?: missing)
        debug("arch     = ", arch ?: missing)
        debug("v3       = ", v3 ?: missing)
        debug("suffix   = ", suffix ?: missing)

        val releaseInfo = readReleaseInfo(rootDir)
        val implementor = releaseInfo?.get("IMPLEMENTOR")
        val implementorVersion = releaseInfo?.get("IMPLEMENTOR_VERSION")
        val javaVersion = releaseInfo?.get("JAVA_VERSION")
        debug("IMPLEMENTOR -> ", implementor ?: "<null>")
        debug("IMPLEMENTOR_VERSION -> ", implementorVersion ?: "<null>")
        debug("JAVA_VERSION -> ", javaVersion ?: "<null>")

        // TODO: Squeeze more metadata out of rt.jar's META-INF/MANIFEST, if present.
        // unzip -p .../jre/lib/rt.jar META-INF/MANIFEST.MF

        return JavaInfo(
            rootPath,
            libjvmPath,
            releaseInfo,
            prefix,
            distro,
            features,
            os,
            variant,
            arch,
            suffix,
            javaVersion ?: v1 ?: v2 ?: v3,
        )
    }
}

data class JavaInfo(
    val rootPath: String,
    val libjvmPath: String?,
    val releaseInfo: Map<String, String>?,
    val prefix: String?,
    val distro: String?,
    val features: String?,
    val os: String?,
    val variant: String?,
    val arch: String?,
    val suffix: String?,
    val javaVersion: String?,
) {
    fun fits(versionMin: Long?, versionMax: Long?, distrosAllowed: List<String>, distrosBlocked: List<String>): Boolean {
        if (libjvmPath == null) {
            debug("No JVM library found.")
            return false
        }

        // TODO: Actually check things.
        return true
    }
}
