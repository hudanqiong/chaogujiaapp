package com.chaogujia.app

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** 简单的网络请求封装：GET / POST(JSON)，返回字符串（在子线程调用） */
object Net {
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * token 三通道发送（与网页版 app.js 保持一致）：
     * ① URL 参数 ?token=xxx（主，因为 Apache 可能不透传 Authorization 头）
     * ② Authorization: Bearer xxx（备）
     * 后端 current_user() 支持参数兜底读取。
     */
    private fun withToken(url: String, token: String?): String {
        if (token.isNullOrEmpty()) return url
        val sep = if (url.contains("?")) "&" else "?"
        return url + sep + "token=" + java.net.URLEncoder.encode(token, "UTF-8")
    }

    /** GET 请求，可带 token */
    fun get(url: String, token: String? = null): String {
        val b = Request.Builder().url(withToken(url, token))
        if (!token.isNullOrEmpty()) b.addHeader("Authorization", "Bearer $token")
        client.newCall(b.build()).execute().use { resp ->
            return resp.body?.string() ?: ""
        }
    }

    /** POST JSON 请求，可带 token */
    fun postJson(url: String, json: String, token: String? = null, withTokenParam: Boolean = true): String {
        val finalUrl = if (withTokenParam) withToken(url, token) else url
        val b = Request.Builder().url(finalUrl).post(json.toRequestBody(JSON))
        if (!token.isNullOrEmpty()) b.addHeader("Authorization", "Bearer $token")
        client.newCall(b.build()).execute().use { resp ->
            return resp.body?.string() ?: ""
        }
    }
}