import kotlin.reflect.KClass
import kotlin.reflect.cast

/**
 * All of Jaunch's configuration in a tidy bundle.
 *
 * Instances of this class are typically coalesced from a TOML configuration file.
 * See [the stock jaunch.toml file](https://github.com/scijava/jaunch/blob/-/jaunch.toml)
 * for a full explanation of what all these fields mean, and how to configure them.
 */
@Suppress("ArrayInDataClass")
data class JaunchConfig (

    /** Jaunch configuration schema version. */
    val jaunchVersion: Int? = null,

    /** Name of the program being launched by Jaunch. */
    val programName: String? = null,

    /** The list of command line options understood by Jaunch. */
    val supportedOptions: Array<String> = emptyArray(),

    /**
     * The list of arguments that Jaunch will recognize as belonging to the JVM,
     * as opposed to the application's main method.
     */
    val recognizedJvmArgs: Array<String> = emptyArray(),

    /** Whether to allow unrecognized arguments to be passed to the JVM. */
    val allowUnrecognizedJvmArgs: Boolean? = null,

    /** Whether to attempt to launch with mysterious flavors of the JVM. */
    val allowWeirdJvms: Boolean? = null,

    /** Minimum acceptable Java version to match. */
    val javaVersionMin: String? = null,

    /** Maximum acceptable Java version to match. */
    val javaVersionMax: String? = null,

    /** Acceptable distributions/vendors/flavors of Java to match. */
    val javaDistrosAllowed: Array<String> = emptyArray(),

    /** Unacceptable distributions/vendors/flavors of Java to (not) match. */
    val javaDistrosBlocked: Array<String> = emptyArray(),

    /** Aliases for operating system names. */
    val osAliases: Array<String> = emptyArray(),

    /** Aliases for CPU architectures. */
    val archAliases: Array<String> = emptyArray(),

    /** Paths to check for Java installations. */
    val jvmRootPaths: Array<String> = emptyArray(),

    /** List of places within a Java installation to look for the JVM library. */
    val libjvmSuffixes: Array<String> = emptyArray(),

    /** List of additional hints to enable or disable based on other hints. */
    val modes: Array<String> = emptyArray(),

    /** Commands that override Jaunch's usual launching behavior. */
    val directives: Array<String> = emptyArray(),

    /** Runtime classpath elements (e.g. JAR files) to pass to Java. */
    val classpath: Array<String> = emptyArray(),

    /** Maximum amount of memory for the Java heap to consume. */
    val maxHeap: String? = null,

    /** Arguments to pass to the JVM. */
    val jvmArgs: Array<String> = emptyArray(),

    /** The main class to launch. */
    val mainClass: String? = null,

    /** A list of candidate main classes, one of which will get launched. */
    val mainClassCandidates: Array<String> = emptyArray(),

    /** Arguments to pass to the main class on the Java side. */
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

// NB: Previously, the TOML config reader was implemented simply using ktoml.
// It worked well, but bloated the jaunch configurator binary by several megabytes.
// Therefore, to belittle the launcher, Jaunch now does its own minimal parsing.

fun readConfig(tomlFile: File): JaunchConfig {
    debug("Reading config file: $tomlFile");
    if (!tomlFile.exists) return JaunchConfig()

    // Declare Jaunch config fields.
    var jaunchVersion: Int? = null
    var programName: String? = null
    var supportedOptions: List<String>? = null
    var recognizedJvmArgs: List<String>? = null
    var allowUnrecognizedJvmArgs: Boolean? = null
    var allowWeirdJvms: Boolean? = null
    var javaVersionMin: String? = null
    var javaVersionMax: String? = null
    var javaDistrosAllowed: List<String>? = null
    var javaDistrosBlocked: List<String>? = null
    var osAliases: List<String>? = null
    var archAliases: List<String>? = null
    var jvmRootPaths: List<String>? = null
    var libjvmSuffixes: List<String>? = null
    var modes: List<String>? = null
    var directives: List<String>? = null
    var classpath: List<String>? = null
    var maxHeap: String? = null
    var jvmArgs: List<String>? = null
    var mainClass: String? = null
    var mainClassCandidates: List<String>? = null
    var mainArgs: List<String>? = null

    // Parse TOML file lines into tokens.
    val tokens = mutableListOf<Any>()
    tomlFile.lines().forEach { appendTokens(it, tokens) }

    // Process the tokens to merge list of elements into list tokens.
    // HACK: For now, we ignore commas and newlines. It means people can
    // write invalid TOML that this parser still accepts, but who cares.
    val processedTokens = processLists(tokens).filter { it !in listOf(',', '\n') }

    // The expected pattern at this point is VarAssign, value, VarAssign, value, ...
    // If we encounter a value outside this pattern, issue a warning and ignore it.
    var i = 0
    while (i < processedTokens.size) {
        when (val token = processedTokens[i++]) {
            is VarAssign -> {
                val name = token.name
                val value = if (i < processedTokens.size) processedTokens[i++] else null
                when (name) {
                    "jaunch-version" -> jaunchVersion = asInt(value)
                    "program-name" -> programName = asString(value)
                    "supported-options" -> supportedOptions = asList(value)
                    "recognized-jvm-args" -> recognizedJvmArgs = asList(value)
                    "allow-unrecognized-jvm-args" -> allowUnrecognizedJvmArgs = asBoolean(value)
                    "allow-weird-jvms" -> allowWeirdJvms = asBoolean(value)
                    "java-version-min" -> javaVersionMin = asString(value)
                    "java-version-max" -> javaVersionMax = asString(value)
                    "java-distros-allowed" -> javaDistrosAllowed = asList(value)
                    "java-distros-blocked" -> javaDistrosBlocked = asList(value)
                    "os-aliases" -> osAliases = asList(value)
                    "arch-aliases" -> archAliases = asList(value)
                    "jvm-root-paths" -> jvmRootPaths = asList(value)
                    "libjvm-suffixes" -> libjvmSuffixes = asList(value)
                    "modes" -> modes = asList(value)
                    "directives" -> directives = asList(value)
                    "classpath" -> classpath = asList(value)
                    "max-heap" -> maxHeap = asString(value)
                    "jvm-args" -> jvmArgs = asList(value)
                    "main-class" -> mainClass = asString(value)
                    "main-class-candidates" -> mainClassCandidates = asList(value)
                    "main-args" -> mainArgs = asList(value)
                }
            }
            else -> warn("[TOML] Ignoring extraneous token: '$token' [${token::class.simpleName}]")
        }
    }

    // Return the final result.
    return JaunchConfig(
        jaunchVersion = jaunchVersion,
        programName = programName,
        supportedOptions = asArray(supportedOptions),
        recognizedJvmArgs = asArray(recognizedJvmArgs),
        allowUnrecognizedJvmArgs = allowUnrecognizedJvmArgs,
        allowWeirdJvms = allowWeirdJvms,
        javaVersionMin = javaVersionMin,
        javaVersionMax = javaVersionMax,
        javaDistrosAllowed = asArray(javaDistrosAllowed),
        javaDistrosBlocked = asArray(javaDistrosBlocked),
        osAliases = asArray(osAliases),
        archAliases = asArray(archAliases),
        jvmRootPaths = asArray(jvmRootPaths),
        libjvmSuffixes = asArray(libjvmSuffixes),
        modes = asArray(modes),
        directives = asArray(directives),
        classpath = asArray(classpath),
        maxHeap = maxHeap,
        jvmArgs = asArray(jvmArgs),
        mainClass = mainClass,
        mainClassCandidates = asArray(mainClassCandidates),
        mainArgs = asArray(mainArgs),
    )
}

private fun <T : Any> castOrWarn(value: Any?, c: KClass<T>): T? {
    if (value == null) return null
    if (c.isInstance(value)) return c.cast(value)
    warn("[TOML] Expected ${c.simpleName} value but got ${value::class.simpleName}: $value")
    return null
}

private fun asBoolean(value: Any?): Boolean? { return castOrWarn(value, Boolean::class) }
private fun asInt(value: Any?): Int? { return castOrWarn(value, Int::class) }
private fun asString(value: Any?): String? { return castOrWarn(value, String::class) }

private fun asList(value: Any?): List<String>? {
    val list = castOrWarn(value, List::class) ?: return null
    val result = mutableListOf<String>()
    for (item in list) result.add(castOrWarn(item, String::class) ?: return null)
    return result
}

private fun asArray(value: List<String>?): Array<String> {
    return value?.toTypedArray() ?: emptyArray()
}

private val stringPattern1 = Regex("('([^']*)')\\s*")
private val stringPattern2 = Regex("(\"([^\"]*)\")\\s*")
private val booleanPattern = Regex("(true|false)\\s*")
private val integerPattern = Regex("(-?\\d+)\\s*")
private val varAssignPattern = Regex("([\\w-]+)\\s*=\\s*")
private val symbolPattern = Regex("([\\[\\],])\\s*")

private data class VarAssign(val name: String)

/** A string with mutable start position. */
private class Pos(val base: String, var start: Int) : CharSequence {

    override val length: Int get() { return base.length - start }

    override fun get(index: Int): Char { return base[start + index] }

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        return base.substring(start + startIndex, start + endIndex)
    }

    fun match(pattern: Regex, groupNo: Int = -1): String? {
        val v = pattern.matchAt(this, 0)?.groupValues ?: return null
        start += v[0].length
        return if (groupNo < 0) v.last() else v[groupNo]
    }

    override fun toString(): String {
      return base.substring(start)
    }
}

private fun appendTokens(line: String, tokens: MutableList<Any>) {
    val s = Pos(line.trim(), 0)
    while (s.isNotEmpty()) {
        if (s[0] == '#') break // comment
        appendToken(tokens, s.match(symbolPattern)?.get(0)) ?: continue
        appendToken(tokens, s.match(booleanPattern)?.toBoolean()) ?: continue
        appendToken(tokens, s.match(stringPattern1)) ?: continue
        appendToken(tokens, s.match(stringPattern2)) ?: continue
        appendToken(tokens, s.match(integerPattern)?.toInt()) ?: continue
        appendToken(tokens, s.match(varAssignPattern)?.let(::VarAssign)) ?: continue
        warn("[TOML] Ignoring unrecognized line segment: $s")
        break
    }
    tokens += '\n'
}

/** Returns null if a non-null token was successfully added, false otherwise. Why? Elvis, baby! */
private fun appendToken(tokens: MutableList<Any>, token: Any?): Boolean? {
    if (token == null) return false
    tokens += token
    return null
}

/** Processes the token list to resolve list declarations. */
private fun processLists(tokens: List<Any>): List<Any> {
    val result = mutableListOf<Any>()
    var i = 0
    while (i < tokens.size) {
        when (val token = tokens[i++]) {
            '[' -> {
                val rightBracket = tokens.indexOf(']', i)
                if (rightBracket < 0) warn("[TOML]: No terminating right bracket symbol for list")
                val endIndex = if (rightBracket < 0) tokens.size else rightBracket
                // HACK: For now, we ignore commas and newlines. It means people can
                // write invalid TOML that this parser still accepts, but who cares.
                result.add(tokens.subList(i, endIndex).filter { it !in listOf(',', '\n') })
                i = endIndex + 1
            }
            else -> result.add(token)
        }
    }
    return result
}

private fun <T> List<T>.indexOf(element: T, startIndex: Int): Int {
    if (startIndex > size) return -1
    val subList = subList(startIndex, size)
    val elementIndex = subList.indexOf(element)
    return if (elementIndex < 0) -1 else startIndex + elementIndex
}
