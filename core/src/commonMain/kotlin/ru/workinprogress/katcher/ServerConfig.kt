package ru.workinprogress.katcher

class ServerConfig(
    val sqlitePath: String = "./data/local.db",
    val sourceMapPath: String = "./data/mappings",
    /** When null, the MCP endpoint is not exposed at all. */
    val mcpToken: String? = null,
    /**
     * Hostnames the MCP endpoint accepts in the Host header. Empty keeps the SDK default
     * of localhost only, so a deployment must declare its public hostname explicitly.
     */
    val mcpAllowedHosts: List<String> = emptyList(),
    /**
     * Куда агент metrik шлёт метрики, `host:port`. `null` — плагин не ставится вовсе: ничего не
     * меряется и никуда не отправляется.
     *
     * Мониторинг опционален намеренно. katcher должен подниматься и без metrik — иначе появляется
     * зависимость сервиса от наблюдателя, то есть ровно та связь, которой у наблюдателя быть не
     * должно.
     */
    val metrikEndpoint: String? = null,
    val metrikKey: String? = null,
    val metrikService: String = "katcher",
    val metrikRelease: String? = null,
)
