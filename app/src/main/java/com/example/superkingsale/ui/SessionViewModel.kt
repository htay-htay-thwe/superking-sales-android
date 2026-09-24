package com.example.superkingsale.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.superkingsale.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SessionState(val loading: Boolean = true, val error: String = "", val restoreFailed: Boolean = false)
class SessionViewModel(private val repository: SalesRepository) : ViewModel() {
    private val _state = MutableStateFlow(SessionState())
    val state = _state.asStateFlow()
    init { restore() }
    fun restore() {
        _state.value = SessionState()
        viewModelScope.launch {
            try { repository.restore(); _state.value = SessionState(false) }
            catch (e: Exception) { _state.value = SessionState(false, e.message.orEmpty(), true) }
        }
    }
    fun login(user: String, password: String) {
        if (_state.value.loading) return
        if (user.isBlank() || password.isBlank()) { _state.value = SessionState(false, "Enter your username and password."); return }
        _state.value = SessionState()
        viewModelScope.launch {
            try { repository.login(user, password); _state.value = SessionState(false) }
            catch (e: ApiFailure) { _state.value = SessionState(false, e.fields.values.joinToString("\n").ifBlank { e.message }) }
            catch (e: Exception) { _state.value = SessionState(false, e.message.orEmpty()) }
        }
    }
    fun logout() = viewModelScope.launch { runCatching { repository.logout() } }
}

