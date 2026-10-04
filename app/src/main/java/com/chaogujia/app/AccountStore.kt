package com.chaogujia.app

import android.content.Context

/**
 * 第 48 轮新增：全局「当前券商账户」持久化存储。
 *
 * 背景：此前交易页(TradePage)、持仓页(PositionPage)、个股行情页下单(StockDetailPage)、
 * 自选页持仓分组(IndexBar) 各自用 `firstOrNull()` 默认取第一个账户，重开 App 必回第一个，
 * 无法记住用户选择。本对象统一读写 SharedPreferences("chaogujia") 的 key = current_broker_id，
 * 四处共享同一值 → 实现「账户联动」。
 */
object AccountStore {
    private const val SP = "chaogujia"
    private const val KEY = "current_broker_id"

    /** 读取已保存的账户 id；未保存或非法返回 0。 */
    fun get(ctx: Context): Int =
        ctx.getSharedPreferences(SP, Context.MODE_PRIVATE).getInt(KEY, 0)

    /** 保存当前账户 id。 */
    fun set(ctx: Context, id: Int) {
        ctx.getSharedPreferences(SP, Context.MODE_PRIVATE)
            .edit().putInt(KEY, id).apply()
    }

    /**
     * 在账户列表中定位"应选中"的账户：
     * - 若已保存 id 在列表中存在，返回它；
     * - 否则返回列表第一个（并顺带把第一个写回存储，保证后续一致）。
     */
    fun <T> pick(ctx: Context, list: List<T>, idOf: (T) -> Int): T? {
        if (list.isEmpty()) return null
        val saved = get(ctx)
        val hit = list.firstOrNull { idOf(it) == saved }
        val chosen = hit ?: list.first()
        if (idOf(chosen) != saved) set(ctx, idOf(chosen))
        return chosen
    }
}