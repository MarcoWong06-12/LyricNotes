package com.linernotes.app.core.network

import okhttp3.ConnectionPool
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object LinerNotesHttpClient {

    private const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val cookieStore = ConcurrentHashMap<String, List<Cookie>>()

    private val inMemoryCookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookieStore[url.host] = cookies
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return cookieStore[url.host] ?: emptyList()
        }
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(32, 5, TimeUnit.MINUTES))
        .cookieJar(inMemoryCookieJar)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    fun get(url: String, headers: Map<String, String> = emptyMap()): String? {
        return try {
            val reqBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", headers["User-Agent"] ?: DEFAULT_USER_AGENT)
                .header("Accept-Language", headers["Accept-Language"] ?: "en-US,en;q=0.9,zh-CN;q=0.8,zh;q=0.7")

            headers.forEach { (k, v) ->
                if (!k.equals("User-Agent", ignoreCase = true) && !k.equals("Accept-Language", ignoreCase = true)) {
                    reqBuilder.header(k, v)
                }
            }

            client.newCall(reqBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()
                } else {
                    android.util.Log.w("LinerNotesHttp", "HTTP GET failed: ${response.code} for $url")
                    null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("LinerNotesHttp", "HTTP GET error for $url: ${e.message}")
            null
        }
    }

    fun postForm(
        url: String,
        formParams: Map<String, String>,
        headers: Map<String, String> = emptyMap()
    ): String? {
        return try {
            val formBuilder = FormBody.Builder()
            formParams.forEach { (k, v) -> formBuilder.add(k, v) }

            val reqBuilder = Request.Builder()
                .url(url)
                .post(formBuilder.build())
                .header("User-Agent", headers["User-Agent"] ?: DEFAULT_USER_AGENT)

            headers.forEach { (k, v) ->
                if (!k.equals("User-Agent", ignoreCase = true)) {
                    reqBuilder.header(k, v)
                }
            }

            client.newCall(reqBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    fun postJson(
        url: String,
        jsonBody: String,
        headers: Map<String, String> = emptyMap()
    ): String? {
        return try {
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = jsonBody.toRequestBody(mediaType)

            val reqBuilder = Request.Builder()
                .url(url)
                .post(body)
                .header("User-Agent", headers["User-Agent"] ?: DEFAULT_USER_AGENT)

            headers.forEach { (k, v) ->
                if (!k.equals("User-Agent", ignoreCase = true)) {
                    reqBuilder.header(k, v)
                }
            }

            client.newCall(reqBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
