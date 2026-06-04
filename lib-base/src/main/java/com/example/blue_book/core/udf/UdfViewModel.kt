package com.example.blue_book.core.udf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

abstract class UdfViewModel<I : UiIntent, S : UiState, E : UiEffect>(
    initialState: S
) : ViewModel() {

    private val intents = MutableSharedFlow<I>(extraBufferCapacity = 64)
    private val _uiState = MutableStateFlow(initialState)
    val uiState: StateFlow<S> = _uiState.asStateFlow()

    private val _uiEffect = MutableSharedFlow<E>(extraBufferCapacity = 16)
    val uiEffect: SharedFlow<E> = _uiEffect.asSharedFlow()

    init {
        viewModelScope.launch {
            intents.collect { handleIntent(it) }
        }
    }

    fun dispatch(intent: I) {
        viewModelScope.launch {
            intents.emit(intent)
        }
    }

    protected abstract suspend fun handleIntent(intent: I)

    protected fun setState(reducer: S.() -> S) {
        _uiState.update(reducer)
    }

    protected suspend fun sendEffect(effect: E) {
        _uiEffect.emit(effect)
    }

    protected suspend fun <T> runResult(
        onStart: suspend () -> Unit = {},
        call: suspend () -> Result<T>,
        onSuccess: suspend (T) -> Unit,
        onFailure: suspend (Throwable) -> Unit,
        onFinally: suspend () -> Unit = {}
    ) {
        onStart()
        try {
            val result = call()
            if (result.isSuccess) {
                onSuccess(result.getOrThrow())
            } else {
                onFailure(result.exceptionOrNull() ?: IllegalStateException("Unknown error"))
            }
        } catch (t: Throwable) {
            onFailure(t)
        } finally {
            onFinally()
        }
    }
}
