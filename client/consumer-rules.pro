# Обработчик находится через ServiceLoader, по имени из META-INF/services: ссылок на него в коде
# нет ни одной, и R8 без этого правила выбрасывает его как недостижимый — вместе с отчётами об
# исключениях, вылетевших из корутин.
-keep class ru.workinprogress.katcher.jvm.KatcherCoroutineExceptionHandler { *; }
