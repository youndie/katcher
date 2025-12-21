package ru.workinprogress.retrace

data class MappingStore(
    val classes: Map<String, ClassMapping>,
)

data class ClassMapping(
    val originalName: String,
    val obfuscatedName: String,
    val methods: Map<String, List<MethodMapping>>,
    val fields: Map<String, String>,
)

data class MethodMapping(
    val originalName: String,
    val obfuscatedName: String,
    val obfuscatedRange: IntRange?,
    val originalRange: IntRange?,
    val originalSourceFile: String? = null,
)
