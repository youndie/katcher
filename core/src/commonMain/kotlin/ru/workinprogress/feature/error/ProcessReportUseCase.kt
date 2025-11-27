package ru.workinprogress.feature.error

import org.kotlincrypto.hash.sha2.SHA256
import ru.workinprogress.feature.report.CreateReportParams
import ru.workinprogress.feature.report.ReportRepository

class DuplicateErrorGroupException(
    message: String,
) : Exception(message)

class ProcessReportUseCase(
    private val errorGroupRepository: ErrorGroupRepository,
    private val reportRepository: ReportRepository,
) {
    suspend fun process(
        createReportParams: CreateReportParams,
        appId: Int,
    ) {
        val fingerprint = generateFingerprint(createReportParams.stacktrace)

        println(
            "M: ${createReportParams.message}\n" +
                "ST: ${createReportParams.stacktrace}\n" +
                "Processing report with fingerprint: $fingerprint",
        )

        var group = errorGroupRepository.findByFingerprint(appId, fingerprint)

        if (group == null) {
            println("Creating new error group for fingerprint: $fingerprint")

            group =
                try {
                    errorGroupRepository.insert(
                        CreateErrorGroupParams(
                            appId = appId,
                            fingerprint = fingerprint,
                            title =
                                createReportParams.stacktrace
                                    .lineSequence()
                                    .take(2)
                                    .joinToString("\n")
                                    .take(255),
                        ),
                    )
                } catch (e: DuplicateErrorGroupException) {
                    println("Race detected — existing error group was just created by another thread")
                    errorGroupRepository.findByFingerprint(appId, fingerprint)
                }
        } else {
            println("Found existing error group with id: ${group.id}")
        }

        if (group == null) {
            println("⚠️ Failed to create or find error group for fingerprint: $fingerprint")
            return
        }

        reportRepository.insert(appId, group.id, createReportParams)
        errorGroupRepository.updateOccurrences(group.id)
    }

    companion object {
        fun generateFingerprint(stackTrace: String?): String {
            val normalizedStack =
                normalize(
                    (stackTrace ?: "")
                        .replace("\r\n", "\n")
                        .lines()
                        .take(5)
                        .joinToString("\n"),
                )
            return normalizedStack.sha256()
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
        private val tsRegex = Regex("""\b\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:?\d{2})?\b""")
        private val emailRegex = Regex("""[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}""")

        fun normalize(text: String): String =
            text
                .lowercase()
                .replace(hexRegex, "<HEX>")
                .replace(pathRegex, "<PATH>")
                .replace(ipRegex, "<IP>")
                .replace(urlRegex, "<URL>")
                .replace(uuidRegex, "<UUID>")
                .replace(tsRegex, "<TIMESTAMP>")
                .replace(emailRegex, "<EMAIL>")
                .replace(Regex("""\s+"""), " ")
                .trim()
    }
}
