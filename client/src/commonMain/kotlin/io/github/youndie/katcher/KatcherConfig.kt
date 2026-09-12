package io.github.youndie.katcher

import kotlin.time.Duration

public data class KatcherConfig(
    var appKey: String = "",
    var remoteHost: String = "",
    var release: String = "Unspecified",
    var environment: String = "Dev",
    var isDebug: Boolean = false,
    /**
     * Каталог, в котором отчёт ждёт отправки. `null` — каталог платформы по умолчанию:
     * `Context.cacheDir/katcher_cache` на Android, `$HOME/Library/Caches/katcher_cache` на Apple,
     * `.katcher_cache` рядом с рабочим каталогом на JVM, Linux и Windows.
     *
     * Умолчание рассчитано на приложение, у которого будет следующий запуск на той же файловой
     * системе. У серверного бинарника в контейнере его может не быть: путь ведёт в writable-слой,
     * который умирает вместе с подом. Такому хосту каталог нужно назвать самому — и указать на том,
     * переживающий перезапуск.
     */
    var cacheDir: String? = null,
    /**
     * Сколько ждать отправки в самом обработчике падения, прежде чем отдать управление дальше
     * (а дальше — обычно конец процесса).
     *
     * Ноль по умолчанию — поведение мобильного клиента: отчёт пишется на диск и уезжает при
     * следующем запуске, крашащийся поток никто не держит. Серверу следующего запуска может не
     * достаться, и он выставляет здесь свой бюджет — [Katcher.catch] при этом остаётся
     * неблокирующим, ждёт только фатальный путь.
     */
    var crashUploadGrace: Duration = Duration.ZERO,
)
