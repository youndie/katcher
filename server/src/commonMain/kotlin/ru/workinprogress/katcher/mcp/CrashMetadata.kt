package ru.workinprogress.katcher.mcp

/**
 * A single stack frame reduced to constrained tokens.
 *
 * Every field is restricted to identifier-shaped characters, so a frame cannot carry a
 * sentence, a markdown block or a URL. That is the point: these are handed to the agent
 * *before* any free-form crash text, so its coherence judgement is formed on data that is
 * hard to inject through. An attacker can name a class `IgnorePreviousInstructions`, but
 * that is a bare identifier with no punctuation — far weaker than a paragraph of prose.
 */
data class CrashFrame(
    val file: String,
    val line: Int?,
    val symbol: String,
)

/** Structured, low-risk facts about a crash. Contains no free-form application text. */
data class CrashMetadata(
    val exceptionType: String?,
    val frames: List<CrashFrame>,
    val frameCount: Int,
    val breadcrumbCount: Int,
    val contextKeys: List<String>,
)

object CrashMetadataExtractor {
    /** `at pkg.Class.method(File.kt:123)` — JVM, Android, and Kotlin/Native `kfun:` frames. */
    private val JVM_FRAME = Regex("""^\s*at\s+([^\s(]+)\s*\(([^):]+)(?::(\d+))?\)""")

    /** `at 3  binary  0xADDR  kfun:pkg.Class#method(...) + 99` — Kotlin/Native. */
    private val NATIVE_FRAME = Regex("""^\s*at\s+\d+\s+\S+\s+0x[0-9a-fA-F]+\s+(\S+)""")

    /** Leading `some.package.ExceptionType: message` line. */
    private val EXCEPTION_TYPE = Regex("""^([A-Za-z_][A-Za-z0-9_.$]*(?:Exception|Error|Throwable))\b""")

    private const val MAX_FRAMES = 40
    private const val MAX_TOKEN_LENGTH = 200
    private const val MAX_CONTEXT_KEYS = 32

    /** Everything outside this set is dropped, not escaped — these are identifiers, not text. */
    private val ALLOWED_TOKEN_CHARS = { c: Char -> c.isLetterOrDigit() || c in "._$#/<>-" }

    fun extract(
        stacktrace: String,
        contextKeys: Collection<String>,
        breadcrumbCount: Int,
    ): CrashMetadata {
        val lines = stacktrace.lines()

        val exceptionType =
            lines
                .firstOrNull { it.isNotBlank() }
                ?.trim()
                ?.let { EXCEPTION_TYPE.find(it)?.groupValues?.get(1) }
                ?.sanitize()

        val frames =
            lines
                .mapNotNull { parseFrame(it) }
                .take(MAX_FRAMES)

        return CrashMetadata(
            exceptionType = exceptionType,
            frames = frames,
            frameCount = frames.size,
            breadcrumbCount = breadcrumbCount,
            contextKeys =
                contextKeys
                    .map { it.sanitize() }
                    .filter { it.isNotEmpty() }
                    .sorted()
                    .take(MAX_CONTEXT_KEYS),
        )
    }

    private fun parseFrame(line: String): CrashFrame? {
        JVM_FRAME.find(line)?.let { match ->
            val symbol = match.groupValues[1].sanitize()
            val file = match.groupValues[2].sanitize()
            val lineNumber = match.groupValues[3].toIntOrNull()
            if (file.isEmpty() && symbol.isEmpty()) return null
            return CrashFrame(file = file, line = lineNumber, symbol = symbol)
        }

        NATIVE_FRAME.find(line)?.let { match ->
            val symbol = match.groupValues[1].sanitize()
            if (symbol.isEmpty()) return null
            // Native frames carry no source file; the symbol is all there is to verify.
            return CrashFrame(file = "", line = null, symbol = symbol)
        }

        return null
    }

    private fun String.sanitize(): String = filter(ALLOWED_TOKEN_CHARS).take(MAX_TOKEN_LENGTH)
}

/**
 * The agent's report on one frame it checked against the repository.
 *
 * [resolvedPath] is required when [existsInRepo] is true so the claim is falsifiable:
 * an agent that did no work has nothing to put here.
 */
data class FrameVerification(
    val file: String,
    val existsInRepo: Boolean,
    val resolvedPath: String?,
)

sealed interface AssessmentOutcome {
    /** The agent's report holds up; crash content may be released. */
    data object Accepted : AssessmentOutcome

    /** Content stays withheld. [reason] is safe to show the agent. */
    data class Refused(
        val reason: String,
    ) : AssessmentOutcome
}

/**
 * Checks the agent's coherence report before releasing crash content.
 *
 * This exists because the static screen in [CrashTrust] only recognises patterns it was
 * told about. It cannot judge whether a crash makes sense *for this codebase* — whether
 * the files exist, whether the frames belong to real code. That is a judgement call, which
 * is what the agent is for.
 *
 * The order matters: the agent forms this judgement from [CrashMetadata] alone, before it
 * has seen any free-form crash text. Asking an agent that has already read an injected
 * payload whether the payload is malicious is asking a compromised component to audit
 * itself.
 *
 * This layer can only ever make the decision stricter. [CrashTrust] remains an absolute
 * gate — a positive assessment cannot unlock content the static screen rejected.
 */
object CrashAssessment {
    fun evaluate(
        metadata: CrashMetadata,
        coherent: Boolean,
        verifications: List<FrameVerification>,
    ): AssessmentOutcome {
        if (!coherent) {
            return AssessmentOutcome.Refused("You reported the crash as incoherent.")
        }

        val fileFrames = metadata.frames.filter { it.file.isNotEmpty() }
        if (fileFrames.isEmpty()) {
            return AssessmentOutcome.Refused(
                "This crash has no source-file frames to verify, so it cannot be corroborated " +
                    "against the repository.",
            )
        }

        if (verifications.isEmpty()) {
            return AssessmentOutcome.Refused("No frame verifications were supplied.")
        }

        // The reported files must come from this crash. Checking a file that is not in the
        // stacktrace proves the report describes something else — a stale reply, the wrong
        // group, or an invented one.
        val actualFiles = fileFrames.map { it.file }.toSet()
        val invented = verifications.map { it.file }.filter { it !in actualFiles }
        if (invented.isNotEmpty()) {
            return AssessmentOutcome.Refused(
                "These files are not in this crash's stacktrace: ${invented.sorted().joinToString()}. " +
                    "Verify the frames this crash actually names.",
            )
        }

        val missing = verifications.filter { !it.existsInRepo }
        if (missing.isNotEmpty()) {
            return AssessmentOutcome.Refused(
                "These frames do not resolve to files in this repository: " +
                    "${missing.map { it.file }.sorted().joinToString()}. A crash whose frames " +
                    "do not exist here is either from another codebase or fabricated.",
            )
        }

        // A claim of existence without a path is unfalsifiable, so it does not count.
        val unsupported = verifications.filter { it.resolvedPath.isNullOrBlank() }
        if (unsupported.isNotEmpty()) {
            return AssessmentOutcome.Refused(
                "No repository path was given for: ${unsupported.map { it.file }.sorted().joinToString()}. " +
                    "Report the path each frame resolved to.",
            )
        }

        return AssessmentOutcome.Accepted
    }
}
