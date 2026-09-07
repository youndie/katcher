package io.github.youndie.katcher.jvm

import kotlinx.coroutines.CoroutineExceptionHandler
import io.github.youndie.katcher.Katcher
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

public class KatcherCoroutineExceptionHandler :
    AbstractCoroutineContextElement(CoroutineExceptionHandler.Key),
    CoroutineExceptionHandler {
    override fun handleException(
        context: CoroutineContext,
        exception: Throwable,
    ) {
        Katcher.catch(exception)
    }
}
