package com.example.superkingsale

import com.example.superkingsale.data.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test

class TransportTest {
    private lateinit var server: MockWebServer
    private lateinit var store: MemoryStore
    @Before fun setup() { server = MockWebServer(); server.start(); store = MemoryStore() }
    @After fun cleanup() { server.shutdown() }
    private fun transport(): Transport {
        val jar = SessionCookies({ store.get("cookies") }, { store.put("cookies", it) })
        return Transport(server.url("/public/").toString(), jar)
    }
    @Test fun csrfCookieAndOriginFollowLaravelSessionContract() = runBlocking {
        val transport = transport()
        server.enqueue(MockResponse().setResponseCode(204)
            .addHeader("Set-Cookie", "XSRF-TOKEN=hello%3D; Path=/")
            .addHeader("Set-Cookie", "inventory_session=opaque; Path=/; HttpOnly"))
        server.enqueue(MockResponse().setBody("""{"user":{"id":1}}"""))
        transport.csrf()
        transport.send("POST", "api/auth/login", mapOf("login" to "tester", "password" to "test", "portal" to "sales"))
        server.takeRequest()
        val login = server.takeRequest()
        assertEquals("/public/api/auth/login", login.path)
        assertEquals("hello=", login.getHeader("X-XSRF-TOKEN"))
        assertTrue(login.getHeader("Cookie").orEmpty().contains("inventory_session=opaque"))
        assertEquals("application/json", login.getHeader("Accept"))
        assertEquals(server.url("/").toString().removeSuffix("/"), login.getHeader("Origin"))
    }
    @Test fun sessionsCanBeRestoredWithoutSavingPassword() = runBlocking {
        val first = transport()
        server.enqueue(MockResponse().addHeader("Set-Cookie", "inventory_session=opaque; Path=/; HttpOnly").setBody("{}"))
        first.read("api/health")
        val restored = transport(); restored.cookies.restore(restored.origin)
        server.enqueue(MockResponse().setBody("{}")); restored.read("api/auth/user")
        server.takeRequest()
        assertTrue(server.takeRequest().getHeader("Cookie").orEmpty().contains("inventory_session=opaque"))
        assertFalse(store.get("cookies").orEmpty().contains("password"))
    }
    @Test fun validationFieldsArePreserved() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(422).setBody("""{"message":"Invalid sale","errors":{"items.0.quantity":["Insufficient stock"]}}"""))
        try { transport().send("POST", "api/sales/sales", emptyMap()); fail() }
        catch (e: ApiFailure) { assertEquals(422, e.status); assertEquals("Insufficient stock", e.fields["items.0.quantity"]); assertFalse(e.uncertain) }
    }
    @Test fun failedCommandReusesDurableKeyAfterRepositoryRecreation() = runBlocking {
        val url = server.url("/public/").toString()
        val body = linkedMapOf<String, Any?>("amount" to 1000L, "notes" to "Handover")
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"message":"Try later"}"""))
        try { SalesRepository(store, url).command("POST", "cash-submissions", body); fail() }
        catch (e: ApiFailure) { assertTrue(e.uncertain) }
        val first = server.takeRequest()
        server.enqueue(MockResponse().setBody("""{"data":{"id":5}}"""))
        SalesRepository(store, url).command("POST", "cash-submissions", body)
        val second = server.takeRequest()
        assertNotNull(first.getHeader("Idempotency-Key"))
        assertEquals(first.getHeader("Idempotency-Key"), second.getHeader("Idempotency-Key"))
        assertEquals(first.body.readUtf8(), second.body.readUtf8())
        server.enqueue(MockResponse().setBody("""{"data":{"id":6}}"""))
        SalesRepository(store, url).command("POST", "cash-submissions", body)
        assertNotEquals(second.getHeader("Idempotency-Key"), server.takeRequest().getHeader("Idempotency-Key"))
    }
    @Test fun expiredSessionClearsCookiesDraftsAndIdentity() = runBlocking {
        store.put("cookies", "[]"); store.put("draft.1.sale.0", "private data")
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"message":"Unauthenticated."}"""))
        val repository = SalesRepository(store, server.url("/public/").toString())
        try { repository.get("dashboard"); fail() } catch (_: ApiFailure) {}
        assertNull(repository.user.value); assertTrue(store.values.isEmpty())
    }
    @Test fun noAutomaticMutationRetryOnServerFailure() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("{}"))
        try { transport().send("POST", "api/sales/sales", emptyMap(), "stable-key"); fail() }
        catch (e: ApiFailure) { assertTrue(e.uncertain) }
        assertEquals(1, server.requestCount)
    }
    @Test fun recordNormalizesNullableValuesAndIntegerStrings() {
        val r = Record.parse("""{"notes":null,"amount":"999999999999999","data":null,"items":[]}""")
        assertEquals("", r.text("notes")); assertEquals(999999999999999L, r.number("amount"))
        assertTrue(r.obj("data").empty); assertTrue(r.rows("items").isEmpty())
    }
    class MemoryStore : PrivateStore {
        val values = mutableMapOf<String, String>()
        override fun get(name: String) = values[name]
        override fun put(name: String, value: String?) { if (value == null) values.remove(name) else values[name] = value }
        override fun clear() = values.clear()
    }
}
