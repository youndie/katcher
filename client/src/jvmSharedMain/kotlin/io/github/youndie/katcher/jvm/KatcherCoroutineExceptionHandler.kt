package io.github.youndie.katcher.jvm

import io.github.youndie.katcher.Katcher
import kotlinx.coroutines.CoroutineExceptionHandler
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
