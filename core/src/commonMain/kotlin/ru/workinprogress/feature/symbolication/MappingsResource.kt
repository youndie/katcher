package ru.workinprogress.feature.symbolication

import io.ktor.resources.Resource

@Resource("mappings")
class MappingsResource {
    @Resource("upload")
    class Upload(
        val parent: MappingsResource = MappingsResource(),
    )
}
