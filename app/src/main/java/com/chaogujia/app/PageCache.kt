package com.chaogujia.app

import android.content.Context
import java.io.File

/**
 * 通用页面级「本地磁盘缓存」（纯前端，不改后端）。
 *
 * 背景：多个页面（持仓页 / 交易页资产卡 / 历史K线）每次进入或切账户都要重新请求后端，
 *   网络往返期间页面空白、体感「慢」。这里做一层磁盘持久缓存：
 *   - 关机 / 重启后依然存在（不是内存缓存）；
 *   - 命中时**秒显上一帧**（stale-while-revalidate），随后由页面后台刷新覆盖。
 *
 * 与本项目已有 DetailCache 的区别：
 *   - DetailCache 固定按 <code>_<k>_<kn> 命名（个股详情专用）；
 *   - PageCache 用调用方给的任意 key（如 "account_3" / "kline_000002_day"），更通用。
 *
 * 存储格式：filesDir/page_cache/<safeKey>.txt
 *   第一行：写入时间戳(ms)
 *   第二行起：原始文本（后端返回的原始 JSON 字符串）
 */
object PageCache {

    private const val DIR = "page_cache"

    private fun dir(ctx: Context): File {
        val d = File(ctx.filesDir, DIR)
        if (!d.exists()) d.mkdirs()
        return d
    }

    private fun fileOf(ctx: Context, key: String): File {
        val safe = key.replace(Regex("[^A-Za-z0-9_]"), "_")
        return File(dir(ctx), safe + ".txt")
    }

    /**
     * 读取缓存。命中且未过期返回原始文本，否则返回 null。
     * @param ttlMs 有效期（毫秒）；传 <=0 表示不检查过期（等同 loadStale）。
     */
    fun load(ctx: Context?, key: String, ttlMs: Long): String? {
        if (ctx == null) return null
        return try {
            val f = fileOf(ctx, key)
            if (!f.exists()) return null
            val text = f.readText()
            val nl = text.indexOf('\n')
            if (nl <= 0) return null
            if (ttlMs > 0) {
                val ts = text.substring(0, nl).trim().toLongOrNull() ?: return null
                if (System.currentTimeMillis() - ts > ttlMs) return null
            }
            val body = text.substring(nl + 1)
            if (body.isBlank()) null else body
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 读取「过期也可用」的缓存（忽略 TTL）：只要文件存在且内容合法就返回。
     * 用途：SWR 首帧——先画上一帧旧数据，不等网络。
     */
    fun loadStale(ctx: Context?, key: String): String? = load(ctx, key, 0L)

    /** 写入缓存（原始文本）。 */
    fun save(ctx: Context?, key: String, raw: String) {
        if (ctx == null) return
        if (raw.isBlank()) return
        try {
            val f = fileOf(ctx, key)
            f.writeText(System.currentTimeMillis().toString() + "\n" + raw)
        } catch (_: Exception) {
            // 缓存写入失败不影响主流程
        }
    }
}
