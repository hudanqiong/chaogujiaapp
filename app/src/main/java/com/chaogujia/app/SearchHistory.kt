package com.chaogujia.app

import android.content.Context
import android.content.SharedPreferences

/**
 * 本地搜索历史（SharedPreferences 存储，不联网）。
 * 规则：最多保留 [MAX] 条；新词插到最前；重复词自动去重并提前。
 */
object SearchHistory {
    private const val SP_NAME = "chaogujia_search"
    private const val KEY = "history"
    private const val MAX = 10

    private fun sp(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)

    /** 读取历史（最新在最前） */
    fun load(ctx: Context): List<String> {
        val raw = sp(ctx).getString(KEY, "") ?: ""
        if (raw.isEmpty()) return emptyList()
        return raw.split("\n").filter { it.isNotBlank() }
    }

    /** 新增一条历史（去重 + 置顶 + 截断到 MAX） */
    fun add(ctx: Context, keyword: String) {
        val kw = keyword.trim()
        if (kw.isEmpty()) return
        val list = load(ctx).toMutableList()
        list.remove(kw)
        list.add(0, kw)
        while (list.size > MAX) list.removeAt(list.size - 1)
        save(ctx, list)
    }

    /** 清空历史 */
    fun clear(ctx: Context) {
        sp(ctx).edit().remove(KEY).apply()
    }

    private fun save(ctx: Context, list: List<String>) {
        sp(ctx).edit().putString(KEY, list.joinToString("\n")).apply()
    }
}
