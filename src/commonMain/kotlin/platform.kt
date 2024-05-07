// Platform-specific function declarations.

import kotlin.experimental.ExperimentalNativeApi

expect val BUILD_TARGET: String

expect fun execute(command: String): List<String>?

expect fun getcwd(): String

expect fun setcwd(cwd: String)

expect fun getenv(name: String): String?

expect fun printlnErr(s: String = "")

expect fun stdinLines(): Array<String>

expect fun mkdir(path: String): Boolean

data class MemoryInfo(var total: Long? = null, var free: Long? = null)

expect fun memInfo(): MemoryInfo

expect val USER_HOME: String?

/** The platform-specific symbol for separating elements in a file path: `/` on POSIX or `\` on Windows. */
expect val SLASH: String

/** The platform-specific symbol for separating files in a list: `:` on POSIX or `;` on Windows. */
expect val COLON: String

/** The platform-specific symbol for terminating a line: `\n` on POSIX or `\r\n` on Windows. */
expect val NL: String

@OptIn(ExperimentalNativeApi::class)
val OS_NAME = Platform.osFamily.name

@OptIn(ExperimentalNativeApi::class)
val CPU_ARCH = Platform.cpuArchitecture.name

fun userHome(): String {
    return USER_HOME ?: throw IllegalArgumentException("Cannot get user's home directory")
}
