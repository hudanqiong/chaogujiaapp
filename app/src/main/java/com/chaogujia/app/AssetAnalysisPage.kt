package com.chaogujia.app

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

/**
 * 资产分析页 v1（第二十二轮）
 * 入口：交易页「月度参考盈亏」栏 → onOpenAssetAnalysis(brokerAccountId)。
 * 参考长图 12 段：顶部栏 / 盈亏头 / 周期Tab / 图例 / 曲线 / 风格 / 指数 / 日历 /
 * 月度个股盈亏 / 个股K线 / 操作分析 / 我的资产+期末资产。
 * 数据全走假盘动态接口；缺失项用 "--" 占位。
 */

/* ========== 视觉 token ========== */
private val AA_RED = Color(0xFFE23B3B)
private val AA_BLUE = Color(0xFF2A7BFF)
private val AA_SH = Color(0xFF648FCC)
private val AA_GRAY = Color(0xFF888888)
private val AA_MAIN = Color(0xFF222222)
private val AA_BG = Color(0xFFF5F6F8)
private val AA_DIV = Color(0xFFEEEEEE)
private val AA_CHIP_BG = Color(0xFFF2F3F5)
private val AAFont = FontFamily(Font(R.font.ths_regular))
private val AA_MA_COLORS = listOf(Color(0xFFE8A33D), Color(0xFF7A6FF0), Color(0xFF2AA198))

private fun aaMoney(v: Double): String = String.format(Locale.CHINA, "%,.2f", v)
private fun aaSigned(v: Double): String =
    if (abs(v) < 5e-3) String.format(Locale.CHINA, "%,.2f", 0.0)
    else String.format(Locale.CHINA, "%+,.2f", v)
private fun aaPct(v: Double): String = String.format(Locale.CHINA, "%+.2f%%", v)
private fun aaCompact(v: Double): String =
    if (abs(v) < 0.5) "0"
    else if (abs(v) >= 10000) String.format(Locale.CHINA, "%+.2f万", v / 10000)
    else String.format(Locale.CHINA, "%+,.0f", v)
private fun aaPnColor(v: Double): Color = when {
    v > 1e-9 -> AA_RED
    v < -1e-9 -> AA_BLUE
    else -> AA_GRAY
}
private fun aaCurMonth(): String = java.text.SimpleDateFormat("yyyy-MM", Locale.CHINA).format(java.util.Date())
private fun aaToday(): String = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(java.util.Date())
private fun aaMonthList(back: Int): List<String> {
    val out = ArrayList<String>()
    val fmt = java.text.SimpleDateFormat("yyyy-MM", Locale.CHINA)
    for (i in back downTo 0) {
        val c = Calendar.getInstance()
        c.add(Calendar.MONTH, -i)
        out.add(fmt.format(c.time))
    }
    return out
}
private fun aaMarkColor(c: String): Color = when (c) {
    "redB" -> Color(0xFFF06E6E)
    "blueS" -> Color(0xFF7DB2F3)
    "T" -> Color(0xFFFEA65E)
    else -> Color(0xFF949494)
}
private fun aaDaysBetween(a: String, b: String): Long = try {
    val f = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
    val d1 = f.parse(a)
    val d2 = f.parse(b)
    if (d1 == null || d2 == null) 0L else (d2.time - d1.time) / 86400000L
} catch (_: Exception) {
    0L
}
private fun fmtMa(v: Double): String = if (v.isNaN()) "--" else String.format(Locale.CHINA, "%.2f", v)

/* ========== 聚合模型（客户端近似，服务端后续可给真值） ========== */
private data class AAStockPnl(val code: String, val name: String, val pnl: Double)

private class AAReplay {
    var openCount = 0
    var closeCount = 0
    var winCount = 0
    var cycleDaysSum = 0L
    var cycleCount = 0
}

/** 按均价法回放交易：统计建仓/清仓次数、清仓胜率、平均持仓天数 */
private fun aaReplay(trades: List<TradeRecord>): AAReplay {
    val r = AAReplay()
    for ((_, list) in trades.sortedBy { it.time }.groupBy { it.code }) {
        var qty = 0
        var cost = 0.0
        var firstBuy = ""
        var realized = 0.0
        for (t in list) {
            if (t.direction == 1) {
                if (qty == 0) {
                    r.openCount++
                    firstBuy = t.time
                    realized = 0.0
                    cost = t.price
                } else if (t.quantity > 0) {
                    cost = (cost * qty + t.price * t.quantity) / (qty + t.quantity)
                }
                qty += t.quantity
            } else {
                val q = if (t.quantity > qty) qty else t.quantity
                if (q <= 0) continue
                realized += (t.price - cost) * q
                qty -= q
                if (qty == 0) {
                    r.closeCount++
                    r.cycleCount++
                    if (realized > 0) r.winCount++
                    r.cycleDaysSum += aaDaysBetween(firstBuy, t.time)
                }
            }
        }
    }
    return r
}

/** 月度个股盈亏聚合（近似）：持仓中取当前浮动盈亏，已清仓取卖出-买入 */
private fun aaMonthStocks(
    trades: List<TradeRecord>,
    positions: List<PositionItem>,
    monthPrefix: String
): List<AAStockPnl> {
    val out = ArrayList<AAStockPnl>()
    val byCode = LinkedHashMap<String, MutableList<TradeRecord>>()
    for (t in trades) {
        if (t.time.startsWith(monthPrefix)) byCode.getOrPut(t.code) { mutableListOf() }.add(t)
    }
    for ((code, list) in byCode) {
        val name = list.firstOrNull()?.name ?: code
        val buyAmt = list.filter { it.direction == 1 }.sumOf { it.amount }
        val sellAmt = list.filter { it.direction == 2 }.sumOf { it.amount }
        val netQty = list.sumOf { if (it.direction == 1) it.quantity else -it.quantity }
        val pos = positions.firstOrNull { it.code == code }
        val pnl = if (netQty > 0 && pos != null) pos.pnl else sellAmt - buyAmt
        out.add(AAStockPnl(code, name, pnl))
    }
    out.sortByDescending { it.pnl }
    return out
}

/** 做T统计：同日同股既有买又有卖算一次；卖均价 > 买均价算成功 */
private fun aaTStats(trades: List<TradeRecord>): Pair<Int, Double?> {
    val map = LinkedHashMap<String, MutableList<TradeRecord>>()
    for (t in trades) {
        val d = t.time.take(10)
        if (d.length < 10) continue
        map.getOrPut(t.code + " " + d) { mutableListOf() }.add(t)
    }
    var cnt = 0
    var win = 0
    for ((_, list) in map) {
        val buys = list.filter { it.direction == 1 }
        val sells = list.filter { it.direction == 2 }
        if (buys.isEmpty() || sells.isEmpty()) continue
        cnt++
        val bq = buys.sumOf { it.quantity }
        val sq = sells.sumOf { it.quantity }
        val bAvg = if (bq > 0) buys.sumOf { it.price * it.quantity } / bq else 0.0
        val sAvg = if (sq > 0) sells.sumOf { it.price * it.quantity } / sq else 0.0
        if (sq > 0 && sAvg > bAvg) win++
    }
    return cnt to (if (cnt > 0) win * 100.0 / cnt else null)
}

private data class AACalCell(
    val day: Int,
    val dateStr: String,
    val pnl: Double?,
    val isWeekend: Boolean,
    val isFuture: Boolean,
    val isHoliday: Boolean
)

/** 生成日历格（周日开头）：工作日无成交=休；周末/未来=空 */
private fun aaCalendar(month: String, dayPnl: Map<String, Double>, today: String): List<AACalCell?> {
    val ym = if (month.isBlank()) aaCurMonth() else month
    if (ym.length < 7) return emptyList()
    val y = ym.substring(0, 4).toIntOrNull() ?: return emptyList()
    val mo = (ym.substring(5, 7).toIntOrNull() ?: 0) - 1
    if (mo < 0) return emptyList()
    val cal = Calendar.getInstance()
    cal.set(y, mo, 1)
    val firstDow = cal.get(Calendar.DAY_OF_WEEK) // 1=周日
    val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val cells = ArrayList<AACalCell?>()
    for (i in 1 until firstDow) cells.add(null)
    for (d in 1..days) {
        val ds = String.format(Locale.CHINA, "%04d-%02d-%02d", y, mo + 1, d)
        cal.set(y, mo, d)
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val weekend = dow == Calendar.SATURDAY || dow == Calendar.SUNDAY
        val future = ds > today
        val p = dayPnl[ds]
        val holiday = !weekend && !future && p == null
        cells.add(AACalCell(d, ds, p, weekend, future, holiday))
    }
    return cells
}

/* ========== 主页面 ========== */
@Composable
fun AssetAnalysisPage(
    token: String,
    brokerAccountId: Int,
    perm: PermissionInfo,
    onClose: () -> Unit
) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("chaogujia", Context.MODE_PRIVATE) }
    var title by remember { mutableStateOf(prefs.getString("asset_title", "资产分析") ?: "资产分析") }
    var editingTitle by remember { mutableStateOf(false) }

    var loading by remember { mutableStateOf(true) }
    var reload by remember { mutableStateOf(0) }
    var pnlData by remember { mutableStateOf(PnlHistory()) }
    var acc by remember { mutableStateOf(AccountInfo()) }
    var positions by remember { mutableStateOf<List<PositionItem>>(emptyList()) }
    var trades by remember { mutableStateOf<List<TradeRecord>>(emptyList()) }
    var indices by remember { mutableStateOf<List<IndexQuote>>(emptyList()) }

    var period by remember { mutableStateOf("本月") }
    var chainPoints by remember { mutableStateOf<List<PnlPoint>>(emptyList()) }
    var chainMoney by remember { mutableStateOf(0.0) }
    var chainPct by remember { mutableStateOf(0.0) }
    var chainLoading by remember { mutableStateOf(false) }

    var legend by remember { mutableStateOf("上证") }
    var chartStyle by remember { mutableStateOf("曲线收益率") }
    var indicator by remember { mutableStateOf("收益率") }
    var chartChip by remember { mutableStateOf("") }

    var styleSel by remember { mutableStateOf("短线") }
    var idxPeriod by remember { mutableStateOf("日收益") }
    var calMode by remember { mutableStateOf("日历图") }
    var monthMode by remember { mutableStateOf("列表") }
    var showBS by remember { mutableStateOf(true) }

    var selectedCode by remember { mutableStateOf("") }
    var kline by remember { mutableStateOf<StockDetail?>(null) }
    var klineLoading by remember { mutableStateOf(false) }

    /* ===== 首批数据（并发） ===== */
    LaunchedEffect(brokerAccountId, reload) {
        loading = true
        withContext(Dispatchers.IO) {
            coroutineScope {
                val p1 = async { runCatching { Api.pnlHistory(token, brokerAccountId) }.getOrDefault(PnlHistory()) }
                val p2 = async { runCatching { Api.account(token, brokerAccountId) }.getOrDefault(AccountInfo()) }
                val p3 = async { runCatching { Api.positions(token, brokerAccountId) }.getOrDefault(emptyList()) }
                val p4 = async { runCatching { Api.trades(token, brokerAccountId) }.getOrDefault(emptyList()) }
                val p5 = async { runCatching { Api.indexQuotesBy("1.000001,0.399001,0.399006") }.getOrDefault(emptyList()) }
                pnlData = p1.await()
                acc = p2.await()
                positions = p3.await()
                trades = p4.await()
                indices = p5.await()
            }
        }
        loading = false
        if (selectedCode.isBlank()) {
            selectedCode = trades.lastOrNull()?.code
                ?: positions.firstOrNull { it.quantity > 0 }?.code
                ?: ""
        }
    }

    /* ===== 近三月 / 今年：多月拼接（链式复利） ===== */
    LaunchedEffect(period, pnlData.month) {
        if (period != "近三月" && period != "今年") {
            chainPoints = emptyList()
            chainMoney = 0.0
            chainPct = 0.0
            return@LaunchedEffect
        }
        chainLoading = true
        withContext(Dispatchers.IO) {
            val months = if (period == "近三月") aaMonthList(2)
            else aaMonthList(Calendar.getInstance().get(Calendar.MONTH))
            val out = ArrayList<PnlPoint>()
            var cum = 1.0
            var cumSh = 1.0
            var money = 0.0
            for (m in months) {
                val h = runCatching { Api.pnlHistory(token, brokerAccountId, m) }.getOrDefault(PnlHistory())
                if (h.points.isEmpty()) continue
                for (p in h.points) {
                    val fm = cum * (1 + p.monthPct / 100.0)
                    val fs = cumSh * (1 + p.shPct / 100.0)
                    out.add(p.copy(monthPct = (fm - 1) * 100.0, shPct = (fs - 1) * 100.0))
                }
                money += h.points.last().monthPnl
                val last = h.points.last()
                cum *= (1 + last.monthPct / 100.0)
                cumSh *= (1 + last.shPct / 100.0)
            }
            out.sortBy { it.date }
            chainPoints = out
            chainMoney = money
            chainPct = (cum - 1) * 100.0
        }
        chainLoading = false
    }

    /* ===== 个股K线 ===== */
    LaunchedEffect(selectedCode) {
        if (selectedCode.isBlank()) {
            kline = null
            return@LaunchedEffect
        }
        klineLoading = true
        kline = withContext(Dispatchers.IO) {
            runCatching { Api.detail(code = selectedCode, k = "day", kn = 120, token = token) }.getOrNull()
        }
        klineLoading = false
    }

    /* ===== 派生值 ===== */
    val lastPoint = pnlData.points.lastOrNull()
    val todayPt = pnlData.today
    val monthVal = todayPt?.monthPnl ?: lastPoint?.monthPnl ?: 0.0
    val monthPctV = todayPt?.monthPct ?: lastPoint?.monthPct ?: 0.0
    val monthExcessV = todayPt?.monthExcess ?: lastPoint?.monthExcess ?: 0.0
    val shPctV = todayPt?.shPct ?: lastPoint?.shPct ?: 0.0
    val totalVal = lastPoint?.totalPnl ?: 0.0
    val totalPctV = lastPoint?.totalPnlPct ?: 0.0

    var hLabel = "本月盈亏"
    var hValue = aaSigned(monthVal)
    var hPct = aaPct(monthPctV)
    var hColor = aaPnColor(monthVal)
    when (period) {
        "当日" -> {
            hLabel = "当日盈亏"
            hValue = aaSigned(acc.dayPn)
            hPct = aaPct(if (acc.monthBase > 0.0) acc.dayPn / acc.monthBase * 100.0 else 0.0)
            hColor = aaPnColor(acc.dayPn)
        }
        "近三月", "今年" -> {
            hLabel = period + "盈亏"
            if (!chainLoading) {
                hValue = aaSigned(chainMoney)
                hPct = aaPct(chainPct)
                hColor = aaPnColor(chainMoney)
            } else {
                hValue = "…"
                hPct = "…"
            }
        }
        "全部" -> {
            hLabel = "累计盈亏"
            hValue = aaSigned(totalVal)
            hPct = aaPct(totalPctV)
            hColor = aaPnColor(totalVal)
        }
        "自定义" -> {
            hLabel = "自定义区间"
            hValue = "--"
            hPct = "--"
            hColor = AA_GRAY
        }
    }

    val useTotal = period == "全部"
    val chartPts = if ((period == "近三月" || period == "今年") && chainPoints.isNotEmpty()) chainPoints else pnlData.points
    val chartHint: String? = if (period == "自定义") "自定义区间暂未接入历史数据，以下为本月走势" else null

    val dayPnlMap = remember(pnlData) {
        val m = LinkedHashMap<String, Double>()
        val pts = pnlData.points
        for (i in 1 until pts.size) m[pts[i].date] = pts[i].monthPnl - pts[i - 1].monthPnl
        m
    }
    val calCells = remember(pnlData.month, dayPnlMap) { aaCalendar(pnlData.month, dayPnlMap, aaToday()) }

    val monthPrefix = pnlData.month.ifBlank { aaCurMonth() }
    val monthItems = remember(trades, positions, monthPrefix) { aaMonthStocks(trades, positions, monthPrefix) }
    val monthTotal = monthVal
    val othersPnl = monthTotal - monthItems.sumOf { it.pnl }

    val replay = remember(trades) { aaReplay(trades) }
    val tStat = remember(trades) { aaTStats(trades) }
    val stockCount = remember(trades) { trades.map { it.code }.distinct().size }
    val avgHoldStr: String = run {
        if (replay.cycleCount > 0)
            String.format(Locale.CHINA, "%.0f", replay.cycleDaysSum.toDouble() / replay.cycleCount)
        else {
            val hs = positions.filter { it.quantity > 0 && (it.holdDays ?: 0) > 0 }.mapNotNull { it.holdDays }
            if (hs.isNotEmpty()) String.format(Locale.CHINA, "%.0f", hs.average()) else "--"
        }
    }
    val winRateStr = if (replay.closeCount > 0)
        String.format(Locale.CHINA, "%.2f%%", replay.winCount * 100.0 / replay.closeCount) else "--"
    val posPctStr = if (acc.total > 0)
        String.format(Locale.CHINA, "%.2f%%", acc.marketValue / acc.total * 100.0) else "--"
    val tCountStr = tStat.first.toString()
    val tWinStr = tStat.second?.let { String.format(Locale.CHINA, "%.2f%%", it) } ?: "--"
    val netInflow = acc.total - acc.initial - acc.totalPn

    fun idxVal(k: String): Pair<String, Color> = when (idxPeriod) {
        "日收益" -> {
            val q = indices.firstOrNull { it.name.contains(k) }
            if (q != null) aaPct(q.changePct) to aaPnColor(q.changePct) else "--" to AA_GRAY
        }
        "月收益" -> {
            if (k == "上证") aaPct(shPctV) to aaPnColor(shPctV) else "--" to AA_GRAY
        }
        else -> "--" to AA_GRAY
    }

    val isEmpty = !loading && pnlData.month.isBlank() && pnlData.points.isEmpty() &&
            trades.isEmpty() && positions.isEmpty() && acc.total <= 0.0

    Column(Modifier.fillMaxSize().background(AA_BG)) {
        /* ===== 顶部栏（返回 + 可编辑标题 + ?） ===== */
        Row(
            modifier = Modifier.fillMaxWidth().background(Color.White).height(48.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(44.dp).clickable { onClose() }, contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(R.drawable.ic_detail_back),
                    contentDescription = "返回",
                    modifier = Modifier.size(20.dp)
                )
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (editingTitle) {
                    BasicTextField(
                        value = title,
                        onValueChange = { title = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = AA_MAIN,
                            textAlign = TextAlign.Center,
                            fontFamily = AAFont
                        ),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                    )
                } else {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = AA_MAIN,
                        modifier = Modifier.pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = { editingTitle = true })
                        }
                    )
                }
            }
            if (editingTitle) {
                Box(Modifier.size(44.dp).clickable {
                    editingTitle = false
                    prefs.edit().putString("asset_title", title).apply()
                }, contentAlignment = Alignment.Center) {
                    Text("完成", fontSize = 13.sp, color = AA_RED)
                }
            } else {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(18.dp).border(1.dp, AA_GRAY, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("?", fontSize = 11.sp, color = AA_GRAY)
                    }
                }
            }
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AA_RED)
            }
            isEmpty -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("数据加载失败或账户暂无数据", fontSize = 13.sp, color = AA_GRAY)
                    Spacer(Modifier.height(12.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(AA_RED)
                            .clickable { reload++ }.padding(horizontal = 18.dp, vertical = 7.dp)
                    ) {
                        Text("点击重试", fontSize = 13.sp, color = Color.White)
                    }
                }
            }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                /* ===== [同花顺外壳] S1 盈亏卡 ===== */
                item {
                    AAYkCard(
                        label = hLabel,
                        value = hValue,
                        valueColor = hColor,
                        syncTime = "同步时间 " + (pnlData.today?.date?.let { aaShortTime(it) } ?: aaToday().substring(5)),
                        onClick = { }
                    )
                    Spacer(Modifier.height(8.dp))
                }
                /* ===== [同花顺外壳] S2 资金四宫格 ===== */
                item {
                    AAFourGrid(
                        totalAsset = if (acc.total > 0) aaMoney(acc.total) else "--",
                        totalAssetColor = AA_MAIN,
                        floatPnl = if (acc.total > 0) aaSigned(acc.totalPn) else "--",
                        floatPnlColor = if (acc.total > 0) aaPnColor(acc.totalPn) else AA_GRAY,
                        marketValue = if (acc.total > 0) aaMoney(acc.marketValue) else "--",
                        marketValueColor = AA_MAIN,
                        balance = if (acc.total > 0) aaMoney(acc.available) else "--",
                        balanceColor = AA_MAIN
                    )
                    Spacer(Modifier.height(8.dp))
                }
                /* ===== [同花顺外壳] S3 底部「资产分析」按钮 ===== */
                item {
                    AAAnalysisButton { }
                    Spacer(Modifier.height(8.dp))
                }
                /* ===== B2/B3 盈亏头 + 周期Tab ===== */
                item {
                    Column(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(hLabel, fontSize = 12.sp, color = AA_GRAY)
                            Spacer(Modifier.weight(1f))
                            Text("收益率 $hPct", fontSize = 12.sp, color = hColor, fontFamily = AAFont)
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(hValue, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = hColor, fontFamily = AAFont)
                            Spacer(Modifier.width(4.dp))
                            Text("元", fontSize = 11.sp, color = AA_GRAY)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth()) {
                            listOf("当日", "本月", "近三月", "今年", "全部", "自定义").forEach { p ->
                                Column(
                                    modifier = Modifier.weight(1f).clickable { period = p },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        p,
                                        fontSize = 12.sp,
                                        color = if (period == p) AA_RED else AA_MAIN,
                                        fontWeight = if (period == p) FontWeight.Bold else FontWeight.Normal
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Box(
                                        Modifier.height(2.dp).width(20.dp)
                                            .background(if (period == p) AA_RED else Color.Transparent, RoundedCornerShape(1.dp))
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                /* ===== B4 图例 + 曲线/K线切换 ===== */
                item {
                    Column(Modifier.fillMaxWidth().background(Color.White).padding(vertical = 8.dp)) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("上证", "深证", "创业板", "上证50", "沪深300", "中证500").forEach { lg ->
                                AAChip(lg, legend == lg) { legend = lg }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            AAChip("曲线收益率", chartStyle == "曲线收益率") { chartStyle = "曲线收益率" }
                            Spacer(Modifier.width(6.dp))
                            AAChip("K线收益率", chartStyle == "K线收益率") { chartStyle = "K线收益率" }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                /* ===== B5 曲线卡（指标切换 + 图 + 四 chips） ===== */
                item {
                    AACard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AASectionTitle("盈亏走势")
                            Spacer(Modifier.weight(1f))
                            listOf("收益率", "盈亏金额", "总资产").forEach { ind ->
                                AAChip(ind, indicator == ind) { indicator = ind }
                                Spacer(Modifier.width(4.dp))
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        AAChartView(
                            points = chartPts,
                            indicator = indicator,
                            style = chartStyle,
                            monthBase = pnlData.monthBase,
                            useTotal = useTotal
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("仓位分析", "极值分析", "最大回撤", "标记买卖点").forEach { c ->
                                AAChip(c, chartChip == c) { chartChip = if (chartChip == c) "" else c }
                            }
                        }
                        if (chartHint != null) {
                            Spacer(Modifier.height(6.dp))
                            Text(chartHint, fontSize = 10.sp, color = AA_GRAY)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                /* ===== B6 风格 + 跑赢/跑输 + 我/上证 ===== */
                item {
                    AACard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("我的交易风格", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AA_MAIN)
                            Spacer(Modifier.weight(1f))
                            Text("立即查看 ›", fontSize = 11.sp, color = AA_RED)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("长线", "短线", "中长线").forEach { s ->
                                AAChip(s, styleSel == s) { styleSel = s }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("本月" + (if (monthExcessV >= 0) "跑赢" else "跑输") + "上证指数 ", fontSize = 12.sp, color = AA_MAIN)
                            Text(aaPct(monthExcessV), fontSize = 12.sp, color = aaPnColor(monthExcessV), fontFamily = AAFont)
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("我 ", fontSize = 11.sp, color = AA_GRAY)
                            Text(aaPct(monthPctV), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = aaPnColor(monthPctV), fontFamily = AAFont)
                            Spacer(Modifier.width(24.dp))
                            Text("上证 ", fontSize = 11.sp, color = AA_GRAY)
                            Text(aaPct(shPctV), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = aaPnColor(shPctV), fontFamily = AAFont)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                /* ===== B7 三指数卡 ===== */
                item {
                    AACard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AASectionTitle("指数")
                            Spacer(Modifier.weight(1f))
                            listOf("日收益", "月收益", "年收益", "阶段收益").forEach { p ->
                                AAChip(p, idxPeriod == p) { idxPeriod = p }
                                Spacer(Modifier.width(4.dp))
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth()) {
                            AAAIndexCell("上证", idxVal("上证"), Modifier.weight(1f))
                            AAAIndexCell("深证", idxVal("深证"), Modifier.weight(1f))
                            AAAIndexCell("创业板", idxVal("创业"), Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                /* ===== B8 日历卡 ===== */
                item {
                    AACard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AAChip("日历图", calMode == "日历图") { calMode = "日历图" }
                            Spacer(Modifier.width(6.dp))
                            AAChip("柱状图", calMode == "柱状图") { calMode = "柱状图" }
                            Spacer(Modifier.weight(1f))
                            val ym = pnlData.month.ifBlank { aaCurMonth() }
                            val ymText = ym.substring(0, 4) + "年" + (ym.substring(5).toIntOrNull() ?: 0) + "月 ▾"
                            Text(ymText, fontSize = 11.sp, color = AA_GRAY)
                        }
                        Spacer(Modifier.height(8.dp))
                        if (calMode == "日历图") {
                            Row(Modifier.fillMaxWidth()) {
                                listOf("日", "一", "二", "三", "四", "五", "六").forEach { d ->
                                    Text(d, Modifier.weight(1f), fontSize = 10.sp, color = AA_GRAY, textAlign = TextAlign.Center)
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            calCells.chunked(7).forEach { week ->
                                Row(Modifier.fillMaxWidth()) {
                                    week.forEach { cell -> AACalCellView(cell, Modifier.weight(1f)) }
                                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("休 = 非交易日（无成交记录）", fontSize = 9.sp, color = AA_GRAY)
                        } else {
                            AACalBars(calCells)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                /* ===== B9 月度个股盈亏 ===== */
                item {
                    AACard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AASectionTitle("月度个股盈亏")
                            Spacer(Modifier.weight(1f))
                            AAChip("图表", monthMode == "图表") { monthMode = "图表" }
                            Spacer(Modifier.width(6.dp))
                            AAChip("列表", monthMode == "列表") { monthMode = "列表" }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text((monthPrefix.substring(5).toIntOrNull() ?: 0).toString() + "月累计收益 ", fontSize = 11.sp, color = AA_GRAY)
                            Text(aaSigned(monthTotal), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = aaPnColor(monthTotal), fontFamily = AAFont)
                            Spacer(Modifier.weight(1f))
                            Text("上证 " + aaPct(shPctV), fontSize = 11.sp, color = aaPnColor(shPctV), fontFamily = AAFont)
                        }
                        Spacer(Modifier.height(8.dp))
                        if (monthMode == "列表") {
                            monthItems.forEachIndexed { i, it0 ->
                                Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier.size(16.dp).clip(CircleShape)
                                            .background(if (i < 3) AA_RED else Color(0xFFD8D8D8)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("${i + 1}", fontSize = 9.sp, color = Color.White)
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(it0.name, fontSize = 13.sp, color = AA_MAIN)
                                        Text(it0.code, fontSize = 10.sp, color = AA_GRAY)
                                    }
                                    Text(aaSigned(it0.pnl), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = aaPnColor(it0.pnl), fontFamily = AAFont)
                                }
                            }
                            Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                                Spacer(Modifier.width(24.dp))
                                Text("其他收益", fontSize = 12.sp, color = AA_GRAY, modifier = Modifier.weight(1f))
                                Text(aaSigned(othersPnl), fontSize = 13.sp, color = aaPnColor(othersPnl), fontFamily = AAFont)
                            }
                        } else {
                            val maxAbs = monthItems.maxOfOrNull { abs(it.pnl) } ?: 1.0
                            monthItems.forEach { it0 ->
                                Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        it0.name, fontSize = 11.sp, color = AA_MAIN,
                                        modifier = Modifier.width(72.dp), maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                    Box(Modifier.weight(1f).height(10.dp)) {
                                        val frac = (abs(it0.pnl) / maxAbs).toFloat().coerceIn(0.02f, 1f)
                                        Box(
                                            Modifier.fillMaxWidth(frac).height(10.dp)
                                                .clip(RoundedCornerShape(5.dp)).background(aaPnColor(it0.pnl))
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        aaSigned(it0.pnl), fontSize = 11.sp, color = aaPnColor(it0.pnl),
                                        fontFamily = AAFont, modifier = Modifier.width(76.dp), textAlign = TextAlign.End
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                /* ===== B10 个股K线 ===== */
                item {
                    AACard {
                        val base = kline?.base
                        val nm = kline?.name ?: (monthItems.firstOrNull { it.code == selectedCode }?.name ?: "个股K线")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(nm, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AA_MAIN)
                            Spacer(Modifier.width(8.dp))
                            if (base != null && base.price > 0) {
                                Text(aaMoney(base.price), fontSize = 15.sp, color = aaPnColor(base.change), fontFamily = AAFont)
                                Spacer(Modifier.width(6.dp))
                                Text(String.format(Locale.CHINA, "%+.2f", base.change), fontSize = 11.sp, color = aaPnColor(base.change), fontFamily = AAFont)
                                Spacer(Modifier.width(4.dp))
                                Text(aaPct(base.changePct), fontSize = 11.sp, color = aaPnColor(base.changePct), fontFamily = AAFont)
                            }
                            Spacer(Modifier.weight(1f))
                            Box(
                                Modifier.border(1.dp, if (showBS) AA_RED else AA_DIV, RoundedCornerShape(50))
                                    .clickable { showBS = !showBS }.padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text("BS点分析", fontSize = 10.sp, color = if (showBS) AA_RED else AA_GRAY)
                            }
                            Spacer(Modifier.width(6.dp))
                            Box(Modifier.border(1.dp, AA_DIV, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                                Text("交易记录", fontSize = 10.sp, color = AA_GRAY)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        val line = (kline?.line ?: emptyList()).takeLast(60)
                        if (line.isNotEmpty()) {
                            val closes = line.map { it.close }
                            val maAt = { n: Int -> if (closes.size >= n) closes.takeLast(n).average() else Double.NaN }
                            Row {
                                Text("MA5 " + fmtMa(maAt(5)), fontSize = 9.sp, color = AA_MA_COLORS[0], fontFamily = AAFont)
                                Spacer(Modifier.width(8.dp))
                                Text("MA10 " + fmtMa(maAt(10)), fontSize = 9.sp, color = AA_MA_COLORS[1], fontFamily = AAFont)
                                Spacer(Modifier.width(8.dp))
                                Text("MA30 " + fmtMa(maAt(30)), fontSize = 9.sp, color = AA_MA_COLORS[2], fontFamily = AAFont)
                                Spacer(Modifier.weight(1f))
                                Text(line.last().date, fontSize = 9.sp, color = AA_GRAY)
                            }
                            Spacer(Modifier.height(6.dp))
                            AAKLineView(line = line, marks = if (showBS) (kline?.marks ?: emptyList()) else emptyList())
                            Spacer(Modifier.height(6.dp))
                        } else {
                            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                                Text(if (klineLoading) "K线加载中…" else "暂无K线数据", fontSize = 12.sp, color = AA_GRAY)
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                        val pos = positions.firstOrNull { it.code == selectedCode && it.quantity > 0 }
                        Row(Modifier.fillMaxWidth()) {
                            Text("持仓股数 ", fontSize = 11.sp, color = AA_GRAY)
                            Text(if (pos != null) pos.quantity.toString() else "--", fontSize = 11.sp, color = AA_MAIN, fontFamily = AAFont)
                            Spacer(Modifier.width(24.dp))
                            Text("当日盈亏 ", fontSize = 11.sp, color = AA_GRAY)
                            val dayPnl = if (pos != null && base != null && base.preClose > 0)
                                pos.quantity * (base.price - base.preClose) else null
                            Text(
                                if (dayPnl != null) aaSigned(dayPnl) else "--",
                                fontSize = 11.sp,
                                color = if (dayPnl != null) aaPnColor(dayPnl) else AA_GRAY,
                                fontFamily = AAFont
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                /* ===== B11 操作分析 ===== */
                item {
                    AACard {
                        AASectionTitle("操作分析")
                        Spacer(Modifier.height(8.dp))
                        Text("操作统计", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AA_MAIN)
                        Spacer(Modifier.height(6.dp))
                        val opItems = listOf(
                            "交易股票数" to stockCount.toString(),
                            "平均持仓天数" to avgHoldStr,
                            "建清仓次数" to (replay.openCount + replay.closeCount).toString(),
                            "交易成功率" to winRateStr,
                            "平均仓位" to posPctStr,
                            "资金周转率" to "--",
                            "打板次数" to "--",
                            "封板率" to "--",
                            "次日高开率" to "--",
                            "次日平均涨幅" to "--"
                        )
                        opItems.chunked(2).forEach { row ->
                            Row(Modifier.fillMaxWidth()) {
                                AAStat(row[0].first, row[0].second, if (row[0].second == "--") AA_GRAY else AA_MAIN, 14, Modifier.weight(1f))
                                if (row.size > 1) {
                                    AAStat(row[1].first, row[1].second, if (row[1].second == "--") AA_GRAY else AA_MAIN, 14, Modifier.weight(1f))
                                } else {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(AA_DIV))
                        Spacer(Modifier.height(10.dp))
                        Text("账户表现", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AA_MAIN)
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth()) {
                            AAStat("做T次数", tCountStr, AA_MAIN, 14, Modifier.weight(1f))
                            AAStat("做T成功率", tWinStr, if (tWinStr == "--") AA_GRAY else AA_MAIN, 14, Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                /* ===== B12a 我的资产 ===== */
                item {
                    AACard {
                        AASectionTitle("我的资产")
                        Spacer(Modifier.height(8.dp))
                        AARow("银证转入", "--", AA_GRAY)
                        AARow("银证转出", "--", AA_GRAY)
                        AARow("期初资产", aaMoney(acc.initial), AA_RED)
                        AARow("净流入", aaSigned(netInflow), aaPnColor(netInflow))
                        AARow("账户盈亏", aaSigned(acc.totalPn), aaPnColor(acc.totalPn))
                    }
                    Spacer(Modifier.height(8.dp))
                }

                /* ===== B12b 期末资产 ===== */
                item {
                    AACard {
                        Row(Modifier.fillMaxWidth()) {
                            AAStat("期初资产", aaMoney(acc.initial), AA_MAIN, 13, Modifier.weight(1f))
                            AAStat("净流入", aaSigned(netInflow), aaPnColor(netInflow), 13, Modifier.weight(1f))
                            AAStat("账户盈亏", aaSigned(acc.totalPn), aaPnColor(acc.totalPn), 13, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(12.dp))
                        Box(Modifier.fillMaxWidth().height(1.dp).background(AA_DIV))
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("期末资产", fontSize = 13.sp, color = AA_MAIN)
                            Spacer(Modifier.weight(1f))
                            Text(aaMoney(acc.total), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = AA_RED, fontFamily = AAFont)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("免责声明：本页数据来自模拟交易账户的实时统计，仅供学习研究参考，不构成任何投资建议。", fontSize = 10.sp, color = AA_GRAY)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Text("水印设置 ›", fontSize = 11.sp, color = AA_GRAY)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

/* ========== [同花顺外壳] 视觉组件 ========== */
/** 盈亏卡：label + 数值 +（可选）问号 + 同步时间 + 右箭头。背景 #F5F5F5。 */
@Composable
private fun AAYkCard(
    label: String,
    value: String,
    valueColor: Color,
    syncTime: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF5F5F5))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = AA_GRAY, fontSize = 13.sp)
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier.size(13.dp)
                    .clip(CircleShape)
                    .border(1.dp, AA_GRAY, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("?", color = AA_GRAY, fontSize = 9.sp, fontFamily = AAFont)
            }
            Spacer(Modifier.weight(1f))
            Text(syncTime, color = AA_GRAY, fontSize = 10.sp)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                color = valueColor,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = AAFont
            )
            Spacer(Modifier.width(3.dp))
            Text("元", color = AA_GRAY, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text("›", color = AA_GRAY, fontSize = 20.sp)
        }
    }
}

/** 资金四宫格：2×2，中缝竖线 + 行间横线。 */
@Composable
private fun AAFourGrid(
    totalAsset: String, totalAssetColor: Color,
    floatPnl: String, floatPnlColor: Color,
    marketValue: String, marketValueColor: Color,
    balance: String, balanceColor: Color
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(vertical = 14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AAGridCell("总资产", totalAsset, totalAssetColor, Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(38.dp).background(AA_DIV))
            AAGridCell("浮动盈亏", floatPnl, floatPnlColor, Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(AA_DIV))
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AAGridCell("总市值", marketValue, marketValueColor, Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(38.dp).background(AA_DIV))
            AAGridCell("资金余额", balance, balanceColor, Modifier.weight(1f))
        }
    }
}

/** 四宫格单元格：标题 12sp 灰 + 数值 16sp。 */
@Composable
private fun AAGridCell(title: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = AA_GRAY, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            value,
            color = valueColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = AAFont
        )
    }
}

/** 底部「资产分析」按钮：白底 46dp 圆角，文字 + 右箭头居中。 */
@Composable
private fun AAAnalysisButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("资产分析", color = AA_MAIN, fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFamily = AAFont)
            Spacer(Modifier.width(4.dp))
            Text("›", color = AA_GRAY, fontSize = 18.sp)
        }
    }
}

/** "yyyy-MM-dd HH:mm:ss" -> "MM-dd HH:mm"。 */
private fun aaShortTime(s: String): String {
    if (s.length >= 16) return s.substring(5, 16)
    if (s.length >= 10) return s.substring(5, 10)
    return s
}

/* ========== 通用小件 ========== */
@Composable
private fun AACard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            .background(Color.White, RoundedCornerShape(12.dp)).padding(12.dp),
        content = content
    )
}

@Composable
private fun AASectionTitle(t: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(3.dp, 14.dp).background(AA_RED, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(6.dp))
        Text(t, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AA_MAIN)
    }
}

@Composable
private fun AAStat(
    label: String,
    value: String,
    valueColor: Color = AA_MAIN,
    valueSize: Int = 15,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text(label, fontSize = 11.sp, color = AA_GRAY)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = valueSize.sp, color = valueColor, fontWeight = FontWeight.Medium, fontFamily = AAFont)
    }
}

@Composable
private fun AAChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        fontSize = 11.sp,
        color = if (selected) Color.White else AA_GRAY,
        maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) AA_RED else AA_CHIP_BG)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun AARow(label: String, value: String, valueColor: Color) {
    Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, color = AA_GRAY)
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 13.sp, color = valueColor, fontFamily = AAFont)
    }
}

@Composable
private fun AAAIndexCell(name: String, v: Pair<String, Color>, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(name, fontSize = 11.sp, color = AA_GRAY)
        Spacer(Modifier.height(4.dp))
        Text(v.first, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = v.second, fontFamily = AAFont)
    }
}

@Composable
private fun AACalCellView(cell: AACalCell?, modifier: Modifier = Modifier) {
    Column(
        modifier.height(46.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (cell != null) {
            val dayColor = when {
                cell.isFuture -> Color(0xFFCCCCCC)
                cell.isWeekend -> Color(0xFFBBBBBB)
                else -> AA_MAIN
            }
            Text(cell.day.toString(), fontSize = 11.sp, color = dayColor)
            when {
                cell.pnl != null -> Text(aaCompact(cell.pnl), fontSize = 8.sp, color = aaPnColor(cell.pnl), fontFamily = AAFont)
                cell.isHoliday -> Text("休", fontSize = 8.sp, color = Color(0xFFC9A227))
                else -> Text(" ", fontSize = 8.sp)
            }
        }
    }
}

@Composable
private fun AACalBars(cells: List<AACalCell?>) {
    val list = cells.filterNotNull()
    if (list.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(120.dp))
        return
    }
    val maxAbs = list.mapNotNull { it.pnl?.let { p -> abs(p) } }.maxOrNull() ?: 1.0
    Canvas(Modifier.fillMaxWidth().height(130.dp)) {
        val w = size.width
        val h = size.height
        val base = h * 0.62f
        val bw = w / list.size
        val labelPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = 9.sp.toPx()
            textAlign = android.graphics.Paint.Align.CENTER
        }
        for ((i, c) in list.withIndex()) {
            val p = c.pnl
            val x = i * bw + bw / 2
            if (p != null && maxAbs > 0) {
                val bh = (abs(p) / maxAbs * (h * 0.42)).toFloat()
                val col = aaPnColor(p)
                if (p >= 0) drawRect(col, topLeft = Offset(x - bw * 0.28f, base - bh), size = Size(bw * 0.56f, bh))
                else drawRect(col, topLeft = Offset(x - bw * 0.28f, base), size = Size(bw * 0.56f, bh))
            }
            if (c.day == 1 || c.day % 5 == 0) {
                labelPaint.color = android.graphics.Color.argb(255, 0x99, 0x99, 0x99)
                drawContext.canvas.nativeCanvas.drawText(c.day.toString(), x, h - 2.dp.toPx(), labelPaint)
            }
        }
        drawLine(Color(0xFFDDDDDD), Offset(0f, base), Offset(w, base), strokeWidth = 1f)
    }
}

/* ========== K线（蜡烛 + 均线 + 量柱 + BST） ========== */
@Composable
private fun AAKLineView(line: List<KLinePoint>, marks: List<TradeMark>) {
    Canvas(Modifier.fillMaxWidth().height(240.dp)) {
        if (line.isEmpty()) return@Canvas
        val w = size.width
        val h = size.height
        val priceH = h * 0.70f
        val volTop = h * 0.78f
        val volH = h * 0.20f
        var mn = line.minOf { it.low }
        var mx = line.maxOf { it.high }
        if (mx - mn < 1e-9) {
            mx += 0.1
            mn -= 0.1
        }
        val pad = (mx - mn) * 0.06
        mn -= pad
        mx += pad
        val n = line.size
        val step = w / n
        fun yAt(v: Double) = ((mx - v) / (mx - mn) * priceH).toFloat()
        fun xAt(i: Int) = step * i + step / 2
        for (i in 0 until n) {
            val k = line[i]
            val col = if (k.close >= k.open) AA_RED else AA_BLUE
            val up = yAt(k.close)
            val dn = yAt(k.open)
            val hi = yAt(k.high)
            val lo = yAt(k.low)
            val x = xAt(i)
            drawLine(col, Offset(x, hi), Offset(x, lo), strokeWidth = 1f)
            val top = minOf(up, dn)
            val bot = maxOf(up, dn)
            val bw = (step * 0.62f).coerceAtLeast(1.5f)
            drawRect(col, topLeft = Offset(x - bw / 2, top), size = Size(bw, (bot - top).coerceAtLeast(1.2f)))
        }
        val closes = line.map { it.close }
        val maDefs = listOf(5 to AA_MA_COLORS[0], 10 to AA_MA_COLORS[1], 30 to AA_MA_COLORS[2])
        for ((m, col) in maDefs) {
            val path = Path()
            var started = false
            for (i in m - 1 until n) {
                var s = 0.0
                for (j in i - m + 1..i) s += closes[j]
                val v = s / m
                val x = xAt(i)
                val y = yAt(v)
                if (!started) {
                    path.moveTo(x, y)
                    started = true
                } else {
                    path.lineTo(x, y)
                }
            }
            if (started) drawPath(path, color = col, style = Stroke(width = 1.2f))
        }
        val maxVol = line.maxOfOrNull { it.vol } ?: 0.0
        if (maxVol > 0) {
            for (i in 0 until n) {
                val k = line[i]
                val col = (if (k.close >= k.open) AA_RED else AA_BLUE).copy(alpha = 0.75f)
                val vh = (k.vol / maxVol * volH).toFloat()
                val bw = (step * 0.62f).coerceAtLeast(1.2f)
                drawRect(col, topLeft = Offset(xAt(i) - bw / 2, volTop + volH - vh), size = Size(bw, vh))
            }
        }
        if (marks.isNotEmpty()) {
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                textSize = 8.sp.toPx()
                textAlign = android.graphics.Paint.Align.CENTER
            }
            val dateIdx = HashMap<String, Int>()
            for (i in 0 until n) dateIdx[line[i].date] = i
            for (mk in marks) {
                val idx = dateIdx[mk.date] ?: continue
                val k = line[idx]
                val x = xAt(idx)
                val anchorV = if (mk.avgPrice > 0) mk.avgPrice else k.close
                val y = if (mk.above) yAt(maxOf(k.high, anchorV)) - 10.dp.toPx()
                else yAt(minOf(k.low, anchorV)) + 10.dp.toPx()
                val yc = y.coerceIn(6.dp.toPx(), priceH - 6.dp.toPx())
                drawCircle(aaMarkColor(mk.color), radius = 3.dp.toPx(), center = Offset(x, yc))
                paint.color = android.graphics.Color.argb(255, 0x88, 0x88, 0x88)
                val t = when (mk.kind) {
                    "B" -> "B"
                    "S" -> "S"
                    "T" -> "T"
                    else -> ""
                }
                if (t.isNotEmpty()) drawContext.canvas.nativeCanvas.drawText(t, x, yc + 3.dp.toPx(), paint)
            }
        }
    }
}

/* ========== 曲线图（双线 / K线聚合 两模式） ========== */
@Composable
private fun AAChartView(
    points: List<PnlPoint>,
    indicator: String,
    style: String,
    monthBase: Double,
    useTotal: Boolean
) {
    Canvas(modifier = Modifier.fillMaxWidth().height(232.dp)) {
        if (points.size < 2) {
            val p = android.graphics.Paint().apply {
                isAntiAlias = true
                textSize = 12.sp.toPx()
                textAlign = android.graphics.Paint.Align.CENTER
                color = android.graphics.Color.argb(255, 0x99, 0x99, 0x99)
            }
            drawContext.canvas.nativeCanvas.drawText("暂无走势数据", size.width / 2, size.height / 2, p)
            return@Canvas
        }
        val my = points.map { p ->
            when (indicator) {
                "盈亏金额" -> p.monthPnl
                "总资产" -> monthBase + p.monthPnl
                else -> if (useTotal) p.totalPnlPct else p.monthPct
            }
        }
        val bench = when (indicator) {
            "收益率" -> points.map { it.shPct }
            "盈亏金额" -> points.map { monthBase * it.shPct / 100.0 }
            else -> emptyList()
        }
        val w = size.width
        val h = size.height
        val padL = 46.dp.toPx()
        val padR = 8.dp.toPx()
        val padT = 10.dp.toPx()
        val padB = 20.dp.toPx()
        val chartW = w - padL - padR
        val chartH = h - padT - padB
        val all = my + bench
        var mn = minOf(all.minOrNull() ?: 0.0, 0.0)
        var mx = maxOf(all.maxOrNull() ?: 0.0, 0.0)
        if (mx - mn < 1e-9) {
            mx += 1
            mn -= 1
        }
        val padV = (mx - mn) * 0.10
        mn -= padV
        mx += padV
        val n = my.size
        fun xAt(i: Int) = padL + chartW * i / (n - 1).toFloat()
        fun yAt(v: Double) = padT + ((mx - v) / (mx - mn)).toFloat() * chartH
        val labelPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = 9.sp.toPx()
        }
        val gridColor = Color(0xFFF0F0F0)
        for (gi in 0..3) {
            val v = mx - (mx - mn) * gi / 3.0
            val y = yAt(v)
            drawLine(gridColor, Offset(padL, y), Offset(w - padR, y), strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f))
            labelPaint.color = android.graphics.Color.argb(255, 0x99, 0x99, 0x99)
            labelPaint.textAlign = android.graphics.Paint.Align.RIGHT
            val lbl = if (indicator == "收益率") String.format(Locale.CHINA, "%+.2f%%", v)
            else String.format(Locale.CHINA, "%,.0f", v)
            drawContext.canvas.nativeCanvas.drawText(lbl, padL - 4.dp.toPx(), y + 3.dp.toPx(), labelPaint)
        }
        if (style == "K线收益率") {
            for (i in 1 until n) {
                val o = my[i - 1]
                val c = my[i]
                val col = if (c >= o) AA_RED else AA_BLUE
                val x = xAt(i)
                drawLine(col, Offset(x, yAt(maxOf(o, c))), Offset(x, yAt(minOf(o, c))), strokeWidth = 1f)
                val top = minOf(yAt(o), yAt(c))
                val bot = maxOf(yAt(o), yAt(c))
                val bw = (chartW / n * 0.55f).coerceAtLeast(1.5f)
                drawRect(col, topLeft = Offset(x - bw / 2, top), size = Size(bw, (bot - top).coerceAtLeast(1.2f)))
            }
        } else {
            val pRed = Path()
            my.forEachIndexed { i, v -> if (i == 0) pRed.moveTo(xAt(i), yAt(v)) else pRed.lineTo(xAt(i), yAt(v)) }
            val fill = Path().apply {
                addPath(pRed)
                lineTo(xAt(n - 1), padT + chartH)
                lineTo(xAt(0), padT + chartH)
                close()
            }
            drawPath(fill, brush = Brush.verticalGradient(
                listOf(AA_RED.copy(alpha = 0.16f), AA_RED.copy(alpha = 0f)),
                startY = padT, endY = padT + chartH
            ))
            drawPath(pRed, color = AA_RED, style = Stroke(width = 2f))
            if (bench.size == n) {
                val pB = Path()
                bench.forEachIndexed { i, v -> if (i == 0) pB.moveTo(xAt(i), yAt(v)) else pB.lineTo(xAt(i), yAt(v)) }
                drawPath(pB, color = AA_SH, style = Stroke(width = 1.6f))
            }
        }
        labelPaint.textAlign = android.graphics.Paint.Align.CENTER
        labelPaint.color = android.graphics.Color.argb(255, 0x99, 0x99, 0x99)
        val ix = listOf(0, (n - 1) / 2, n - 1).distinct()
        for (i in ix) {
            val d = points[i].date
            val lbl = if (d.length >= 10) d.substring(5) else d
            drawContext.canvas.nativeCanvas.drawText(lbl, xAt(i).coerceIn(padL, w - padR), h - 4.dp.toPx(), labelPaint)
        }
    }
}
