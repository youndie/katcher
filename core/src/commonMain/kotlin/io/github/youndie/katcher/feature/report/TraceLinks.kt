package io.github.youndie.katcher.feature.report

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.util.AttributeKey

/** The placeholder a trace URL template carries for the id. */
const val TRACE_ID_PLACEHOLDER = "{traceId}"

private val TraceUrlTemplateKey = AttributeKey<String>("KatcherTraceUrlTemplate")

/**
 * Makes the report pages link a report's `trace.id` to [template] — `KATCHER_TRACE_URL`, for example
 * `https://tracy.example/traces/{traceId}` (B-04). Null leaves the id as plain text: no template is
 * better than a link that goes nowhere.
 *
 * Read from an application attribute rather than passed down, because the context block is drawn
 * inside half a dozen renderers that all have the call and none has the configuration. A template that
 * is not an `http(s)` URL with the placeholder stops the start: every link would be broken otherwise,
 * and nobody would find out until they clicked one.
 */
fun Application.installTraceLinks(template: String?) {
    if (template == null) return
    require(
        (template.startsWith("https://") || template.startsWith("http://")) && TRACE_ID_PLACEHOLDER in template,
    ) { "KATCHER_TRACE_URL must be an http(s) URL containing $TRACE_ID_PLACEHOLDER, got: $template" }
    attributes.put(TraceUrlTemplateKey, template)
}

/**
 * Where [traceId] opens in the tracer, or null when no template is configured or the value is not a
 * trace id. Only a value of the W3C shape reaches the URL: it comes from the reporting application, and
 * thirty-two hex digits cannot carry anything but an id.
 */
context(call: ApplicationCall)
fun traceHref(traceId: String): String? {
    if (!isTraceId(traceId)) return null
    val template = call.application.attributes.getOrNull(TraceUrlTemplateKey) ?: return null
    return template.replace(TRACE_ID_PLACEHOLDER, traceId)
}
