package io.github.youndie.katcher.feature.report

/**
 * The context key a report carries its trace id under — W3C trace context, 32 lowercase hex.
 *
 * A context key rather than a field of [CreateReportParams]: the context map is already on the wire
 * and already stored, so a new client reporting to an old server and an old client to a new one
 * both keep working (B-04). The client knows nothing of any tracer; a consumer that runs one passes
 * the id where it catches:
 *
 * ```kotlin
 * Katcher.catch(cause, mapOf(TRACE_ID_KEY to traceId))
 * ```
 */
public const val TRACE_ID_KEY: String = "trace.id"

private val traceIdShape = Regex("^[0-9a-f]{32}$")

/** All zeros is the W3C "invalid" id: no trace has it. */
private const val INVALID_TRACE_ID = "00000000000000000000000000000000"

/**
 * Whether [value] has the shape of a W3C trace id. Anything else — upper case, a span id, text with
 * markup — is not one, and the server neither links it nor hands it to an agent as an id.
 */
public fun isTraceId(value: String?): Boolean =
    value != null && traceIdShape.matches(value) && value != INVALID_TRACE_ID
