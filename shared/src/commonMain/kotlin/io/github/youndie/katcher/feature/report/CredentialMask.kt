package io.github.youndie.katcher.feature.report

/**
 * Replaces the credentials a client library prints into an exception message, and keeps the shape
 * around them, so `…/bot<redacted>/getUpdates` still says which call failed.
 *
 * The client calls it before a report reaches the device's disk, and the server calls it again at
 * ingest, because the ingest key ships inside applications and a report can arrive from a client
 * that never ran this. Both sides call this one function, so they cannot disagree about what a
 * secret looks like.
 *
 * Masking is by shape, not by entropy. Stack traces are full of hashes, addresses and ids that are
 * not secrets, and an entropy guess would mangle titles nobody asked to change. The shapes:
 *
 * - a JSON Web Token (`eyJ….eyJ….…`);
 * - an `Authorization` value, the scheme kept: `Authorization: Bearer <redacted>`;
 * - the userinfo of a URL: `postgres://<redacted>@db:5432/x`;
 * - a bot-API path segment: `/bot<redacted>/getUpdates`;
 * - a query or form parameter whose name ends in token, key, secret, password, sig or signature:
 *   `?api_key=<redacted>`, `&X-Amz-Signature=<redacted>`.
 *
 * Applying it twice gives what applying it once gave, which is what lets the server re-mask a
 * report the client already masked, and a stored row be re-masked by a migration that may run on
 * a database it has already seen.
 */
public object CredentialMask {
    public const val REDACTED: String = "<redacted>"

    private val jwt = Regex("""\beyJ[A-Za-z0-9_-]+\.eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]*""")

    /**
     * The header name, the separator a client printed it with (`: `, `=`, `=[` for a Ktor header
     * map), an optional scheme, then the value up to the first delimiter.
     */
    private val authorization =
        Regex(
            """(?i)(authorization["']?\s*[:=]\s*["'\[]?\s*)((?:bearer|basic|token|digest|negotiate)\s+)?([^\s,;"'\]]+)""",
        )

    private val userinfo = Regex("""\b([A-Za-z][A-Za-z0-9+.-]*://)([^/\s@?#]+)@""")

    private val botPath = Regex("""/bot\d+:[A-Za-z0-9_-]+""")

    /**
     * A name after a query separator, a line start, a space or the parenthesis of a data class's
     * `toString` (`Config(apiKey=…, …)`). `<` is out of the value so an already masked value is
     * not matched again; `,` `)` `]` `}` are out so the masking stops where a `toString` field ends.
     */
    private val parameter = Regex("""(?m)([?&;(,{\[]|^|\s)([A-Za-z0-9_.-]+)=([^&\s#"'<>,;)\]}]+)""")

    private val camelBoundary = Regex("([a-z0-9])([A-Z])")

    private val secretNameEndings = setOf("token", "key", "apikey", "secret", "password", "passwd", "sig", "signature")

    /** Masks every credential shape in [text]; text with none comes back as it was. */
    public fun mask(text: String): String =
        text
            .replace(jwt, REDACTED)
            .replace(authorization) { match ->
                val (prefix, scheme) = match.destructured
                prefix + scheme + REDACTED
            }.replace(userinfo) { match -> match.groupValues[1] + REDACTED + "@" }
            .replace(botPath, "/bot$REDACTED")
            .replace(parameter) { match ->
                val (lead, name, _) = match.destructured
                if (name.isSecretName()) "$lead$name=$REDACTED" else match.value
            }

    /**
     * The last word of a parameter name, split on `_`, `-`, `.` and on camel case: `refresh_token`,
     * `X-Amz-Signature` and `apiKey` all end in a secret, `monkey` and `keyboard` do not.
     */
    private fun String.isSecretName(): Boolean {
        val words =
            replace(camelBoundary, "$1_$2")
                .lowercase()
                .split('_', '-', '.')
                .filter { it.isNotEmpty() }
        return words.lastOrNull() in secretNameEndings
    }
}

/**
 * The report with [CredentialMask] applied to everything a client writes as text: the message,
 * the stacktrace, the context values and the breadcrumbs. The app key is the one credential the
 * server needs, so it is left alone, and so are the keys of the maps, which name things rather
 * than carry them.
 */
public fun CreateReportParams.maskCredentials(): CreateReportParams =
    copy(
        message = CredentialMask.mask(message),
        stacktrace = CredentialMask.mask(stacktrace),
        context = context?.mapValues { (_, value) -> CredentialMask.mask(value) },
        breadcrumbs = breadcrumbs?.map { it.maskCredentials() },
    )

/** The breadcrumb with [CredentialMask] applied to its message and data values. */
public fun Breadcrumb.maskCredentials(): Breadcrumb =
    copy(
        message = CredentialMask.mask(message),
        data = data?.mapValues { (_, value) -> CredentialMask.mask(value) },
    )
