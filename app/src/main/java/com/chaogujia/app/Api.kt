package com.chaogujia.app

import org.json.JSONObject

/**
 * 后端接口封装。
 * 后端统一返回：{ "code":0, "msg":"...", "data": {...} }
 *  code==0 表示成功。
 */

/** 账户信息（account.php） */
data class AccountInfo(
    val cash: Double = 0.0,
    val marketValue: Double = 0.0,
    val total: Double = 0.0,
    val floatPn: Double = 0.0,
    val totalPn: Double = 0.0,
    val totalPnPct: Double = 0.0,
    val initial: Double = 0.0
)

/** 会员套餐（member.php 的 plans[]） */
data class MemberPlan(
    val name: String = "",
    val desc: String = "",
    val price: Double = 0.0,
    val unit: String = "",
    val hot: Boolean = false,
    val key: String = ""
)

/** 会员信息（member.php） */
data class MemberInfo(
    val isMember: Boolean = false,
    val levelName: String = "普通用户",
    val expireAt: String = "",
    val plans: List<MemberPlan> = emptyList()
)

/** 积分信息（point.php） */
data class PointInfo(
    val points: Int = 0
)

/** 积分流水（point.php 的 logs[]） */
data class PointLog(
    val desc: String = "",
    val points: Int = 0,
    val time: String = ""
)

/** 邀请信息（invite.php） */
data class InviteInfo(
    val code: String = "",
    val invitedCount: Int = 0,
    val totalReward: Int = 0,
    val users: List<InvitedUser> = emptyList()
)

/** 被邀请用户 */
data class InvitedUser(
    val username: String = "",
    val reward: Int = 0,
    val time: String = ""
)

/** 券商 */
data class Broker(
    val id: String = "",
    val name: String = ""
)

/** 我的券商账户 */
data class MyBroker(
    val broker: String = "",
    val account: String = ""
)

/** 自选分组（watch_group.php） */
data class WatchGroup(
    val id: Int = 0,
    val name: String = "",
    val sort: Int = 0
)
/** 股票条目（search.php / watchlist.php list / 行情通用） */
data class StockItem(
    val code: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val change: Double = 0.0,
    val changePct: Double = 0.0,
    // 成交额（元，实时）
    val amount: Double = 0.0,
    // 集合竞价涨幅%（9:25 定格，快照）
    val aucPct: Double = 0.0,
    // 集合竞价金额（元，9:25 定格，快照）
    val aucAmount: Double = 0.0,
    // ★ 服务端返回的全量原始字段（腾讯接口 ~ 拆分，按索引对齐）
    // 用途：「编辑表头」任意取用，以后加列无需改 data class
    val raw: List<String> = emptyList()
)

/** 表头字段元数据（后端 fields.php 下发，唯一真源） */
data class WatchField(
    val key: String = "",
    val title: String = "",
    val unit: String = "",
    val width: Int = 80,
    /** "updown" 涨红跌绿 / "none" 普通色 */
    val color: String = "none",
    /** "item" 取 StockItem 字段 / "raw" 取 raw 数组 */
    val src: String = "raw",
    /** src=raw 时腾讯 raw 下标；否则 -1 */
    val rawIdx: Int = -1,
    /** 格式枚举：plain/num0/num2/signed2/signed2pct/pct2/lots/wanAmount/yi/shares/str/amount */
    val fmt: String = "num2",
    /** src=item 时的 StockItem 字段名 */
    val item: String = "",
    val group: String = "",
    val def: Boolean = false,
    val avail: Boolean = true
)

/** 大盘指数（index_quote.php） */
data class IndexQuote(
    val code: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val change: Double = 0.0,
    val changePct: Double = 0.0
)

/** 指数清单项（index_list.php） */
data class IndexItemInfo(
    val secid: String = "",
    val name: String = "",
    val short: String = "",
    val code: String = ""
)

/** 分时点（index_detail.php 的 trends[]） */
data class TrendPoint(
    val t: String = "",      // HH:MM
    val p: Double = 0.0,     // 当前价
    val avg: Double = 0.0,   // 均价
    val v: Double = 0.0      // 成交量
)

/** 指数详情（index_detail.php） */
data class IndexDetail(
    val secid: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val change: Double = 0.0,
    val changePct: Double = 0.0,
    val amount: Double = 0.0,
    val preClose: Double = 0.0,
    val upCount: Int = 0,
    val downCount: Int = 0,
    val flatCount: Int = 0,
    val trends: List<TrendPoint> = emptyList()
)

/** 个股头部统计（detail.php 的 base 对象） */
data class StockBase(
    val code: String = "",
    val name: String = "",
    val price: Double = 0.0,       // 当前价
    val preClose: Double = 0.0,    // 昨收
    val open: Double = 0.0,        // 今开
    val high: Double = 0.0,        // 最高
    val low: Double = 0.0,         // 最低
    val change: Double = 0.0,      // 涨跌额
    val changePct: Double = 0.0,   // 涨跌幅 %
    val volume: Double = 0.0,      // 成交量(手)
    val amount: Double = 0.0,      // 成交额(万)
    val turnover: Double = 0.0,    // 换手率 %
    val volRatio: Double = 0.0,    // 量比
    val mktCap: Double = 0.0,      // 总市值(亿)
    val floatCap: Double = 0.0,    // 流通市值(亿)
    val pe: Double = 0.0,          // 市盈率(动)
    val pb: Double = 0.0,          // 市净率
    val amplitude: Double = 0.0    // 振幅 %
)

/** 分时点：[分钟序号, 价格, 累计量(手), 累计额(元)] */
data class DetailTrend(
    val min: Int = 0,
    /** 该分钟瞬时成交价（行情原生推送，非计算值）：即分时白线上一个点的高度。
     *  光标定位到某分钟时，信息条显示的就是此 price（如 5.17）。 */
    val price: Double = 0.0,
    val vol: Double = 0.0,
    val amount: Double = 0.0
) {
    /** 均价 = 累计额 / (累计量 * 100)，量单位是手 */
    val avg: Double get() = if (vol > 0) amount / (vol * 100.0) else price
}

/** K线点：[日期, 开, 收, 高, 低, 量] */
data class KLinePoint(
    val date: String = "",
    val open: Double = 0.0,
    val close: Double = 0.0,
    val high: Double = 0.0,
    val low: Double = 0.0,
    val vol: Double = 0.0,
    val amount: Double = 0.0,
    val turnover: Double = 0.0    // 换手率(%)：东财 f58 真值；无数据为 0
)

/** 五日分时中的一天：[日期, 当天分时点] */
data class DetailDay(
    val date: String = "",
    val points: List<DetailTrend> = emptyList()
)

/** 个股详情（detail.php） */
data class StockDetail(
    val code: String = "",
    val name: String = "",
    val market: String = "",
    val k: String = "day",
    val base: StockBase = StockBase(),
    val trends: List<DetailTrend> = emptyList(),
    val trends5: List<DetailDay> = emptyList(),
    val line: List<KLinePoint> = emptyList(),
    /** 分时逐笔聚合，key = 分钟序号（0 起，午休已跳过）；来自 detail.php 的 deals 对象 */
    val minuteDeals: Map<Int, MinuteDeal> = emptyMap()
)

/** 五档中的一档：[价格, 数量(手)] */
data class OrderLevel(
    val price: Double = 0.0,
    val vol: Double = 0.0
)

/** 逐笔成交一笔 */
data class DealItem(
    val no: Int = 0,
    val time: String = "",
    val price: Double = 0.0,
    val change: Double = 0.0,
    val vol: Double = 0.0,       // 手
    val amount: Double = 0.0,    // 元
    val dir: String = "M"        // B=买 S=卖 M=中性
)

/** 分时量区：某分钟的逐笔聚合（detail.php 的 deals 对象，key=分钟序号）
 *  量   = hand     该分钟总成交量(手)
 *  额   = amt      该分钟成交金额(元)
 *  现手 = lastHand 该分钟最后一笔成交手数，lastDir = B主买/S主卖/M中性 */
data class MinuteDeal(
    val m: Int = 0,
    val hand: Double = 0.0,
    val amt: Double = 0.0,
    val buy: Double = 0.0,
    val sell: Double = 0.0,
    val lastHand: Double = 0.0,
    val lastDir: String = "M"
) {
    /** 主买-主卖净额，用于着色：>0 偏多，<0 偏空 */
    val net: Double get() = buy - sell
}

/**
 * 盘后固定价格交易 —— 单分钟点。
 *   time  : "1506"（HHmm，盘后时段 1506..1530，1 分钟 1 点）
 *   price : 该分钟成交价（盘后价恒等于收盘价）
 *   vol   : 该分钟成交量（手，已由接口的「累计量」差分得到）
 *   amt   : 该分钟成交额（元，已差分）
 *
 * 说明：数据来源为腾讯 minute/query，其原始 vol/amt 均为「当日累计」，
 * 解析时必须对相邻两条做差分，切勿直接读取单条值。
 */
data class AfterMinute(
    val time: String = "",
    /** 该分钟成交价（= 收盘价） */
    val price: Double = 0.0,
    /** 该分钟成交量（手） */
    val vol: Double = 0.0,
    /** 该分钟成交额（元） */
    val amt: Double = 0.0
)

/**
 * 盘后固定价格交易 —— 聚合结果。
 *   price    : 盘后固定价（= 当日收盘价）
 *   totalVol : 盘后总成交量（手）
 *   totalAmt : 盘后总成交额（元）
 *   minutes  : 盘后逐分钟点（时间升序，1506..1530，仅含有成交的分钟）
 *   supported: 该标的当日是否有盘后固定价交易（无成交时为 false，UI 不显示）
 *
 * 盘后固定价格交易现覆盖全部 A 股；接口对当日无盘后成交的标的返回空列表。
 */
data class AfterHours(
    val price: Double = 0.0,
    val totalVol: Double = 0.0,
    val totalAmt: Double = 0.0,
    val minutes: List<AfterMinute> = emptyList(),
    val supported: Boolean = false
)

/** 五档盘口 + 成交明细（orderbook.php） */
data class OrderBook(
    val code: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val prevClose: Double = 0.0,
    val open: Double = 0.0,
    val volume: Double = 0.0,
    val outer: Double = 0.0,     // 外盘（主动买，手）
    val inner: Double = 0.0,     // 内盘（主动卖，手）
    val bids: List<OrderLevel> = emptyList(),  // 买1~买5
    val asks: List<OrderLevel> = emptyList(),  // 卖1~卖5
    val deals: List<DealItem> = emptyList(),
    val limitUp: Double = 0.0,    // 涨停价（后端 limitUp）
    val limitDown: Double = 0.0,  // 跌停价（后端 limitDown）
    val maxSealAmount: Double = 0.0 // 当日最高封单额（元，后端 maxSealAmount，盘中峰值）
)

object Api {

    private fun base(url: String) = Config.BASE_URL + url

    private fun jsonOf(vararg pairs: Pair<String, Any?>): String {
        val o = JSONObject()
        for ((k, v) in pairs) o.put(k, v)
        return o.toString()
    }

    /** 登录结果：成功返回 token+username，失败返回错误信息 */
    data class AuthResult(val ok: Boolean, val token: String = "", val username: String = "", val msg: String = "")

    /** 通用结果 */
    data class Res(val ok: Boolean, val msg: String = "")

    /** 大盘指数（上证/深证/创业板），无需登录 */
    fun indexQuotes(): List<IndexQuote> {
        return try {
            val raw = Net.get(base("index_quote.php"))
            val o = JSONObject(raw)
            if (o.optInt("code", -1) != 0) return emptyList()
            val arr = o.optJSONArray("data") ?: return emptyList()
            val list = ArrayList<IndexQuote>()
            for (i in 0 until arr.length()) {
                val j = arr.optJSONObject(i) ?: continue
                list.add(
                    IndexQuote(
                        code = j.optString("code", ""),
                        name = j.optString("name", ""),
                        price = j.optDouble("price", 0.0),
                        change = j.optDouble("change", 0.0),
                        changePct = j.optDouble("changePct", 0.0)
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 指数清单（23 个常用指数，含首字简称），无需登录 */
    fun indexList(): List<IndexItemInfo> {
        return try {
            val raw = Net.get(base("index_list.php"))
            val o = JSONObject(raw)
            if (o.optInt("code", -1) != 0) return emptyList()
            val arr = o.optJSONArray("data") ?: return emptyList()
            val list = ArrayList<IndexItemInfo>()
            for (i in 0 until arr.length()) {
                val j = arr.optJSONObject(i) ?: continue
                list.add(
                    IndexItemInfo(
                        secid = j.optString("secid", ""),
                        name = j.optString("name", ""),
                        short = j.optString("short", ""),
                        code = j.optString("code", "")
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 指定指数的批量行情（secid 逗号分隔，最多 30 个），无需登录 */
    fun indexQuotesBy(secids: String): List<IndexQuote> {
        return try {
            val raw = Net.get(base("index_quote.php?secid=") + secids)
            val o = JSONObject(raw)
            if (o.optInt("code", -1) != 0) return emptyList()
            val arr = o.optJSONArray("data") ?: return emptyList()
            val list = ArrayList<IndexQuote>()
            for (i in 0 until arr.length()) {
                val j = arr.optJSONObject(i) ?: continue
                list.add(
                    IndexQuote(
                        code = j.optString("code", ""),
                        name = j.optString("name", ""),
                        price = j.optDouble("price", 0.0),
                        change = j.optDouble("change", 0.0),
                        changePct = j.optDouble("changePct", 0.0)
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 指数详情（点数/涨跌/成交额/涨跌家数/分时），无需登录 */
    fun indexDetail(secid: String): IndexDetail? {
        return try {
            val raw = Net.get(base("index_detail.php?secid=") + secid)
            val o = JSONObject(raw)
            if (o.optInt("code", -1) != 0) return null
            val d = o.optJSONObject("data") ?: return null
            val trends = ArrayList<TrendPoint>()
            val ta = d.optJSONArray("trends")
            if (ta != null) {
                for (i in 0 until ta.length()) {
                    val j = ta.optJSONObject(i) ?: continue
                    trends.add(
                        TrendPoint(
                            t = j.optString("t", ""),
                            p = j.optDouble("p", 0.0),
                            avg = j.optDouble("avg", 0.0),
                            v = j.optDouble("v", 0.0)
                        )
                    )
                }
            }
            IndexDetail(
                secid = d.optString("secid", secid),
                name = d.optString("name", ""),
                price = d.optDouble("price", 0.0),
                change = d.optDouble("change", 0.0),
                changePct = d.optDouble("changePct", 0.0),
                amount = d.optDouble("amount", 0.0),
                preClose = d.optDouble("preClose", 0.0),
                upCount = d.optInt("upCount", 0),
                downCount = d.optInt("downCount", 0),
                flatCount = d.optInt("flatCount", 0),
                trends = trends
            )
        } catch (e: Exception) {
            null
        }
    }

    /** 登录 */
    fun login(username: String, password: String): AuthResult {
        return try {
            val raw = Net.postJson(
                base("login.php"),
                jsonOf("username" to username, "password" to password),
                token = null,
                withTokenParam = false
            )
            val o = JSONObject(raw)
            if (o.optInt("code", -1) == 0) {
                val d = o.optJSONObject("data")
                AuthResult(
                    ok = true,
                    token = d?.optString("token", "") ?: "",
                    username = d?.optString("username", username) ?: username
                )
            } else {
                AuthResult(ok = false, msg = o.optString("msg", "登录失败"))
            }
        } catch (e: Exception) {
            AuthResult(ok = false, msg = e.message ?: "网络异常")
        }
    }

    /** 注册 */
    fun register(username: String, password: String): AuthResult {
        return try {
            val raw = Net.postJson(
                base("register.php"),
                jsonOf("username" to username, "password" to password),
                token = null,
                withTokenParam = false
            )
            val o = JSONObject(raw)
            if (o.optInt("code", -1) == 0) {
                val d = o.optJSONObject("data")
                AuthResult(
                    ok = true,
                    token = d?.optString("token", "") ?: "",
                    username = d?.optString("username", username) ?: username,
                    msg = o.optString("msg", "注册成功")
                )
            } else {
                AuthResult(ok = false, msg = o.optString("msg", "注册失败"))
            }
        } catch (e: Exception) {
            AuthResult(ok = false, msg = e.message ?: "网络异常")
        }
    }

    /** 表头字段元数据（fields.php）：后端唯一真源，前端据此动态建列 */
    fun fields(group: String? = null): List<WatchField> {
        return try {
            val url = if (group.isNullOrBlank()) base("fields.php")
                      else base("fields.php?group=") + java.net.URLEncoder.encode(group, "UTF-8")
            val o = JSONObject(Net.get(url))
            if (o.optInt("code", -1) != 0) return emptyList()
            val d = o.optJSONObject("data") ?: return emptyList()
            val arr = d.optJSONArray("fields") ?: return emptyList()
            val list = ArrayList<WatchField>(arr.length())
            for (i in 0 until arr.length()) {
                val j = arr.optJSONObject(i) ?: continue
                list.add(
                    WatchField(
                        key = j.optString("key", ""),
                        title = j.optString("title", ""),
                        unit = j.optString("unit", ""),
                        width = j.optInt("width", 80),
                        color = j.optString("color", "none"),
                        src = j.optString("src", "raw"),
                        rawIdx = j.optInt("rawIdx", -1),
                        fmt = j.optString("fmt", "num2"),
                        item = j.optString("item", ""),
                        group = j.optString("group", ""),
                        def = j.optBoolean("def", false),
                        avail = j.optBoolean("avail", true)
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 从 {code,msg,data} 里取出 data 对象 */
    private fun dataOf(raw: String): JSONObject? {
        return try {
            val o = JSONObject(raw)
            if (o.optInt("code", -1) == 0) o.optJSONObject("data") else null
        } catch (e: Exception) {
            null
        }
    }
    /** 解析服务端返回的 raw 数组（腾讯全量字段）为 List<String> */
    private fun parseRaw(arr: org.json.JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val list = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) list.add(arr.optString(i, ""))
        return list
    }

    /** 从 {code,msg,data:[...]} 里取出 data 数组（search.php / watchlist list 用） */
    private fun dataArrayOf(raw: String): List<StockItem> {
        return try {
            val o = JSONObject(raw)
            if (o.optInt("code", -1) != 0) return emptyList()
            val arr = o.optJSONArray("data") ?: return emptyList()
            val list = ArrayList<StockItem>()
            for (i in 0 until arr.length()) {
                val p = arr.optJSONObject(i) ?: continue
                list.add(
                    StockItem(
                        code = p.optString("code", ""),
                        name = p.optString("name", ""),
                        price = p.optString("price", "0").toDoubleOrNull() ?: 0.0,
                        change = p.optString("change", "0").toDoubleOrNull() ?: 0.0,
                        changePct = p.optString("changePct", "0").toDoubleOrNull() ?: 0.0,
                        amount = p.optString("amount", "0").toDoubleOrNull() ?: 0.0,
                        aucPct = p.optString("aucPct", "0").toDoubleOrNull() ?: 0.0,
                        aucAmount = p.optString("aucAmount", "0").toDoubleOrNull() ?: 0.0,
                        raw = parseRaw(p.optJSONArray("raw"))
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 通用请求：返回 (ok, msg) */
    private fun post(url: String, token: String?, vararg pairs: Pair<String, Any?>): Res {
        return try {
            val raw = Net.postJson(base(url), jsonOf(*pairs), token)
            val o = JSONObject(raw)
            Res(o.optInt("code", -1) == 0, o.optString("msg", ""))
        } catch (e: Exception) {
            Res(false, e.message ?: "网络异常")
        }
    }

    /** 拉取账户信息 */
    fun account(token: String?): AccountInfo {
        val raw = Net.get(base("account.php"), token)
        val d = dataOf(raw) ?: return AccountInfo()
        return AccountInfo(
            cash = d.optDouble("cash", 0.0),
            marketValue = d.optDouble("marketValue", 0.0),
            total = d.optDouble("total", 0.0),
            floatPn = d.optDouble("floatPnl", 0.0),
            totalPn = d.optDouble("totalPnl", 0.0),
            totalPnPct = d.optDouble("totalPnlPct", 0.0),
            initial = d.optDouble("initial", 0.0)
        )
    }

    /** 设置初始资金 */
    fun setInitial(token: String?, amount: Double): Res =
        post("account.php", token, "action" to "set_balance", "initial" to amount)

    /** 拉取会员信息 */
    fun member(token: String?): MemberInfo {
        val raw = Net.get(base("member.php"), token)
        val d = dataOf(raw) ?: return MemberInfo()
        val arr = d.optJSONArray("plans")
        val plans = ArrayList<MemberPlan>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val p = arr.optJSONObject(i) ?: continue
                plans.add(
                    MemberPlan(
                        name = p.optString("name", ""),
                        desc = p.optString("desc", ""),
                        price = p.optDouble("price", 0.0),
                        unit = p.optString("unit", ""),
                        hot = p.optBoolean("hot", false),
                        key = p.optString("key", "")
                    )
                )
            }
        }
        return MemberInfo(
            isMember = d.optBoolean("isMember", false),
            levelName = d.optString("levelName", "普通用户"),
            expireAt = d.optString("expireAt", ""),
            plans = plans
        )
    }

    /** 积分兑换会员 */
    fun exchangeMember(token: String?, plan: String): Res =
        post("exchange_member.php", token, "plan" to plan)

    /** 拉取积分信息 */
    fun point(token: String?): PointInfo {
        val raw = Net.get(base("point.php"), token)
        val d = dataOf(raw) ?: return PointInfo()
        return PointInfo(points = d.optInt("points", 0))
    }

    /** 拉取积分流水 */
    fun pointLogs(token: String?): List<PointLog> {
        val raw = Net.get(base("point.php?action=logs"), token)
        val d = dataOf(raw) ?: return emptyList()
        val arr = d.optJSONArray("logs") ?: return emptyList()
        val list = ArrayList<PointLog>()
        for (i in 0 until arr.length()) {
            val p = arr.optJSONObject(i) ?: continue
            list.add(
                PointLog(
                    desc = p.optString("desc", ""),
                    points = p.optInt("points", 0),
                    time = p.optString("time", "")
                )
            )
        }
        return list
    }

    /** 拉取邀请信息 */
    fun invite(token: String?): InviteInfo {
        val raw = Net.get(base("invite.php"), token)
        val d = dataOf(raw) ?: return InviteInfo()
        val arr = d.optJSONArray("users")
        val users = ArrayList<InvitedUser>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val p = arr.optJSONObject(i) ?: continue
                users.add(
                    InvitedUser(
                        username = p.optString("username", ""),
                        reward = p.optInt("reward", 0),
                        time = p.optString("time", "")
                    )
                )
            }
        }
        return InviteInfo(
            code = d.optString("code", ""),
            invitedCount = d.optInt("invitedCount", 0),
            totalReward = d.optInt("totalReward", 0),
            users = users
        )
    }

    /** 内置券商列表 */
    val BROKERS = listOf(
        Broker("htzq", "华泰证券"),
        Broker("zszq", "招商证券"),
        Broker("gjzq", "国泰君安"),
        Broker("zxzq", "中信证券"),
        Broker("gyzq", "国元证券"),
        Broker("dfzq", "东方证券"),
        Broker("xyzq", "兴业证券"),
        Broker("gfzq", "广发证券"),
        Broker("pa", "平安证券"),
        Broker("cjzq", "长江证券")
    )

    /** 拉取我的券商账户 */
    fun myBroker(token: String?): MyBroker {
        val raw = Net.get(base("broker.php"), token)
        val d = dataOf(raw) ?: return MyBroker()
        return MyBroker(
            broker = d.optString("broker", ""),
            account = d.optString("account", "")
        )
    }

    /** 保存券商账户 */
    fun saveBroker(token: String?, broker: String, account: String): Res =
        post("broker.php", token, "broker" to broker, "account" to account)

    /** 自选分组列表 */
    fun watchGroups(token: String?): List<WatchGroup> {
        val raw = Net.postJson(base("watch_group.php"), jsonOf("action" to "list"), token)
        val d = dataOf(raw) ?: return emptyList()
        val arr = d.optJSONArray("groups") ?: return emptyList()
        val list = ArrayList<WatchGroup>()
        for (i in 0 until arr.length()) {
            val p = arr.optJSONObject(i) ?: continue
            list.add(
                WatchGroup(
                    id = p.optInt("id", 0),
                    name = p.optString("name", ""),
                    sort = p.optInt("sort", 0)
                )
            )
        }
        return list
    }

    /** 新增分组 */
    fun addGroup(token: String?, name: String): Res =
        post("watch_group.php", token, "action" to "add", "name" to name)

    /** 分组改名 */
    fun renameGroup(token: String?, gid: Int, name: String): Res =
        post("watch_group.php", token, "action" to "rename", "gid" to gid, "name" to name)

    /** 删除分组 */
    fun delGroup(token: String?, gid: Int): Res =
        post("watch_group.php", token, "action" to "del", "gid" to gid)

    /** 加自选（到指定分组） */
    fun addWatch(token: String?, code: String, gid: Int): Res =
        post("watchlist.php", token, "action" to "add", "code" to code, "gid" to gid)

    /** 删除自选（gid<=0 时按 code 从所有分组全局删除） */
    fun delWatch(token: String?, code: String, gid: Int = 0): Res =
        post("watchlist.php", token, "action" to "del", "code" to code, "gid" to gid)

    /** 概念归属+涨跌幅（concept.php）。返回 best(优先展示的概念) 与 list(全部概念)。 */
    data class Concept(val name: String, val code: String, val pct: Double?, val price: Double?)
    data class ConceptRes(val ok: Boolean, val best: Concept?, val list: List<Concept>)
    fun concept(code: String): ConceptRes {
        return try {
            val raw = Net.get(base("concept.php?code=" + code))
            val o = JSONObject(raw)
            if (!o.optBoolean("ok", false)) return ConceptRes(false, null, emptyList())
            fun parse(c: JSONObject): Concept {
                val p = if (c.isNull("pct")) null else c.optDouble("pct")
                val pr = if (c.isNull("price")) null else c.optDouble("price")
                return Concept(c.optString("name"), c.optString("code"), p, pr)
            }
            val best = o.optJSONObject("best")?.let { parse(it) }
            val arr = o.optJSONArray("list")
            val lst = ArrayList<Concept>()
            if (arr != null) for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { lst.add(parse(it)) }
            }
            ConceptRes(true, best, lst)
        } catch (e: Exception) {
            ConceptRes(false, null, emptyList())
        }
    }
    /** 股票搜索（search.php）。kw 为空返回热门（最多 30 条）。无需登录。 */
    fun search(kw: String): List<StockItem> {
        return try {
            val raw = Net.get(base("search.php?kw=" + java.net.URLEncoder.encode(kw, "UTF-8")))
            dataArrayOf(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }
    /** 某分组下的自选股列表（watchlist.php?action=list&gid=X）。gid<=0 取默认分组。 */
    fun stockList(token: String?, gid: Int): List<StockItem> {
        return try {
            val url = base("watchlist.php?action=list&gid=" + gid)
            val raw = Net.get(url, token)
            dataArrayOf(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 拉取「所有分组」里的自选股代码集合（用于搜索页判断是否已加自选） */
    fun allWatchCodes(token: String?): Set<String> {
        val out = HashSet<String>()
        try {
            val groups = watchGroups(token)
            val gids = if (groups.isEmpty()) listOf(0) else groups.map { it.id }
            for (g in gids) {
                for (it in stockList(token, g)) {
                    if (it.code.isNotEmpty()) out.add(it.code)
                }
            }
        } catch (e: Exception) {
            // 忽略，返回已拿到的
        }
        return out
    }

    /** 修改密码 */
    fun changePwd(token: String?, oldPwd: String, newPwd: String): Res =
        post("user.php", token, "action" to "change_pwd", "old" to oldPwd, "new" to newPwd)

    /** 退出登录（服务端可无视，前端清理本地即可） */
    fun logout(token: String?): Res = post("logout.php", token)

    /**
     * 个股详情（detail.php）
     * @param code 6 位股票代码，如 "000980"
     * @param k    周期：day / week / month
     * @param kn   K线条数，默认 320
     * 无需登录。失败返回 null。
     */
    fun detail(code: String, k: String = "day", kn: Int = 320): StockDetail? {
        return try {
            val url = base("detail.php?code=") + code + "&k=" + k + "&kn=" + kn
            val raw = Net.get(url)
            val o = JSONObject(raw)
            if (!o.optBoolean("ok", false)) return null
            val b = o.optJSONObject("base") ?: JSONObject()
            val bas = StockBase(
                code = b.optString("code", code),
                name = b.optString("name", ""),
                price = b.optDouble("price", 0.0),
                preClose = b.optDouble("preClose", 0.0),
                open = b.optDouble("open", 0.0),
                high = b.optDouble("high", 0.0),
                low = b.optDouble("low", 0.0),
                change = if (b.has("chg")) b.optDouble("chg", 0.0) else b.optDouble("change", 0.0),
                changePct = if (b.has("chgPct")) b.optDouble("chgPct", 0.0) else b.optDouble("changePct", 0.0),
                volume = b.optDouble("volume", 0.0),
                amount = b.optDouble("amount", 0.0),
                turnover = b.optDouble("turnover", 0.0),
                volRatio = b.optDouble("volRatio", 0.0),
                mktCap = b.optDouble("mktCap", 0.0),
                floatCap = b.optDouble("floatCap", 0.0),
                pe = b.optDouble("pe", 0.0),
                pb = b.optDouble("pb", 0.0),
                amplitude = b.optDouble("amplitude", 0.0)
            )
            // 分时
            val trends = ArrayList<DetailTrend>()
            val tr = o.optJSONObject("trends")?.optJSONArray("points")
            if (tr != null) {
                for (i in 0 until tr.length()) {
                    val p = tr.optJSONArray(i) ?: continue
                    trends.add(
                        DetailTrend(
                            min = p.optInt(0, 0),
                            price = p.optDouble(1, 0.0),
                            vol = p.optDouble(2, 0.0),
                            amount = p.optDouble(3, 0.0)
                        )
                    )
                }
            }
            // 五日分时 trends5
            val days5 = ArrayList<DetailDay>()
            val t5 = o.optJSONArray("trends5")
            if (t5 != null) {
                for (di in 0 until t5.length()) {
                    val dj = t5.optJSONObject(di) ?: continue
                    val dpts = ArrayList<DetailTrend>()
                    val dp = dj.optJSONArray("points")
                    if (dp != null) {
                        for (i in 0 until dp.length()) {
                            val p = dp.optJSONArray(i) ?: continue
                            dpts.add(
                                DetailTrend(
                                    min = p.optInt(0, 0),
                                    price = p.optDouble(1, 0.0),
                                    vol = p.optDouble(2, 0.0),
                                    amount = p.optDouble(3, 0.0)
                                )
                            )
                        }
                    }
                    days5.add(DetailDay(date = dj.optString("date", ""), points = dpts))
                }
            }
            // 分时逐笔聚合 deals -> minuteDeals（key=分钟序号）
            val minuteDeals = HashMap<Int, MinuteDeal>()
            run {
                val dj = o.optJSONObject("deals")
                if (dj != null) {
                    val it = dj.keys()
                    while (it.hasNext()) {
                        val kk = it.next()
                        val mm = kk.toIntOrNull() ?: continue
                        val v = dj.optJSONObject(kk) ?: continue
                        minuteDeals[mm] = MinuteDeal(
                            m = v.optInt("m", mm),
                            hand = v.optDouble("hand", 0.0),
                            amt = v.optDouble("amt", 0.0),
                            buy = v.optDouble("buy", 0.0),
                            sell = v.optDouble("sell", 0.0),
                            lastHand = v.optDouble("lastHand", 0.0),
                            lastDir = v.optString("lastDir", "M")
                        )
                    }
                }
            }
            // K线
            var line = ArrayList<KLinePoint>()
            val ln = o.optJSONObject("line")?.optJSONArray("points")
            if (ln != null) {
                for (i in 0 until ln.length()) {
                    val p = ln.optJSONArray(i) ?: continue
                    line.add(
                        KLinePoint(
                            date = p.optString(0, ""),
                            open = p.optDouble(1, 0.0),
                            close = p.optDouble(2, 0.0),
                            high = p.optDouble(3, 0.0),
                            low = p.optDouble(4, 0.0),
                            vol = p.optDouble(5, 0.0),
                            amount = p.optDouble(6, 0.0)
                        )
                    )
                }
            }
            // ===== 主链路：同花顺 K线（第8列为真换手率，字段统一，实测可用） =====
            val market = o.optString("market", "")
            run {
                val pure = code.trim().let { if (it.length >= 6) it.takeLast(6) else it }
                val thsCode = when (k) { "week" -> "11"; "month" -> "21"; else -> "01" }
                // 优先取全历史（all.js 的 sortYear 逐年 {year}.js 拼接），失败回退 last.js 最近一段
                val ths = if (k == "day" || k == "week" || k == "month") {
                    parseThsKlineFull(pure, thsCode)
                } else ArrayList<KLinePoint>()
                if (ths.isNotEmpty()) {
                    line = ths
                } else {
                    val thsUrl = "https://d.10jqka.com.cn/v6/line/hs_" + pure + "/" + thsCode + "/last.js"
                    val rawThs = Net.getRaw(thsUrl, referer = "https://stockpage.10jqka.com.cn/")
                    val thsLast = parseThsKline(rawThs)
                    if (thsLast.isNotEmpty()) line = thsLast
                }
                // ===== 多源 fallback：同花顺拿不到数据时切东财 / 腾讯 / 新浪，保证 K 线稳定出数据 =====
                if (line.isEmpty()) {
                    line = fetchKlineFallback(market, pure, k)
                }
            }
            StockDetail(
                code = o.optString("code", code),
                name = o.optString("name", ""),
                market = o.optString("market", ""),
                k = o.optString("k", k),
                base = bas,
                trends = trends,
                trends5 = days5,
                line = line,
                minuteDeals = minuteDeals
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * K 线多源 fallback：东财接口不可用时，依次尝试腾讯 / 新浪，保证 K 线稳定出数据。
     * 返回为空表示全部源都失败（此时上层仍会显示「暂无K线数据」）。
     *
     * @param market "sh" / "sz"
     * @param pure   6 位纯代码
     * @param k      "day" / "week" / "month"
     */
    /**
     * 全历史取数：请求 all.js 拿 sortYear 年份清单 -> 逐年请求 {year}.js -> 拼接升序。
     * 同花顺逐年接口偶发 502，单年最多重试 3 次；个别年份彻底失败不影响整体。
     * 返回空表示整体失败（上层回退 last.js / 其它源）。
     *
     * @param pure    6 位纯代码，如 "000651"
     * @param thsCode 同花顺周期码："01"日 / "11"周 / "21"月
     */
    private fun parseThsKlineFull(pure: String, thsCode: String): ArrayList<KLinePoint> {
        return try {
            val referer = "https://stockpage.10jqka.com.cn/"
            val baseUrl = "https://d.10jqka.com.cn/v6/line/hs_" + pure + "/" + thsCode + "/"
            // 1) all.js 取 sortYear: [[year,count],...]（偶发 502，重试 3 次）
            var allRaw: String? = null
            for (attempt in 0 until 3) {
                allRaw = Net.getRaw(baseUrl + "all.js", referer = referer)
                if (allRaw != null && allRaw.contains("(")) break
            }
            val raw0: String = allRaw ?: return ArrayList()
            if (raw0.isEmpty()) return ArrayList()
            val a = raw0.indexOf('(')
            val b = raw0.lastIndexOf(')')
            if (a < 0 || b <= a) return ArrayList()
            val jo = JSONObject(raw0.substring(a + 1, b))
            val sortYear = jo.optJSONArray("sortYear") ?: return ArrayList()
            val years = ArrayList<Int>(sortYear.length())
            for (i in 0 until sortYear.length()) {
                val pair = sortYear.optJSONArray(i) ?: continue
                val y = pair.optInt(0, 0)
                if (y > 1900) years.add(y)
            }
            if (years.isEmpty()) return ArrayList()
            // 2) 逐年请求 {year}.js，带重试
            val all = LinkedHashMap<String, KLinePoint>()
            for (y in years) {
                var rows: ArrayList<KLinePoint>? = null
                for (attempt in 0 until 3) {
                    val rawY = Net.getRaw(baseUrl + y + ".js", referer = referer)
                    val r = parseThsKline(rawY)
                    if (r.isNotEmpty()) { rows = r; break }
                }
                if (rows != null) for (pt in rows) all[pt.date] = pt
            }
            if (all.isEmpty()) return ArrayList()
            // 3) 去重 + 日期升序
            val out = ArrayList(all.values).sortedWith(compareBy({ it.date }))
            android.util.Log.i("Api", "K线(同花顺全历史) $pure/$thsCode 成功: ${out.size} 根, ${out.firstOrNull()?.date}~${out.lastOrNull()?.date}")
            ArrayList(out)
        } catch (e: Exception) {
            android.util.Log.w("Api", "K线(同花顺全历史) 失败: ${e.message}")
            ArrayList()
        }
    }

    /**
     * 解析同花顺 K线 JSONP：quotebridge_v6_line_hs_xxx_01_last({...})
     * data 为分号分隔，每条：日期,开,高,低,收,成交量(股),成交额(元),换手率(%),,,,,
     * 第 8 列即真换手率；返回空列表表示解析失败。
     */
    private fun parseThsKline(raw: String?): ArrayList<KLinePoint> {
        if (raw.isNullOrEmpty()) return ArrayList()
        return try {
            val a = raw.indexOf('(')
            val b = raw.lastIndexOf(')')
            if (a < 0 || b <= a) return ArrayList()
            val jo = JSONObject(raw.substring(a + 1, b))
            val data = jo.optString("data", "")
            if (data.isEmpty()) return ArrayList()
            val rows = data.split(";")
            val out = ArrayList<KLinePoint>(rows.size)
            for (row in rows) {
                if (row.isEmpty()) continue
                val f = row.split(",")
                if (f.size < 8) continue
                out.add(
                    KLinePoint(
                        date = f[0],
                        open = f[1].toDoubleOrNull() ?: 0.0,
                        high = f[2].toDoubleOrNull() ?: 0.0,
                        low = f[3].toDoubleOrNull() ?: 0.0,
                        close = f[4].toDoubleOrNull() ?: 0.0,
                        vol = f[5].toDoubleOrNull() ?: 0.0,        // 股，与东财 f56 同口径
                        amount = (f[6].toDoubleOrNull() ?: 0.0) / 10000.0, // 元 -> 万元
                        turnover = f[7].toDoubleOrNull() ?: 0.0    // 换手率(%): 同花顺真值
                    )
                )
            }
            if (out.isNotEmpty()) android.util.Log.i("Api", "K线(同花顺) 成功: ${out.size} 根")
            out
        } catch (e: Exception) {
            android.util.Log.w("Api", "K线(同花顺) 解析失败: ${e.message}")
            ArrayList()
        }
    }

    private fun fetchKlineFallback(market: String, pure: String, k: String): ArrayList<KLinePoint> {
        val prefix = when (market) {
            "sh" -> "sh"
            "bj" -> "bj"
            "sz" -> "sz"
            else -> if (pure.startsWith("6") || pure.startsWith("5") || pure.startsWith("9")) "sh" else "sz"
        }
        val sym = prefix + pure
        // ---- 源 1：腾讯（字段顺序与东财一致，成交量为「手」）----
        try {
            val tK = when (k) { "week" -> "week"; "month" -> "month"; else -> "day" }
            val url = "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get?param=" +
                sym + "," + tK + ",,,1000,qfq"
            val raw = Net.getRaw(url)
            if (!raw.isNullOrEmpty()) {
                val data = JSONObject(raw).optJSONObject("data")?.optJSONObject(sym)
                // 键名：qfqday / qfqweek / qfqmonth（无复权时可能是 day/week/month，做兜底）
                val arr = data?.optJSONArray("qfq" + tK) ?: data?.optJSONArray(tK)
                if (arr != null && arr.length() > 0) {
                    val out = ArrayList<KLinePoint>(arr.length())
                    for (i in 0 until arr.length()) {
                        val row = arr.optJSONArray(i) ?: continue
                        if (row.length() < 6) continue
                        out.add(
                            KLinePoint(
                                date = row.optString(0, ""),
                                open = row.optString(1, "0").toDoubleOrNull() ?: 0.0,
                                close = row.optString(2, "0").toDoubleOrNull() ?: 0.0,
                                high = row.optString(3, "0").toDoubleOrNull() ?: 0.0,
                                low = row.optString(4, "0").toDoubleOrNull() ?: 0.0,
                                vol = row.optString(5, "0").toDoubleOrNull() ?: 0.0, // 手，与后端/东财同口径
                                amount = 0.0 // 腾讯 K 线无成交额字段，置 0（不影响 K 线绘制）
                            )
                        )
                    }
                    if (out.isNotEmpty()) {
                        android.util.Log.i("Api", "K线 fallback(腾讯) 成功: $sym $k ${out.size} 根")
                        return out
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("Api", "K线 fallback(腾讯) 失败: ${e.message}")
        }
        // ---- 源 2：新浪（字段顺序不同：day/open/high/low/close/volume，volume 单位为「股」需 ÷100 转手）----
        try {
            val scale = when (k) { "week" -> 1680; "month" -> 7200; else -> 240 }
            val url = "https://money.finance.sina.com.cn/quotes_service/api/json_v2.php/" +
                "CN_MarketData.getKLineData?symbol=" + sym + "&scale=" + scale + "&ma=no&datalen=1000"
            val raw = Net.getRaw(url)
            if (!raw.isNullOrEmpty()) {
                val arr = org.json.JSONArray(raw)
                if (arr.length() > 0) {
                    val out = ArrayList<KLinePoint>(arr.length())
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        out.add(
                            KLinePoint(
                                date = o.optString("day", ""),
                                open = o.optString("open", "0").toDoubleOrNull() ?: 0.0,
                                close = o.optString("close", "0").toDoubleOrNull() ?: 0.0,
                                high = o.optString("high", "0").toDoubleOrNull() ?: 0.0,
                                low = o.optString("low", "0").toDoubleOrNull() ?: 0.0,
                                vol = (o.optString("volume", "0").toDoubleOrNull() ?: 0.0) / 100.0, // 股 -> 手
                                amount = 0.0
                            )
                        )
                    }
                    if (out.isNotEmpty()) {
                        android.util.Log.i("Api", "K线 fallback(新浪) 成功: $sym $k ${out.size} 根")
                        return out
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("Api", "K线 fallback(新浪) 失败: ${e.message}")
        }
        android.util.Log.w("Api", "K线 fallback 全部失败: $sym $k（东财/腾讯/新浪均无数据）")
        return ArrayList()
    }

    /**
     * 五档盘口 + 成交明细（orderbook.php）
     * @param code 股票代码（6 位纯代码或带前缀均可）
     * @param p    逐笔页码，从 1 开始（每页 70 条），默认 1（最新一页）
     * @param n    返回逐笔条数，1~70，默认 20
     */
    fun orderbook(code: String, p: Int = 1, n: Int = 20): OrderBook? {
        return try {
            val url = base("orderbook.php?code=") + code + "&p=" + p + "&n=" + n
            val raw = Net.get(url)
            val o = JSONObject(raw)
            if (o.optInt("code", -1) != 0) return null
            val d = o.optJSONObject("data") ?: return null

            val bids = ArrayList<OrderLevel>()
            d.optJSONArray("bids")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val j = arr.optJSONObject(i) ?: continue
                    bids.add(OrderLevel(j.optDouble("price", 0.0), j.optDouble("vol", 0.0)))
                }
            }
            val asks = ArrayList<OrderLevel>()
            d.optJSONArray("asks")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val j = arr.optJSONObject(i) ?: continue
                    asks.add(OrderLevel(j.optDouble("price", 0.0), j.optDouble("vol", 0.0)))
                }
            }
            val deals = ArrayList<DealItem>()
            d.optJSONArray("deals")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val j = arr.optJSONObject(i) ?: continue
                    deals.add(
                        DealItem(
                            no = j.optInt("no", 0),
                            time = j.optString("time", ""),
                            price = j.optDouble("price", 0.0),
                            change = j.optDouble("change", 0.0),
                            vol = j.optDouble("vol", 0.0),
                            amount = j.optDouble("amount", 0.0),
                            dir = j.optString("dir", "M")
                        )
                    )
                }
            }
            OrderBook(
                code = d.optString("code", code),
                name = d.optString("name", ""),
                price = d.optDouble("price", 0.0),
                prevClose = d.optDouble("prevClose", 0.0),
                open = d.optDouble("open", 0.0),
                volume = d.optDouble("volume", 0.0),
                outer = d.optDouble("outer", 0.0),
                inner = d.optDouble("inner", 0.0),
                bids = bids,
                asks = asks,
                deals = deals,
                limitUp = d.optDouble("limitUp", 0.0),
                limitDown = d.optDouble("limitDown", 0.0),
                maxSealAmount = d.optDouble("maxSealAmount", 0.0)
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 盘后固定价格交易（科创板 688/689、创业板 300/301，15:05–15:30）。
     *
     * 数据源：腾讯分时 minute/query —— 其返回的「当日分时」尾部自带 15:00 之后
     * 的盘后分钟点（1506..1530，1 分钟 1 根），实测 5 个标识标的全部命中。
     * 每行格式固定为 4 列：`HHmm 价 累计量(手) 累计额(元)`，因此必须对相邻两条
     * 做差分才能得到「这一分钟」的量与额。
     *
     * @param code   6 位纯代码或带市场前缀（如 sh688981）均可
     * @param market "sh"/"sz"，为空时按代码首位推断
     * @return AfterHours（拿不到数据时 supported=false、minutes 为空）
     */
    fun afterHours(code: String, market: String = ""): AfterHours {
        // ---- 归一化代码 & 前缀 ----
        val pure = code.trim().let { if (it.length >= 6) it.takeLast(6) else it }
        if (pure.isEmpty()) return AfterHours()
        // 市场前缀：优先用调用方（detail.php 已按类型正确判定）传入的 market；
        // 兜底按代码首位推断，覆盖 股票/ETF/LOF/基金/B股/指数 全类型：
        //   沪：6(主板)/5(沪基金ETF)/9(沪B)/68(科创)  → sh
        //   深：0(主板)/3(创业板)/1(深基金ETF/LOF)/2(深B)/15/16/18 → sz
        // 市场前缀：优先用调用方（detail.php 已按类型正确判定）传入的 market；
        // 兜底按代码首位推断，覆盖 股票/ETF/LOF/基金/B股/指数 全类型：
        //   沪：6(主板)/5(沪基金ETF)/9(沪B)/68(科创) -> sh
        //   深：0(主板)/3(创业板)/1(深基金ETF/LOF)/2(深B) -> sz
        val prefix = when {
            market == "sh" || market == "sz" || market == "bj" -> market
            pure.startsWith("6") || pure.startsWith("5") || pure.startsWith("9") -> "sh"
            else -> "sz"
        }
        val sym = prefix + pure
        // 盘后固定价格交易已扩展至全部 A 股（沪深主板/科创/创业均可参与），
        // 不再按板块限制；是否有盘后成交完全以接口返回的分钟数据为准（无成交则 minutes 为空 → 不支持）。
        try {
            val url = "https://web.ifzq.gtimg.cn/appstock/app/minute/query?code=$sym"
            val raw = Net.getRaw(url, referer = "https://gu.qq.com/") ?: return AfterHours()
            val arr = JSONObject(raw)
                .optJSONObject("data")
                ?.optJSONObject(sym)
                ?.optJSONObject("data")
                ?.optJSONArray("data")
                ?: return AfterHours()
            if (arr.length() == 0) return AfterHours()

            // ---- 解析为原始行列表：[HHmm, price, 累计量(手), 累计额(元)] ----
            data class Raw(val hhmm: String, val price: Double, val cVol: Double, val cAmt: Double)
            val rows = ArrayList<Raw>(arr.length())
            for (i in 0 until arr.length()) {
                val line = arr.optString(i, "")
                if (line.isEmpty()) continue
                // 行内以空格分隔；个别源可能用制表符/多空格，统一按空白切分
                val f = line.trim().split(Regex("\\s+"))
                if (f.size < 4) continue
                val hhmm = f[0]
                val price = f[1].toDoubleOrNull() ?: continue
                val cVol = f[2].toDoubleOrNull() ?: 0.0
                val cAmt = f[3].toDoubleOrNull() ?: 0.0
                rows.add(Raw(hhmm, price, cVol, cAmt))
            }
            if (rows.isEmpty()) return AfterHours()

            // ---- 定位 15:00 的累计快照，作为差分的基准 ----
            var baseIdx = -1
            for (i in rows.indices) {
                if (rows[i].hhmm == "1500") { baseIdx = i; break }
            }
            // 无 1500 行（极少数情况）则退化为「最后一条 ≤1500 的」作基准
            if (baseIdx < 0) {
                for (i in rows.indices) {
                    if (rows[i].hhmm <= "1500") baseIdx = i
                }
            }
            if (baseIdx < 0) baseIdx = 0

            // ---- 差分：只取严格晚于 15:00 的盘后分钟点 ----
            val minutes = ArrayList<AfterMinute>()
            var prevVol = rows[baseIdx].cVol
            var prevAmt = rows[baseIdx].cAmt
            var lastPrice = rows[baseIdx].price
            for (i in (baseIdx + 1) until rows.size) {
                val r = rows[i]
                if (r.hhmm <= "1500") continue          // 仍在连续竞价内，跳过
                if (r.hhmm > "1530") break              // 超出盘后时段，结束
                val dv = (r.cVol - prevVol).coerceAtLeast(0.0)
                val da = (r.cAmt - prevAmt).coerceAtLeast(0.0)
                prevVol = r.cVol
                prevAmt = r.cAmt
                lastPrice = if (r.price > 0) r.price else lastPrice
                // 只保留「有成交」的分钟，界面柱状图与明细才不会出现一排空柱
                if (dv > 0) {
                    minutes.add(AfterMinute(time = r.hhmm, price = lastPrice, vol = dv, amt = da))
                }
            }

            if (minutes.isEmpty()) return AfterHours()

            // 盘后总量 = 15:30 累计 − 15:00 累计
            val endSnapshot = rows.last { it.hhmm <= "1530" }
            val endIdx = rows.indexOfLast { it.hhmm <= "1530" }
            val totalVol = (rows[endIdx].cVol - rows[baseIdx].cVol).coerceAtLeast(0.0)
            val totalAmt = (rows[endIdx].cAmt - rows[baseIdx].cAmt).coerceAtLeast(0.0)

            android.util.Log.i(
                "Api",
                "盘后固定价 $sym: 分钟点 ${minutes.size} 个, 总量 ${totalVol}手, 总额 ${totalAmt}元, 价 $lastPrice"
            )
            return AfterHours(
                price = lastPrice,
                totalVol = totalVol,
                totalAmt = totalAmt,
                minutes = minutes,
                supported = true
            )
        } catch (e: Exception) {
            android.util.Log.w("Api", "盘后固定价失败: $sym ${e.message}")
            return AfterHours()
        }
    }
}
