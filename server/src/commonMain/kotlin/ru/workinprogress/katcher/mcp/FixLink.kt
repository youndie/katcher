package ru.workinprogress.katcher.mcp

sealed interface FixLinkResult {
    data class Valid(
        val url: String,
    ) : FixLinkResult

    data class Rejected(
        val reason: String,
    ) : FixLinkResult
}

/**
 * Validates a pull-request URL supplied by an agent.
 *
 * This is the only value an MCP client can write into Katcher, and it is written by the
 * component most exposed to injection. It then renders as a link in Katcher's own
 * dashboard, where a human is inclined to trust it — so an unchecked URL turns the crash
 * list into a phishing surface inside the tool the team already trusts.
 *
 * What this can and cannot do:
 *
 * - It **can** reject schemes that execute (`javascript:`, `data:`), embedded credentials,
 *   invisible or non-ASCII characters used for homograph tricks, and anything that is not
 *   shaped like a link to a page.
 * - It **cannot** tell `https://github.com/you/repo/pull/1` from
 *   `https://evil.test/you/repo/pull/1`. Katcher deliberately holds no repository
 *   configuration, so there is nothing to check the host against. The mitigation is in the
 *   UI: the link is rendered showing its full URL rather than friendly text, so the
 *   destination is visible before anyone clicks. Host allowlisting would be stronger and
 *   is not implemented.
 */
object FixLinkValidator {
    private const val SCHEME = "https://"
    private const val MAX_LENGTH = 2048

    /** Characters RFC 3986 requires to be percent-encoded rather than appearing literally. */
    private val FORBIDDEN_CHARS = setOf('"', '<', '>', '`', '\\', '{', '}', '|', '^', ' ')

    fun validate(raw: String): FixLinkResult {
        val url = raw.trim()

        if (url.isEmpty()) return FixLinkResult.Rejected("The URL is empty.")
        if (url.length > MAX_LENGTH) return FixLinkResult.Rejected("The URL is longer than $MAX_LENGTH characters.")

        // Printable ASCII only. Legitimate forge URLs are ASCII, while non-ASCII buys an
        // attacker homograph domains that read as a familiar host, and control characters
        // let a URL hide part of itself from whoever reviews it.
        url.firstOrNull { it.code < 0x20 || it.code > 0x7E }?.let { char ->
            return FixLinkResult.Rejected(
                "The URL contains a non-printable or non-ASCII character " +
                    "(U+${char.code.toString(16).uppercase().padStart(4, '0')}).",
            )
        }

        // Characters RFC 3986 does not permit unencoded. kotlinx.html does escape attribute
        // values, so a quote here would not actually break out of the href — this is a
        // second line so the guarantee does not rest solely on the templating engine, and
        // none of these belong in a real pull-request URL anyway.
        url.firstOrNull { it in FORBIDDEN_CHARS }?.let { char ->
            return FixLinkResult.Rejected("The URL contains a character that is not allowed unencoded ('$char').")
        }

        if (!url.startsWith(SCHEME, ignoreCase = true)) {
            // Also what rejects javascript: and data:, which would otherwise execute when
            // clicked from the dashboard.
            return FixLinkResult.Rejected("The URL must start with https://.")
        }

        val rest = url.substring(SCHEME.length)
        val slash = rest.indexOf('/')
        if (slash <= 0) {
            return FixLinkResult.Rejected("The URL must point at a page, not just a host.")
        }

        val authority = rest.substring(0, slash)
        val path = rest.substring(slash)

        if ('@' in authority) {
            return FixLinkResult.Rejected("The URL must not embed credentials.")
        }

        val host = authority.substringBefore(':')
        if ('.' !in host || host.startsWith('.') || host.endsWith('.')) {
            return FixLinkResult.Rejected("The URL does not contain a valid host.")
        }

        val port = authority.substringAfter(':', "")
        if (port.isNotEmpty() && (port.toIntOrNull() == null || port.toInt() !in 1..65535)) {
            return FixLinkResult.Rejected("The URL has an invalid port.")
        }

        if (path.length <= 1) {
            return FixLinkResult.Rejected("The URL must point at a page, not just a host.")
        }

        return FixLinkResult.Valid(url)
    }
}
