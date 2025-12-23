package ru.workinprogress.feature.symbolication

class SymbolicationService(
    private val symbolMapRepository: SymbolMapRepository,
    private val fileStorage: FileStorage,
    private val strategies: Map<MappingType, Symbolicator>,
) {
    suspend fun processCrash(
        appId: Int,
        buildUuid: String?,
        rawStacktrace: String,
    ): String {
        if (buildUuid == null) {
            return rawStacktrace
        }

        val mapMetadata =
            symbolMapRepository.find(appId, buildUuid)
                ?: return rawStacktrace

        val strategy =
            strategies[mapMetadata.type]
                ?: return rawStacktrace

        val content = fileStorage.readText(mapMetadata.filePath)
        val result = strategy.symbolicate(rawStacktrace, content)
        return result
    }
}
