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
        width = 96,
        value = { fmtAmount(it.amount) }
    )

    /** 竞价涨幅%（9:25 定格快照） */
    val AUC_PCT = WatchColumn(
        key = "aucPct",
        title = "竞价涨幅%",
        width = 104,
        value = { String.format(Locale.CHINA, "%+.2f%%", it.aucPct) },
        colorMode = WatchColumn.ColorMode.UP_DOWN
    )

    /** 竞价金额（9:25 定格快照，元→万/亿） */
    val AUC_AMOUNT = WatchColumn(
        key = "aucAmount",
        title = "竞价金额",
        width = 96,
        value = { fmtAmount(it.aucAmount) }
    )

    // ============================================================
    //  ★ 全量字段列（基于服务端返回的 raw 数组，按腾讯接口索引取值）
    //  加列零成本：以后只改这里，不用动 StockItem / 后端
    // ============================================================

    /** 取 raw 中第 i 个字段的字符串原文（空/无值返回 "--"） */
    private fun rawStr(it: StockItem, i: Int): String {
        val v = it.raw.getOrNull(i)?.trim() ?: return "--"
        return if (v.isEmpty()) "--" else v
    }

    /** 取 raw 中第 i 个字段当数字，用 fmt 格式化；非数字/空 → "--" */
    private fun rawNum(it: StockItem, i: Int, fmt: String): String {
        val v = rawStr(it, i)
        if (v == "--") return "--"
        val d = v.toDoubleOrNull() ?: return "--"
        return try { String.format(Locale.CHINA, fmt, d) } catch (e: Exception) { "--" }
    }

    /** 取 raw 中第 i 个字段当数字，带正负号 */
    private fun rawSigned(it: StockItem, i: Int, fmt: String): String {
        val v = rawStr(it, i)
        if (v == "--") return "--"
        val d = v.toDoubleOrNull() ?: return "--"
        return try { String.format(Locale.CHINA, fmt, d) } catch (e: Exception) { "--" }
    }

    /** 成交量：手 → 万手/手 */
    private fun fmtLots(it: StockItem, i: Int): String {
        val v = rawStr(it, i); if (v == "--") return "--"
        val d = v.toDoubleOrNull() ?: return "--"
        return when {
            d >= 1e4 -> String.format(Locale.CHINA, "%.2f万手", d / 1e4)
            else     -> String.format(Locale.CHINA, "%.0f手", d)
        }
    }

    /** 成交额（raw 里是万元）→ 亿/万 */
    private fun fmtWanAmount(it: StockItem, i: Int): String {
        val v = rawStr(it, i); if (v == "--") return "--"
        val d = v.toDoubleOrNull() ?: return "--"
        val yuan = d * 10000.0
        return if (yuan <= 0.0) "--" else fmtAmount(yuan)
    }

    /** 市值/股本类（raw 里单位已是亿） */
    private fun fmtYi(it: StockItem, i: Int): String {
        val v = rawStr(it, i); if (v == "--") return "--"
        val d = v.toDoubleOrNull() ?: return "--"
        return String.format(Locale.CHINA, "%.2f亿", d)
    }

    /** 股本（股）→ 亿股/万股 */
    private fun fmtShares(it: StockItem, i: Int): String {
        val v = rawStr(it, i); if (v == "--") return "--"
        val d = v.toDoubleOrNull() ?: return "--"
        return when {
            d >= 1e8 -> String.format(Locale.CHINA, "%.2f亿股", d / 1e8)
            d >= 1e4 -> String.format(Locale.CHINA, "%.2f万股", d / 1e4)
            else     -> String.format(Locale.CHINA, "%.0f股", d)
        }
    }

    /** 小工具：造一个基于 raw 的列 */
    private fun col(
        key: String, title: String, width: Int, idx: Int,
        fmt: String = "%.2f",
        upDown: Boolean = false,
        mode: Int = 0 // 0=普通数字 1=带符号 2=成交量 3=成交额(万) 4=亿 5=股本
    ): WatchColumn {
        val f: (StockItem) -> String = when (mode) {
            1 -> fun(s: StockItem): String = rawSigned(s, idx, fmt)
            2 -> fun(s: StockItem): String = fmtLots(s, idx)
            3 -> fun(s: StockItem): String = fmtWanAmount(s, idx)
            4 -> fun(s: StockItem): String = fmtYi(s, idx)
            5 -> fun(s: StockItem): String = fmtShares(s, idx)
            else -> fun(s: StockItem): String = rawNum(s, idx, fmt)
        }
        return WatchColumn(
            key = key, title = title, width = width,
            value = f,
            colorMode = if (upDown) WatchColumn.ColorMode.UP_DOWN else WatchColumn.ColorMode.DEFAULT
        )
    }

    // ---- 基础行情 ----
    val R_PRICE      = col("r_price",      "最新价",   72, 3)
    val R_PREVCLOSE  = col("r_prevClose",  "昨收",     72, 4)
    val R_OPEN       = col("r_open",       "今开",     72, 5)
    val R_HIGH       = col("r_high",       "最高",     72, 33)
    val R_LOW        = col("r_low",        "最低",     72, 34)
    val R_AVG        = col("r_avg",        "均价",     72, 51)
    val R_CHANGE     = col("r_change",     "涨跌额",   74, 31, "%+.2f", upDown = true, mode = 1)
    val R_CHANGE_PCT = col("r_changePct",  "涨跌幅",   74, 32, "%+.2f%%", upDown = true, mode = 1)

    // ---- 量额 / 盘口 ----
    val R_VOL        = col("r_vol",        "成交量",   84, 36, mode = 2)
    val R_TURN       = col("r_turn",       "成交额",   84, 37, mode = 3)
    val R_TURNOVER   = col("r_turnover",   "换手率",   74, 38, "%.2f%%")
    val R_VOLRATIO   = col("r_volRatio",   "量比",     68, 49)
    val R_SPEED      = col("r_speed",      "涨速",     68, 56, "%+.2f%%", upDown = true, mode = 1)
    val R_OUTER      = col("r_outer",      "外盘",     78, 6)
    val R_INNER      = col("r_inner",      "内盘",     78, 7)
    val R_AMP        = col("r_amp",        "振幅",     68, 43, "%.2f%%")

    // ---- 估值 / 市值 ----
    val R_PE_TTM     = col("r_peTtm",      "市盈率TTM", 88, 39)
    val R_PE_STATIC  = col("r_peStatic",   "市盈率静", 84, 52)
    val R_PE_DYN     = col("r_peDyn",      "市盈率动", 84, 53)
    val R_PB         = col("r_pb",         "市净率",   72, 46)
    val R_FLOAT_MV   = col("r_floatMv",    "流通市值", 88, 44, mode = 4)
    val R_TOTAL_MV   = col("r_totalMv",    "总市值",   88, 45, mode = 4)
    val R_FLOAT_SH   = col("r_floatSh",    "流通股本", 88, 72, mode = 5)
    val R_TOTAL_SH   = col("r_totalSh",    "总股本",   88, 73, mode = 5)
    val R_DIVIDEND   = col("r_dividend",   "股息率",   74, 74, "%.2f%%")

    // ---- 涨跌停 ----
    val R_LIMIT_UP   = col("r_limitUp",    "涨停价",   72, 47)
    val R_LIMIT_DOWN = col("r_limitDown",  "跌停价",   72, 48)

    // ---- 多周期涨幅 ----
    val R_5MIN       = col("r_5min",       "5分涨速",  78, 62, "%+.2f%%", upDown = true, mode = 1)
    val R_D3         = col("r_d3",         "3日涨幅",  78, 64, "%+.2f%%", upDown = true, mode = 1)
    val R_D6         = col("r_d6",         "6日涨幅",  78, 65, "%+.2f%%", upDown = true, mode = 1)
    val R_D7         = col("r_d7",         "7日涨幅",  78, 70, "%+.2f%%", upDown = true, mode = 1)
    val R_D20        = col("r_d20",        "20日涨幅", 82, 66, "%+.2f%%", upDown = true, mode = 1)
    val R_D60        = col("r_d60",        "60日涨幅", 82, 71, "%+.2f%%", upDown = true, mode = 1)
    val R_MTD        = col("r_ytd",        "年初至今", 82, 69, "%+.2f%%", upDown = true, mode = 1)
    val R_H60        = col("r_h60",        "60日高",   72, 67)
    val R_L60        = col("r_l60",        "60日低",   72, 68)

    // ---- 五档盘口（关键档位） ----
    val R_B1         = col("r_b1",         "买一价",   72, 9)
    val R_B2         = col("r_b2",         "买二价",   72, 11)
    val R_B3         = col("r_b3",         "买三价",   72, 13)
    val R_B4         = col("r_b4",         "买四价",   72, 15)
    val R_B5         = col("r_b5",         "买五价",   72, 17)
    val R_S1         = col("r_s1",         "卖一价",   72, 19)
    val R_S2         = col("r_s2",         "卖二价",   72, 21)
    val R_S3         = col("r_s3",         "卖三价",   72, 23)
    val R_S4         = col("r_s4",         "卖四价",   72, 25)
    val R_S5         = col("r_s5",         "卖五价",   72, 27)

    /** 批量：全量扩展列 */
    val EXTRA: List<WatchColumn> = listOf(
        R_PRICE, R_PREVCLOSE, R_OPEN, R_HIGH, R_LOW, R_AVG, R_CHANGE, R_CHANGE_PCT,
        R_VOL, R_TURN, R_TURNOVER, R_VOLRATIO, R_SPEED, R_OUTER, R_INNER, R_AMP,
        R_PE_TTM, R_PE_STATIC, R_PE_DYN, R_PB, R_FLOAT_MV, R_TOTAL_MV, R_FLOAT_SH, R_TOTAL_SH, R_DIVIDEND,
        R_LIMIT_UP, R_LIMIT_DOWN,
        R_5MIN, R_D3, R_D6, R_D7, R_D20, R_D60, R_MTD, R_H60, R_L60,
        R_B1, R_B2, R_B3, R_B4, R_B5, R_S1, R_S2, R_S3, R_S4, R_S5
    )

    /**
     * 所有可选列。以后扩充字段在这里加。
     * 注意：扩展到 StockItem 里没有的字段时，需要先给 StockItem 加字段。
     * 注意：扩展到 StockItem 里没有的字段时，需要先给 StockItem 加字段。
     */
    val ALL: List<WatchColumn> = listOf(PCT_PRICE, PRICE, CHANGE_PCT, CHANGE, AMOUNT, AUC_PCT, AUC_AMOUNT) + EXTRA

    /** 默认显示的列 key（顺序敏感）：只默认显示「涨幅」合并列（上百分比 / 下价格） */
    // 默认不启用任何横滑统计列（「涨幅」已作为右侧固定列单独渲染）
    val DEFAULT_KEYS: List<String> = listOf(AUC_PCT.key, AUC_AMOUNT.key, AMOUNT.key)

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
    private const val CUR_VER = 6

    /** 读用户配置；没存过则返回默认 */
    fun load(ctx: android.content.Context): List<String> {
        return try {
            val sp = ctx.getSharedPreferences(SP_NAME, android.content.Context.MODE_PRIVATE)
            val ver = sp.getInt(KEY_VER, 1)
            if (ver < CUR_VER) {
                // 老版本配置迁移：重置为默认三列（成交额 / 竞价涨幅% / 竞价金额）
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
