package ru.workinprogress.feature.error

import org.kotlincrypto.hash.sha2.SHA256
import ru.workinprogress.feature.report.CreateReportParams
import ru.workinprogress.feature.report.ReportRepository
import ru.workinprogress.feature.symbolication.SymbolicationService

class DuplicateErrorGroupException(
    message: String,
) : Exception(message)

class ProcessReportUseCase(
    private val symbolicationService: SymbolicationService,
    private val errorGroupRepository: ErrorGroupRepository,
    private val reportRepository: ReportRepository,
    private val visitedRepository: ErrorGroupViewedRepository,
) {
    suspend fun process(
        createReportParams: CreateReportParams,
        appId: Int,
    ) {
        val retraced =
            createReportParams.copy(
                stacktrace =
                    symbolicationService.processCrash(
                        appId = appId,
                        buildUuid = createReportParams.context?.get("build_uuid"),
                        rawStacktrace = createReportParams.stacktrace,
                    ),
            )

        val fingerprint = generateFingerprint(retraced.stacktrace)
        var group = errorGroupRepository.findByFingerprint(appId, fingerprint)

        if (group == null) {
            group =
                try {
                    errorGroupRepository.insert(
                        CreateErrorGroupParams(
                            appId = appId,
                            fingerprint = fingerprint,
                            title =
                                retraced.stacktrace
                                    .lineSequence()
                                    .take(2)
                                    .joinToString("\n")
                                    .take(255),
                        ),
                    )
                } catch (e: DuplicateErrorGroupException) {
                    errorGroupRepository.findByFingerprint(appId, fingerprint)
                }
        }

        if (group == null) {
            return
        }

        reportRepository.insert(appId, group.id, retraced)
        errorGroupRepository.updateOccurrences(group.id)
        visitedRepository.removeVisits(group.id)
    }

    companion object {
        /** Frames below this depth say more about the framework than about the bug. */
        private const val FRAME_DEPTH = 5

        /** Used only when nothing in the report parses as a frame. */
        private const val HEADLESS_LINES = 5

        /**
         * A message longer than this is a dump, and a dump is where the volatile parts live.
         */
        private const val MESSAGE_HEAD = 200

        /**
         * Identifies a crash by what stays the same between two reports of it: the exception
         * type, the shape of the top frames, and the head of the message.
         *
         * The message is deliberately not trusted whole. Drivers print state into it —
         * `MongoCommandException` carries the entire server response, cluster timestamps and
         * signature hashes included — so two reports of one bug differ byte for byte, and
         * hashing the raw text opened a group per occurrence. Frames do not drift like that,
         * so they carry the identity; the normalized message head only refines it.
         */
        fun generateFingerprint(stackTrace: String?): String {
            val lines =
                (stackTrace ?: "")
                    .replace("\r\n", "\n")
                    .lines()

            val frames =
                lines
                    .asSequence()
                    .mapNotNull(StackFrames::parse)
                    .take(FRAME_DEPTH)
                    .toList()

            val signature =
                if (frames.isEmpty()) {
                    // No frames parsed: an unsymbolicated crash, or a report that is only text.
                    // The message is all there is, so it has to carry the group on its own.
                    normalize(lines.take(HEADLESS_LINES).joinToString("\n"))
                } else {
                    val header = lines.firstOrNull { it.isNotBlank() }.orEmpty().trim()
                    // The type is kept verbatim: normalize() reads a dotted name as a host and
                    // would replace the whole of it, merging unrelated exceptions.
                    val type = header.substringBefore(':').trim().lowercase()
                    val message = normalize(header.substringAfter(':', "")).take(MESSAGE_HEAD)
                    // Line numbers are left out on purpose: an edit above the throw site moves
                    // every frame under it, and that is not a new bug.
                    val shape = frames.map { frame -> frame.symbol + "(" + frame.file + ")" }
                    (listOf(type, message) + shape).joinToString("\n")
                }

            return signature.sha256()
        }

        private fun String.sha256(): String {
            val digest = SHA256().digest(encodeToByteArray())
            return digest.joinToString("") { byte ->
                val v = byte.toInt() and 0xff
                v.toString(16).padStart(2, '0')
            }
        }

        private val hexRegex = Regex("""\b0x[0-9a-f]+\b""")

        private val pathRegex = Regex("""(?:[a-zA-Z]:\\|/)(?:[^<>:"/\\|?*\n]*[/\\])*[^<>:"/\\|?*\n]*""")
        private val ipRegex = Regex("""\b(?:\d{1,3}\.){3}\d{1,3}\b""")
        private val urlRegex = Regex("""(?:https?://)?(?:[\w-]+\.)+[\w-]+(?:/[^/\s]*)*""")
        private val uuidRegex = Regex("""\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b""")

        // Lowercase forms: normalize() lowercases before any of these run, so an uppercase
        // `T` or `Z` here would never match.
        private val tsRegex = Regex("""\b\d{4}-\d{2}-\d{2}[t ]\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:z|[+-]\d{2}:?\d{2})?\b""")
        private val emailRegex = Regex("""[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}""")

        /**
         * Base64 blobs, object ids, session and request ids: opaque runs that are never the
         * same twice. Only runs carrying a digit are replaced, so a long ordinary word
         * survives.
         */
        private val opaqueRegex = Regex("""[a-z0-9+/=]{16,}""")

        private val numberRegex = Regex("""\b\d+(\.\d+)?(e[-+]?\d+)?\b""")

        private val whitespaceRegex = Regex("""\s+""")

        /**
         * Replaces the parts of a message that differ between two reports of the same bug.
         *
         * Order is load-bearing: the shapes that contain digits — timestamps, addresses,
         * opaque ids — have to be recognised before [numberRegex] takes the digits away.
         */
        fun normalize(text: String): String =
            text
                .lowercase()
                .replace(emailRegex, "<EMAIL>")
                .replace(uuidRegex, "<UUID>")
                .replace(tsRegex, "<TIMESTAMP>")
                .replace(ipRegex, "<IP>")
                .replace(hexRegex, "<HEX>")
                .replace(urlRegex, "<URL>")
                .replace(opaqueRegex) { match ->
                    if (match.value.any(Char::isDigit)) "<TOKEN>" else match.value
                }.replace(pathRegex, "<PATH>")
                .replace(numberRegex, "<NUM>")
                .replace(whitespaceRegex, " ")
                .trim()
    }
}
