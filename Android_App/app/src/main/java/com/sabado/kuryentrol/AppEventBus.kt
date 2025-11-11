package com.sabado.kuryentrol

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * A simple event bus for application-wide events, e.g., error messages.
 */
object AppEventBus {

    private val _errorMessages = MutableSharedFlow<String>()
    val errorMessages: SharedFlow<String> = _errorMessages.asSharedFlow()

    suspend fun emitErrorMessage(message: String) {
        _errorMessages.emit(message)
    }
}
