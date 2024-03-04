// Platform-specific function declarations.

import kotlin.experimental.ExperimentalNativeApi

expect val BUILD_TARGET: String

expect fun execute(command: String): List<String>?

expect fun getcwd(): String

expect fun setcwd(cwd: String)

expect fun getenv(name: String): String?

expect fun printlnErr(s: String = "")

expect fun stdinLines(): Array<String>

data class MemoryInfo(var total: Long? = null, var free: Long? = null)

expect fun memInfo(): MemoryInfo

expect val USER_HOME: String?
expect val SLASH: String
expect val COLON: String
expect val NL: String

@OptIn(ExperimentalNativeApi::class)
val OS_NAME = Platform.osFamily.name

@OptIn(ExperimentalNativeApi::class)
val CPU_ARCH = Platform.cpuArchitecture.name

fun userHome(): String {
    return USER_HOME ?: throw IllegalArgumentException("Cannot get user's home directory")
}
