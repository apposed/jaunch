import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * All of Jaunch's configuration in a tidy bundle.
 *
 * Instances of this class are typically coalesced from a TOML configuration file.
 * See [the stock jaunch.toml file](https://github.com/scijava/jaunch/blob/-/jaunch.toml)
 * for a full explanation of what all these fields mean, and how to configure them.
 */
@Serializable
@Suppress("ArrayInDataClass")
data class JaunchConfig (

    /** Jaunch configuration schema version. */
    @SerialName("jaunch-version")
    val jaunchVersion: Int? = null,

    /** Name of the program being launched by Jaunch. */
    @SerialName("program-name")
    val programName: String? = null,

    /** The list of command line options understood by Jaunch. */
    @SerialName("supported-options")
    val supportedOptions: Array<String> = emptyArray(),

    /**
     * The list of arguments that Jaunch will recognize as belonging to the JVM,
     * as opposed to the application's main method.
     */
    @SerialName("recognized-jvm-args")
    val recognizedJvmArgs: Array<String> = emptyArray(),

    /** Whether to allow unrecognized arguments to be passed to the JVM. */
    @SerialName("allow-unrecognized-jvm-args")
    val allowUnrecognizedJvmArgs: Boolean? = null,

    /** Whether to attempt to launch with mysterious flavors of the JVM. */
    @SerialName("allow-weird-jvms")
    val allowWeirdJvms: Boolean? = null,

    /** Minimum acceptable Java version to match. */
    @SerialName("java-version-min")
    val javaVersionMin: String? = null,

    /** Maximum acceptable Java version to match. */
    @SerialName("java-version-max")
    val javaVersionMax: String? = null,

    /** Acceptable distributions/vendors/flavors of Java to match. */
    @SerialName("java-distros-allowed")
    val javaDistrosAllowed: Array<String> = emptyArray(),

    /** Unacceptable distributions/vendors/flavors of Java to (not) match. */
    @SerialName("java-distros-blocked")
    val javaDistrosBlocked: Array<String> = emptyArray(),

    /** Aliases for operating system names. */
    @SerialName("os-aliases")
    val osAliases: Array<String> = emptyArray(),

    /** Aliases for CPU architectures. */
    @SerialName("arch-aliases")
    val archAliases: Array<String> = emptyArray(),

    /** Paths to check for Java installations. */
    @SerialName("jvm-root-paths")
    val jvmRootPaths: Array<String> = emptyArray(),

    /** List of places within a Java installation to look for the JVM library. */
    @SerialName("libjvm-suffixes")
    val libjvmSuffixes: Array<String> = emptyArray(),

    /** List of additional hints to enable or disable based on other hints. */
    val modes: Array<String> = emptyArray(),

    /** Commands that override Jaunch's usual behavior of launching Java. */
    val directives: Array<String> = emptyArray(),

    /** Runtime classpath elements (e.g. JAR files) to pass to Java. */
    val classpath: Array<String> = emptyArray(),

    /** Maximum amount of memory for the Java heap to consume. */
    @SerialName("max-heap")
    val maxHeap: String? = null,

    /** Arguments to pass to the JVM. */
    @SerialName("jvm-args")
    val jvmArgs: Array<String> = emptyArray(),

    /** The main class to launch. */
    @SerialName("main-class")
    val mainClass: String? = null,

    /** A list of candidate main classes, one of which will get launched. */
    @SerialName("main-class-candidates")
    val mainClassCandidates: Array<String> = emptyArray(),

    /** Arguments to pass to the main class on the Java side. */
    @SerialName("main-args")
    val mainArgs: Array<String> = emptyArray(),
) {
    /** Unified list of possible main classes, including both [mainClassCandidates] and [mainClass]. */
    val mainClasses: Array<String>
        get() = if (mainClass == null) mainClassCandidates else mainClassCandidates + mainClass

    /** Return true iff the given argument is on the list of [recognizedJvmArgs]. */
    fun recognizes(arg: String): Boolean {
        for (okArg in recognizedJvmArgs) {
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
            classpath = config.classpath + classpath,
            maxHeap = config.maxHeap ?: maxHeap,
            supportedOptions = config.supportedOptions + supportedOptions,
            recognizedJvmArgs = config.recognizedJvmArgs + recognizedJvmArgs,
            allowUnrecognizedJvmArgs = config.allowUnrecognizedJvmArgs ?: allowUnrecognizedJvmArgs,
            allowWeirdJvms = config.allowWeirdJvms ?: allowWeirdJvms,
            javaVersionMin = config.javaVersionMin ?: javaVersionMin,
            javaVersionMax = config.javaVersionMax ?: javaVersionMax,
            javaDistrosAllowed = config.javaDistrosAllowed + javaDistrosAllowed,
            javaDistrosBlocked = config.javaDistrosBlocked + javaDistrosBlocked,
            osAliases = config.osAliases + osAliases,
            archAliases = config.archAliases + archAliases,
            jvmRootPaths = config.jvmRootPaths + jvmRootPaths,
            libjvmSuffixes = config.libjvmSuffixes + libjvmSuffixes,
            modes = config.modes + modes,
            directives = config.directives + directives,
            jvmArgs = config.jvmArgs + jvmArgs,
            mainClass = config.mainClass ?: mainClass,
            mainClassCandidates = config.mainClassCandidates + mainClassCandidates,
            mainArgs = config.mainArgs + mainArgs,
        )
    }
}
