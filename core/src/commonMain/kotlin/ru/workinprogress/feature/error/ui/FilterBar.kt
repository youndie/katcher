package ru.workinprogress.feature.error.ui

import io.ktor.htmx.HxSwap
import io.ktor.htmx.html.hx
import io.ktor.server.application.ApplicationCall
import io.ktor.server.resources.href
import kotlinx.html.FlowContent
import kotlinx.html.InputType
import kotlinx.html.div
import kotlinx.html.input
import kotlinx.html.option
import kotlinx.html.select
import kotlinx.html.span
import ru.workinprogress.feature.app.AppsResource
import ru.workinprogress.feature.error.ErrorGroupFilterOptions
import ru.workinprogress.feature.error.ErrorGroupsPaginated
import ru.workinprogress.feature.report.ErrorGroupFilter

/**
 * Search, environment, release, period, unresolved.
 *
 * Every control is a request for the same URL with every parameter on it, so the list is a
 * function of the query string and nothing else. Nothing is remembered on the client, which
 * is what makes reload, back and a pasted link agree with each other.
 */
context(call: ApplicationCall)
fun FlowContent.filterBar(
    appId: Int,
    data: ErrorGroupsPaginated,
    options: ErrorGroupFilterOptions,
    expandedOnPhone: Boolean,
) {
    val filter = data.filter

    div(classes = "px-3 py-3 border-b border-border flex items-center gap-2 flex-wrap") {
        div(classes = "relative flex items-center flex-1 min-w-[180px]") {
            input(type = InputType.search, classes = SEARCH_CLASSES) {
                name = "q"
                value = filter.query.orEmpty()
                placeholder = "Search type, message, file"

                attributes.hx {
                    get = filterHref(appId, data, filter.copy(query = null))
                    trigger = "keyup changed delay:300ms, search"
                    target = "#errors-table-body"
                    swap = HxSwap.innerHtml
                }
                // The typed value travels as `q`; everything else is already on the URL above.
                attributes["hx-include"] = "this"
            }
        }

        // On a phone the controls hide behind one button that says how many are set.
        div(
            classes =
                (if (expandedOnPhone) "flex" else "hidden") + " sm:flex items-center gap-2 flex-wrap",
        ) {
            filterSelect(
                appId = appId,
                data = data,
                label = "All environments",
                values = options.environments,
                current = filter.environment,
            ) { value -> filter.copy(environment = value) }

            filterSelect(
                appId = appId,
                data = data,
                label = "All releases",
                values = options.releases,
                current = filter.release,
            ) { value -> filter.copy(release = value) }

            div(classes = "flex border border-input") {
                PERIODS.forEach { (days, label) ->
                    periodTab(appId, data, days, label, active = filter.days == days)
                }
            }

            toggle(
                appId = appId,
                data = data,
                label = "Unresolved only",
                active = filter.unresolvedOnly,
                next = filter.copy(unresolvedOnly = !filter.unresolvedOnly),
            )
        }

        if (!expandedOnPhone) {
            span(classes = "sm:hidden") {
                attributes.hx {
                    get = filterHref(appId, data, filter, filtersOpen = true)
                    target = "#errors-table-body"
                    swap = HxSwap.innerHtml
                }

                span(
                    classes =
                        "h-11 px-3 inline-flex items-center gap-2 text-[13px] border border-input cursor-pointer",
                ) {
                    +"Filters"
                    if (filter.activeCount > 0) {
                        span(classes = "px-1.5 py-0.5 text-[10px] font-semibold bg-primary text-primary-foreground") {
                            +filter.activeCount.toString()
                        }
                    }
                }
            }
        }

        span(classes = "ml-auto text-xs font-mono text-muted-foreground") {
            +if (filter.isEmpty) {
                "${data.totalUnfiltered} groups"
            } else {
                "${data.total} of ${data.totalUnfiltered} groups"
            }
        }
    }
}

context(call: ApplicationCall)
private fun FlowContent.filterSelect(
    appId: Int,
    data: ErrorGroupsPaginated,
    label: String,
    values: List<String>,
    current: String?,
    next: (String?) -> ErrorGroupFilter,
) {
    if (values.isEmpty()) return

    div(classes = "relative inline-flex items-center") {
        select(classes = SELECT_CLASSES) {
            attributes.hx {
                get = filterHref(appId, data, next(null))
                trigger = "change"
                target = "#errors-table-body"
                swap = HxSwap.innerHtml
            }
            // Reading the chosen option out of the control keeps one URL per select instead
            // of one per option. The empty option sends an empty string, not null: a null here
            // is serialised into the query as the four letters n-u-l-l and filters for an
            // environment by that name.
            attributes["hx-vals"] = "js:{${paramOf(next)}: event.target.value}"

            option {
                value = ""
                selected = current == null
                +label
            }

            values.forEach { candidate ->
                option {
                    value = candidate
                    selected = current == candidate
                    +candidate
                }
            }
        }
    }
}

context(call: ApplicationCall)
private fun FlowContent.periodTab(
    appId: Int,
    data: ErrorGroupsPaginated,
    days: Int?,
    label: String,
    active: Boolean,
) {
    span(
        classes =
            "h-8 px-2.5 inline-flex items-center text-[13px] cursor-pointer transition " +
                (if (active) "bg-foreground text-background font-medium" else "text-muted-foreground hover:text-foreground") +
                " border-l border-input first:border-l-0",
    ) {
        attributes.hx {
            get = filterHref(appId, data, data.filter.copy(days = days))
            target = "#errors-table-body"
            swap = HxSwap.innerHtml
        }
        +label
    }
}

context(call: ApplicationCall)
private fun FlowContent.toggle(
    appId: Int,
    data: ErrorGroupsPaginated,
    label: String,
    active: Boolean,
    next: ErrorGroupFilter,
) {
    span(
        classes =
            "h-8 px-2.5 inline-flex items-center text-[13px] border border-input cursor-pointer transition " +
                if (active) "bg-foreground text-background font-medium" else "text-muted-foreground hover:text-foreground",
    ) {
        attributes.hx {
            get = filterHref(appId, data, next)
            target = "#errors-table-body"
            swap = HxSwap.innerHtml
        }
        +label
    }
}

context(call: ApplicationCall)
private fun filterHref(
    appId: Int,
    data: ErrorGroupsPaginated,
    filter: ErrorGroupFilter,
    filtersOpen: Boolean = false,
): String =
    call.application.href(
        AppsResource.AppId.Errors.Paginated(
            parent = AppsResource.AppId.Errors(appId = appId),
            // Any change to the filters is a new list, so it starts at its first page.
            page = 1,
            sortBy = data.sortBy,
            sortOrder = data.sortOrder,
            q = filter.query,
            environment = filter.environment,
            release = filter.release,
            days = filter.days,
            unresolved = filter.unresolvedOnly,
            filters = filtersOpen,
        ),
    )

/** Which query parameter a select writes into, derived from the copy it produces. */
private fun paramOf(next: (String?) -> ErrorGroupFilter): String {
    val probe = next("probe")
    return if (probe.environment == "probe") "environment" else "release"
}

private val PERIODS = listOf<Pair<Int?, String>>(1 to "24 h", 7 to "7 d", 30 to "30 d", null to "All")

private const val SEARCH_CLASSES =
    "h-8 w-full box-border border border-input bg-background text-foreground " +
        "px-2.5 text-[13px] rounded-none"

private const val SELECT_CLASSES =
    "h-8 border border-input bg-background text-foreground px-2.5 pr-7 text-[13px] rounded-none appearance-none"
