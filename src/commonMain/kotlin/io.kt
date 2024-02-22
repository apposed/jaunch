import kotlin.reflect.KClass
import kotlin.reflect.cast

const val BUFFER_SIZE = 65536

// NB: Previously, the TOML config reader was implemented simply using ktoml.
// It worked well, but bloated the jaunch configurator binary by several megabytes.
// Therefore, to belittle the launcher, Jaunch now does its own minimal parsing.

fun readConfig(tomlFile: File): JaunchConfig {
    debug("Reading config file: $tomlFile")
    if (!tomlFile.exists) return JaunchConfig()

    // Declare Jaunch config fields.
    var jaunchVersion: Int? = null
    var programName: String? = null
    var supportedOptions: List<String>? = null
    var osAliases: List<String>? = null
    var archAliases: List<String>? = null
    var modes: List<String>? = null
    var directives: List<String>? = null
    var pythonRecognizedArgs: List<String>? = null
    var pythonRootPaths: List<String>? = null
    var pythonLibSuffixes: List<String>? = null
    var pythonVersionMin: String? = null
    var pythonVersionMax: String? = null
    var pythonPackages: List<String>? = null
    var pythonRuntimeArgs: List<String>? = null
    var pythonScriptPath: List<String>? = null
    var pythonMainArgs: List<String>? = null
    var jvmRecognizedArgs: List<String>? = null
    var jvmAllowUnrecognizedArgs: Boolean? = null
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
                    "supported-options" -> supportedOptions = asList(value)
                    "os-aliases" -> osAliases = asList(value)
                    "arch-aliases" -> archAliases = asList(value)
                    "modes" -> modes = asList(value)
                    "directives" -> directives = asList(value)
                    "python.recognized-args" -> pythonRecognizedArgs = asList(value)
                    "python.root-paths" -> pythonRootPaths = asList(value)
                    "python.lib-suffixes" -> pythonLibSuffixes = asList(value)
                    "python.version-min" -> pythonVersionMin = asString(value)
                    "python.version-max" -> pythonVersionMax = asString(value)
                    "python.packages" -> pythonPackages = asList(value)
                    "python.runtime-args" -> pythonRuntimeArgs = asList(value)
                    "python.script-path" -> pythonScriptPath = asList(value)
                    "python.main-args" -> pythonMainArgs = asList(value)
                    "jvm.recognized-args" -> jvmRecognizedArgs = asList(value)
                    "jvm.allow-unrecognized-args" -> jvmAllowUnrecognizedArgs = asBoolean(value)
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

    // Return the final result.
    return JaunchConfig(
        jaunchVersion = jaunchVersion,
        programName = programName,
        supportedOptions = asArray(supportedOptions),
        osAliases = asArray(osAliases),
        archAliases = asArray(archAliases),
        modes = asArray(modes),
        directives = asArray(directives),
        pythonRecognizedArgs = asArray(pythonRecognizedArgs),
        pythonRootPaths = asArray(pythonRootPaths),
        pythonLibSuffixes = asArray(pythonLibSuffixes),
        pythonVersionMin = pythonVersionMin,
        pythonVersionMax = pythonVersionMax,
        pythonPackages = asArray(pythonPackages),
        pythonRuntimeArgs = asArray(pythonRuntimeArgs),
        pythonScriptPath = asArray(pythonScriptPath),
        pythonMainArgs = asArray(pythonMainArgs),
        jvmRecognizedArgs = asArray(jvmRecognizedArgs),
        jvmAllowUnrecognizedArgs = jvmAllowUnrecognizedArgs,
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
