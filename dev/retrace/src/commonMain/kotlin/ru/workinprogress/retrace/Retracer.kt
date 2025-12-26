package ru.workinprogress.retrace

class Retracer(
    private val mappingStore: MappingStore,
) {
    companion object {
        private val CLASS_NAME_EXTRACTOR = Regex("""([a-zA-Z0-9_$]+(?:\.[a-zA-Z0-9_$]+)+)""")

        fun extractClassesFromStacktrace(stacktrace: String): Set<String> =
            buildSet {
                CLASS_NAME_EXTRACTOR.findAll(stacktrace).forEach { match ->
                    val fqn = match.value

                    add(fqn)

                    val lastDotIndex = fqn.lastIndexOf('.')
                    if (lastDotIndex > 0) {
                        add(fqn.substring(0, lastDotIndex))
                    }
                }
            }
    }

    private val stackTraceRegex = Regex("""^\s*at\s+(.+)\.([^\.]+)\((.*)\)\s*$""")
    private val potentialClassNameRegex = Regex("""([a-zA-Z0-9_$]+(?:\.[a-zA-Z0-9_$]+)*)""")

    fun retrace(logLine: String): String {
        val frameMatch = stackTraceRegex.find(logLine)
        if (frameMatch != null) {
            return retraceStackFrame(logLine, frameMatch)
        }
        return retracePlainLine(logLine)
    }

    private fun retraceStackFrame(
        originalLine: String,
        match: MatchResult,
    ): String {
        val fullClassName = match.groupValues[1]
        val methodName = match.groupValues[2]
        val sourceInfo = match.groupValues[3]

        val lineNumber =
            if (sourceInfo.contains(':')) {
                sourceInfo.substringAfterLast(':').toIntOrNull() ?: 0
            } else {
                0
            }

        val classMapping = mappingStore.classes[fullClassName] ?: return originalLine

        val candidates = classMapping.methods[methodName]
        val bestMatch =
            if (!candidates.isNullOrEmpty()) {
                candidates.firstOrNull {
                    it.obfuscatedRange != null && lineNumber in it.obfuscatedRange
                } ?: candidates.firstOrNull { it.obfuscatedRange == null }
            } else {
                null
            }

        var finalClassName = classMapping.originalName
        var finalMethodName = bestMatch?.originalName ?: methodName

        if (finalMethodName.contains('.')) {
            val lastDotIndex = finalMethodName.lastIndexOf('.')
            finalClassName = finalMethodName.substring(0, lastDotIndex)
            finalMethodName = finalMethodName.substring(lastDotIndex + 1)
        }

        val newLine =
            if (bestMatch?.obfuscatedRange != null && bestMatch.originalRange != null) {
                (lineNumber - bestMatch.obfuscatedRange.first) + bestMatch.originalRange.first
            } else {
                lineNumber
            }

        val simpleName = finalClassName.substringAfterLast('.').substringBefore('$')
        val newSource = "$simpleName.kt"

        val sourceStr = if (newLine > 0) "$newSource:$newLine" else newSource

        val prefix = originalLine.takeWhile { it.isWhitespace() }

        return "${prefix}at $finalClassName.$finalMethodName($sourceStr)"
    }

    private fun retracePlainLine(line: String): String =
        potentialClassNameRegex.replace(line) { matchResult ->
            val potentialClass = matchResult.value
            mappingStore.classes[potentialClass]?.originalName ?: potentialClass
        }
}
