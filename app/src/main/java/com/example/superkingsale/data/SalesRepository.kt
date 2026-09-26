package com.example.superkingsale.data

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID

class SalesRepository(private val store: PrivateStore, baseUrl: String = "https://www.superkingmyanmar.com/public/") {
    private val cookies = SessionCookies({ store.get("cookies") }, { store.put("cookies", it) })
    val transport = Transport(baseUrl, cookies)
    private val _user = MutableStateFlow<Record?>(null)
    val user = _user.asStateFlow()
    private val gate = Mutex()
    private val cache = mutableMapOf<String, Record>()
    val revisions = MutableStateFlow(0L)
    var branding = Record()
        private set

    suspend fun restore(): Boolean = withContext(Dispatchers.IO) {
        cookies.restore(transport.origin)
        branding = runCatching { transport.read("api/branding").let { if (it.obj("branding").empty) it else it.obj("branding") } }.getOrDefault(Record())
        if (store.get("user") == null) return@withContext false
        try {
            val restored = transport.read("api/sales/me").obj("user")
            accept(restored)
            true
        } catch (e: ApiFailure) {
            if (e.status == 401 || e.status == 419 || e.status == 403) { clear(); false }
            else throw e
        }
    }
    suspend fun login(login: String, password: String, remember: Boolean = false) = withContext(Dispatchers.IO) {
        transport.csrf()
        val response = transport.send("POST", "api/auth/login",
            mapOf("login" to login.trim(), "password" to password, "portal" to "sales", "remember" to remember))
        accept(response.obj("user"))
    }
    private fun accept(user: Record) {
        if (user.id == 0L || !user.strings("roles").contains("sales-representative"))
            throw ApiFailure("A sales representative account is required.", 403)
        store.put("user", user.json())
        _user.value = user
    }
    suspend fun logout() = gate.withLock {
        withContext(Dispatchers.IO) {
            try { transport.send("POST", "api/auth/logout", emptyMap()) } finally { clear() }
        }
    }
    private fun clear() {
        cookies.clear(); store.clear(); synchronized(cache) { cache.clear() }; _user.value = null
    }
    fun cached(path: String, query: Map<String, String>): Record? = synchronized(cache) { cache[path + query.toSortedMap()] }
    suspend fun get(path: String, query: Map<String, String> = emptyMap()): Record = withContext(Dispatchers.IO) {
        try {
            transport.read("api/sales/$path", query).also {
                synchronized(cache) { cache[path + query.toSortedMap()] = it }
            }
        } catch (e: ApiFailure) {
            if (e.status == 401 || e.status == 419 || e.code in listOf("ACCOUNT_INACTIVE", "REPRESENTATIVE_PROFILE_UNAVAILABLE")) clear()
            throw e
        }
    }
    /** A timed-out command reuses its durable key and identical body, including after process death. */
    suspend fun command(method: String, path: String, payload: Map<String, Any?> = emptyMap(), idempotent: Boolean = true): Record =
        gate.withLock {
            withContext(Dispatchers.IO) {
                val body = Gson().toJson(payload)
                val fingerprint = MessageDigest.getInstance("SHA-256").digest((method + path + body).toByteArray())
                    .joinToString("") { "%02x".format(it) }
                val storageKey = "command.$fingerprint"
                val key = if (idempotent) store.get(storageKey) ?: UUID.randomUUID().toString().also { store.put(storageKey, it) } else null
                try {
                    transport.send(method, "api/sales/$path", payload, key).also {
                        if (path == "profile" && !it.obj("user").empty && _user.value != null) {
                            val merged = com.google.gson.JsonParser.parseString(_user.value!!.json()).asJsonObject
                            com.google.gson.JsonParser.parseString(it.obj("user").json()).asJsonObject.entrySet().forEach { entry -> merged.add(entry.key, entry.value) }
                            accept(Record(merged))
                        }
                        store.put(storageKey, null)
                        synchronized(cache) { cache.clear() }
                        revisions.value++
                    }
                } catch (e: ApiFailure) {
                    if (e.status == 401 || e.status == 419) clear()
                    throw e
                }
            }
        }
    suspend fun draft(value: String?, slot: String) = withContext(Dispatchers.IO) { store.put("draft.${user.value?.id}.$slot", value) }
    suspend fun draft(slot: String): String? = withContext(Dispatchers.IO) { store.get("draft.${user.value?.id}.$slot") }
}
