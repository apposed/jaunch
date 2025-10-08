// Logic for discovery and inspection of Python installations.

import kotlin.math.min

data class PythonConstraints(
    val configDir: File,
    val exeSuffixes: List<String>,
    val versionMin: String?,
    val versionMax: String?,
    val osAliases: List<String>,
    val archAliases: List<String>,
    val targetOS: String,
    val targetArch: String,
)

class PythonRuntimeConfig(recognizedArgs: Array<String>) :
    RuntimeConfig("python", "PYTHON", recognizedArgs)
{
    var python: PythonInstallation? = null

    override val supportedDirectives: DirectivesMap = mutableMapOf(
        "print-python-home" to { _ -> printlnErr(pythonHome()) },
        "print-python-info" to { _ -> printlnErr(pythonInfo()) },
    )

    override fun configure(
        configDir: File,
        config: JaunchConfig,
        hints: MutableSet<String>,
        vars: Vars
    ) {
        // Calculate all the places to search for Python.
        val appDir = vars["app-dir"] as String
        val pythonRootPaths = vars.calculate(config.pythonRootPaths, hints)
                .flatMap { glob(it) }
                .map {
                    // Relativize beneath app-dir as appropriate.
                    if (File(it).isDirectory) it
                    else (File(appDir) / it).path
                }
                .filter { File(it).isDirectory }
                .toSet()

        debug()
        debug("Root paths to search for Python:")
        pythonRootPaths.forEach { debug("* ", it) }

        // Calculate all the places to look for the Python executable.
        val pythonSuffixes = vars.calculate(config.pythonExeSuffixes, hints)

        debug()
        debug("Suffixes to check for Python executable:")
        pythonSuffixes.forEach { debug("* ", it) }

        // Calculate Python installation constraints.
        val osAliases = vars.calculate(config.osAliases, hints)
        val archAliases = vars.calculate(config.archAliases, hints)
        val constraints = PythonConstraints(
            configDir, pythonSuffixes,
            config.pythonVersionMin, config.pythonVersionMax,
            osAliases, archAliases, config.targetOS, config.targetArch,
        )

        // Discover Python.
        debug()
        debug("Discovering Python installations...")
        var python: PythonInstallation? = null
        for (pythonPath in pythonRootPaths) {
            debug("Analyzing candidate Python directory: '", pythonPath, "'")
            val pythonCandidate = PythonInstallation(pythonPath, constraints)
            if (pythonCandidate.conforms) {
                // Installation looks good! Moving on.
                python = pythonCandidate
                break
            }
        }
        if (python == null) {
            debug("No Python installation found.")
            return
        }
        debug("Successfully discovered Python installation:")
        debug("* rootPath -> ", python.rootPath)
        debug("* binPython -> ", python.binPython ?: "<null>")
        debug("* libPythonPath -> ", python.libPythonPath ?: "<null>")

        // Apply PYTHON: hints.
        val majorMinor = python.majorMinorVersion
        if (majorMinor != null) {
            val (major, minor) = majorMinor
            hints += "PYTHON:$major.$minor"
            // If minor version is OVER 9000, something went wrong in the parsing.
            // Let's not explode the hints set with too many bogus values.
            for (v in 0..min(minor, 9000)) hints += "PYTHON:$major.$v+"
        }
        debug("* hints -> ", hints)

        // Calculate runtime arguments.
        runtimeArgs += vars.calculate(config.pythonRuntimeArgs, hints)
        debugList("Python arguments calculated:", runtimeArgs)

        // Calculate main script.
        debug()
        debug("Calculating main script path...")
        val scriptPaths = vars.calculate(config.pythonScriptPath, hints)
        mainProgram = scriptPaths.firstOrNull()
        debug("mainProgram -> ", mainProgram ?: "<null>")

        // Calculate main args.
        mainArgs += vars.calculate(config.pythonMainArgs, hints)
        debugList("Main arguments calculated:", mainArgs)

        this.python = python
    }

    override fun injectInto(vars: Vars) {
        maybeAssign(vars, "scriptPath", mainProgram)
        maybeAssign(vars, "rootPath", python?.rootPath)
        maybeAssign(vars, "binPython", python?.binPython)
        maybeAssign(vars, "libPythonPath", python?.libPythonPath)
        maybeAssign(vars, "version", python?.version)
    }

    override fun tweakArgs(args: MutableList<String>) {
        // No-op
    }

    override fun launch(args: ProgramArgs, directiveArg: String?): Pair<String, List<String>> {
        if (directiveArg != null) error("Ignoring invalid $directive directive argument $directiveArg")

        val binPython = python?.binPython ?: fail("No matching Python installations found.")
        val libPythonPath = python?.libPythonPath ?: fail("No shared library found for Python: $binPython")

        val dryRun = buildString {
            append(python?.binPython ?: "python")
            args.runtime.forEach { append(" $it") }
            if (mainProgram != null) append(" $mainProgram")
            args.main.forEach { append(" $it") }
        }

        val lines = buildList {
            add(libPythonPath)
            add(binPython)
            addAll(args.runtime)
            if (mainProgram != null) add(mainProgram!!)
            addAll(args.main)
        }
        val emissions = listOf(directive, lines.size.toString()) + lines

        return Pair(dryRun, emissions)
    }

    // -- Directive handlers --

    fun pythonHome(): String {
        return python?.rootPath ?: fail("No matching Python installations found.")
    }

    fun pythonInfo(): String {
        return python?.toString() ?: fail("No matching Python installations found.")
    }
}

/**
 * A Python installation, rooted at a particular directory.
 *
 * This class contains heuristics for discerning the Python installation's version
 * and installed packages, by invoking the Python binary and reading the output.
 */
class PythonInstallation(
    rootPath: String,
    val constraints: PythonConstraints,
) : RuntimeInstallation(rootPath) {
    val binPython: String? by lazy { findBinPython() }
    val libPythonPath: String? by lazy { guessLibPython() }
    val version: String? by lazy { guessPythonVersion() }
    val osName: String? by lazy { guessOperatingSystemName() }
    val cpuArch: String? by lazy { guessCpuArchitecture() }
    val packages: Map<String, String> by lazy { guessInstalledPackages() }
    val props: Map<String, String>? by lazy { askPythonForProperties() }

    /** Gets the major.minor version digits of the Python installation. */
    val majorMinorVersion: Pair<Int, Int>?
        get() {
            val digits = versionDigits(version ?: return null)
            return if (digits.size < 2) null else Pair(digits[0], digits[1])
        }

    override fun toString(): String {
        return listOf(
            "root: $rootPath",
            "binPython: $binPython",
            "libPython: $libPythonPath",
            "version: $version",
            "packages:${bulletList(packages)}",
        ).joinToString(NL)
    }

    // -- Lazy evaluation functions --

    override fun checkConstraints(): Boolean {
        // Ensure libpython is present.
        if (libPythonPath == null) return fail("No Python library found.")

        // Check OS name and CPU architecture constraints.
        if (osName == null)
            return fail("Unknown operating system.")
        if (osName != constraints.targetOS)
            return fail("Operating system '$osName' does not match current platform ${constraints.targetOS}")
        if (cpuArch == null)
            return fail("Unknown CPU architecture.")
        if (cpuArch != constraints.targetArch)
            return fail("CPU architecture '$cpuArch' does not match target architecture ${constraints.targetArch}")

        // Check Python version constraints.
        if (constraints.versionMin != null || constraints.versionMax != null) {
            if (version == null)
                return fail("Version constraints exist, but version is unknown.")
            if (version != null) {
                if (versionOutOfBounds(version!!, constraints.versionMin, constraints.versionMax))
                    return fail("Version '$version' is outside specified bounds " +
                            "[${constraints.versionMin}, ${constraints.versionMax}].")
            }
        }

        // TODO: Verify installation matches targetOS and targetArch.

        // Check installed package constraints.
        // TODO: Actually check packages. ;-)

        // All checks passed!
        return true
    }

    private fun findBinPython(): String? {
        for (candidate in constraints.exeSuffixes) {
            val pythonFile = File("$rootPath$SLASH$candidate")
            if (pythonFile.exists) return pythonFile.path
        }
        return null
    }

    private fun guessLibPython(): String? {
        return guess("Python library") {
            props?.get("jaunch.libpython_path")
        }
    }

    private fun guessPythonVersion(): String? {
        return guess("Python version") {
            props?.get("cvars.py_version") ?:
            extractPythonVersion(props?.get("sys.version"))
        }
    }

    private fun guessOperatingSystemName(): String? {
        return guess("OS name") {
            guessAlias(constraints.osAliases, "platform.system")
        }
    }

    private fun guessCpuArchitecture(): String? {
        return guess("CPU architecture") {
            guessAlias(constraints.archAliases, "platform.machine")
        }
    }

    private fun guessInstalledPackages(): Map<String, String> {
        return guess("installed packages") { askPipForPackages() }
    }

    /** Calls `python props.py` to receive Python environment details from the boss. */
    private fun askPythonForProperties(): Map<String, String>? {
        val pythonExe = binPython
        if (pythonExe == null) {
            debug("Python executable does not exist.")
            return null
        }

        // NB: Temporarily change the current working directory to the one containing
        // the props.py helper program. This lets us invoke the python executable
        // in a simpler way, avoiding quoting complexity, especially on Windows.
        val cwd = getcwd()
        setcwd(constraints.configDir.path)

        debug("Invoking `\"", pythonExe, "\" props.py`...")
        val propsExists = File("props.py").exists
        if (!propsExists) warn("props.py not found at: ", constraints.configDir.path)
        val stdout: List<String>? =
            if (propsExists) execute("\"$pythonExe\" props.py")
            else null

        // NB: Restore original working directory.
        setcwd(cwd)

        return if (stdout == null) null else linesToMap(stdout, "=")
    }

    // -- Helper methods --

    private fun askPipForPackages(): Map<String, String> {
        val pythonExe = binPython
        if (pythonExe == null) {
            debug("Python executable does not exist.")
            return emptyMap()
        }
        debug("Invoking `\"", pythonExe, "\" -m pip list`...")
        val lines = execute("\"$pythonExe\" -m pip list") ?: emptyList()
        // Start at index 2 to skip the table headers.
        return lines.subList(min(2, lines.size), lines.size)
            .map { it.split(Regex("\\s+")) }
            .associate { it[0] to if (it.isEmpty()) "" else it[1] }
    }

    private fun guessAlias(aliasLines: Iterable<String>, propsField: String): String? {
        val aliasMap = linesToMapOfLists(aliasLines)

        val alias =
            // Extract the field from the Python properties.
            props?.get(propsField)?.lowercase() ?: return null

        // Find the canonical name of the extracted value.
        return aliasMap.entries.firstOrNull { (_, aliases) -> aliases.contains(alias) }?.key ?: alias
    }
}

private fun extractPythonVersion(sysVersion: String?): String? {
    if (sysVersion == null) return null
    val versionPattern = Regex("(\\d+\\.\\d+\\.\\d+[^ ]*)")
    return versionPattern.find(sysVersion)?.value
}
