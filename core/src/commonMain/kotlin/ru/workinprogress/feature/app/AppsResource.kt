package ru.workinprogress.feature.app

import io.ktor.resources.Resource
import ru.workinprogress.feature.report.ErrorGroupSort
import ru.workinprogress.feature.report.ErrorGroupSortOrder

@Resource("apps")
class AppsResource {
    @Resource("{appId}")
    class AppId(
        val appId: Int,
        val parent: AppsResource = AppsResource(),
    ) {
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
            )

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

                @Resource("resolve")
                class Resolve(
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
