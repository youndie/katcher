package ru.workinprogress.katcher.mcp

/** One reason a report was held back. Never includes the offending text verbatim. */
data class TrustFinding(
    val rule: String,
    val field: String,
    val detail: String,
)

sealed interface TrustVerdict {
    /** Nothing suspicious found. Content may be forwarded to an agent. */
    data object Trusted : TrustVerdict

    /** Content is withheld; the agent must stay read-only and stop. */
    data class Suspect(
        val findings: List<TrustFinding>,
    ) : TrustVerdict
}

/** Fields of a report that get screened, paired with a label used in findings. */
data class ScreenedField(
    val name: String,
    val value: String,
    /** Scalars (release, environment, context keys) must never span lines or hold markup. */
    val expectedScalar: Boolean = false,
)

/**
 * Screens crash content before it is handed to a coding agent.
 *
 * ## Why this exists
 *
 * Katcher's ingest endpoint accepts any report carrying a valid `appKey`, and that key
 * ships inside client applications. Anyone who extracts it can post arbitrary text that
 * Katcher will store and display as a genuine crash. If an agent then reads that text
 * through MCP, the text is in a position to issue instructions to the agent — which holds
 * write access to a repository and a shell.
 *
 * This is the "agentjacking" attack demonstrated against Sentry in June 2026: a crafted
 * error event, ingested through a leaked write-only key, reliably drove coding agents into
 * running attacker-supplied commands. Researchers measured an 85% success rate and were
 * explicit that the root cause is a model limitation — agents do not reliably separate
 * data from instructions — rather than a bug that can be patched away.
 *
 * So this screen is not trying to make untrusted text safe. It is a bouncer: content that
 * looks like it is addressed to a reader rather than describing a crash does not get
 * forwarded, and the agent is told to stop instead of being handed the payload.
 *
 * ## Deliberate bias toward false positives
 *
 * A false positive costs a developer one glance at the Katcher UI. A false negative costs
 * whatever the agent's credentials can reach. Rules therefore err toward flagging, and
 * none of them may be waived by anything contained in the report itself.
 */
object CrashTrust {
    /**
     * Characters that render as nothing, or reorder what a human sees relative to what a
     * model reads. Legitimate stack traces have no reason to contain them, while they are
     * a standard way to smuggle instructions past a reviewer glancing at the UI.
     *
     * Written as escapes rather than literals on purpose: as literals they are invisible
     * in a diff, and a formatter already silently dropped one of them from this set.
     */
    private val HIDDEN_CHARS =
        setOf(
            '\u200B', // zero-width space
            '\u200C', // zero-width non-joiner
            '\u200D', // zero-width joiner
            '\uFEFF', // zero-width no-break space / BOM
            '\u202A', // left-to-right embedding
            '\u202B', // right-to-left embedding
            '\u202C', // pop directional formatting
            '\u202D', // left-to-right override
            '\u202E', // right-to-left override
            '\u2066', // left-to-right isolate
            '\u2067', // right-to-left isolate
            '\u2068', // first strong isolate
            '\u2069', // pop directional isolate
            '\u001B', // escape, opens an ANSI sequence
        )

    /**
     * Phrases that address a reader instead of describing a failure. A stack trace has no
     * reason to speak in the second person or to talk about instructions and assistants.
     */
    private val AGENT_DIRECTED =
        listOf(
            "ignore previous",
            "ignore all previous",
            "ignore the above",
            "disregard previous",
            "disregard the above",
            "system prompt",
            "system:",
            "assistant:",
            "user:",
            "you must",
            "you should now",
            "you are an",
            "your task is",
            "new instructions",
            "updated instructions",
            "important instruction",
            "do not tell",
            "do not mention",
            "without telling",
            "before fixing",
            "first, run",
            "first run",
            "please run",
            "please execute",
            "as an ai",
            "language model",
        )

    /** Shell and package-manager verbs. Fixing a crash never requires the report to name these. */
    private val SHELL_PATTERNS =
        listOf(
            "curl ",
            "wget ",
            "bash -c",
            "sh -c",
            "/bin/sh",
            "/bin/bash",
            "rm -rf",
            "chmod +x",
            "eval(",
            "exec(",
            "os.system",
            "subprocess",
            "npm install",
            "pip install",
            "powershell",
            "invoke-expression",
            "base64 -d",
            "| sh",
            "|sh",
            "| bash",
        )

    /** Names of things worth stealing. Their presence suggests exfiltration, not diagnostics. */
    private val CREDENTIAL_PATTERNS =
        listOf(
            "aws_secret",
            "aws_access_key",
            "github_token",
            "gh_token",
            "process.env",
            "\$env:",
            "id_rsa",
            ".npmrc",
            ".aws/credentials",
            "private_key",
            "client_secret",
            "authorization: bearer",
            "ssh_key",
            "kubeconfig",
        )

    /** Markdown scaffolding used to make injected text look like tool output. */
    private val MARKUP_PATTERNS = listOf("```", "<instructions", "<system", "</", "[//]: #")

    private const val MAX_FIELD_LENGTH = 64 * 1024
    private const val MAX_SCALAR_LENGTH = 512

    /**
     * A stack frame in any runtime Katcher accepts: JVM/Android (`at com.x.Y.z(F.kt:1)`),
     * Kotlin/Native (`at 3  binary  0x... kfun:...`), and JS (`at fn (file.js:1:2)`).
     */
    private val FRAME_REGEX = Regex("""^\s*(at\s|Caused by:|\.\.\.\s*\d+\s*more|\w+\.\w+.*\(.*\))""")

    /** Below this share of frame-shaped lines, a "stack trace" is mostly prose. */
    private const val MIN_FRAME_RATIO = 0.5

    /** Prose check only kicks in once there are enough lines for the ratio to mean anything. */
    private const val MIN_LINES_FOR_RATIO = 4

    fun screen(fields: List<ScreenedField>): TrustVerdict {
        val findings = fields.flatMap { screenField(it) }
        return if (findings.isEmpty()) TrustVerdict.Trusted else TrustVerdict.Suspect(findings)
    }

    private fun screenField(field: ScreenedField): List<TrustFinding> {
        val findings = mutableListOf<TrustFinding>()
        val value = field.value
        val lower = value.lowercase()

        value.firstOrNull { it in HIDDEN_CHARS }?.let { char ->
            findings +=
                TrustFinding(
                    rule = "hidden-characters",
                    field = field.name,
                    // Report the code point, never the surrounding text: findings are shown
                    // to the same agent we are protecting.
                    detail = "contains non-printing character U+${char.code.toString(16).uppercase().padStart(4, '0')}",
                )
        }

        AGENT_DIRECTED.firstOrNull { it in lower }?.let {
            findings +=
                TrustFinding(
                    "agent-directed-language",
                    field.name,
                    "reads as an instruction to a reader rather than a description of a failure",
                )
        }

        SHELL_PATTERNS.firstOrNull { it in lower }?.let {
            findings += TrustFinding("shell-command", field.name, "contains shell or package-manager invocation")
        }

        CREDENTIAL_PATTERNS.firstOrNull { it in lower }?.let {
            findings += TrustFinding("credential-reference", field.name, "references credentials or secret material")
        }

        if (value.length > MAX_FIELD_LENGTH) {
            findings += TrustFinding("oversized-field", field.name, "exceeds ${MAX_FIELD_LENGTH} characters")
        }

        if (field.expectedScalar) {
            if (value.length > MAX_SCALAR_LENGTH) {
                findings += TrustFinding("oversized-field", field.name, "scalar exceeds $MAX_SCALAR_LENGTH characters")
            }
            if (value.any { it == '\n' || it == '\r' }) {
                findings += TrustFinding("multiline-scalar", field.name, "single-value field spans multiple lines")
            }
            MARKUP_PATTERNS.firstOrNull { it in lower }?.let {
                findings += TrustFinding("markup-in-scalar", field.name, "single-value field contains markup")
            }
        }

        return findings
    }

    /**
     * Structural check for the stacktrace field specifically: does it actually look like a
     * stack trace? Injected payloads have to carry readable prose to be effective, which
     * pushes the share of frame-shaped lines down.
     *
     * Kept separate from [screen] because it is heuristic and shape-based rather than a
     * pattern match, and because it only applies to one field.
     */
    fun screenStacktraceShape(stacktrace: String): List<TrustFinding> {
        val lines = stacktrace.lines().filter { it.isNotBlank() }
        if (lines.size < MIN_LINES_FOR_RATIO) return emptyList()

        val frameLines = lines.count { FRAME_REGEX.containsMatchIn(it) }
        val ratio = frameLines.toDouble() / lines.size
        return if (ratio < MIN_FRAME_RATIO) {
            listOf(
                TrustFinding(
                    rule = "unstacktrace-like",
                    field = "stacktrace",
                    detail = "only $frameLines of ${lines.size} lines look like stack frames",
                ),
            )
        } else {
            emptyList()
        }
    }
}
