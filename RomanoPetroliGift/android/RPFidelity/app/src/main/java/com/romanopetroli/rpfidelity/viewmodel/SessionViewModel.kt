package com.romanopetroli.rpfidelity.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.romanopetroli.rpfidelity.data.ApiClient
import com.romanopetroli.rpfidelity.data.SessionStore
import com.romanopetroli.rpfidelity.data.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SessionViewModel(application: Application) : AndroidViewModel(application) {

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _initialized = MutableStateFlow(false)
    val initialized: StateFlow<Boolean> = _initialized

    init {
        restoreSession()
    }

    private fun restoreSession() {
        val context = getApplication<Application>()
        val token = SessionStore.loadToken(context)
        val cachedUser = SessionStore.loadUser(context)

        if (token != null && cachedUser != null) {
            ApiClient.token = token
            _user.value = User.fromJson(cachedUser)
        }
        _initialized.value = true
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val result = ApiClient.post("/login", mapOf("email" to email, "password" to password))
            _loading.value = false

            if (result.success) {
                applySession(result.body.optString("token"), result.body.optJSONObject("user"))
            } else {
                _error.value = result.errorMessage
            }
        }
    }

    fun register(nome: String, cognome: String, email: String, telefono: String, password: String) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val result = ApiClient.post(
                "/registrati",
                mapOf(
                    "nome" to nome,
                    "cognome" to cognome,
                    "email" to email,
                    "telefono" to telefono,
                    "password" to password
                )
            )
            _loading.value = false

            if (result.success) {
                applySession(result.body.optString("token"), result.body.optJSONObject("user"))
            } else {
                _error.value = result.errorMessage
            }
        }
    }

    private fun applySession(token: String, userJson: org.json.JSONObject?) {
        if (userJson == null) {
            _error.value = "Risposta del server non valida."
            return
        }
        ApiClient.token = token
        SessionStore.save(getApplication(), token, userJson)
        _user.value = User.fromJson(userJson)
    }

    fun refreshUser() {
        viewModelScope.launch {
            val result = ApiClient.get("/me")
            if (result.success) {
                val userJson = result.body.optJSONObject("user") ?: return@launch
                _user.value = User.fromJson(userJson)
                SessionStore.loadToken(getApplication())?.let { token ->
                    SessionStore.save(getApplication(), token, userJson)
                }
            }
        }
    }

    fun logout() {
        ApiClient.token = null
        SessionStore.clear(getApplication())
        _user.value = null
    }

    fun clearError() {
        _error.value = null
    }
}
