expect fun executeCommand(command: String): Int

expect fun getenv(name: String): String?

expect fun stdinLines(): Array<String>

expect val SLASH: String
expect val COLON: String
