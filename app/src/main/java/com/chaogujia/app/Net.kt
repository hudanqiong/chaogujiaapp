package com.chaogujia.app

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** 简单的网络请求封装：GET / POST 表单，返回字符串（在子线程调用） */
object Net {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun get(url: String): String {
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { resp ->
            return resp.body?.string() ?: ""
        }
    }
}