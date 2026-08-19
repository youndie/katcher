package ru.workinprogress.feature.error

/**
 * What a crash is called in a list: the exception type, the first line of what it said, and
 * the first frame that belongs to the application.
 *
 * The old title was the first two lines of the stacktrace cut at 255 characters, which for
 * a driver exception meant ten rows whose visible text was identical. These three fields are
 * composed instead of truncated, so a row differs from its neighbour where it actually
 * differs.
 */
data class CrashSummary(
    val exceptionType: String?,
    val message: String?,
    val location: String?,
) {
    companion object {
        private const val MAX_MESSAGE = 500

        /**
         * Frames from these packages are somebody else's code. The list is deliberately short
         * and deliberately incomplete: a wrong guess costs one line of a row, and the row says
         * so — an unrecognised frame is shown, never hidden.
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

        fun of(stacktrace: String?): CrashSummary {
            val lines = (stacktrace ?: "").replace("\r\n", "\n").lines()
            val header = lines.firstOrNull { it.isNotBlank() }?.trim().orEmpty()

            val exceptionType = header.substringBefore(':').trim().takeIf { it.isNotEmpty() && it.looksLikeType() }
            val message =
                if (exceptionType == null) {
                    header.takeIf { it.isNotEmpty() }
                } else {
                    header.substringAfter(':', "").trim().takeIf { it.isNotEmpty() }
                }

            val ownFrame =
                lines
                    .asSequence()
                    .mapNotNull(StackFrames::parse)
                    .firstOrNull { frame -> frame.symbol.isOwn() }

            return CrashSummary(
                exceptionType = exceptionType?.substringAfterLast('.')?.takeIf { it.isNotEmpty() } ?: exceptionType,
                message = message?.take(MAX_MESSAGE),
                location = ownFrame?.let { frame -> frame.file + frame.line?.let { ":$it" }.orEmpty() }?.takeIf { it.isNotEmpty() },
            )
        }

        private fun String.looksLikeType(): Boolean = none { it.isWhitespace() } && any { it.isLetter() }

        private fun String.isOwn(): Boolean {
            val symbol = removePrefix("kfun:")
            return FOREIGN_PREFIXES.none { prefix -> symbol.startsWith(prefix) }
        }
    }
}
