/**
 * All of Jaunch's configuration in a tidy bundle.
 *
 * Instances of this class are typically coalesced from a TOML configuration file.
 * See [the stock jaunch.toml file](https://github.com/scijava/jaunch/blob/-/jaunch.toml)
 * for a full explanation of what all these fields mean, and how to configure them.
 */
@Suppress("ArrayInDataClass")
data class JaunchConfig (

    // -- General configuration fields --

    /** Jaunch configuration schema version. */
    val jaunchVersion: Int? = null,

    /** Name of the program being launched by Jaunch. */
    val programName: String? = null,

    /** The list of command line options understood by Jaunch. */
    val supportedOptions: Array<String> = emptyArray(),

    /** Aliases for operating system names. */
    val osAliases: Array<String> = emptyArray(),

    /** Aliases for CPU architectures. */
    val archAliases: Array<String> = emptyArray(),

    /** List of additional hints to enable or disable based on other hints. */
    val modes: Array<String> = emptyArray(),

    /** Commands that control Jaunch's launching behavior. */
    val directives: Array<String> = emptyArray(),

    // -- Python-specific configuration fields --

    /**
     * The list of arguments that Jaunch will recognize as belonging to the Python interpreter,
     * as opposed to the application's main method.
     */
    val pythonRecognizedArgs: Array<String> = emptyArray(),

    /** Paths to check for Python installations. */
    val pythonRootPaths: Array<String> = emptyArray(),

    /** List of places within a Python installation to look for the Python library. */
    val pythonLibSuffixes: Array<String> = emptyArray(),

    /** Minimum acceptable Python version to match. */
    val pythonVersionMin: String? = null,

    /** Maximum acceptable Python version to match. */
    val pythonVersionMax: String? = null,

    /** List of packages that must be present in a suitable Python installation. */
    val pythonPackages: Array<String> = emptyArray(),

    /** Arguments to pass to the Python runtime. */
    val pythonRuntimeArgs: Array<String> = emptyArray(),

    /** A list of candidate Python scripts, one of which will get launched. */
    val pythonScriptPath: Array<String> = emptyArray(),

    /** Arguments to pass to the Python program itself. */
    val pythonMainArgs: Array<String> = emptyArray(),


    // -- JVM-specific configuration fields --

    /**
     * The list of arguments that Jaunch will recognize as belonging to the JVM,
     * as opposed to the application's main method.
     */
    val jvmRecognizedArgs: Array<String> = emptyArray(),

    /** Whether to allow unrecognized arguments to be passed to the JVM. */
    val jvmAllowUnrecognizedArgs: Boolean? = null,

    /** Whether to attempt to launch with mysterious flavors of the JVM. */
    val jvmAllowWeirdRuntimes: Boolean? = null,

    /** Minimum acceptable Java version to match. */
    val jvmVersionMin: String? = null,

    /** Maximum acceptable Java version to match. */
    val jvmVersionMax: String? = null,

    /** Acceptable distributions/vendors/flavors of Java to match. */
    val jvmDistrosAllowed: Array<String> = emptyArray(),

    /** Unacceptable distributions/vendors/flavors of Java to (not) match. */
    val jvmDistrosBlocked: Array<String> = emptyArray(),

    /** Paths to check for Java installations. */
    val jvmRootPaths: Array<String> = emptyArray(),

    /** List of places within a Java installation to look for the JVM library. */
    val jvmLibSuffixes: Array<String> = emptyArray(),

    /** Runtime classpath elements (e.g. JAR files) to pass to Java. */
    val jvmClasspath: Array<String> = emptyArray(),

    /** Maximum amount of memory for the Java heap to consume. */
    val jvmMaxHeap: String? = null,

    /** Arguments to pass to the JVM. */
    val jvmRuntimeArgs: Array<String> = emptyArray(),

    /** A list of candidate main classes, one of which will get launched. */
    val jvmMainClass: Array<String> = emptyArray(),

    /** Arguments to pass to the main class on the Java side. */
    val jvmMainArgs: Array<String> = emptyArray(),

) {
    /** Return true iff the given argument is on the specified list of recognized args. */
    fun recognizes(arg: String, recognizedArgs: Array<String>): Boolean {
        for (okArg in recognizedArgs) {
            if (okArg.endsWith('*')) {
                val prefix = okArg.substring(0, okArg.length - 1)
                if (arg.startsWith(prefix)) return true else continue
            }
            var trimmed = arg
            for (symbol in listOf(':', '=')) {
                val index = trimmed.indexOf(symbol)
                if (index >= 0) trimmed = trimmed.substring(0, index)
            }
            if (trimmed == okArg) return true
        }
        return false
    }

    /** Union another Jaunch configuration with this one. */
    operator fun plus(config: JaunchConfig): JaunchConfig {
        if (config.jaunchVersion != null && jaunchVersion != null &&
            config.jaunchVersion != jaunchVersion)
        {
            throw IllegalArgumentException("Config versions are incompatible: ${config.jaunchVersion} != $jaunchVersion")
        }
        return JaunchConfig(
            programName = config.programName ?: programName,
            supportedOptions = config.supportedOptions + supportedOptions,
            osAliases = config.osAliases + osAliases,
            archAliases = config.archAliases + archAliases,
            modes = config.modes + modes,
            directives = config.directives + directives,

            pythonRecognizedArgs = config.pythonRecognizedArgs + pythonRecognizedArgs,
            pythonRootPaths = config.pythonRootPaths + pythonRootPaths,
            pythonLibSuffixes = config.pythonLibSuffixes + pythonLibSuffixes,
            pythonVersionMin = config.pythonVersionMin ?: pythonVersionMin,
            pythonVersionMax = config.pythonVersionMax ?: pythonVersionMax,
            pythonPackages = config.pythonPackages + pythonPackages,
            pythonRuntimeArgs = config.pythonRuntimeArgs + pythonRuntimeArgs,
            pythonScriptPath = config.pythonScriptPath + pythonScriptPath,
            pythonMainArgs = config.pythonMainArgs + pythonMainArgs,

            jvmRecognizedArgs = config.jvmRecognizedArgs + jvmRecognizedArgs,
            jvmAllowUnrecognizedArgs = config.jvmAllowUnrecognizedArgs ?: jvmAllowUnrecognizedArgs,
            jvmAllowWeirdRuntimes = config.jvmAllowWeirdRuntimes ?: jvmAllowWeirdRuntimes,
            jvmVersionMin = config.jvmVersionMin ?: jvmVersionMin,
            jvmVersionMax = config.jvmVersionMax ?: jvmVersionMax,
            jvmDistrosAllowed = config.jvmDistrosAllowed + jvmDistrosAllowed,
            jvmDistrosBlocked = config.jvmDistrosBlocked + jvmDistrosBlocked,
            jvmRootPaths = config.jvmRootPaths + jvmRootPaths,
            jvmLibSuffixes = config.jvmLibSuffixes + jvmLibSuffixes,
            jvmClasspath = config.jvmClasspath + jvmClasspath,
            jvmMaxHeap = config.jvmMaxHeap ?: jvmMaxHeap,
            jvmRuntimeArgs = config.jvmRuntimeArgs + jvmRuntimeArgs,
            jvmMainClass = config.jvmMainClass + jvmMainClass,
            jvmMainArgs = config.jvmMainArgs + jvmMainArgs,
        )
    }
}
