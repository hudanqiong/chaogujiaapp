package com.chaogujia.app

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject

/**
 * 后端接口封装。
 * 后端统一返回：{ "code":0, "msg":"...", "data": {...} }
 *  code==0 表示成功。
 */

/** 账户信息（account.php，按 broker_account_id 隔离） */
data class AccountInfo(
    val cash: Double = 0.0,          // 现金余额
    val available: Double = 0.0,     // 可用资金 = 现金 - 冻结
    val frozen: Double = 0.0,        // 冻结资金（挂单占用）
    val marketValue: Double = 0.0,   // 持仓市值
    val total: Double = 0.0,         // 总资产
    val floatPn: Double = 0.0,       // 浮动盈亏
    val dayPn: Double = 0.0,         // 当日参考盈亏（R50c）
    val totalPn: Double = 0.0,       // 总盈亏
    val totalPnPct: Double = 0.0,    // 总盈亏率 %
    val initial: Double = 0.0,       // 初始资金
    val monthBase: Double = 0.0      // R55h-22: 月基准(=初始资金+上月末累计盈亏)，当日参考盈亏%统一分母
)

/** 持仓明细（positions.php 的 data[]） */
data class PositionItem(
    val code: String = "",
    val name: String = "",
    val quantity: Int = 0,
    val avgCost: Double = 0.0,
    val price: Double = 0.0,
    val marketValue: Double = 0.0,
    val pnl: Double = 0.0,
    val pnlPct: Double = 0.0,
    val dayPnl: Double = 0.0,        // R51：当日盈亏（= (现价-昨收)×数量，Σ 后与顶部当日参考盈亏一致）
    val holdDays: Int? = null        // 第40轮：持仓交易日数（后端 holdDays；null=暂无）
)
/** 月度参考盈亏 - 单日数据点（pnl_history.php 的 data.points[]） */
data class PnlPoint(
    val date: String = "",           // yyyy-MM-dd
    val totalPnl: Double = 0.0,      // 总参考盈亏（元）
    val monthPnl: Double = 0.0,      // 月度参考盈亏（元）
    val totalPnlPct: Double = 0.0,   // 总盈亏率 %
    val shIndex: Double = 0.0,       // 上证指数
    val shPct: Double = 0.0,         // 上证涨跌幅 %
    val excess: Double = 0.0,        // 超额（总）我 - 上证 %
    val monthPct: Double = 0.0,      // 月度收益率 %（后端 month_pct，口径C）
    val monthExcess: Double = 0.0,   // 月度跑赢上证 %（后端 month_excess，口径C）
    val tradeIdx: Int = 0            // R55h-22h2：该点是当月第几个交易日（从1开始；0=未知/非交易日）
)
/** 月度参考盈亏 - 整体返回（pnl_history.php 的 data） */
data class PnlHistory(
    val month: String = "",          // yyyy-MM
    val monthTradeDays: Int = 0,     // 本月交易日总数（后端自动推算，用于横轴分格）
    val lastMonthEnd: Double = 0.0,  // 上月末基准
    val monthPct: Double = 0.0,      // 月度收益率 %（顶层，口径C）
    val monthExcess: Double = 0.0,   // 月度跑赢上证 %（顶层，口径C）
    val points: List<PnlPoint> = emptyList(),
    val today: PnlPoint? = null      // 今日实时点（可能为 null）
)
/** 下单结果（buy.php / sell.php 的 data）
 *  quick（方式A）：立即成交，字段 price/quantity/amount/commission/settle
 *  limit（方式B）：挂单，字段 orderId/status/mode/price/quantity/frozen */
data class TradeResult(
    val ok: Boolean = false,
    val msg: String = "",
    val price: Double = 0.0,
    val quantity: Int = 0,
    val amount: Double = 0.0,
    val commission: Double = 0.0,
    /** 买入：总支出；卖出：净收入 */
    val settle: Double = 0.0,
    /** 挂单模式返回：订单号 */
    val orderId: Int = 0,
    /** 挂单模式返回：filled / pending */
    val status: String = "",
    /** quick / limit */
    val mode: String = "",
    /** 挂单冻结金额（买入） */
    val frozen: Double = 0.0
)
/** 交易记录（trades.php 的 data[]） */
data class TradeRecord(
    val id: Int = 0,
    val code: String = "",
    val name: String = "",
    /** 1=买入 2=卖出 */
    val direction: Int = 0,
    val price: Double = 0.0,
    val quantity: Int = 0,
    val amount: Double = 0.0,
    val commission: Double = 0.0,
    val time: String = ""
)
/** 挂单/委托单（order_list.php 的 data[]） */
data class OrderItem(
    val id: Int = 0,
    val accountId: Int = 0,
    val code: String = "",
    val name: String = "",
    /** buy / sell */
    val type: String = "",
    /** 1=买入 2=卖出 */
    val direction: Int = 0,
    val price: Double = 0.0,
    val quantity: Int = 0,
    val frozen: Double = 0.0,
    /** pending / filled / cancelled / failed */
    val status: String = "",
    val filledPrice: Double = 0.0,
    val time: String = ""
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

/** 权限网关返回（my_permissions.php）—— App 端功能门禁依据 */
data class PermissionInfo(
    val isMember: Boolean = false,
    val level: String = "free",          // free / month / year / life
    val levelName: String = "普通用户",
    val expireAt: String = "",
    val perms: Set<String> = emptySet(), // 可用功能 key 集合（存 Set）
    val features: List<FeatureInfo> = emptyList()
) {
    /** 是否拥有某功能权限 */
    fun has(key: String): Boolean = perms.contains(key)
}

/** 功能字典项（features 表） */
data class FeatureInfo(
    val key: String = "",
    val name: String = "",
    val category: String = "",
    val sortOrder: Int = 0
)

/** 套餐（plans.php / pay_create.php） */
data class PayPlan(
    val key: String = "",
    val name: String = "",
    val price: String = "",
    val days: Int = 0,
    val desc: String = "",
    val hot: Boolean = false
)
/** 套餐+收款码（plans.php） */
data class PlansResp(
    val plans: List<PayPlan> = emptyList(),
    val qrcodeWechat: String = "",
    val qrcodeAlipay: String = ""
)
/** 订单（pay_create.php / pay_status.php） */
data class PayOrder(
    val id: Int = 0,
    val levelKey: String = "",
    val levelName: String = "",
    val amount: String = "",
    val days: Int = 0,
    val payMethod: String = "wechat",
    val qrcode: String = "",
    val status: String = "pending",
    val remark: String = "",
    val adminRemark: String = "",
    val createdAt: String = ""
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

/** 券商（key 用于映射 drawable/broker_<key>.png，无图时为 ""） */
data class Broker(
    val id: String = "",
    val name: String = "",
    val key: String = ""
)
/** 我的券商账户（多账户：id 为后端主键，key 用于映射 drawable/broker_<key>.png）
 *  cash/frozen/initial 为后端 broker.php list 随行返回的资金字段 */
data class MyBroker(
    val id: Int = 0,
    val broker: String = "",
    val account: String = "",
    val key: String = "",
    val cash: Double = 0.0,
    val frozen: Double = 0.0,
    val initial: Double = 0.0,
    val commRate: Double = 0.00008,
    val minComm: Double = 0.1,
    val exemptMin: Int = 0
) {
    /** 可用资金 = 现金 - 冻结 */
    val available: Double get() = cash - frozen
}
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

/** BST 买卖标记中的一笔成交（marks[].trades[] 元素） */
data class MarkTrade(
    val time: String = "",      // "HH:mm"
    val dir: String = "",       // B=买 S=卖
    val price: Double = 0.0,
    val qty: Double = 0.0       // 股数
)

/**
 * 某交易日的 BST 买卖标记（后端 _marks.php 的 compute_marks 输出）。
 *  - kind : B=只买 / S=只卖 / T=当日既买又卖
 *  - color: redB/greyB/blueS/greyS/T —— 前端按源码映射成颜色
 *  - above: true=画在该日K柱上方 / false=画在下方
 *  - avgPrice : 当日买卖均价
 *  - dayClose : 该日收盘价（kline.php 有真值；detail.php 可能为 0）
 *  - trades   : 当日逐笔明细（原始成交，用于分时打点/详情）
 */
data class TradeMark(
    val date: String = "",
    val kind: String = "",
    val color: String = "",
    val above: Boolean = false,
    val avgPrice: Double = 0.0,
    val dayClose: Double = 0.0,
    val trades: List<MarkTrade> = emptyList()
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
    val minuteDeals: Map<Int, MinuteDeal> = emptyMap(),
    /** BST 买卖标记（每个交易日至多一条）；来自 detail.php / kline.php 的 marks */
    val marks: List<TradeMark> = emptyList()
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
    val avgPrice: Double = 0.0,   // 当日均价（后端 avgPrice）
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

    /** 注册。inviteCode 为邀请人的邀请码（选填，填了则注册后绑定邀请关系） */
    fun register(username: String, password: String, inviteCode: String = ""): AuthResult {
        return try {
            val body = if (inviteCode.isBlank()) {
                jsonOf("username" to username, "password" to password)
            } else {
                jsonOf("username" to username, "password" to password, "invite_code" to inviteCode.trim())
            }
            val raw = Net.postJson(
                base("register.php"),
                body,
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

    /** 拉取月度参考盈亏（pnl_history.php ?broker_account_id=&month=）
     *  month 空则取当前月。返回 points（本月每交易日）+ today（今日实时） */
    fun pnlHistory(token: String?, brokerAccountId: Int, month: String = ""): PnlHistory {
        return try {
            var url = "pnl_history.php?broker_account_id=$brokerAccountId"
            if (month.isNotEmpty()) url += "&month=$month"
            val raw = Net.get(base(url), token)
            val o = JSONObject(raw)
            if (o.optInt("code", -1) != 0) return PnlHistory()
            val d = o.optJSONObject("data") ?: return PnlHistory()
            val list = ArrayList<PnlPoint>()
            val arr = d.optJSONArray("points")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val pt = arr.optJSONObject(i) ?: continue
                    list.add(
                        PnlPoint(
                            date = pt.optString("date", ""),
                            totalPnl = pt.optDouble("total_pnl", 0.0),
                            monthPnl = pt.optDouble("month_pnl", 0.0),
                            totalPnlPct = pt.optDouble("total_pnl_pct", 0.0),
                            shIndex = pt.optDouble("sh_index", 0.0),
                            shPct = pt.optDouble("sh_pct", 0.0),
                            excess = pt.optDouble("excess", 0.0),
                            monthPct = pt.optDouble("month_pct", 0.0),
                            monthExcess = pt.optDouble("month_excess", 0.0),
                            tradeIdx = pt.optInt("trade_idx", 0)
                        )
                    )
                }
            }
            val t = d.optJSONObject("today")
            val today = if (t != null && t.length() > 0) PnlPoint(
                date = t.optString("date", ""),
                totalPnl = t.optDouble("total_pnl", 0.0),
                monthPnl = t.optDouble("month_pnl", 0.0),
                totalPnlPct = t.optDouble("total_pnl_pct", 0.0),
                shIndex = t.optDouble("sh_index", 0.0),
                shPct = t.optDouble("sh_pct", 0.0),
                excess = t.optDouble("excess", 0.0),
                monthPct = t.optDouble("month_pct", 0.0),
                monthExcess = t.optDouble("month_excess", 0.0),
                tradeIdx = t.optInt("trade_idx", 0)
            ) else null
            PnlHistory(
                month = d.optString("month", ""),
                monthTradeDays = d.optInt("month_trade_days", 0),
                lastMonthEnd = d.optDouble("last_month_end", 0.0),
                monthPct = d.optDouble("month_pct", 0.0),
                monthExcess = d.optDouble("month_excess", 0.0),
                points = list,
                today = today
            )
        } catch (e: Exception) {
            PnlHistory()
        }
    }
    /** 拉取持仓列表（positions.php ?broker_account_id=） */
    fun positions(token: String?, brokerAccountId: Int): List<PositionItem> {
        return try {
            val raw = Net.get(base("positions.php?broker_account_id=$brokerAccountId"), token)
            val o = JSONObject(raw)
            if (o.optInt("code", -1) != 0) return emptyList()
            val arr = o.optJSONArray("data") ?: return emptyList()
            val list = ArrayList<PositionItem>()
            for (i in 0 until arr.length()) {
                val p = arr.optJSONObject(i) ?: continue
                list.add(
                    PositionItem(
                        code = p.optString("code", ""),
                        name = p.optString("name", ""),
                        quantity = p.optInt("quantity", 0),
                        avgCost = p.optDouble("avgCost", 0.0),
                        price = p.optDouble("price", 0.0),
                        marketValue = p.optDouble("marketValue", 0.0),
                        pnl = p.optDouble("pnl", 0.0),
                        pnlPct = p.optDouble("pnlPct", 0.0),
                        // R51：逐股当日盈亏（后端 dayPnl），列表「当日盈亏」列改用此字段
                        dayPnl = p.optDouble("dayPnl", 0.0),
                        // 第40轮修复：此前漏读 holdDays → 永远 null → 表格显示「--」
                        holdDays = if (p.has("holdDays") && !p.isNull("holdDays")) p.optInt("holdDays") else null
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }
    /** 买入（buy.php）
     *  mode=quick（方式A）：手填价即时成交，必传 price
     *  mode=limit（方式B）：按价挂单，命中即成交
     *  body {broker_account_id,code,name,quantity,mode,price} */
    fun buy(
        token: String?,
        brokerAccountId: Int,
        code: String,
        name: String,
        quantity: Int,
        mode: String = "quick",
        price: Double = 0.0
    ): TradeResult {
        return try {
            val raw = Net.postJson(
                base("buy.php"),
                jsonOf(
                    "broker_account_id" to brokerAccountId,
                    "code" to code,
                    "name" to name,
                    "quantity" to quantity,
                    "mode" to mode,
                    "price" to price
                ),
                token
            )
            val o = JSONObject(raw)
            val d = o.optJSONObject("data")
            TradeResult(
                ok = o.optInt("code", -1) == 0,
                msg = o.optString("msg", ""),
                price = d?.optDouble("price", 0.0) ?: 0.0,
                quantity = d?.optInt("quantity", 0) ?: 0,
                amount = d?.optDouble("amount", 0.0) ?: 0.0,
                commission = d?.optDouble("commission", 0.0) ?: 0.0,
                settle = d?.optDouble("totalCost", 0.0) ?: 0.0,
                orderId = d?.optInt("orderId", 0) ?: 0,
                status = d?.optString("status", "") ?: "",
                mode = d?.optString("mode", mode) ?: mode,
                frozen = d?.optDouble("frozen", 0.0) ?: 0.0
            )
        } catch (e: Exception) {
            TradeResult(false, e.message ?: "网络异常")
        }
    }
    /** 卖出（sell.php）
     *  mode=quick（方式A）：手填价即时成交，必传 price
     *  mode=limit（方式B）：按价挂单（不冻结资金，成交时二次校验持仓）
     *  body {broker_account_id,code,quantity,mode,price} */
    fun sell(
        token: String?,
        brokerAccountId: Int,
        code: String,
        quantity: Int,
        mode: String = "quick",
        price: Double = 0.0
    ): TradeResult {
        return try {
            val raw = Net.postJson(
                base("sell.php"),
                jsonOf(
                    "broker_account_id" to brokerAccountId,
                    "code" to code,
                    "quantity" to quantity,
                    "mode" to mode,
                    "price" to price
                ),
                token
            )
            val o = JSONObject(raw)
            val d = o.optJSONObject("data")
            TradeResult(
                ok = o.optInt("code", -1) == 0,
                msg = o.optString("msg", ""),
                price = d?.optDouble("price", 0.0) ?: 0.0,
                quantity = d?.optInt("quantity", 0) ?: 0,
                amount = d?.optDouble("amount", 0.0) ?: 0.0,
                commission = d?.optDouble("commission", 0.0) ?: 0.0,
                settle = d?.optDouble("netIncome", 0.0) ?: 0.0,
                orderId = d?.optInt("orderId", 0) ?: 0,
                status = d?.optString("status", "") ?: "",
                mode = d?.optString("mode", mode) ?: mode
            )
        } catch (e: Exception) {
            TradeResult(false, e.message ?: "网络异常")
        }
    }
    /** 交易记录（trades.php ?broker_account_id=） */
    fun trades(token: String?, brokerAccountId: Int): List<TradeRecord> {
        return try {
            val raw = Net.get(base("trades.php?broker_account_id=$brokerAccountId"), token)
            val o = JSONObject(raw)
            if (o.optInt("code", -1) != 0) return emptyList()
            val arr = o.optJSONArray("data") ?: return emptyList()
            val list = ArrayList<TradeRecord>()
            for (i in 0 until arr.length()) {
                val p = arr.optJSONObject(i) ?: continue
                list.add(
                    TradeRecord(
                        id = p.optInt("id", 0),
                        code = p.optString("code", ""),
                        name = p.optString("name", ""),
                        direction = p.optInt("direction", 0),
                        price = p.optDouble("price", 0.0),
                        quantity = p.optInt("quantity", 0),
                        amount = p.optDouble("amount", 0.0),
                        commission = p.optDouble("commission", 0.0),
                        time = p.optString("time", "")
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }
    /** 交易记录管理-列表（trade_edit.php action=list，POST JSON body） */
    fun tradeEditList(token: String?, brokerAccountId: Int): List<TradeRecord> {
        return try {
            val raw = Net.postJson(
                base("trade_edit.php"),
                jsonOf(
                    "action" to "list",
                    "broker_account_id" to brokerAccountId,
                    "token" to (token ?: "")
                ),
                token
            )
            val o = JSONObject(raw)
            if (o.optInt("code", -1) != 0) return emptyList()
            val arr = o.optJSONArray("data") ?: return emptyList()
            val list = ArrayList<TradeRecord>()
            for (i in 0 until arr.length()) {
                val p = arr.optJSONObject(i) ?: continue
                list.add(
                    TradeRecord(
                        id = p.optInt("id", 0),
                        code = p.optString("code", ""),
                        name = p.optString("name", ""),
                        direction = p.optInt("direction", 0),
                        price = p.optDouble("price", 0.0),
                        quantity = p.optInt("quantity", 0),
                        amount = p.optDouble("amount", 0.0),
                        commission = p.optDouble("fee", 0.0),
                        time = p.optString("time", "")
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }
    /** 交易记录管理-修改（trade_edit.php action=update，改日期+价格后整账户重放） */
    fun tradeUpdate(
        token: String?,
        brokerAccountId: Int,
        id: Int,
        price: Double,
        date: String
    ): TradeResult {
        return try {
            val raw = Net.postJson(
                base("trade_edit.php"),
                jsonOf(
                    "action" to "update",
                    "broker_account_id" to brokerAccountId,
                    "id" to id,
                    "price" to price,
                    "date" to date,
                    "token" to (token ?: "")
                ),
                token
            )
            val o = JSONObject(raw)
            val d = o.optJSONObject("data")
            TradeResult(
                ok = o.optInt("code", -1) == 0,
                msg = o.optString("msg", ""),
                settle = d?.optDouble("cash", 0.0) ?: 0.0
            )
        } catch (e: Exception) {
            TradeResult(false, e.message ?: "网络异常")
        }
    }
    /** 一键模拟：批量生成买卖T记录（simulate.php） */
    fun simulate(
        token: String?,
        brokerAccountId: Int,
        code: String,
        dateStart: String,
        dateEnd: String,
        mode: String,
        total: Double
    ): TradeResult {
        return try {
            val raw = Net.postJson(
                base("simulate.php"),
                jsonOf(
                    "broker_account_id" to brokerAccountId,
                    "code" to code,
                    "date_start" to dateStart,
                    "date_end" to dateEnd,
                    "mode" to mode,
                    "total" to total,
                    "token" to (token ?: "")
                ),
                token
            )
            val o = JSONObject(raw)
            val d = o.optJSONObject("data")
            TradeResult(
                ok = o.optInt("code", -1) == 0,
                msg = o.optString("msg", ""),
                quantity = d?.optInt("generated", 0) ?: 0,
                settle = d?.optDouble("cash", 0.0) ?: 0.0
            )
        } catch (e: Exception) {
            TradeResult(false, e.message ?: "网络异常")
        }
    }
    /** 拉取账户信息（account.php ?broker_account_id=） */
    fun account(token: String?, brokerAccountId: Int): AccountInfo {
        val raw = Net.get(base("account.php?broker_account_id=$brokerAccountId"), token)
        val d = dataOf(raw) ?: return AccountInfo()
        return AccountInfo(
            cash = d.optDouble("cash", 0.0),
            available = d.optDouble("available", 0.0),
            frozen = d.optDouble("frozen", 0.0),
            marketValue = d.optDouble("marketValue", 0.0),
            total = d.optDouble("total", 0.0),
            floatPn = d.optDouble("floatPnl", 0.0),
            dayPn = d.optDouble("dayPnl", 0.0),
            totalPn = d.optDouble("totalPnl", 0.0),
            totalPnPct = d.optDouble("totalPnlPct", 0.0),
            initial = d.optDouble("initial", 0.0),
            monthBase = d.optDouble("monthBase", 0.0)
        )
    }

    /** 设置初始资金（account.php action=set_balance，按账户） */
    fun setInitial(token: String?, brokerAccountId: Int, amount: Double): Res =
        post(
            "account.php", token,
            "action" to "set_balance",
            "broker_account_id" to brokerAccountId,
            "initial" to amount
        )

    /** 一键重置数据（reset.php）：清空当前账户全部持仓+交易记录，现金重置为初始资金（第43轮） */
    fun resetData(token: String?, brokerAccountId: Int): Res {
        return try {
            val raw = Net.postJson(
                base("reset.php"),
                jsonOf(
                    "broker_account_id" to brokerAccountId,
                    "token" to (token ?: "")
                ),
                token
            )
            val o = JSONObject(raw)
            Res(
                ok = o.optInt("code", -1) == 0,
                msg = o.optString("msg", "")
            )
        } catch (e: Exception) {
            Res(false, e.message ?: "网络异常")
        }
    }

    /** 挂单列表（order_list.php）
     *  status 默认 pending；传 "all" 取全部 */
    fun orderList(
        token: String?,
        brokerAccountId: Int,
        code: String = "",
        status: String = "pending"
    ): List<OrderItem> {
        return try {
            var url = "order_list.php?broker_account_id=$brokerAccountId&status=$status"
            if (code.isNotEmpty()) url += "&code=$code"
            val raw = Net.get(base(url), token)
            val o = JSONObject(raw)
            if (o.optInt("code", -1) != 0) return emptyList()
            val arr = o.optJSONArray("data") ?: return emptyList()
            val list = ArrayList<OrderItem>()
            for (i in 0 until arr.length()) {
                val p = arr.optJSONObject(i) ?: continue
                list.add(
                    OrderItem(
                        id = p.optInt("id", 0),
                        accountId = p.optInt("accountId", 0),
                        code = p.optString("code", ""),
                        name = p.optString("name", ""),
                        type = p.optString("type", ""),
                        direction = p.optInt("direction", 0),
                        price = p.optDouble("price", 0.0),
                        quantity = p.optInt("quantity", 0),
                        frozen = p.optDouble("frozen", 0.0),
                        status = p.optString("status", ""),
                        filledPrice = p.optDouble("filledPrice", 0.0),
                        time = p.optString("time", "")
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 撤单（order_cancel.php，body {order_id}） */
    fun orderCancel(token: String?, orderId: Int): Res =
        post("order_cancel.php", token, "order_id" to orderId)

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

    /** 券商列表缓存（首次调用从 brokers.php 动态拉取，之后复用） */
    @Volatile
    private var brokerCache: List<Broker>? = null

    /** 内置兜底券商列表（网络不可用时至少能选） */
    private val BROKERS_FALLBACK = listOf(
        Broker("htzq", "华泰证券", "htzq"),
        Broker("zszq", "招商证券", "zszq"),
        Broker("gjzq", "国泰君安", "gjzq"),
        Broker("zxzq", "中信证券", "zxzq"),
        Broker("gyzq", "国元证券", "gyzq"),
        Broker("dfzq", "东方证券", "dfzq"),
        Broker("xyzq", "兴业证券", "xyzq"),
        Broker("gfzq", "广发证券", "gfzq"),
        Broker("pa", "平安证券", "pa"),
        Broker("cjzq", "长江证券", "cjzq")
    )

    /** 按券商名反查图标 key（未收录时返回空串，前端走「圆圈+首字」兜底） */
    fun keyOfBroker(name: String): String {
        if (name.isEmpty()) return ""
        for (b in brokers()) {
            if (b.name == name) return b.key
        }
        return ""
    }
    /** 拉取券商名单（brokers.php，99 家，带缓存） */
    fun brokers(): List<Broker> {
        brokerCache?.let { return it }
        val list = try {
            val raw = Net.get(base("brokers.php"))
            val o = JSONObject(raw)
            if (o.optInt("code", -1) != 0) emptyList()
            else {
                val arr = o.optJSONArray("data") ?: org.json.JSONArray()
                val out = ArrayList<Broker>(arr.length())
                for (i in 0 until arr.length()) {
                    val j = arr.optJSONObject(i) ?: continue
                    out.add(
                        Broker(
                            id = j.optString("id", ""),
                            name = j.optString("name", ""),
                            key = j.optString("key", "")
                        )
                    )
                }
                out
            }
        } catch (e: Exception) {
            emptyList()
        }
        val result = if (list.isNotEmpty()) list else BROKERS_FALLBACK
        brokerCache = result
        return result
    }

    /** 兼容旧调用：内置券商列表（动态拉取） */
    val BROKERS: List<Broker> get() = brokers()

    /** 拉取我的全部券商账户（多账户，broker.php 默认 GET） */
    fun brokerList(token: String?): List<MyBroker> {
        return try {
            val raw = Net.get(base("broker.php"), token)
            val o = JSONObject(raw)
            if (o.optInt("code", -1) != 0) return emptyList()
            val d = o.optJSONObject("data") ?: return emptyList()
            val arr = d.optJSONArray("list") ?: return emptyList()
            val out = ArrayList<MyBroker>(arr.length())
            for (i in 0 until arr.length()) {
                val j = arr.optJSONObject(i) ?: continue
                val bname = j.optString("broker", "")
                out.add(
                    MyBroker(
                        id = j.optInt("id", 0),
                        broker = bname,
                        account = j.optString("account_no", ""),
                        key = keyOfBroker(bname),
                        cash = j.optDouble("cash", 0.0),
                        frozen = j.optDouble("frozen", 0.0),
                        initial = j.optDouble("initial", 0.0),
                        commRate = j.optDouble("commissionRate", 0.00008),
                        minComm = j.optDouble("minCommission", 0.1),
                        exemptMin = j.optInt("exemptMin", 0)
                    )
                )
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 新增券商账户（action=add，可自设初始资金 initial） */
    fun addBroker(
        token: String?,
        broker: String,
        account: String,
        initial: Double = 1_000_000.0,
        commissionRate: Double = 0.00008,
        minCommission: Double = 0.1,
        exemptMin: Int = 0
    ): Res = post(
        "broker.php", token,
        "action" to "add",
        "broker" to broker,
        "account_no" to account,
        "initial" to initial,
        "commission_rate" to commissionRate,
        "min_commission" to minCommission,
        "exempt_min" to exemptMin
    )

    /** 编辑券商账户（action=edit，按 id；券商名/账号/费用参数可改） */
    fun editBroker(
        token: String?,
        id: Int,
        broker: String,
        account: String,
        commissionRate: Double,
        minCommission: Double,
        exemptMin: Int
    ): Res = post(
        "broker.php", token,
        "action" to "edit",
        "id" to id,
        "broker" to broker,
        "account_no" to account,
        "commission_rate" to commissionRate,
        "min_commission" to minCommission,
        "exempt_min" to exemptMin
    )

    /** 删除券商账户（action=delete，按 id） */
    fun delBroker(token: String?, id: Int): Res =
        post("broker.php", token, "action" to "delete", "id" to id)

    /** 兼容旧接口：拉取我的券商账户（返回第一个） */
    fun myBroker(token: String?): MyBroker {
        val l = brokerList(token)
        return l.firstOrNull() ?: MyBroker()
    }
    /** 兼容旧接口：保存券商账户（旧版单账户语义，先清空再存） */
    fun saveBroker(token: String?, broker: String, account: String): Res =
        post("broker.php", token, "action" to "save", "broker" to broker, "account" to account)

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

    // ============ 权限网关（my_permissions.php，第15轮 批2D） ============
    /**
     * 拉取当前用户权限集合：可用功能 key 列表 + 功能字典。
     * 返回后 App 端存成 Set，用于各页面门禁判断（灰化/拦截）。
     * 未登录或异常 → 返回空权限（等同 free）。
     */
    fun permissions(token: String?): PermissionInfo {
        return try {
            val raw = Net.get(base("my_permissions.php"), token)
            val d = dataOf(raw) ?: return PermissionInfo()
            val permArr = d.optJSONArray("perms")
            val perms = linkedSetOf<String>()
            if (permArr != null) for (i in 0 until permArr.length()) perms.add(permArr.optString(i))
            val featArr = d.optJSONArray("features")
            val feats = ArrayList<FeatureInfo>()
            if (featArr != null) {
                for (i in 0 until featArr.length()) {
                    val f = featArr.optJSONObject(i) ?: continue
                    feats.add(
                        FeatureInfo(
                            key = f.optString("feature_key", ""),
                            name = f.optString("name", ""),
                            category = f.optString("category", ""),
                            sortOrder = f.optInt("sort_order", 0)
                        )
                    )
                }
            }
            PermissionInfo(
                isMember = d.optBoolean("isMember", false),
                level = d.optString("level", "free"),
                levelName = d.optString("levelName", "普通用户"),
                expireAt = d.optString("expireAt", ""),
                perms = perms,
                features = feats
            )
        } catch (e: Exception) {
            PermissionInfo()
        }
    }

    // ============ 用户反馈（feedback_submit.php，第15轮 批2D） ============
    /**
     * 上传单张图片（upload.php，multipart/form-data）。
     * @param type "feedback"（反馈截图）或 "proof"（支付凭证）
     * @return 服务器相对路径，如 "/stock_api/uploads/feedback/xxx.png"；失败返回 null
     */
    fun uploadImage(token: String?, file: java.io.File, type: String = "feedback"): String? {
        return try {
            val url = base("upload.php") + (if (token.isNullOrEmpty()) "" else "?token=" + java.net.URLEncoder.encode(token, "UTF-8"))
            val body = okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("type", type)
                .addFormDataPart(
                    "file", file.name,
                    okhttp3.RequestBody.create("image/*".toMediaTypeOrNull(), file)
                ).build()
                        val req = okhttp3.Request.Builder().url(url).post(body).apply {
                if (!token.isNullOrEmpty()) addHeader("Authorization", "Bearer $token")
            }.build()
            Net.rawClient.newCall(req).execute().use { resp ->
                val s = resp.body?.string() ?: return null
                val d = dataOf(s) ?: return null
                d.optString("url", "").ifEmpty { null }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 提交反馈（文字 + 图片 URL 列表）。
     * @param content 反馈正文
     * @param images 已上传图片的相对路径列表（可空）
     */
    fun submitFeedback(token: String?, content: String, images: List<String>): Res {
        return try {
            val arr = org.json.JSONArray()
            images.forEach { arr.put(it) }
            val json = JSONObject().apply {
                put("content", content)
                put("images", arr)
            }.toString()
            val raw = Net.postJson(base("feedback_submit.php"), json, token)
            val o = JSONObject(raw)
            Res(o.optInt("code", -1) == 0, o.optString("msg", ""))
        } catch (e: Exception) {
            Res(false, e.message ?: "网络异常")
        }
    }

    // ============ 会员开通 · 收款页（第15轮 批2D）============
    /**
     * 套餐列表 + 收款码（plans.php，可未登录）。失败返回空套餐。
     */
    fun plans(token: String?): PlansResp {
        return try {
            val raw = Net.get(base("plans.php"), token)
            val d = dataOf(raw) ?: return PlansResp()
            val arr = d.optJSONArray("plans")
            val list = ArrayList<PayPlan>()
            if (arr != null) for (i in 0 until arr.length()) {
                val p = arr.optJSONObject(i) ?: continue
                list.add(
                    PayPlan(
                        key = p.optString("key", ""),
                        name = p.optString("name", ""),
                        price = p.optString("price", ""),
                        days = p.optInt("days", 0),
                        desc = p.optString("desc", ""),
                        hot = p.optBoolean("hot", false)
                    )
                )
            }
            val qr = d.optJSONObject("qrcode")
            PlansResp(
                plans = list,
                qrcodeWechat = qr?.optString("wechat", "") ?: "",
                qrcodeAlipay = qr?.optString("alipay", "") ?: ""
            )
        } catch (e: Exception) {
            PlansResp()
        }
    }

    /**
     * 创建支付订单（pay_create.php）。返回含订单号 + 对应收款码的订单；失败返回 null。
     */
    fun payCreate(token: String?, plan: String, payMethod: String): PayOrder? {
        return try {
            val raw = Net.postJson(
                base("pay_create.php"),
                JSONObject().apply {
                    put("plan", plan)
                    put("pay_method", payMethod)
                }.toString(),
                token
            )
            val o = JSONObject(raw)
            if (o.optInt("code", -1) != 0) return null
            val d = o.optJSONObject("data") ?: return null
            PayOrder(
                id = d.optInt("orderId", 0),
                levelKey = d.optString("levelKey", ""),
                levelName = d.optString("levelName", ""),
                amount = d.optString("amount", ""),
                days = d.optInt("days", 0),
                qrcode = d.optString("qrcode", ""),
                payMethod = d.optString("payMethod", payMethod)
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 提交支付凭证（pay_submit_proof.php）。proofUrl 为 upload.php(type=proof) 返回的相对路径。
     */
    fun paySubmitProof(token: String?, orderId: Int, proofUrl: String, remark: String): Res =
        post("pay_submit_proof.php", token, "order_id" to orderId, "proof_url" to proofUrl, "remark" to remark)

    /**
     * 查询订单状态（pay_status.php）。orderId>0 查单笔，否则返回最近 50 笔。
     */
    fun payStatus(token: String?, orderId: Int = 0): List<PayOrder> {
        return try {
            val url = if (orderId > 0) base("pay_status.php?order_id=$orderId") else base("pay_status.php")
            val raw = Net.get(url, token)
            val d = dataOf(raw) ?: return emptyList()
            val out = ArrayList<PayOrder>()
            // 单笔：data 直接是订单对象
            if (orderId > 0) {
                out.add(
                    PayOrder(
                        id = d.optInt("id", 0),
                        levelKey = d.optString("level_key", ""),
                        amount = d.optString("amount", ""),
                        days = d.optInt("days", 0),
                        payMethod = d.optString("pay_method", "wechat"),
                        status = d.optString("status", "pending"),
                        adminRemark = d.optString("admin_remark", ""),
                        createdAt = d.optString("created_at", "")
                    )
                )
                return out
            }
            val arr = d.optJSONArray("orders") ?: return emptyList()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out.add(
                    PayOrder(
                        id = o.optInt("id", 0),
                        levelKey = o.optString("level_key", ""),
                        amount = o.optString("amount", ""),
                        days = o.optInt("days", 0),
                        payMethod = o.optString("pay_method", "wechat"),
                        status = o.optString("status", "pending"),
                        adminRemark = o.optString("admin_remark", ""),
                        createdAt = o.optString("created_at", "")
                    )
                )
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 个股详情（detail.php）
     * @param code 6 位股票代码，如 "000980"
     * @param k    周期：day / week / month
     * @param kn   K线条数，默认 320
     * @param token 登录 token（可选）。★ 必须带上：detail.php 的 marks（BST 买卖标记）
     *              依赖 current_user() 解析用户，不带 token 时 marks 恒为空 → K 线不画徽标。
     * 行情本身免登录，失败返回 null。
     */
    fun detail(code: String, k: String = "day", kn: Int = 1, token: String? = null, forceRefresh: Boolean = false): StockDetail? {
        return try {
            // 1) 先查本地磁盘缓存：命中的是「原始 JSON 字符串」，直接走同一套解析。
            //    这样 detail.php 冷请求 ~4.5s 的开销，在缓存有效期内（默认 5 分钟）被完全省掉。
            //    forceRefresh=true 时跳过缓存（用于"秒出缓存后后台静默刷新"）。
            val t0 = android.os.SystemClock.elapsedRealtime()
            val cachedRaw = if (forceRefresh) null else DetailCache.load(Net.appContext, code, k, kn)
            val raw: String
            val fromCache: Boolean
            if (cachedRaw != null) {
                raw = cachedRaw
                fromCache = true
            } else {
                fromCache = false
                val url = base("detail.php?code=") + code + "&k=" + k + "&kn=" + kn
                raw = Net.get(url, token)
                // 2) 请求成功（含 ok=true）才写缓存，避免把错误响应缓存下来。
                try {
                    if (JSONObject(raw).optBoolean("ok", false)) {
                        DetailCache.save(Net.appContext, code, k, kn, raw)
                    }
                } catch (_: Exception) { /* 解析失败则不缓存 */ }
            }
            android.util.Log.e(
                "CGJ_DTL",
                "code=$code k=$k kn=$kn force=$forceRefresh cached=$fromCache net=${if (fromCache) 0 else (android.os.SystemClock.elapsedRealtime() - t0)}ms ctx=${Net.appContext != null}"
            )
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
            // ===== BST 买卖标记（marks）：每交易日至多一条，共用一个聚合函数 =====
            // 注意：detail.php 的 marks.dayClose 可能为 0（其内部日期口径与内部 line 不一致）；
            // 若 dayClose<=0，用同日 line 的 close 兜底，保证画柱定位正确。
            // 注意：line 的 date 与 marks 的 date 口径可能不同（同花顺 "20260924" vs 后端 "2026-09-24"），
            // 必须归一化后再做同日期查表，否则兜底永远取不到值。
            val closeByDate = HashMap<String, Double>()
            for (p in line) if (p.date.isNotEmpty()) closeByDate[normDateKey(p.date)] = p.close
            val marks = ArrayList<TradeMark>()
            o.optJSONArray("marks")?.let { ma ->
                for (i in 0 until ma.length()) {
                    val m = ma.optJSONObject(i) ?: continue
                    val d = m.optString("date", "")
                    var dc = m.optDouble("dayClose", 0.0)
                    if (dc <= 0.0) dc = closeByDate[normDateKey(d)] ?: 0.0
                    val trs = ArrayList<MarkTrade>()
                    m.optJSONArray("trades")?.let { ta ->
                        for (j in 0 until ta.length()) {
                            val t = ta.optJSONObject(j) ?: continue
                            trs.add(
                                MarkTrade(
                                    time = t.optString("time", ""),
                                    dir = t.optString("dir", ""),
                                    price = t.optDouble("price", 0.0),
                                    qty = t.optDouble("qty", 0.0)
                                )
                            )
                        }
                    }
                    marks.add(
                        TradeMark(
                            date = d,
                            kind = m.optString("kind", ""),
                            color = m.optString("color", ""),
                            above = m.optBoolean("above", false),
                            avgPrice = m.optDouble("avgPrice", 0.0),
                            dayClose = dc,
                            trades = trs
                        )
                    )
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
                minuteDeals = minuteDeals,
                marks = marks
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
                avgPrice = d.optDouble("avgPrice", 0.0),
                limitUp = d.optDouble("limitUp", 0.0),
                limitDown = d.optDouble("limitDown", 0.0),
                maxSealAmount = d.optDouble("maxSealAmount", 0.0)
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 逐笔成交 —— 单页拉取（orderbook.php 的 deals 数组）。
     *
     * 后端分页规则（实测）：p 从 1 开始，p=1 为当天最早一页，p 越大越新；
     * 每页最多 70 条；越界（无数据）返回空数组。
     */
    fun orderbookDeals(code: String, p: Int, n: Int = 70): List<DealItem> {
        return try {
            val url = base("orderbook.php?code=") + code + "&p=" + p + "&n=" + n
            val raw = Net.get(url)
            val d = JSONObject(raw).optJSONObject("data") ?: return emptyList()
            val out = ArrayList<DealItem>()
            d.optJSONArray("deals")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val j = arr.optJSONObject(i) ?: continue
                    out.add(
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
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 探测当天逐笔的「最后一页」页码（= 最新一页）。
     *
     * p=1 最早；页号越大越新；越界返回空。
     * 先用指数探测（1,2,4,8,…）找到第一个空页，再在 (last, firstEmpty) 间二分，
     * 定位最后一个非空页。无任何成交时返回 0。
     */
    fun orderbookDealsLastPage(code: String): Int {
        if (orderbookDeals(code, 1, 1).isEmpty()) return 0
        var last = 1
        var step = 1
        var firstEmpty = 0
        while (step <= 64) {
            val p = last + step
            if (orderbookDeals(code, p, 1).isEmpty()) {
                firstEmpty = p
                break
            }
            last = p
            step *= 2
        }
        if (firstEmpty == 0) firstEmpty = last + 1
        var lo = last
        var hi = firstEmpty
        while (hi - lo > 1) {
            val mid = (lo + hi) / 2
            if (orderbookDeals(code, mid, 1).isEmpty()) hi = mid else lo = mid
        }
        return lo
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