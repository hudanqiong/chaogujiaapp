package com.chaogujia.app

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** 简单的网络请求封装：GET / POST(JSON)，返回字符串（在子线程调用） */
object Net {
    /**
     * 全局 Application Context（由 MainActivity.onCreate 注入一次）。
     * 供 DetailCache 等本地缓存层使用，避免各处重复传递 Context。
     */
    @Volatile
    var appContext: android.content.Context? = null

    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * 原始 OkHttpClient（供 multipart 上传等高级请求复用连接池）。
     * 仅供 Api.uploadImage 等内部使用。
     */
    val rawClient: OkHttpClient get() = client

    /**
     * 服务器时间校正偏移（毫秒）：
     *   serverTimeOffset = 服务器当前时间 - 本机当前时间
     * 开机/无网络时默认为 0，首次成功请求后即被更新。
     * 所有涉及「08:45–09:15 重置时段」的判断都应使用 [serverNow] 而非本机时间。
     */
    @Volatile
    private var serverTimeOffset: Long = 0L

    /** 用 HTTP 响应头的 Date（RFC 1123, GMT）更新服务器时间偏移 */
    private fun updateServerTime(resp: okhttp3.Response) {
        try {
            val dateStr = resp.header("Date") ?: return
            val fmt = java.text.SimpleDateFormat(
                "EEE, dd MMM yyyy HH:mm:ss 'GMT'", java.util.Locale.US
            )
            fmt.timeZone = java.util.TimeZone.getTimeZone("GMT")
            val serverMs = fmt.parse(dateStr)?.time ?: return
            serverTimeOffset = serverMs - System.currentTimeMillis()
        } catch (_: Exception) {
            // 解析失败则保持原偏移
        }
    }

    /** 校正后的当前时间（毫秒） */
    fun serverNow(): Long = System.currentTimeMillis() + serverTimeOffset

    /** 当前服务器时间偏移（毫秒），调试用 */
    fun serverTimeOffsetMs(): Long = serverTimeOffset

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
            updateServerTime(resp)
            return resp.body?.string() ?: ""
        }
    }

    /**
     * 直连外网 GET（不走 base()、不带 token），用于前端直接请求东财 K 线等第三方接口。
     * 失败返回 null（不抛）。
     */
    fun getRaw(url: String, referer: String = "https://quote.eastmoney.com/"): String? {
        return try {
            val b = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
                .header("Referer", referer)
            client.newCall(b.build()).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string()
            }
        } catch (_: Exception) {
            null
        }
    }

    /** POST JSON 请求，可带 token */
    fun postJson(url: String, json: String, token: String? = null, withTokenParam: Boolean = true): String {
        val finalUrl = if (withTokenParam) withToken(url, token) else url
        val b = Request.Builder().url(finalUrl).post(json.toRequestBody(JSON))
        if (!token.isNullOrEmpty()) b.addHeader("Authorization", "Bearer $token")
        client.newCall(b.build()).execute().use { resp ->
            updateServerTime(resp)
            return resp.body?.string() ?: ""
        }
    }
}