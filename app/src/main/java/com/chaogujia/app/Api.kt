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
    val amount: Double = 0.0
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
    val line: List<KLinePoint> = emptyList()
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
                change = b.optDouble("change", 0.0),
                changePct = b.optDouble("changePct", 0.0),
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
            // ===== 方案 B：前端（手机）直连东财，获取全历史 + 成交额真值，覆盖后端 K 线 =====
            val market = o.optString("market", "")
            run {
                val emkt = if (market == "sh") "1" else "0"
                val pure = code.trim().let { if (it.length >= 6) it.takeLast(6) else it }
                val klt = when (k) { "week" -> 102; "month" -> 103; else -> 101 }
                val emUrl = "https://push2his.eastmoney.com/api/qt/stock/kline/get?secid=" +
                    emkt + "." + pure +
                    "&fields1=f1,f2,f3,f4,f5" +
                    "&fields2=f51,f52,f53,f54,f55,f56,f57,f58" +
                    "&klt=" + klt + "&fqt=1&beg=0&end=20500101&lmt=100000"
                val rawEm = Net.getRaw(emUrl)
                if (!rawEm.isNullOrEmpty()) {
                    val jo = JSONObject(rawEm)
                    val klines = jo.optJSONObject("data")?.optJSONArray("klines")
                    if (klines != null && klines.length() > 0) {
                        val em = ArrayList<KLinePoint>(klines.length())
                        for (i in 0 until klines.length()) {
                            val row = klines.optString(i, "")
                            if (row.isEmpty()) continue
                            val f = row.split(",")
                            if (f.size < 6) continue
                            em.add(
                                KLinePoint(
                                    date = f[0],
                                    open = f[1].toDoubleOrNull() ?: 0.0,
                                    close = f[2].toDoubleOrNull() ?: 0.0,
                                    high = f[3].toDoubleOrNull() ?: 0.0,
                                    low = f[4].toDoubleOrNull() ?: 0.0,
                                    vol = f[5].toDoubleOrNull() ?: 0.0,  // 与后端同口径（手）
                                    amount = (f.getOrNull(6)?.toDoubleOrNull() ?: 0.0) / 10000.0  // 元 -> 万元
                                )
                            )
                        }
                        if (em.isNotEmpty()) line = em
                    }
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
                line = line
            )
        } catch (e: Exception) {
            null
        }
    }
}