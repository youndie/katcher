package ru.workinprogress.feature.error

/**
 * A stacktrace as the group page shows it: your frames, and the runs of somebody else's
 * frames between them folded into one line each.
 *
 * A forty-frame trace where four frames are yours is unreadable in full and misleading when
 * cut, so the foreign runs stay — named and counted — instead of disappearing.
 */
sealed interface StackChunk {
    /** The exception line, a `Caused by:` line, anything that is not a frame. */
    data class Text(
        val text: String,
    ) : StackChunk

    data class Own(
        val text: String,
        val frame: ParsedFrame,
    ) : StackChunk

    data class Foreign(
        val count: Int,
        val packages: List<String>,
        val lines: List<String>,
    ) : StackChunk {
        /** "6 frames from io.ktor.server, kotlinx.coroutines". */
        val label: String
            get() {
                val frames = if (count == 1) "1 frame" else "$count frames"
                return if (packages.isEmpty()) frames else "$frames from ${packages.joinToString(", ")}"
            }
    }
}

object StackTrace {
    /** More than this in one run's label and it stops being a label. */
    private const val MAX_PACKAGES = 2

    fun fold(
        stacktrace: String?,
        expandAll: Boolean = false,
    ): List<StackChunk> {
        val lines = (stacktrace ?: "").replace("\r\n", "\n").lines().filter { it.isNotBlank() }
        val chunks = mutableListOf<StackChunk>()
        val pending = mutableListOf<Pair<String, ParsedFrame>>()

        fun flush() {
            if (pending.isEmpty()) return

            if (expandAll) {
                pending.forEach { (text, frame) -> chunks += StackChunk.Own(text, frame) }
            } else {
                chunks +=
                    StackChunk.Foreign(
                        count = pending.size,
                        packages =
                            pending
                                .map { (_, frame) -> StackFrames.packageOf(frame.symbol) }
                                .distinct()
                                .take(MAX_PACKAGES),
                        lines = pending.map { (text, _) -> text },
                    )
            }
            pending.clear()
        }

        lines.forEach { line ->
            val frame = StackFrames.parse(line)

            when {
                frame == null -> {
                    flush()
                    chunks += StackChunk.Text(line.trim())
                }

                StackFrames.isOwn(frame.symbol) -> {
                    flush()
                    chunks += StackChunk.Own(line.trim(), frame)
                }

                else -> {
                    pending += line.trim() to frame
                }
            }
        }
        flush()

        return chunks
    }

    /** "34 frames · 4 yours" — the count under the panel title. */
    fun frameCounts(stacktrace: String?): Pair<Int, Int> {
        val frames =
            (stacktrace ?: "")
                .replace("\r\n", "\n")
                .lines()
                .mapNotNull(StackFrames::parse)

        return frames.size to frames.count { StackFrames.isOwn(it.symbol) }
    }
}
