package ru.workinprogress.katcher.jvm

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.runBlocking
import ru.workinprogress.katcher.Katcher
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class KatcherCoroutineExceptionHandler :
    AbstractCoroutineContextElement(CoroutineExceptionHandler.Key),
    CoroutineExceptionHandler {
    init {
        println("KatcherCoroutineExceptionHandler init")
    }

    override fun handleException(
        context: CoroutineContext,
        exception: Throwable,
    ) {
        runBlocking { Katcher.catch(exception) }
    }
}
