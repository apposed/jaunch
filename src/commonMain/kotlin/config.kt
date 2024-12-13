// Jaunch configuration data structure and I/O.

import kotlin.reflect.KClass
import kotlin.reflect.cast

/**
 * All of Jaunch's configuration in a tidy bundle.
 *
 * Instances of this class are typically coalesced from a TOML configuration file.
 * See [the common.toml file](https://github.com/scijava/jaunch/blob/-/configs/common.toml)
 * for a full explanation of what all these fields mean, and how to configure them.
 */
@Suppress("ArrayInDataClass")
data class JaunchConfig (

    // -- General configuration fields --

    /** Jaunch configuration schema version. */
    val jaunchVersion: Int? = null,

    /** Name of the program being launched by Jaunch. */
    val programName: String? = null,

    /** List of other configuration files to import. */
    val includes: Array<String> = emptyArray(),

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

    /** Whether to allow unrecognized arguments to be passed to the runtime. */
    val allowUnrecognizedArgs: Boolean? = null,

    // -- Python-specific configuration fields --

    /** If true, search for suitable Python installations. */
    val pythonEnabled: Boolean? = null,

    /**
     * The list of arguments that Jaunch will recognize as belonging to the Python interpreter,
     * as opposed to the application's main program.
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

    /** If true, search for suitable JVM installations. */
    val jvmEnabled: Boolean? = null,

    /**
     * The list of arguments that Jaunch will recognize as belonging to the JVM,
     * as opposed to the application's main method.
     */
    val jvmRecognizedArgs: Array<String> = emptyArray(),

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
            // NB: when relevant, prefer values in the new config
            jaunchVersion = config.jaunchVersion ?: jaunchVersion,
            programName = config.programName ?: programName,
            includes = merge(config.includes, includes),
            supportedOptions = merge(config.supportedOptions, supportedOptions),
            osAliases = merge(config.osAliases, osAliases),
            archAliases = merge(config.archAliases, archAliases),
            modes = merge(config.modes, modes),
            directives = merge(config.directives, directives),
            allowUnrecognizedArgs = config.allowUnrecognizedArgs ?: allowUnrecognizedArgs,

            pythonEnabled = config.pythonEnabled ?: pythonEnabled,
            pythonRecognizedArgs = merge(config.pythonRecognizedArgs, pythonRecognizedArgs),
            pythonRootPaths = merge(config.pythonRootPaths, pythonRootPaths),
            pythonLibSuffixes = merge(config.pythonLibSuffixes, pythonLibSuffixes),
            pythonVersionMin = config.pythonVersionMin ?: pythonVersionMin,
            pythonVersionMax = config.pythonVersionMax ?: pythonVersionMax,
            pythonPackages = merge(config.pythonPackages, pythonPackages),
            pythonRuntimeArgs = merge(config.pythonRuntimeArgs, pythonRuntimeArgs),
            pythonScriptPath = merge(config.pythonScriptPath, pythonScriptPath),
            pythonMainArgs = merge(config.pythonMainArgs, pythonMainArgs),

            jvmEnabled = config.jvmEnabled ?: jvmEnabled,
            jvmRecognizedArgs = merge(config.jvmRecognizedArgs, jvmRecognizedArgs),
            jvmAllowWeirdRuntimes = config.jvmAllowWeirdRuntimes ?: jvmAllowWeirdRuntimes,
            jvmVersionMin = config.jvmVersionMin ?: jvmVersionMin,
            jvmVersionMax = config.jvmVersionMax ?: jvmVersionMax,
            jvmDistrosAllowed = merge(config.jvmDistrosAllowed, jvmDistrosAllowed),
            jvmDistrosBlocked = merge(config.jvmDistrosBlocked, jvmDistrosBlocked),
            jvmRootPaths = merge(config.jvmRootPaths, jvmRootPaths),
            jvmLibSuffixes = merge(config.jvmLibSuffixes, jvmLibSuffixes),
            jvmClasspath = merge(config.jvmClasspath, jvmClasspath),
            jvmMaxHeap = config.jvmMaxHeap ?: jvmMaxHeap,
            jvmRuntimeArgs = merge(config.jvmRuntimeArgs, jvmRuntimeArgs),
            jvmMainClass = merge(config.jvmMainClass, jvmMainClass),
            jvmMainArgs = merge(config.jvmMainArgs, jvmMainArgs),
        )
    }

    /** Helper method to combine arrays without duplication */
    private fun merge(base: Array<String>, toMerge: Array<String>): Array<String> {
        return (base + toMerge).distinct().toTypedArray()
    }
}

// NB: Previously, the TOML config reader was implemented simply using ktoml.
// It worked well, but bloated the jaunch configurator binary by several megabytes.
// Therefore, to belittle the launcher, Jaunch now does its own minimal parsing.

fun readConfig(
    tomlFile: File,
    config: JaunchConfig? = null,
    visited: MutableSet<String>? = null
): JaunchConfig {
    var theConfig = config ?: JaunchConfig()
    val theVisited = visited ?: mutableSetOf()
    if (!tomlFile.exists) warn("Included config file does not exist: $tomlFile")
    if (!tomlFile.exists || tomlFile.path in theVisited) return theConfig

    debug("Reading config file: $tomlFile")
    theVisited += tomlFile.path

    // Declare Jaunch config fields.
    var jaunchVersion: Int? = null
    var programName: String? = null
    var includes: List<String>? = null
    var supportedOptions: List<String>? = null
    var osAliases: List<String>? = null
    var archAliases: List<String>? = null
    var modes: List<String>? = null
    var directives: List<String>? = null
    var allowUnrecognizedArgs: Boolean? = null
    var pythonEnabled: Boolean? = null
    var pythonRecognizedArgs: List<String>? = null
    var pythonRootPaths: List<String>? = null
    var pythonLibSuffixes: List<String>? = null
    var pythonVersionMin: String? = null
    var pythonVersionMax: String? = null
    var pythonPackages: List<String>? = null
    var pythonRuntimeArgs: List<String>? = null
    var pythonScriptPath: List<String>? = null
    var pythonMainArgs: List<String>? = null
    var jvmEnabled: Boolean? = null
    var jvmRecognizedArgs: List<String>? = null
    var jvmAllowWeirdRuntimes: Boolean? = null
    var jvmVersionMin: String? = null
    var jvmVersionMax: String? = null
    var jvmDistrosAllowed: List<String>? = null
    var jvmDistrosBlocked: List<String>? = null
    var jvmRootPaths: List<String>? = null
    var jvmLibSuffixes: List<String>? = null
    var jvmClasspath: List<String>? = null
    var jvmMaxHeap: String? = null
    var jvmRuntimeArgs: List<String>? = null
    var jvmMainClass: List<String>? = null
    var jvmMainArgs: List<String>? = null

    // Parse TOML file lines into tokens.
    val tokens = mutableListOf<Any>()
    tomlFile.lines().forEach { appendTokens(it, tokens) }

    // Process the tokens to merge list of elements into list tokens.
    // HACK: For now, we ignore commas and newlines. It means people can
    // write invalid TOML that this parser still accepts, but who cares.
    val processedTokens = processLists(tokens).filter { it !in listOf(',', '\n') }

    // The expected pattern at this point is VarAssign, value, VarAssign, value, ...
    // Maybe with a TablePrefix in there occasionally.
    // If we encounter a value outside this pattern, issue a warning and ignore it.
    var i = 0
    var tablePrefix = ""
    while (i < processedTokens.size) {
        when (val token = processedTokens[i++]) {
            is TablePrefix -> tablePrefix = token.prefix
            is VarAssign -> {
                val name = tablePrefix + token.name
                val value = if (i < processedTokens.size) processedTokens[i++] else null
                when (name) {
                    "jaunch-version" -> jaunchVersion = asInt(value)
                    "program-name" -> programName = asString(value)
                    "includes" -> includes = asList(value)
                    "supported-options" -> supportedOptions = asList(value)
                    "os-aliases" -> osAliases = asList(value)
                    "arch-aliases" -> archAliases = asList(value)
                    "modes" -> modes = asList(value)
                    "directives" -> directives = asList(value)
                    "allow-unrecognized-args" -> allowUnrecognizedArgs = asBoolean(value)
                    "python.enabled" -> pythonEnabled = asBoolean(value)
                    "python.recognized-args" -> pythonRecognizedArgs = asList(value)
                    "python.root-paths" -> pythonRootPaths = asList(value)
                    "python.lib-suffixes" -> pythonLibSuffixes = asList(value)
                    "python.version-min" -> pythonVersionMin = asString(value)
                    "python.version-max" -> pythonVersionMax = asString(value)
                    "python.packages" -> pythonPackages = asList(value)
                    "python.runtime-args" -> pythonRuntimeArgs = asList(value)
                    "python.script-path" -> pythonScriptPath = asList(value)
                    "python.main-args" -> pythonMainArgs = asList(value)
                    "jvm.enabled" -> jvmEnabled = asBoolean(value)
                    "jvm.recognized-args" -> jvmRecognizedArgs = asList(value)
                    "jvm.allow-weird-runtimes" -> jvmAllowWeirdRuntimes = asBoolean(value)
                    "jvm.version-min" -> jvmVersionMin = asString(value)
                    "jvm.version-max" -> jvmVersionMax = asString(value)
                    "jvm.distros-allowed" -> jvmDistrosAllowed = asList(value)
                    "jvm.distros-blocked" -> jvmDistrosBlocked = asList(value)
                    "jvm.root-paths" -> jvmRootPaths = asList(value)
                    "jvm.lib-suffixes" -> jvmLibSuffixes = asList(value)
                    "jvm.classpath" -> jvmClasspath = asList(value)
                    "jvm.max-heap" -> jvmMaxHeap = asString(value)
                    "jvm.runtime-args" -> jvmRuntimeArgs = asList(value)
                    "jvm.main-class" -> jvmMainClass = asList(value)
                    "jvm.main-args" -> jvmMainArgs = asList(value)
                }
            }
            else -> warn("[TOML] Ignoring extraneous token: '$token' [${token::class.simpleName}]")
        }
    }

    // Recursively read config file includes.
    for (path in includes ?: emptyList()) {
        theConfig = readConfig(tomlFile.dir / path, theConfig, theVisited)
    }

    // Return the final result.
    return theConfig + JaunchConfig(
        jaunchVersion = jaunchVersion,
        programName = programName,
        includes = asArray(includes),
        supportedOptions = asArray(supportedOptions),
        osAliases = asArray(osAliases),
        archAliases = asArray(archAliases),
        modes = asArray(modes),
        directives = asArray(directives),
        allowUnrecognizedArgs = allowUnrecognizedArgs,
        pythonEnabled = pythonEnabled,
        pythonRecognizedArgs = asArray(pythonRecognizedArgs),
        pythonRootPaths = asArray(pythonRootPaths),
        pythonLibSuffixes = asArray(pythonLibSuffixes),
        pythonVersionMin = pythonVersionMin,
        pythonVersionMax = pythonVersionMax,
        pythonPackages = asArray(pythonPackages),
        pythonRuntimeArgs = asArray(pythonRuntimeArgs),
        pythonScriptPath = asArray(pythonScriptPath),
        pythonMainArgs = asArray(pythonMainArgs),
        jvmEnabled = jvmEnabled,
        jvmRecognizedArgs = asArray(jvmRecognizedArgs),
        jvmAllowWeirdRuntimes = jvmAllowWeirdRuntimes,
        jvmVersionMin = jvmVersionMin,
        jvmVersionMax = jvmVersionMax,
        jvmDistrosAllowed = asArray(jvmDistrosAllowed),
        jvmDistrosBlocked = asArray(jvmDistrosBlocked),
        jvmRootPaths = asArray(jvmRootPaths),
        jvmLibSuffixes = asArray(jvmLibSuffixes),
        jvmClasspath = asArray(jvmClasspath),
        jvmMaxHeap = jvmMaxHeap,
        jvmRuntimeArgs = asArray(jvmRuntimeArgs),
        jvmMainClass = asArray(jvmMainClass),
        jvmMainArgs = asArray(jvmMainArgs),
    )
}

// -- I/O helper functions --

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
private val varAssignPattern = Regex("([\\w.-]+)\\s*=\\s*")
private val symbolPattern = Regex("([\\[\\],])\\s*")

private data class TablePrefix(val prefix: String)
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
    // Parse table headers as entire lines.
    if (s.isNotEmpty() && s[0] == '[') {
        val rBracket = s.indexOf(']')
        if (rBracket >= 0) {
            appendToken(tokens, TablePrefix(s.substring(1, rBracket) + "."))
            return
        }
    }
    // Parse other things token by token.
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
