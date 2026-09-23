package com.chaogujia.app

import java.util.Locale

/**
 * 自选页「股票列表列配置」。
 *
 * 设计目标：列表左侧「名称/代码」固定不动，右侧统计列可横向滑动，
 * 且统计列种类可扩展 —— 以后用户可以自己勾选要显示哪些列。
 *
 * 用法：
 *   - [WatchColumn] 是列的元定义（key / 标题 / 宽度 / 取值 / 颜色规则）
 *   - [WatchColumnRegistry.ALL] 是所有可选列（用户从这里挑）
 *   - [WatchColumnRegistry.DEFAULT] 是默认显示列
 *   - 用户配置的 key 列表存在 [WatchColumnPrefs] 里（SharedPreferences）
 *
 * 想加新列：在 [WatchColumnRegistry.ALL] 里加一条即可，UI 会自动支持。
 */

/** 一列的完整定义 */
data class WatchColumn(
    /** 唯一 key，用于持久化用户选择 */
    val key: String,
    /** 表头显示的文字 */
    val title: String,
    /** 列宽（dp） */
    val width: Int,
    /** 取该行要显示的文本 */
    val value: (StockItem) -> String,
    /** 文本颜色：null 表示用默认正文色；[UP_DOWN] 表示按涨跌染色 */
    val colorMode: ColorMode = ColorMode.DEFAULT
) {
    enum class ColorMode { DEFAULT, UP_DOWN }
}

/** 全部可选列（顺序即默认展示顺序） */
object WatchColumnRegistry {

    /** 最新价 */
    val PRICE = WatchColumn(
        key = "price",
        title = "最新价",
        width = 84,
        value = { String.format(Locale.CHINA, "%.2f", it.price) },
        colorMode = WatchColumn.ColorMode.UP_DOWN
    )

    /** 涨跌幅 % */
    val CHANGE_PCT = WatchColumn(
        key = "changePct",
        title = "涨跌幅",
        width = 78,
        value = { String.format(Locale.CHINA, "%+.2f%%", it.changePct) },
        colorMode = WatchColumn.ColorMode.UP_DOWN
    )

    /**
     * 涨幅（合并列）：上行 = 涨跌幅百分比（红涨绿跌），下行 = 最新价（灰色）。
     * 注意：此列为两行特殊布局，value 仅作占位（真正渲染在 StockRow 里特判 key）。
     */
    val PCT_PRICE = WatchColumn(
        key = "pctPrice",
        title = "涨幅",
        width = 84,
        value = { String.format(Locale.CHINA, "%+.2f%%", it.changePct) },
        colorMode = WatchColumn.ColorMode.UP_DOWN
    )

    /** 涨跌额 */
    val CHANGE = WatchColumn(
        key = "change",
        title = "涨跌额",
        width = 76,
        value = { String.format(Locale.CHINA, "%+.2f", it.change) },
        colorMode = WatchColumn.ColorMode.UP_DOWN
    )

    /** 成交额（实时，元→亿/万） */
    val AMOUNT = WatchColumn(
        key = "amount",
        title = "成交额",
        width = 76,
        value = { fmtAmount(it.amount) }
    )

    /** 竞价涨幅%（9:25 定格快照） */
    val AUC_PCT = WatchColumn(
        key = "aucPct",
        title = "竞价涨幅",
        width = 72,
        value = { String.format(Locale.CHINA, "%+.2f%%", it.aucPct) },
        colorMode = WatchColumn.ColorMode.UP_DOWN
    )

    /** 竞价金额（9:25 定格快照，元→万/亿） */
    val AUC_AMOUNT = WatchColumn(
        key = "aucAmount",
        title = "竞价金额",
        width = 76,
        value = { fmtAmount(it.aucAmount) }
    )

    /**
     * 所有可选列。以后扩充字段（如成交量/换手/市值）时在这里加。
     * 注意：扩展到 StockItem 里没有的字段时，需要先给 StockItem 加字段。
     */
    val ALL: List<WatchColumn> = listOf(PCT_PRICE, PRICE, CHANGE_PCT, CHANGE, AMOUNT, AUC_PCT, AUC_AMOUNT)

    /** 默认显示的列 key（顺序敏感）：只默认显示「涨幅」合并列（上百分比 / 下价格） */
    // 默认不启用任何横滑统计列（「涨幅」已作为右侧固定列单独渲染）
    val DEFAULT_KEYS: List<String> = listOf(AMOUNT.key, AUC_PCT.key, AUC_AMOUNT.key)

    /** 按 key 取列定义 */
    fun byKey(key: String): WatchColumn? = ALL.firstOrNull { it.key == key }

    /** 把用户配置的 key 列表转成列定义列表（过滤掉不认识的 key；pctPrice 已作为右侧固定列，不再参与横滑） */
    fun resolve(keys: List<String>): List<WatchColumn> =
        keys.filter { it != PCT_PRICE.key }.mapNotNull { byKey(it) }
}

/** 用户列偏好（本地持久化，简单实现，避免额外依赖） */
object WatchColumnPrefs {
    private const val SP_NAME = "watch_cols"
    private const val KEY_ORDER = "order"
    private const val KEY_VER = "ver"

    /** 当前列配置版本。改列定义时 +1，可触发老配置一次性迁移。 */
    private const val CUR_VER = 3

    /** 读用户配置；没存过则返回默认 */
    fun load(ctx: android.content.Context): List<String> {
        return try {
            val sp = ctx.getSharedPreferences(SP_NAME, android.content.Context.MODE_PRIVATE)
            val ver = sp.getInt(KEY_VER, 1)
            if (ver < CUR_VER) {
                // 老版本配置迁移：把「最新价 / 涨跌幅」两列合并为「涨幅」合并列
                sp.edit().putInt(KEY_VER, CUR_VER).apply()
                return WatchColumnRegistry.DEFAULT_KEYS
            }
            val raw = sp.getString(KEY_ORDER, null) ?: return WatchColumnRegistry.DEFAULT_KEYS
            val list = raw.split(",").filter { it.isNotBlank() }
            val valid = list.filter { WatchColumnRegistry.byKey(it) != null }
            if (valid.isEmpty()) WatchColumnRegistry.DEFAULT_KEYS else valid
        } catch (e: Exception) {
            WatchColumnRegistry.DEFAULT_KEYS
        }
    }

    /** 存用户配置 */
    fun save(ctx: android.content.Context, keys: List<String>) {
        try {
            val sp = ctx.getSharedPreferences(SP_NAME, android.content.Context.MODE_PRIVATE)
            sp.edit().putString(KEY_ORDER, keys.joinToString(",")).putInt(KEY_VER, CUR_VER).apply()
        } catch (e: Exception) { /* 忽略 */ }
    }
}

/** 金额格式化：元 → 亿 / 万，保留 2 位小数；<=0 显示 "--" */
fun fmtAmount(yuan: Double): String {
    if (yuan <= 0.0) return "--"
    return when {
        yuan >= 1e8 -> String.format(Locale.CHINA, "%.2f亿", yuan / 1e8)
        yuan >= 1e4 -> String.format(Locale.CHINA, "%.2f万", yuan / 1e4)
        else        -> String.format(Locale.CHINA, "%.0f", yuan)
    }
}
