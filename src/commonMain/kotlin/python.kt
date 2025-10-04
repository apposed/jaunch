// Logic for discovery and inspection of Python installations.

import kotlin.math.min

data class PythonConstraints(
    val configDir: File,
    val exeSuffixes: List<String>,
    val versionMin: String?,
    val versionMax: String?,
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

        // Calculate Python distro and version constraints.
        val constraints = PythonConstraints(
            configDir,
            pythonSuffixes,
            config.pythonVersionMin, config.pythonVersionMax,
            config.targetOS, config.targetArch,
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

    private fun guessInstalledPackages(): Map<String, String> {
        return guess("installed packages") { askPipForPackages() }
    }

    // -- Helper methods --

    /** Calls `python props.py` to receive Python environment details from the boss. */
    private fun askPythonForProperties(): Map<String, String>? {
        val pythonExe = binPython
        if (pythonExe == null) {
            debug("Python executable does not exist.")
            return null
        }

        // Use props.py to discover the libpython location.
        // See doc/PYTHON.md for details on the platform-specific logic.
        val propsScript = constraints.configDir / "props.py"
        if (!propsScript.exists) {
            warn("props.py not found at: ", propsScript.path)
            return null
        }

        debug("Invoking `\"", pythonExe, "\" props.py`...")
        val stdout = execute("\"$pythonExe\" \"${propsScript.path}\"") ?: return null
        return linesToMap(stdout, "=")
    }

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
}

private fun extractPythonVersion(sysVersion: String?): String? {
    if (sysVersion == null) return null
    val versionPattern = Regex("(\\d+\\.\\d+\\.\\d+[^ ]*)")
    return versionPattern.find(sysVersion)?.value
}
