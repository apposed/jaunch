import kotlin.experimental.ExperimentalNativeApi

expect fun execute(command: String): List<String>?

expect fun getenv(name: String): String?

expect fun printlnErr(s: String = "")

expect fun stdinLines(): Array<String>

data class MemoryInfo(var total: Long? = null, var free: Long? = null)

expect fun memInfo(): MemoryInfo

expect val USER_HOME: String?
expect val SLASH: String
expect val COLON: String

@OptIn(ExperimentalNativeApi::class)
val OS_NAME = Platform.osFamily.name

@OptIn(ExperimentalNativeApi::class)
val CPU_ARCH = Platform.cpuArchitecture.name
