package ru.workinprogress.feature.error

/** One stack frame as the report printed it, before anything is made of it. */
data class ParsedFrame(
    val file: String,
    val line: Int?,
    val symbol: String,
)

/**
 * Recognises the frame lines of a stacktrace.
 *
 * Grouping and the MCP metadata view both need to tell a frame from prose, and they must
 * agree on the answer: a frame that grouping reads but the metadata view does not is a
 * crash whose identity nobody can check.
 */
object StackFrames {
    /** `at pkg.Class.method(File.kt:123)` — JVM, Android, and Kotlin/Native `kfun:` frames. */
    val JVM_FRAME = Regex("""^\s*at\s+([^\s(]+)\s*\(([^):]+)(?::(\d+))?\)""")

    /** `at 3  binary  0xADDR  kfun:pkg.Class#method(...) + 99` — Kotlin/Native. */
    val NATIVE_FRAME = Regex("""^\s*at\s+\d+\s+\S+\s+0x[0-9a-fA-F]+\s+(\S+)""")

    fun parse(line: String): ParsedFrame? {
        JVM_FRAME.find(line)?.let { match ->
            return ParsedFrame(
                file = match.groupValues[2],
                line = match.groupValues[3].toIntOrNull(),
                symbol = match.groupValues[1],
            )
        }

        NATIVE_FRAME.find(line)?.let { match ->
            // Native frames carry no source file; the symbol is all there is.
            return ParsedFrame(file = "", line = null, symbol = match.groupValues[1])
        }

        return null
    }
}
