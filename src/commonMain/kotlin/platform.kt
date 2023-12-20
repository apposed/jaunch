expect fun executeCommand(command: String): Int

expect fun getenv(name: String): String?

expect fun stdinLines(): Array<String>

expect val slash: String
expect val colon: String
