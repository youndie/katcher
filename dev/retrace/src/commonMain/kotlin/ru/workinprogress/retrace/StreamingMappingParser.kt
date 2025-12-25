package ru.workinprogress.retrace

import okio.BufferedSource
import ru.workinprogress.retrace.ClassMapping
import ru.workinprogress.retrace.MappingStore
import ru.workinprogress.retrace.MethodMapping

object StreamingMappingParser {
    fun parse(
        source: BufferedSource,
        filterClasses: Set<String>,
    ): MappingStore {
        val classes = HashMap<String, ClassMapping>()

        var currentClassObfuscatedName: String? = null
        var currentClassOriginalName: String? = null
        var currentMethods = HashMap<String, MutableList<MethodMapping>>()

        var isCurrentClassRelevant = false

        fun flushCurrentClass() {
            if (isCurrentClassRelevant && currentClassObfuscatedName != null) {
                classes[currentClassObfuscatedName!!] =
                    ClassMapping(
                        originalName = currentClassOriginalName!!,
                        obfuscatedName = currentClassObfuscatedName!!,
                        methods = currentMethods,
                    )
            }
            currentClassObfuscatedName = null
            currentMethods = HashMap()
            isCurrentClassRelevant = false
        }

        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            if (line.isBlank() || line.startsWith("#")) continue

            if (!line.startsWith(" ")) {
                flushCurrentClass()

                val arrowIndex = line.indexOf(" -> ")
                if (arrowIndex == -1) continue

                val originalName = line.substring(0, arrowIndex).trim()
                var obfuscatedName = line.substring(arrowIndex + 4).trim()
                if (obfuscatedName.endsWith(":")) {
                    obfuscatedName = obfuscatedName.substring(0, obfuscatedName.length - 1)
                }

                if (filterClasses.contains(obfuscatedName)) {
                    isCurrentClassRelevant = true
                    currentClassOriginalName = originalName
                    currentClassObfuscatedName = obfuscatedName
                } else {
                    isCurrentClassRelevant = false
                }
            } else if (isCurrentClassRelevant) {
                parseMemberLine(line.trim(), currentMethods)
            }
        }

        flushCurrentClass()

        return MappingStore(classes)
    }

    private fun parseMemberLine(
        line: String,
        methods: HashMap<String, MutableList<MethodMapping>>,
    ) {
        val arrowIndex = line.indexOf(" -> ")
        if (arrowIndex == -1) return

        val rightPart = line.substring(arrowIndex + 4) // обфусцированное имя
        val leftPart = line.substring(0, arrowIndex)

        val firstColon = leftPart.indexOf(':')
        if (firstColon == -1) {
            return
        }

        try {
            val secondColon = leftPart.indexOf(':', firstColon + 1)
            if (secondColon == -1) return

            val startLineObfuscated = leftPart.substring(0, firstColon).toInt()
            val endLineObfuscated = leftPart.substring(firstColon + 1, secondColon).toInt()

            val openParen = leftPart.indexOf('(')
            val closeParen = leftPart.indexOf(')')
            if (openParen == -1 || closeParen == -1) return

            val spaceBeforeName = leftPart.lastIndexOf(' ', openParen)
            if (spaceBeforeName == -1) return

            val originalMethodName = leftPart.substring(spaceBeforeName + 1, openParen)

            var startLineOriginal = startLineObfuscated
            val firstOriginalColon = leftPart.indexOf(':', closeParen)
            if (firstOriginalColon != -1) {
                val secondOriginalColon = leftPart.indexOf(':', firstOriginalColon + 1)
                if (secondOriginalColon != -1) {
                    startLineOriginal = leftPart.substring(firstOriginalColon + 1, secondOriginalColon).toInt()
                }
            }

            val mapping =
                MethodMapping(
                    originalName = originalMethodName,
                    obfuscatedRange = startLineObfuscated..endLineObfuscated,
                    originalRange = startLineOriginal..startLineOriginal,
                )

            methods.getOrPut(rightPart) { ArrayList() }.add(mapping)
        } catch (e: NumberFormatException) {
        }
    }
}
