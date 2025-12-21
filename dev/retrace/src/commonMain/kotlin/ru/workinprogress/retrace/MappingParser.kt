package ru.workinprogress.retrace

class MappingParser {
    private val classHeaderRegex = Regex("""^(\S+) -> (\S+):$""")
    private val methodRegex = Regex("""^(?:(\d+:\d+):)?(.+?)(?::(\d+:\d+))? -> (\S+)""")

    fun parse(lines: Sequence<String>): MappingStore {
        val classes = mutableMapOf<String, ClassMapping>()

        var currentOriginalClassName: String? = null
        var currentObfuscatedClassName: String? = null
        var currentMethods = mutableMapOf<String, MutableList<MethodMapping>>()
        var currentFields = mutableMapOf<String, String>()

        fun flushClass() {
            if (currentObfuscatedClassName != null && currentOriginalClassName != null) {
                classes[currentObfuscatedClassName!!] =
                    ClassMapping(
                        originalName = currentOriginalClassName!!,
                        obfuscatedName = currentObfuscatedClassName!!,
                        methods = currentMethods,
                        fields = currentFields,
                    )
            }
            currentMethods = mutableMapOf()
            currentFields = mutableMapOf()
        }

        for (line in lines) {
            if (line.isBlank() || line.startsWith("#")) continue

            if (!line.startsWith(" ")) {
                flushClass()
                val match = classHeaderRegex.find(line)
                if (match != null) {
                    currentOriginalClassName = match.groupValues[1]
                    currentObfuscatedClassName = match.groupValues[2]
                }
            } else {
                val trimmed = line.trim()

                val methodMatch = methodRegex.find(trimmed)
                if (methodMatch != null) {
                    val obfRangeStr = methodMatch.groupValues[1]
                    val signatureAndRet = methodMatch.groupValues[2]
                    val origRangeStr = methodMatch.groupValues[3]
                    val obfName = methodMatch.groupValues[4]

                    val parenIndex = signatureAndRet.indexOf('(')
                    val spaceIndex = signatureAndRet.lastIndexOf(' ', parenIndex)
                    val originalMethodName =
                        if (parenIndex > 0 && spaceIndex >= 0) {
                            signatureAndRet.substring(spaceIndex + 1, parenIndex)
                        } else {
                            signatureAndRet // fallback
                        }

                    val obfRange = parseRange(obfRangeStr)
                    val origRange = parseRange(origRangeStr)

                    val mapping =
                        MethodMapping(
                            originalName = originalMethodName,
                            obfuscatedName = obfName,
                            obfuscatedRange = obfRange,
                            originalRange = origRange,
                        )

                    currentMethods.getOrPut(obfName) { mutableListOf() }.add(mapping)
                    continue
                }
            }
        }
        flushClass()

        return MappingStore(classes)
    }

    private fun parseRange(rangeStr: String): IntRange? {
        if (rangeStr.isEmpty()) return null
        val parts = rangeStr.split(':')
        return if (parts.size == 2) {
            parts[0].toInt()..parts[1].toInt()
        } else {
            parts[0].toInt()..parts[0].toInt()
        }
    }
}
