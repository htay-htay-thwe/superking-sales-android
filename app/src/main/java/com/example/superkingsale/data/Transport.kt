package com.example.superkingsale.data

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

interface SalesService {
    @GET suspend fun get(@Url path: String, @QueryMap query: Map<String, String>): Response<JsonObject>
    @HTTP(method = "POST", hasBody = true) suspend fun post(@Url path: String, @Body body: Map<String, @JvmSuppressWildcards Any?>, @Header("Idempotency-Key") key: String?): Response<JsonObject>
    @HTTP(method = "PUT", hasBody = true) suspend fun put(@Url path: String, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<JsonObject>
    @HTTP(method = "DELETE") suspend fun delete(@Url path: String): Response<JsonObject>
    @GET suspend fun csrf(@Url path: String): Response<ResponseBody>
}

class SessionCookies(private val read: () -> String?, private val write: (String?) -> Unit) : CookieJar {
    private val gson = Gson()
    private var cookies = mutableListOf<Cookie>()
    @Synchronized fun restore(origin: HttpUrl) {
        val saved = runCatching { gson.fromJson(read(), Array<String>::class.java) }.getOrNull() ?: emptyArray()
        cookies = saved.mapNotNull { Cookie.parse(origin, it) }.toMutableList()
    }
    @Synchronized override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { next ->
            this.cookies.removeAll { it.name == next.name && it.domain == next.domain && it.path == next.path }
            if (next.expiresAt > System.currentTimeMillis()) this.cookies.add(next)
        }
        write(gson.toJson(this.cookies.map { it.toString() }))
    }
    @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> =
        cookies.filter { it.matches(url) && it.expiresAt > System.currentTimeMillis() }
    @Synchronized fun csrf(url: HttpUrl): String? = loadForRequest(url).find { it.name == "XSRF-TOKEN" }
        ?.value?.let { URLDecoder.decode(it, "UTF-8") }
    @Synchronized fun clear() { cookies.clear(); write(null) }
}

class Transport(
    val baseUrl: String,
    val cookies: SessionCookies,
) {
    val origin = baseUrl.toHttpUrl()
    val client = OkHttpClient.Builder().cookieJar(cookies)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(35, TimeUnit.SECONDS)
        .callTimeout(50, TimeUnit.SECONDS).retryOnConnectionFailure(false)
        .followRedirects(false)
        .addInterceptor { chain ->
            val request = chain.request()
            check(request.url.host == origin.host && request.url.scheme == origin.scheme && request.url.port == origin.port) { "Unexpected API host" }
            val builder = request.newBuilder().header("Accept", "application/json")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Origin", origin.newBuilder().encodedPath("/").query(null).build().toString().removeSuffix("/"))
                .header("Referer", baseUrl + "sales/")
            cookies.csrf(request.url)?.let { builder.header("X-XSRF-TOKEN", it) }
            chain.proceed(builder.build())
        }.build()
    val api: SalesService = Retrofit.Builder().baseUrl(baseUrl).client(client)
        .addConverterFactory(GsonConverterFactory.create()).build().create(SalesService::class.java)

    suspend fun read(path: String, query: Map<String, String> = emptyMap()): Record =
        checked { api.get(path, query) }

    suspend fun csrf() {
        val response = api.csrf("sanctum/csrf-cookie")
        response.body()?.close()
        if (!response.isSuccessful) throw ApiFailure("Unable to establish a secure session.", response.code())
    }

    suspend fun send(method: String, path: String, body: Map<String, Any?>, key: String? = null): Record =
        checked(method != "GET") {
            when (method) {
                "POST" -> api.post(path, body, key)
                "PUT" -> api.put(path, body)
                "DELETE" -> api.delete(path)
                else -> error("Unsupported method")
            }
        }

    private suspend fun checked(mutation: Boolean = false, block: suspend () -> Response<JsonObject>): Record {
        try {
            val response = block()
            if (response.isSuccessful) return Record(response.body() ?: JsonObject())
            val error = runCatching { Record.parse(response.errorBody()?.string().orEmpty()) }.getOrDefault(Record())
            val fields = runCatching {
                Gson().fromJson(error.obj("errors").json(), JsonObject::class.java).entrySet()
                    .associate { it.key to (if (it.value.isJsonArray) it.value.asJsonArray.firstOrNull()?.asString.orEmpty() else it.value.asString) }
            }.getOrDefault(emptyMap())
            val message = error.text("message", when (response.code()) {
                401 -> "Your session has expired. Sign in again."
                403 -> "Your account does not have access to this action."
                419 -> "Your session needs refreshing. Sign in again."
                429 -> "Too many requests. Please wait and try again."
                else -> "The server could not complete the request."
            })
            throw ApiFailure(message + response.headers()["X-Request-ID"]?.let { "\nRequest: $it" }.orEmpty(),
                response.code(), error.text("code"), fields, mutation && response.code() >= 500)
        } catch (e: CancellationException) { throw e }
        catch (e: ApiFailure) { throw e }
        catch (_: com.google.gson.JsonParseException) {
            throw ApiFailure(if (mutation) "The server returned an unreadable response. Check the record before retrying."
                else "The server returned an unreadable response. Please retry.", uncertain = mutation)
        }
        catch (_: java.io.IOException) {
            throw ApiFailure(if (mutation) "Connection lost. The action may have reached the server. Refresh its history before retrying."
                else "Cannot reach the server. Check your connection and retry.", uncertain = mutation)
        }
    }
}
