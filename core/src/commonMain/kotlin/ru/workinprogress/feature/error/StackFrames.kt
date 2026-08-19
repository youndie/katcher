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

    /**
     * Frames from these packages are somebody else's code. The list is deliberately short and
     * deliberately incomplete: a wrong guess costs one line of a row or one folded run, and
     * both say what they did — an unrecognised frame is shown, never hidden.
     */
    private val FOREIGN_PREFIXES =
        listOf(
            "java.",
            "javax.",
            "jdk.",
            "sun.",
            "com.sun.",
            "kotlin.",
            "kotlinx.",
            "android.",
            "androidx.",
            "com.android.",
            "dalvik.",
            "libcore.",
            "io.ktor.",
            "io.netty.",
            "io.micrometer.",
            "io.reactivex.",
            "reactor.",
            "okhttp3.",
            "okio.",
            "retrofit2.",
            "com.squareup.",
            "com.google.",
            "com.fasterxml.",
            "com.mongodb.",
            "org.mongodb.",
            "org.apache.",
            "org.springframework.",
            "org.slf4j.",
            "ch.qos.",
            "org.jetbrains.",
            "org.junit.",
            "org.gradle.",
            "org.postgresql.",
            "org.hibernate.",
        )

    /** Whether a frame belongs to the application rather than to a library under it. */
    fun isOwn(symbol: String): Boolean {
        val name = symbol.removePrefix("kfun:")
        return FOREIGN_PREFIXES.none { prefix -> name.startsWith(prefix) }
    }

    /**
     * The package a foreign frame is named by when a run of them is folded into one line.
     * The JDK module prefix — `java.base/` — is dropped because it names the module rather
     * than the code, and the trailing class and method are dropped because the label answers
     * "whose frames are these", not "which ones".
     */
    fun packageOf(symbol: String): String {
        val name = symbol.removePrefix("kfun:").substringAfterLast('/').substringBefore('#')
        val segments = name.split('.')
        // A native symbol is `package.file#method`, so the part before `#` is already the
        // package; a JVM one ends in Class.method.
        val packageSegments = if (symbol.contains('#')) segments else segments.dropLast(2)

        return packageSegments.take(3).joinToString(".").ifEmpty { name }
    }

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
