package ru.workinprogress.feature.app

import io.ktor.resources.Resource
import ru.workinprogress.feature.report.ErrorGroupFilter
import ru.workinprogress.feature.report.ErrorGroupSort
import ru.workinprogress.feature.report.ErrorGroupSortOrder

@Resource("apps")
class AppsResource {
    @Resource("{appId}")
    class AppId(
        val appId: Int,
        val parent: AppsResource = AppsResource(),
    ) {
        /** Reveals the api key of one app — a fragment, not a page. */
        @Resource("key")
        class Key(
            val parent: AppId,
        ) {
            companion object {
                operator fun invoke(appId: Int) = Key(AppId(appId))
            }
        }

        /** The ⋯ menu of one card. Open and closed are two responses, not a class toggle. */
        @Resource("menu")
        class Menu(
            val parent: AppId,
            val open: Boolean = true,
        ) {
            companion object {
                operator fun invoke(
                    appId: Int,
                    open: Boolean = true,
                ) = Menu(AppId(appId), open)
            }
        }

        @Resource("rename")
        class Rename(
            val parent: AppId,
        ) {
            companion object {
                operator fun invoke(appId: Int) = Rename(AppId(appId))
            }
        }

        /** POST issues a key; the dialog that asks first is [Reissue]. */
        @Resource("keys")
        class Keys(
            val parent: AppId,
        ) {
            companion object {
                operator fun invoke(appId: Int) = Keys(AppId(appId))
            }

            @Resource("{keyId}/revoke")
            class Revoke(
                val parent: Keys,
                val keyId: Long,
            ) {
                companion object {
                    operator fun invoke(
                        appId: Int,
                        keyId: Long,
                    ) = Revoke(Keys(AppId(appId)), keyId)
                }
            }
        }

        @Resource("reissue")
        class Reissue(
            val parent: AppId,
        ) {
            companion object {
                operator fun invoke(appId: Int) = Reissue(AppId(appId))
            }
        }

        /** GET asks, DELETE does it. */
        @Resource("delete")
        class Delete(
            val parent: AppId,
        ) {
            companion object {
                operator fun invoke(appId: Int) = Delete(AppId(appId))
            }
        }

        @Resource("errors")
        class Errors(
            val parent: AppId,
        ) {
            companion object {
                operator fun invoke(appId: Int) = Errors(AppId(appId))
            }

            @Resource("")
            class Paginated(
                val parent: Errors,
                val page: Int = 1,
                val pageSize: Int = 15,
                val sortBy: ErrorGroupSort = ErrorGroupSort.id,
                val sortOrder: ErrorGroupSortOrder = ErrorGroupSortOrder.desc,
                // Filters travel in the query string too, so the fragment renders from the URL
                // alone and a shared link shows the same list.
                val q: String? = null,
                val environment: String? = null,
                val release: String? = null,
                val days: Int? = null,
                val unresolved: Boolean = false,
                /** On a narrow screen the controls hide behind one button; this is that button. */
                val filters: Boolean = false,
            ) {
                fun filter() =
                    ErrorGroupFilter(
                        // A control that was cleared sends an empty value rather than dropping
                        // its parameter, and an empty value is not a filter.
                        query = q?.takeIf { it.isNotBlank() },
                        environment = environment?.takeIf { it.isNotBlank() },
                        release = release?.takeIf { it.isNotBlank() },
                        days = days,
                        unresolvedOnly = unresolved,
                    )
            }

            @Resource("{groupId}")
            class GroupId(
                val parent: Errors,
                val groupId: Long,
            ) {
                companion object {
                    operator fun invoke(
                        appId: Int,
                        groupId: Long,
                    ) = GroupId(Errors(AppId(appId)), groupId)
                }

                @Resource("reports")
                class Reports(
                    val parent: GroupId,
                ) {
                    @Resource("{reportId}")
                    class ReportId(
                        val parent: Reports,
                        val reportId: Long,
                    ) {
                        companion object {
                            operator fun invoke(
                                appId: Int,
                                groupId: Long,
                                reportId: Long,
                            ) = ReportId(Reports(GroupId(appId, groupId)), reportId)
                        }
                    }

                    @Resource("")
                    class Paginated(
                        val page: Int = 1,
                        val pageSize: Int = 15,
                        val parent: Reports,
                    ) {
                        companion object {
                            operator fun invoke(
                                appId: Int,
                                groupId: Long,
                                page: Int = 1,
                                pageSize: Int = 15,
                            ) = Paginated(
                                page = page,
                                pageSize = pageSize,
                                parent = Reports(GroupId(appId, groupId)),
                            )
                        }
                    }
                }

                /** The stacktrace panel on its own — the frames toggle swaps only this. */
                @Resource("frames")
                class Frames(
                    val parent: GroupId,
                    val all: Boolean = false,
                )

                @Resource("resolve")
                class Resolve(
                    val parent: GroupId,
                )

                @Resource("reopen")
                class Reopen(
                    val parent: GroupId,
                )

                /** Downloads the crash as JSON for an external AI fixer. */
                @Resource("crash.json")
                class CrashJson(
                    val parent: GroupId,
                )
            }
        }
    }

    @Resource("/form")
    class Form(
        val parent: AppsResource = AppsResource(),
    )
}
