package com.chaogujia.app

import android.content.Context
import java.io.File

/**
 * 个股详情 detail.php 的「本地磁盘缓存」（纯前端兜底，不改后端）。
 *
 * 背景：detail.php 冷请求约 4.5s（后端等上游行情源的固定延迟），热缓存仅 ~20~30s。
 * 因此 App 侧做一层本地磁盘缓存：缓存**原始 JSON 字符串**，命中且未过期时
 * 直接复用缓存 raw 走同一套解析逻辑（不改任何解析代码），实现"打开即秒开"。
 *
 * 策略：
 *  - 文件放 filesDir/detail_cache/ 下，文件名为 <code>_<k>_<kn>.json
 *  - 文件内容格式：第一行为写入时间戳(ms)，第二行起为原始 JSON
 *  - TTL 默认 5 分钟（远长于后端 20~30s，但足够保证行情"新鲜度"体感）
 */
object DetailCache {

    /** 缓存有效期：5 分钟（毫秒） */
    const val TTL_MS: Long = 5 * 60 * 1000L

    private const val DIR = "detail_cache"

    private fun dir(ctx: Context): File {
        val d = File(ctx.filesDir, DIR)
        if (!d.exists()) d.mkdirs()
        return d
    }

    private fun fileOf(ctx: Context, code: String, k: String, kn: Int): File {
        val safe = code.replace(Regex("[^A-Za-z0-9]"), "_")
        return File(dir(ctx), "${safe}_${k}_$kn.json")
    }

    /**
     * 读取缓存。命中且未过期返回原始 JSON 字符串，否则返回 null。
     */
    fun load(ctx: Context?, code: String, k: String, kn: Int, ttlMs: Long = TTL_MS): String? {
        if (ctx == null) return null
        return try {
            val f = fileOf(ctx, code, k, kn)
            if (!f.exists()) return null
            val text = f.readText()
            val nl = text.indexOf('\n')
            if (nl <= 0) return null
            val ts = text.substring(0, nl).trim().toLongOrNull() ?: return null
            if (System.currentTimeMillis() - ts > ttlMs) return null
            val body = text.substring(nl + 1)
            if (body.isBlank()) null else body
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 读取“过期也可用”的缓存（忽略 TTL）：只要文件存在且内容合法就返回。
     * 用途：自选列表 mini 分时图——宁可先画上一帧旧数据（秒级显示），
     * 也不阻塞等网络（detail.php 冷请求 ~4.5s）。刷新由后台预热负责。
     */
    fun loadStale(ctx: Context?, code: String, k: String, kn: Int): String? {
        if (ctx == null) return null
        return try {
            val f = fileOf(ctx, code, k, kn)
            if (!f.exists()) return null
            val text = f.readText()
            val nl = text.indexOf('
')
            if (nl <= 0) return null
            val body = text.substring(nl + 1)
            if (body.isBlank()) null else body
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 写入缓存（原始 JSON 字符串）。
     */
    fun save(ctx: Context?, code: String, k: String, kn: Int, raw: String) {
        if (ctx == null) return
        if (raw.isBlank()) return
        try {
            val f = fileOf(ctx, code, k, kn)
            f.writeText(System.currentTimeMillis().toString() + "\n" + raw)
        } catch (_: Exception) {
            // 缓存写入失败不影响主流程
        }
    }
}