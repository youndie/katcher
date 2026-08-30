package ru.workinprogress.katcher

public data class KatcherConfig(
    var appKey: String = "",
    var remoteHost: String = "",
    var release: String = "Unspecified",
    var environment: String = "Dev",
    var isDebug: Boolean = false,
)
