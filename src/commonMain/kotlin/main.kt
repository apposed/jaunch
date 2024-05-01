// Main entry point for Jaunch configurator.

import platform.posix.exit

// Main entry point for Jaunch configurator.

const val USAGE_MESSAGE = """
Hello! You have found the Jaunch configurator.
Your curiosity is an asset. :-)

This program is intended to be called internally by Jaunch's native
launcher executable. Normally, you do not need to run it yourself.

However, if you wish, you can test its behavior by passing command line
arguments in the same manner as you would to Jaunch's native launcher,
prepended by the name of the native launcher executable.

For example, if your native launcher is called fizzbuzz, you could try:

    jaunch fizzbuzz --heap 2g --debugger 8000

and watch how Jaunch transforms the arguments.
If the behavior is not what you expect, try using the --debug flag:

    jaunch fizzbuzz --heap 2g --debugger 8000 --debug

You can also see similar information using Jaunch's --dry-run option:

    fizzbuzz --heap 2g --debugger 8000 --dry-run

For more details, check out the nearby TOML files. Happy Jaunching!
"""

@Suppress("ArrayInDataClass")
data class JaunchOption(
    val flags: Array<String>,
    val assignment: String?,
    val help: String?,
) {
    fun help(): String = buildString {
        val indent = "$NL                    "
        append(flags.joinToString(", "))
        assignment?.let { append(" $it") }
        help?.let { append("$indent${it.replace("\n", indent)}") }
    }
}

typealias JaunchOptions = Map<String, JaunchOption>

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        // The program was run without arguments, likely manually.
        // So we display a friendly greeting with tips, then quit.
        printlnErr(USAGE_MESSAGE)
        exit(1)
    }

    val (exeFile, inputArgs) = parseArguments(args)
    val appDir = discernAppDirectory(exeFile)
    val configDir = findConfigDirectory(appDir)
    val config = readConfigFile(configDir, exeFile)

    val programName = config.programName ?: exeFile?.base?.name ?: "Jaunch"
    debug("programName -> ", programName)

    val supportedOptions: JaunchOptions = parseSupportedOptions(config.supportedOptions.asIterable())

    val hints = createHints()
    val vars = createVars(appDir, configDir, exeFile)

    // Sort out the arguments, keeping the user-specified runtime and main arguments in a struct. At this point,
    // it may yet be ambiguous whether certain user args belong with the runtime, the main program, or neither.
    val userArgs = classifyArguments(inputArgs, supportedOptions, vars, hints)

    applyModeHints(config.modes, hints, vars)

    val runtimes = configureRuntimes(config, configDir, hints, vars)
    val (launchDirectives, configDirectives) = calculateDirectives(config, hints, vars)

    // Ensure that the user arguments meet our expectations.
    validateUserArgs(config, runtimes, userArgs)

    // Calculate program arguments *in context* -- i.e. with respect to each runtime.
    // This is a map from each runtime prefix to its contextualized arguments (bundle of runtime and main args).
    val argsInContext = runtimes.associate { it.prefix to contextualizeArgs(runtimes, it, userArgs) }
    vars += argsInContext.entries.associate { (k, v) -> k to v.toString() }

    // Now evaluate any expressions in the contextualized arguments.
    // We do this in a subsequent step separated from the previous one because the expressions
    // in question might themselves refer to the contextualized arguments of other runtimes.
    // Why? So that we can e.g. pass the final JVM arguments to a PYTHON launch, where the
    // Python script being invoked will itself start up a JVM with those given JVM arguments.
    // In the case of cyclic variable references between runtimes, the interpolation will be incomplete.
    interpolateArgs(argsInContext, vars)

    // Declare the global (runtime-agnostic) directives.
    val globalDirectiveFunctions: DirectivesMap = mutableMapOf(
        "help" to { _ -> help(exeFile, programName, supportedOptions) }
    )

    // Finally, execute all the directives! \^_^/
    executeDirectives(globalDirectiveFunctions,
        configDirectives, launchDirectives,
        runtimes, userArgs, argsInContext)
}

// -- Program flow functions --

private fun parseArguments(args: Array<String>): Pair<File?, List<String>> {
    // If a sole `-` argument was given on the CLI, read the arguments from stdin.
    val theArgs = if (args.size == 1 && args[0] == "-") stdinLines() else args

    // The first argument is the path to the calling executable.
    val executable = theArgs.getOrNull(0)

    // Subsequent arguments were specified by the user.
    val inputArgs = theArgs.slice(1..<theArgs.size)

    // Enable debug mode when --debug flag is present.
    debugMode = inputArgs.contains("--debug")

    debug("executable -> ", executable ?: "<null>")
    debug("inputArgs -> ", inputArgs)

    val exeFile = executable?.let(::File) // The native launcher program.
    return Pair(exeFile, inputArgs)
}

private fun discernAppDirectory(exeFile: File?): File {
    // Check for native launcher in Contents/MacOS directory.
    // If so, treat the app directory as two directories higher up.
    // We do it this way, rather than OS_NAME == "MACOSX", so that the native
    // launcher also works on macOS when placed directly in the app directory.
    val exeDir = exeFile?.dir ?: File(".")
    val appDir = if (exeDir.name == "MacOS" && exeDir.dir.name == "Contents") exeDir.dir.dir else exeDir
    debug("appDir -> ", appDir)
    return appDir
}

private fun findConfigDirectory(appDir: File): File {
    // NB: This list should match the JAUNCH_SEARCH_PATHS array in jaunch.c.
    val configDirs = listOf(
        appDir / "jaunch",
        appDir / ".jaunch",
        appDir / "config" / "jaunch",
        appDir / ".config" / "jaunch"
    )
    val configDir = configDirs.find { it.isDirectory }
        ?: fail("Jaunch config directory not found. Please place config in one of: $configDirs")
    debug("configDir -> ", configDir)
    return configDir
}

private fun readConfigFile(configDir: File, exeFile: File?): JaunchConfig {
    var fileName = if (exeFile == null) "jaunch" else "${exeFile.base.name}"
    // The launcher might have trailing suffixes such as OS_NAME and/or CPU_ARCH.
    // For example: fizzbuzz-linux-x64. In such a situation, we want to look for
    // fizzbuzz-linux-x64.toml, fizzbuzz-linux.toml, and fizzbuzz.toml.
    while (true) {
        val configFile = configDir / "$fileName.toml"
        debug("Looking for config file: $configFile")
        if (configFile.exists) return readConfig(configFile)
        val dash = fileName.lastIndexOf("-")
        if (dash < 0) break
        fileName = fileName.substring(0, dash)
    }
    fail("No config file found for $fileName")
}

/**
 * Parse the configuration's declared Jaunch options, wrapping each into a `JaunchOption` object.
 *
 * For each option, we have a string of the form:
 *   --alpha,...,--omega=assignment|Description of what this option does.
 *
 * We need to parse out the help text, the actual flags list, and whether the flags expect an assignment value.
 *
 * @param supportedOptions A `supportedOptions` list from a `JaunchConfig`.
 * @return a map of supported options, from each of the option's flags to the corresponding JaunchOption object.
 */
private fun parseSupportedOptions(supportedOptions: Iterable<String>): JaunchOptions {
    debug()
    debug("Parsing supported options...")
    return buildMap {
        for (optionLine in supportedOptions) {
            val (optionString, help) = optionLine bisect '|'
            val (flagsString, assignment) = optionString bisect '='
            val flags = flagsString.split(',')
            val option = JaunchOption(flags.toTypedArray(), assignment, help)
            debug("* ", option)
            for (flag in flags) this[flag] = option
        }
    }
}

/**
 * Declare a set of active hints, for activation of configuration elements.
 * Initially populated with hints for the current operating system and CPU architecture,
 * but it will grow over the course of the configuration process below.
 */
private fun createHints(): MutableSet<String> {
    val hints = mutableSetOf(
        // Kotlin knows these operating systems:
        //   UNKNOWN, MACOSX, IOS, LINUX, WINDOWS, ANDROID, WASM, TVOS, WATCHOS
        "OS:$OS_NAME",
        // Kotlin knows these CPU architectures:
        //   UNKNOWN, ARM32, ARM64, X86, X64, MIPS32, MIPSEL32, WASM32
        "ARCH:$CPU_ARCH"
    )
    return hints
}

/**
 * Declare a set to store option parameter values.
 * It will be populated at argument parsing time.
 */
private fun createVars(
    appDir: File,
    configDir: File,
    exeFile: File?
): MutableMap<String, String> {
    val vars = mutableMapOf(
        // Special variable containing application directory path.
        "app-dir" to appDir.path,
        // Special variable containing config directory path.
        "config-dir" to configDir.path,
    )
    if (exeFile?.exists == true) vars["executable"] = exeFile.path
    return vars
}

/**
 * Categorize the configurator's input arguments.
 *
 * An input argument matching one of Jaunch's supported options becomes an active hint.
 * So e.g. `--foo` would be added to the hints set as `--foo`.
 *
 * A matching input argument that takes a parameter not only adds the option as a hint,
 * but also adds the parameter value to the vars map.
 * So e.g. `--sigmas=5` would add the `--sigma` hint, and add `"5"` as the value for the `sigma` variable.
 * Variable values will be interpolated into argument strings later in the configuration process.
 *
 * Non-matching input arguments will be returned as a [ProgramArgs] struct, with each argument
 * sorted into one of three buckets: runtime args, main args, and ambiguous args. The exact
 * behavior will depend on whether the user provided the `--` separator in the input argument list.
 */
private fun classifyArguments(
    inputArgs: List<String>,
    supportedOptions: JaunchOptions,
    vars: MutableMap<String, String>,
    hints: MutableSet<String>
): ProgramArgs {
    val userArgs = ProgramArgs()
    val divider = inputArgs.indexOf("--")
    var i = 0
    while (i < inputArgs.size) {
        val arg = inputArgs[i++]

        // Handle both --key and --key=value kinds of arguments.
        val equals = arg.indexOf("=")
        val argKey = if (equals >= 0) arg.substring(0, equals) else arg
        val argVal = if (equals >= 0) arg.substring(equals + 1) else null

        if (argKey == "--") {
            if (argVal != null) fail("Divider symbol (--) does not accept a parameter")
            if (i - 1 != divider) fail("Divider symbol (--) may only be given once")
        } else if ((divider < 0 || i <= divider) && argKey in supportedOptions) {
            // The argument is declared in Jaunch's configuration. Deal with it appropriately.
            val option: JaunchOption = supportedOptions[argKey]!!
            if (option.assignment == null) {
                // standalone option
                if (argVal != null) fail("Option $argKey does not accept a parameter")
            } else {
                // option with value assignment
                val v = argVal ?: if (i < inputArgs.size) inputArgs[i++] else
                    fail("No parameter value given for argument $argKey")

                // Normalize the argument to the primary flag on the comma-separated list.
                // Then strip leading dashes: --class-path becomes class-path, -v becomes v, etc.
                var varName = option.flags.first()
                while (varName.startsWith("-")) varName = varName.substring(1)

                // Store assignment value as a variable named after the normalized argument.
                // E.g. if the matching supported option is `--heap,--mem=<max>|Maximum heap size`,
                // and `--mem 52g` is given, it will store `"52g"` for the variable `heap`.
                vars[varName] = v
            }
            hints += argKey
        } else {
            // The argument is not a Jaunch one. Save it for later.
            if (divider < 0) {
                // No dash-dash divider was given, so we will need to guess later: runtime arg or main arg?
                userArgs.ambiguous += arg
            } else if (i <= divider) {
                // This argument is before the dash-dash divider, so must be treated as a runtime arg.
                userArgs.runtime += arg
            } else {
                // This argument is after the dash-dash divider, so we treat it as a main arg.
                userArgs.main += arg
            }
        }
    }

    debug()
    debug("Input arguments processed:")
    debug("* hints -> ", hints)
    debug("* vars -> ", vars)
    debug("* userArgs.runtime -> ", userArgs.runtime)
    debug("* userArgs.main -> ", userArgs.main)
    debug("* userArgs.ambiguous -> ", userArgs.ambiguous)

    return userArgs
}

private fun applyModeHints(
    modes: Array<String>,
    hints: MutableSet<String>,
    vars: MutableMap<String, String>
) {
    for (mode in calculate(modes, hints, vars)) {
        if (mode.startsWith("!")) {
            // Negated mode expression: remove the mode hint.
            hints -= mode.substring(1)
        } else hints += mode
    }
    debug()
    debug("Modes applied:")
    debug("* hints -> ", hints)
}

private fun configureRuntimes(
    config: JaunchConfig,
    configDir: File,
    hints: MutableSet<String>,
    vars: MutableMap<String, String>
): List<RuntimeConfig> {
    debug()
    debug("Configuring runtimes...")

    // Build the list of enabled runtimes.
    val runtimes = mutableListOf<RuntimeConfig>()
    if (config.jvmEnabled == true) runtimes += JvmRuntimeConfig(config.jvmRecognizedArgs)
    if (config.pythonEnabled == true) runtimes += PythonRuntimeConfig(config.pythonRecognizedArgs)

    // Discover the runtime installations.
    for (r in runtimes) r.configure(configDir, config, hints, vars)

    return runtimes
}

/** Discern directives to perform. */
private fun calculateDirectives(
    config: JaunchConfig,
    hints: MutableSet<String>,
    vars: MutableMap<String, String>
): Pair<List<String>, List<String>> {
    val directives = calculate(config.directives, hints, vars).flatMap { it.split(",") }.toSet()
    val (launchDirectives, configDirectives) = directives.partition { it == it.uppercase() }

    debug()
    debug("Directives parsed:")
    debug("* directives -> ", directives)
    debug("* launchDirectives -> ", launchDirectives)
    debug("* configDirectives -> ", configDirectives)

    return Pair(launchDirectives, configDirectives)
}

/** Ensure user arguments meet expectations. */
fun validateUserArgs(
    config: JaunchConfig,
    runtimes: List<RuntimeConfig>,
    userArgs: ProgramArgs
) {
    debug()
    debug("Validating user arguments...")

    // Verify that some runtime recognizes each argument given as a runtime arg,
    // unless allow-unrecognized-args is set to true.
    val strict = config.allowUnrecognizedArgs != true
    for (arg in userArgs.runtime) {
        if (strict && unknownArg(runtimes, arg)) fail("Unrecognized runtime argument: $arg")
    }
}

fun contextualizeArgs(
    runtimes: List<RuntimeConfig>,
    runtime: RuntimeConfig,
    userArgs: ProgramArgs
): ProgramArgs {
    val resolved = ProgramArgs()

    // Add the already-resolved arguments for this specific runtime.
    resolved.runtime += runtime.runtimeArgs
    resolved.main += runtime.mainArgs

    // Add user-specified arguments intended as runtime args.
    for (arg in userArgs.runtime) {
        // There are three scenarios here:
        // 1) This runtime recognizes the argument, so we add it to this runtime's list of runtime args.
        // 2) This runtime does not recognize it, but some other runtime(s) do, in which case we skip it.
        // 3) No runtime recognizes the argument, so we add it to this (and all) runtime's list of runtime args.
        //
        // Note that if we are at this point in the code and scenario (3) happens, it must be because the
        // allow-unrecognized-args flag was set to true (otherwise the validateUserArgs check would have failed),
        // so it makes sense to throw up our hands and pass this weird argument to all enabled runtimes.
        if (arg in runtime.recognizedArgs || unknownArg(runtimes, arg)) resolved.runtime += arg
    }

    // Add user-specified arguments intended as main args. Nothing tricky here.
    resolved.main += userArgs.main

    // Finally: we need to sort through the ambiguous user arguments.
    // If the user used the minus-minus (--) separator, this list will be empty.
    for (arg in userArgs.ambiguous) {
        if (arg in runtime.recognizedArgs) resolved.runtime += arg
        else if (unknownArg(runtimes, arg)) resolved.main += arg
        // else some other runtime will snarf up this arg, so this one should ignore it.
    }

    return resolved
}

private fun unknownArg(
    runtimes: List<RuntimeConfig>,
    arg: String
): Boolean {
    return runtimes.firstOrNull { arg in it.recognizedArgs } == null
}

private fun interpolateArgs(argsInContext: Map<String, ProgramArgs>, vars: Map<String, String>) {
    // TODO: something ;-)
    /*
    for (args in argsInContext.values) {
        val nooRuntime = mutableListOf<String>()
        for (arg in args.runtime) nooRuntime += interpolate(arg)
        args.runtime.clear()
        args.runtime += nooRuntime

        val nooMain = mutableListOf<String>()
        for (arg in args.main) nooMain += interpolate(arg)
        args.main.clear()
        args.main += nooMain
    }
    */
}

private fun executeDirectives(
    globalDirectiveFunctions: DirectivesMap,
    configDirectives: List<String>,
    launchDirectives: List<String>,
    runtimes: List<RuntimeConfig>,
    userArgs: ProgramArgs,
    argsInContext: Map<String, ProgramArgs>
) {
    // Execute the configurator-side directives.
    debug()
    debug("Executing configurator-side directives...")
    for (directive in configDirectives) {
        // Execute the directive globally if possible.
        val doDirective = globalDirectiveFunctions[directive]
        if (doDirective != null) {
            doDirective(userArgs)
            continue
        }

        // Not a global directive -- delegate execution to activated runtimes.
        var success = false
        for (runtime in runtimes) {
            val myArgs = argsInContext[runtime.prefix]
                ?: fail("No contextual args for {runtime.prefix} runtime?!")
            success = success || runtime.tryDirective(directive, myArgs)
        }
        if (!success) fail("Invalid directive: $directive")
    }

    // Emit launch-side directives.
    debug()
    debug("Emitting launch directives to stdout...")
    // HACK: If ABORT appears in the launch directives, don't also launch other things.
    // Further thought and config wrangling needed, but it gets the job done for now.
    val finalDirectives =
        if (launchDirectives.isEmpty() || "ABORT" in launchDirectives) listOf("ABORT") else launchDirectives
    for (directive in finalDirectives) {
        val runtime = runtimes.firstOrNull { it.directive == directive }
        val lines = runtime?.launch(argsInContext[runtime.prefix]!!) ?: emptyList()
        println(directive)
        println(lines.size)
        lines.forEach { println(it) }
    }
}

// -- Helper functions --

private infix fun String.bisect(delimiter: Char): Pair<String, String?> {
    val index = indexOf(delimiter)
    return if (index < 0) Pair(this, null) else Pair(substring(0, index), substring(index + 1))
}

// -- Directive handlers --

private fun help(exeFile: File?, programName: String, supportedOptions: JaunchOptions) {
    val exeName = exeFile?.name ?: "jaunch"
    printlnErr("Usage: $exeName [<Runtime options>.. --] [<main arguments>..]")
    printlnErr()
    printlnErr("$programName launcher (Jaunch v$JAUNCH_VERSION / $JAUNCH_BUILD / $BUILD_TARGET)")
    printlnErr("Runtime options are passed to the runtime platform (JVM or Python),")
    printlnErr("main arguments to the launched program ($programName).")
    printlnErr()
    printlnErr("In addition, the following options are supported:")
    val optionsUnique = linkedSetOf(*supportedOptions.values.toTypedArray())
    optionsUnique.forEach { printlnErr(it.help()) }
}
