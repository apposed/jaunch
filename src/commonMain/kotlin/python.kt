// Logic for discovery and inspection of Python installations.

import kotlin.math.min

data class PythonConstraints(
    val configDir: File,
    val libSuffixes: List<String>,
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

        // Calculate all the places to look for the Python library.
        val libPythonSuffixes = vars.calculate(config.pythonLibSuffixes, hints)

        debug()
        debug("Suffixes to check for libpython:")
        libPythonSuffixes.forEach { debug("* ", it) }

        // Calculate Python distro and version constraints.
        val constraints = PythonConstraints(
            configDir,
            libPythonSuffixes,
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
        debug("* libPythonPath -> ", python.libPythonPath ?: "<null>")
        debug("* binPython -> ", python.binPython ?: "<null>")

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
        maybeAssign(vars, "libPythonPath", python?.libPythonPath)
        maybeAssign(vars, "binPython", python?.binPython)
        maybeAssign(vars, "version", python?.version)
    }

    override fun tweakArgs(args: MutableList<String>) {
        // No-op
    }

    override fun launch(args: ProgramArgs): List<String> {
        val libPythonPath = python?.libPythonPath ?: fail("No matching Python installations found.")

        dryRun(buildString {
            append(python?.binPython ?: "python")
            args.runtime.forEach { append(" $it") }
            if (mainProgram != null) append(" $mainProgram")
            args.main.forEach { append(" $it") }
        })

        return buildList {
            add(libPythonPath)
            addAll(args.runtime)
            if (mainProgram != null) add(mainProgram!!)
            addAll(args.main)
        }
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
    val rootPath: String,
    val constraints: PythonConstraints,
) {
    val libPythonPath: String? by lazy { findLibPython() }
    val binPython: String? by lazy { findBinPython(constraints.targetOS) }
    val version: String? by lazy { guessPythonVersion() }
    val packages: Map<String, String> by lazy { guessInstalledPackages() }
    val conforms: Boolean by lazy { checkConstraints() }

    /** Gets the major.minor version digits of the Python installation. */
    val majorMinorVersion: Pair<Int, Int>?
        get() {
            val digits = versionDigits(version ?: return null)
            return if (digits.size < 2) null else Pair(digits[0], digits[1])
        }

    override fun toString(): String {
        return listOf(
            "root: $rootPath",
            "libPython: $libPythonPath",
            "version: $version",
            "packages:${bulletList(packages)}",
        ).joinToString(NL)
    }

    // -- Lazy evaluation functions --

    private fun findLibPython(): String? {
        return constraints.libSuffixes.map { File("$rootPath$SLASH$it") }.firstOrNull { it.exists }?.path
    }

    private fun findBinPython(targetOS: String): String? {
        val extension = if (targetOS == "WINDOWS") ".exe" else ""

        // Note: The order below matters! In particular, on macOS,
        // Homebrew Python will be installed somewhere like:
        //
        //     /opt/homebrew/Cellar/python@3.13/3.13.5/Frameworks/Python.framework/Versions/Current
        //
        // Beneath that is something like:
        //
        //     $ tree -L 1 . bin lib
        //     .
        //     |-- _CodeSignature
        //     |-- bin
        //     |-- Headers -> include/python3.13
        //     |-- include
        //     |-- lib
        //     |-- Python
        //     |-- Resources
        //     \-- share
        //     bin
        //     |-- idle3 -> idle3.13
        //     |-- idle3.13
        //     |-- pip3
        //     |-- pip3.13
        //     |-- pydoc3 -> pydoc3.13
        //     |-- pydoc3.13
        //     |-- python3 -> python3.13
        //     |-- python3-config -> python3.13-config
        //     |-- python3.13
        //     \-- python3.13-config
        //     lib
        //     |-- libpython3.13.dylib -> ../Python
        //     |-- pkgconfig
        //     \-- python3.13
        //
        // So we have this bizarre situation where `${root}/lib/libpython*.dylib`
        // is symlinked to `${root}/Python`. But because macOS filesystems are
        // typically case-insensitive, the candidate check for `${root}/python`
        // will match this `${root}/Python`, Jaunch will attempt to execute it,
        // and the execution will fail with an error like:
        //
        //     sh: .../Python.framework/Versions/3.13/python: cannot execute binary file
        //
        // To sidestep this headache, we check for `bin/python` and `bin/python3`
        // before `python` and `python3`.

        for (candidate in arrayOf("bin${SLASH}python", "bin${SLASH}python3", "python", "python3")) {
            val pythonFile = File("$rootPath$SLASH$candidate$extension")
            if (pythonFile.exists) return pythonFile.path
        }
        return null
    }

    private fun guessPythonVersion(): String {
        return guess("Python version") { askPythonForVersion() ?: "<unknown>" }
    }

    private fun guessInstalledPackages(): Map<String, String> {
        return guess("installed packages") { askPipForPackages() }
    }

    private fun <T> guess(label: String, doGuess: () -> T): T {
        debug("Guessing $label...")
        val result = doGuess()
        debug("-> $label: $result")
        return result
    }

    /** Calls `python --version` to be told the Python version from the boss. */
    private fun askPythonForVersion(): String? {
        val pythonExe = binPython
        if (pythonExe == null) {
            debug("Python executable does not exist")
            return null
        }
        debug("Invoking `\"", pythonExe, "\" --version`...")
        val line = execute("\"$pythonExe\" --version")?.get(0) ?: return null
        val versionPattern = Regex("(\\d+\\.\\d+\\.\\d+[^ ]*)")
        return versionPattern.find(line)?.value
    }

    private fun askPipForPackages(): Map<String, String> {
        val pythonExe = binPython
        if (pythonExe == null) {
            debug("Python executable does not exist")
            return emptyMap()
        }
        debug("Invoking `\"", pythonExe, "\" -m pip list`...")
        val lines = execute("\"$pythonExe\" -m pip list") ?: emptyList()
        // Start at index 2 to skip the table headers.
        return lines.subList(min(2, lines.size), lines.size)
            .map { it.split(Regex("\\s+")) }
            .associate { it[0] to if (it.isEmpty()) "" else it[1] }
    }

    private fun checkConstraints(): Boolean {
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

    // -- Helper methods --

    private fun bulletList(map: Map<String, String>?, bullet: String = "* "): String {
        return when {
            map == null -> " <none>"
            map.isEmpty() -> " <empty>"
            else -> "$NL$bullet" + map.entries.joinToString("$NL$bullet")
        }
    }

    private fun fail(vararg args: Any): Boolean { debug(*args); return false }
}
