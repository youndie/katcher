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
                    .firstOrNull { frame -> StackFrames.isOwn(frame.symbol) }

            return CrashSummary(
                exceptionType = exceptionType?.substringAfterLast('.')?.takeIf { it.isNotEmpty() } ?: exceptionType,
                message = message?.take(MAX_MESSAGE),
                location = ownFrame?.let { frame -> frame.file + frame.line?.let { ":$it" }.orEmpty() }?.takeIf { it.isNotEmpty() },
            )
        }

        private fun String.looksLikeType(): Boolean = none { it.isWhitespace() } && any { it.isLetter() }
    }
}
