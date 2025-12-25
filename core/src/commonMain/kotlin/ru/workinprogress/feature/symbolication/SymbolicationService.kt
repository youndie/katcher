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
        println("SymbolicationService.processCrash - Starting symbolication for appId=$appId, buildUuid=$buildUuid")

        if (buildUuid == null) {
            println("SymbolicationService.processCrash - buildUuid is null, returning raw stacktrace")
            return rawStacktrace
        }

        println("SymbolicationService.processCrash - Looking up symbol map for appId=$appId, buildUuid=$buildUuid")
        val mapMetadata =
            symbolMapRepository.find(appId, buildUuid)
                ?: run {
                    println("SymbolicationService.processCrash - No symbol map found, returning raw stacktrace")
                    return rawStacktrace
                }

        println("SymbolicationService.processCrash - Found symbol map: type=${mapMetadata.type}, filePath=${mapMetadata.filePath}")
        val strategy =
            strategies[mapMetadata.type]
                ?: run {
                    println("SymbolicationService.processCrash - No strategy found for type=${mapMetadata.type}, returning raw stacktrace")
                    return rawStacktrace
                }

        println("SymbolicationService.processCrash - Reading mapping file from ${mapMetadata.filePath}")
        val content = fileStorage.readText(mapMetadata.filePath)
        println("SymbolicationService.processCrash - Mapping file read, size=${content.length} chars")

        println("SymbolicationService.processCrash - Starting symbolication with strategy=${strategy::class.simpleName}")
        val result = strategy.symbolicate(rawStacktrace, content)
        println("SymbolicationService.processCrash - Symbolication completed successfully")
        return result
    }
}
