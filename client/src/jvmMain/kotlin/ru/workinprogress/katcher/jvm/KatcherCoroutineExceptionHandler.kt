package ru.workinprogress.katcher.jvm

import kotlinx.coroutines.CoroutineExceptionHandler
import ru.workinprogress.katcher.Katcher
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class KatcherCoroutineExceptionHandler :
    AbstractCoroutineContextElement(CoroutineExceptionHandler.Key),
    CoroutineExceptionHandler {
    override fun handleException(
        context: CoroutineContext,
        exception: Throwable,
    ) {
        Katcher.catch(exception)
    }
}
