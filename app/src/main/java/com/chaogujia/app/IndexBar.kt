package com.chaogujia.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 自选页 —— 红条正下方的「指数工具条」。
 *
 * 结构（用户定稿）：
 *   第一排：点数（大粗红） + 涨跌点（小红）
 *   第二排：名称（黑） + 涨跌幅%（红） + ▼（下拉三角）
 *   点 ▼ → 从红条底部向下弹出「指数详情面板」：
 *       - 指数名 / 点数 / 涨跌点 / 涨跌幅 / 成交额
 *       - 当天大分时走势曲线（09:30 - 11:30 - 15:00）
 *       - 成交量 / 成交额 / 涨家数 / 跌家数
 *       - 最右侧一竖列「指数快捷方块」（首字 + 实时涨跌幅%，可上下滑，点击切换）
 */

/** 涨跌颜色：>=0 红，<0 绿 */
private val COLOR_UP = Color(0xFFF5222D)    // 红（涨）——更鲜红
private val COLOR_DOWN = Color(0xFF00B050)  // 绿（跌）——翠绿（同花顺）
private val COLOR_FLAT = Color(0xFF000000)  // 平——纯黑
private val COLOR_TEXT = Color(0xFF222222)
private val COLOR_SUB = Color(0xFF888888)

private fun colorOf(pct: Double): Color =
    if (pct > 0.000001) COLOR_UP else if (pct < -0.000001) COLOR_DOWN else COLOR_FLAT

/** 行情数字字体开关（已定稿 = Lato）：
 *  - 选 Lato 的原因：6/9 尾巴长度最接近同花顺（经 4 批对比页实机挑选）
 *  - 想换别的：改成 FontFamily(Font(R.font.xxx)) 即可
 */
// 行情数字字体（同花顺 THS 金融体，从「趣股道」APK 扒出）
// 想换别的：改动下面的 R.font.xxx 即可，或换 ths_money / din_medium
private val NumFont = FontFamily(Font(R.font.ths_regular))

@Composable
fun IndexBar(token: String? = null, onOpenNews: () -> Unit = {}) {
    // 顶部当前选中指数
    var secid by remember { mutableStateOf("1.000001") }
    var quote by remember { mutableStateOf<IndexQuote?>(null) }
    // 指数清单（右侧方块用）
    var list by remember { mutableStateOf<List<IndexItemInfo>>(emptyList()) }
    // 清单每个指数的实时行情（code -> 涨跌幅%），用于右侧方块显示
    var boxQuotes by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    // 是否展开详情面板
    var expanded by remember { mutableStateOf(false) }

    // ===== 自选分组标签 =====
    // 当前选中的分组 key：负数为内置组（-1 自选股 / -2 持仓股），>=0 为用户自建组 id
    var selectedGroup by remember { mutableStateOf(-1) }
    // 用户自建分组（来自后端 watch_group.php）
    var userGroups by remember { mutableStateOf<List<WatchGroup>>(emptyList()) }

    // 当前分组下的股票列表
    var stocks by remember { mutableStateOf<List<StockItem>>(emptyList()) }
    // 列表是否加载中
    var loadingStocks by remember { mutableStateOf(false) }
    // 拉一次分组列表
    LaunchedEffect(token) {
        try {
            val g = withContext(Dispatchers.IO) { Api.watchGroups(token) }
            if (g.isNotEmpty()) userGroups = g.sortedBy { it.sort }
        } catch (e: Exception) { /* 忽略 */ }
    }
    // 切换分组时拉该组股票（自选股=-1 或自建组 id；持仓股=-2 后续接）
    LaunchedEffect(selectedGroup, token) {
        loadingStocks = true
        stocks = emptyList()
        try {
            val gid = if (selectedGroup >= 0) selectedGroup else -1
            if (selectedGroup != -2) {
                val l = withContext(Dispatchers.IO) { Api.stockList(token, gid) }
                stocks = l
            }
        } catch (e: Exception) { /* 忽略 */ }
        loadingStocks = false
    }

    // 初始拉一次指数清单
    LaunchedEffect(Unit) {
        try {
            val l = withContext(Dispatchers.IO) { Api.indexList() }
            if (l.isNotEmpty()) list = l
        } catch (e: Exception) { /* 忽略 */ }
    }

    // 顶部指数行情：5 秒轮询
    LaunchedEffect(secid) {
        while (true) {
            try {
                val q = withContext(Dispatchers.IO) { Api.indexQuotesBy(secid) }
                if (q.isNotEmpty()) quote = q.first()
            } catch (e: Exception) { /* 忽略 */ }
            delay(5000)
        }
    }

    // 右侧方块行情：8 秒轮询全部指数
    LaunchedEffect(list) {
        if (list.isEmpty()) return@LaunchedEffect
        while (true) {
            try {
                val secids = list.joinToString(",") { it.secid }
                val qs = withContext(Dispatchers.IO) { Api.indexQuotesBy(secids) }
                val m = HashMap<String, Double>()
                for (q in qs) m[q.code] = q.changePct
                if (m.isNotEmpty()) boxQuotes = m
            } catch (e: Exception) { /* 忽略 */ }
            delay(8000)
        }
    }

    // 顶部红条高度由内容自适应，面板紧贴其底部，无需硬编码
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {

        // ---------- 主列：红条 + 分割线 +（展开时）面板 +（展开时）关闭遮罩 ----------
        Column(Modifier.fillMaxWidth()) {

            // ================= 顶部两排版工具条（红条）=================
            TopBar(
                quote = quote,
                expanded = expanded,
                onToggle = { expanded = !expanded },
                onOpenNews = onOpenNews
            )

            if (expanded) {
                // 展开时：详情面板【直接】从红条底部向下延伸，中间无分割线、无空白
                IndexDetailPanel(
                    secid = secid,
                    list = list,
                    boxQuotes = boxQuotes,
                    onPick = { sid ->
                        secid = sid
                        expanded = false
                    }
                )

                // 面板以下的剩余区域：透明遮罩，点面板以外任何地方都关闭
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { expanded = false }
                )
            } else {
                // 未展开时：红条（指数显示区）与下方白色区之间的分割线
                HorizontalDivider(
                    thickness = 2.dp,
                    color = Color(0xFFEEEEEE)
                )

                // 分组标签栏（自选股 / 持仓股 / 用户自建分组）
                GroupTabsBar(
                    selected = selectedGroup,
                    userGroups = userGroups,
                    onSelect = { selectedGroup = it }
                )

                // 标签栏与股票列表之间的细分隔线
                HorizontalDivider(
                    thickness = 1.dp,
                    color = Color(0xFFEEEEEE)
                )

                // 列表统计/列头（名称代码 | 最新价 | 涨跌幅），与 StockRow 三段对齐
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF7F7F7))
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "名称 / 代码",
                        color = COLOR_SUB,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "最新价",
                        color = COLOR_SUB,
                        fontSize = 12.sp,
                        modifier = Modifier.width(96.dp),
                        textAlign = TextAlign.Right
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = "涨跌幅",
                        color = COLOR_SUB,
                        fontSize = 12.sp,
                        modifier = Modifier.width(72.dp),
                        textAlign = TextAlign.Center
                    )
                }
                HorizontalDivider(
                    thickness = 1.dp,
                    color = Color(0xFFEEEEEE)
                )
                // 该组股票列表
                if (stocks.isEmpty()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Text(
                            text = if (loadingStocks) "加载中…" else "「${groupNameOf(selectedGroup, userGroups)}」暂无股票",
                            color = Color(0xFF999999),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 40.dp)
                        )
                    }
                } else {
                    LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                        items(stocks) { s -> StockRow(s) }
                    }
                }
            }
        }
    }
}

/* ---------------- 顶部两排版 ---------------- */

@Composable
private fun TopBar(quote: IndexQuote?, expanded: Boolean, onToggle: () -> Unit, onOpenNews: () -> Unit) {
    val pct = quote?.changePct ?: 0.0
    val c = colorOf(pct)
    val priceText = quote?.let { fmt(it.price) } ?: "--"
    val chgText = quote?.let { fmt(it.change) } ?: "--"
    val pctText = quote?.let { fmt(it.changePct) + "%" } ?: "--"
    val nameText = quote?.name ?: "上证指数"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(Color.White)
            .clickable { onToggle() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            // 第一排：点数（大粗红） + 涨跌点（小红）  —— 数字用真窄体 Oswald
            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = priceText,
                    color = c,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = NumFont
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = chgText,
                    color = c,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = NumFont,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
            // 两排之间加大间距
            Spacer(Modifier.height(6.dp))
            // 第二排：名称（纯黑，中粗，别太粗） + 涨跌幅%（红） + ▼
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = nameText,
                    color = Color(0xFF000000),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = pctText,
                    color = c,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = NumFont
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (expanded) "▲" else "▼",
                    color = Color(0xFF111111),
                    fontSize = 12.sp,
                    modifier = Modifier.scale(1f, 0.6f)
                )
            }
        }

        // ---------- 右侧四个功能按钮（上图标 + 下文字）----------
        TopActionButton(R.drawable.btn_fund, "资金", enabled = false)
        TopActionButton(R.drawable.btn_news, "资讯", enabled = true) { onOpenNews() }
        TopActionButton(R.drawable.btn_asset, "资产", enabled = false)
        TopActionButton(R.drawable.btn_analysis, "分析", enabled = false)
    }
}

/** 顶部功能按钮：上图标 + 下文字。enabled=false 时暂不绑定功能（点了没反应） */
@Composable
private fun TopActionButton(
    iconRes: Int,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .width(48.dp)
            .clickable(enabled = enabled) { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = label,
            modifier = Modifier.size(24.dp),
            contentScale = ContentScale.Fit
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            color = Color(0xFF333333),
            fontSize = 11.sp,
            fontWeight = if (enabled) FontWeight.Bold else FontWeight.Normal
        )
    }
}

/* ---------------- 自选股票列表行 ---------------- */
@Composable
private fun StockRow(s: StockItem) {
    val c = colorOf(s.changePct)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左：名称 + 代码
        Column(Modifier.weight(1f)) {
            Text(
                text = s.name,
                color = COLOR_TEXT,
                fontSize = 15.sp,
                maxLines = 1
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = s.code,
                color = COLOR_SUB,
                fontSize = 12.sp,
                fontFamily = NumFont
            )
        }
        // 中：现价
        Text(
            text = String.format(Locale.CHINA, "%.2f", s.price),
            color = c,
            fontSize = 16.sp,
            fontFamily = NumFont,
            modifier = Modifier.width(96.dp),
            textAlign = TextAlign.Right
        )
        // 右：涨跌幅（固定宽度右对齐，画一个色块底）
        Box(
            Modifier
                .width(72.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(c)
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = String.format(Locale.CHINA, "%+.2f%%", s.changePct),
                color = Color.White,
                fontSize = 13.sp,
                fontFamily = NumFont
            )
        }
    }
}


/* ---------------- 自选分组标签栏 ---------------- */

/** 内置两个固定分组：自选股(-1) / 持仓股(-2) */
/** 选择项 key 对应的显示名：-1 自选股 / -2 持仓股 / >=0 用户分组名 */
private fun groupNameOf(key: Int, groups: List<WatchGroup>): String = when (key) {
    -1 -> "自选股"
    -2 -> "持仓股"
    else -> groups.firstOrNull { it.id == key }?.name?.ifEmpty { "分组$key" } ?: "自选股"
}

data class GroupTabItem(val key: Int, val name: String)

/**
 * 分组标签栏：横向可滑动。
 * 前两个固定为「自选股」「持仓股」，其后为用户自建分组。
 * 点亮态 = 主色红 + 稍放大 + 名字下方居中一根固定一个字宽的短红线。
 */
@Composable
private fun GroupTabsBar(
    selected: Int,
    userGroups: List<WatchGroup>,
    onSelect: (Int) -> Unit
) {
    val items = remember(userGroups) {
        val base = arrayListOf(
            GroupTabItem(-1, "自选股"),
            GroupTabItem(-2, "持仓股")
        )
        for (g in userGroups) base.add(GroupTabItem(g.id, g.name.ifEmpty { "分组${g.id}" }))
        base
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(Color.White)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(8.dp))
        for (it in items) {
            GroupTab(
                name = it.name,
                active = it.key == selected,
                onClick = { onSelect(it.key) }
            )
        }
        Spacer(Modifier.width(8.dp))
    }
}

/** 单个分组标签：上文字、下短红线（点亮时）。红线固定一个字宽并居中。 */
@Composable
private fun GroupTab(name: String, active: Boolean, onClick: () -> Unit) {
    val textColor = if (active) Color(0xFFF03036) else Color(0xFF888888)
    val fontSize = if (active) 16.sp else 15.sp
    Column(
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = name,
            color = textColor,
            fontSize = fontSize,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
        Spacer(Modifier.height(3.dp))
        // 短红线：固定一个汉字宽（16dp），居中；未点亮时占位透明，避免跳动
        Box(
            Modifier
                .width(16.dp)
                .height(2.dp)
                .background(if (active) Color(0xFFF03036) else Color.Transparent)
        )
    }
}

/* ---------------- 详情大面板 ---------------- */

@Composable
private fun IndexDetailPanel(
    secid: String,
    list: List<IndexItemInfo>,
    boxQuotes: Map<String, Double>,
    onPick: (String) -> Unit
) {
    var detail by remember { mutableStateOf<IndexDetail?>(null) }

    // 详情数据：3 秒轮询（只拉当前指数）
    LaunchedEffect(secid) {
        while (true) {
            try {
                val d = withContext(Dispatchers.IO) { Api.indexDetail(secid) }
                if (d != null) detail = d
            } catch (e: Exception) { /* 忽略 */ }
            delay(3000)
        }
    }

    val d = detail

    Row(
        Modifier
            .fillMaxWidth()
            .height(360.dp)
            .background(Color(0xFFF7F7F7))
    ) {
        // 左侧：详情主体（可上下滚动）
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(10.dp)
        ) {
            if (d == null) {
                Text("加载中…", color = COLOR_SUB, fontSize = 12.sp)
            } else {
                val c = colorOf(d.changePct)

                // 指数名 + 点数 + 涨跌点 + 涨跌幅
                Text(d.name, color = COLOR_TEXT, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(fmt(d.price), color = c, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = NumFont)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        (if (d.change >= 0) "+" else "") + fmt(d.change),
                        color = c, fontSize = 13.sp, fontFamily = NumFont, modifier = Modifier.padding(bottom = 3.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        (if (d.changePct >= 0) "+" else "") + fmt(d.changePct) + "%",
                        color = c, fontSize = 13.sp, fontFamily = NumFont, modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text("成交额 " + fmtAmount(d.amount), color = COLOR_SUB, fontSize = 11.sp)

                Spacer(Modifier.height(10.dp))

                // 分时走势大曲线
                TrendChart(d, Modifier.fillMaxWidth().height(180.dp))

                Spacer(Modifier.height(6.dp))

                // 成交量（迷你柱）
                VolumeChart(d, Modifier.fillMaxWidth().height(48.dp))

                Spacer(Modifier.height(10.dp))

                // 成交额 + 涨跌家数
                InfoRow("成交额", fmtAmount(d.amount), COLOR_TEXT)
                InfoRow("涨家数", d.upCount.toString(), COLOR_UP)
                InfoRow("跌家数", d.downCount.toString(), COLOR_DOWN)
                InfoRow("平家数", d.flatCount.toString(), COLOR_FLAT)
            }
        }

        // 右侧：一竖列指数快捷方块（可上下滑）
        Box(
            Modifier
                .width(58.dp)
                .fillMaxHeight()
                .background(Color.White)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item { Spacer(Modifier.height(6.dp)) }
                items(list) { it2 ->
                    val pct = boxQuotes[it2.code]
                    IndexChip(
                        short = it2.short.ifEmpty { it2.name.take(1) },
                        pct = pct,
                        selected = it2.secid == secid,
                        onClick = { onPick(it2.secid) }
                    )
                }
                item { Spacer(Modifier.height(6.dp)) }
            }
        }
    }
}

/** 右侧竖排方块：首字 + 实时涨跌幅% */
@Composable
private fun IndexChip(short: String, pct: Double?, selected: Boolean, onClick: () -> Unit) {
    val c = if (pct == null) COLOR_SUB else colorOf(pct)
    Column(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Color(0xFFFDECEA) else Color(0xFFF2F2F2))
            .clickable { onClick() }
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = short, color = COLOR_TEXT, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(
            text = if (pct == null) "--" else (if (pct >= 0) "+" else "") + fmt(pct) + "%",
            color = c,
            fontSize = 9.sp,
            fontFamily = NumFont
        )
    }
}

/* ---------------- 分时走势曲线 ---------------- */

@Composable
private fun TrendChart(d: IndexDetail, modifier: Modifier = Modifier) {
    val trends = d.trends
    val priceColor = colorOf(d.changePct)

    Box(modifier) {
        Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
            val w = size.width
            val h = size.height

            // 基线（昨收）在竖直方向居中：以 preClose 为中轴，取上下对称幅度
            val base = if (d.preClose > 0) d.preClose else d.price

            // 计算最大偏差（含均价）
            var maxDev = 0.0
            for (t in trends) {
                maxDev = maxOf(maxDev, Math.abs(t.p - base))
                maxDev = maxOf(maxDev, Math.abs(t.avg - base))
            }
            if (maxDev <= 0.0) maxDev = base * 0.005

            val cy = h / 2f
            // 上下各留约 8% 边距
            val scale = (h * 0.42) / maxDev

            // 横向网格线：上 / 中 / 下 三条
            val gridColor = Color(0xFFDDDDDD)
            drawLine(gridColor, Offset(0f, cy), Offset(w, cy), strokeWidth = 1f)
            drawLine(gridColor, Offset(0f, h * 0.12f), Offset(w, h * 0.12f), strokeWidth = 1f)
            drawLine(gridColor, Offset(0f, h * 0.88f), Offset(w, h * 0.88f), strokeWidth = 1f)

            if (trends.size < 2) return@Canvas

            val n = trends.size
            fun xOf(i: Int): Float = if (n <= 1) 0f else w * i / (n - 1).toFloat()
            fun yOf(p: Double): Float = (cy - ((p - base) * scale)).toFloat()

            // 价格曲线
            val pricePath = Path()
            for (i in 0 until n) {
                val x = xOf(i)
                val y = yOf(trends[i].p)
                if (i == 0) pricePath.moveTo(x, y) else pricePath.lineTo(x, y)
            }
            // 价格曲线下方淡色填充
            val fill = Path()
            fill.addPath(pricePath)
            fill.lineTo(w, cy)
            fill.lineTo(0f, cy)
            fill.close()
            drawPath(fill, color = priceColor.copy(alpha = 0.08f))
            drawPath(pricePath, color = priceColor, style = Stroke(width = 2.5f))

            // 均价曲线（黄色细线）
            val avgPath = Path()
            var started = false
            for (i in 0 until n) {
                if (trends[i].avg <= 0.0) continue
                val x = xOf(i)
                val y = yOf(trends[i].avg)
                if (!started) { avgPath.moveTo(x, y); started = true } else avgPath.lineTo(x, y)
            }
            if (started) drawPath(avgPath, color = Color(0xFFE6A23C), style = Stroke(width = 1.2f))
        }

        // 时间轴标签：左 / 中 / 右
        Row(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("09:30", color = COLOR_SUB, fontSize = 9.sp, fontFamily = NumFont)
            Text("11:30/13:00", color = COLOR_SUB, fontSize = 9.sp, fontFamily = NumFont)
            Text("15:00", color = COLOR_SUB, fontSize = 9.sp, fontFamily = NumFont)
        }
    }
}

/* ---------------- 成交量迷你柱 ---------------- */

@Composable
private fun VolumeChart(d: IndexDetail, modifier: Modifier = Modifier) {
    val trends = d.trends
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val n = trends.size
        if (n < 1) return@Canvas

        var maxV = 0.0
        for (t in trends) if (t.v > maxV) maxV = t.v
        if (maxV <= 0.0) return@Canvas

        val bw = (w / n) * 0.7f
        for (i in 0 until n) {
            val x = w * i / n.toFloat()
            val bh = (trends[i].v / maxV * h).toFloat()
            // 与前一柱比较判红绿
            val up = if (i > 0) trends[i].p >= trends[i - 1].p else true
            drawRect(
                color = if (up) COLOR_UP.copy(alpha = 0.7f) else COLOR_DOWN.copy(alpha = 0.7f),
                topLeft = Offset(x, h - bh),
                size = Size(bw, bh)
            )
        }
    }
}

/* ---------------- 小工具 ---------------- */

@Composable
private fun InfoRow(label: String, value: String, valueColor: Color) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = COLOR_SUB, fontSize = 12.sp)
        Text(value, color = valueColor, fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = NumFont)
    }
}

/** 点数两位小数 */
private fun fmt(v: Double): String = String.format(Locale.CHINA, "%.2f", v)

/** 成交额：>=1万亿 显示「X.XX万亿」，>=1亿 显示「X亿」，否则「X万」 */
private fun fmtAmount(v: Double): String {
    if (v <= 0.0) return "--"
    return when {
        v >= 1e12 -> String.format(Locale.CHINA, "%.2f万亿", v / 1e12)
        v >= 1e8 -> String.format(Locale.CHINA, "%.0f亿", v / 1e8)
        v >= 1e4 -> String.format(Locale.CHINA, "%.0f万", v / 1e4)
        else -> String.format(Locale.CHINA, "%.0f", v)
    }
}

/* ======================= 数字字体对比页（临时调试，挑完删除） ======================= */
/*
 * 来源：从「趣股道」APK 扒出的同花顺字体。
 *   ths_money   = THS Money font Medium（同花顺金额字体，纯数字+符号）
 *   ths_regular = THS（同花顺金融体）
 *   din_medium  = DIN
 * 数字重点看 0-9 的形态，尤其 6/9 的尾巴。
 */

private data class NumCand(val label: String, val font: FontFamily?)

private val NUM_CANDS: List<NumCand> = listOf(
    NumCand("系统默认（参考）", null),
    NumCand("THS金额体 ths_money", FontFamily(Font(R.font.ths_money))),
    NumCand("THS金融体 ths_regular", FontFamily(Font(R.font.ths_regular))),
    NumCand("THS金融体 ths_medium", FontFamily(Font(R.font.ths_medium))),
    NumCand("THS金融体 ths_bold", FontFamily(Font(R.font.ths_bold))),
    NumCand("DIN din_medium", FontFamily(Font(R.font.din_medium))),
)

/** 下跌绿候选：label + 颜色。挑一个最顺眼的，告诉我 label 前字母即可 */
private data class ColorCand(val label: String, val color: Color)
private val DOWN_CANDS = listOf(
    ColorCand("① 现状 2E7D32（偏黑）", Color(0xFF2E7D32)),
    ColorCand("② 00A05A（清新绿）", Color(0xFF00A05A)),
    ColorCand("③ 00B050（翠绿·同花顺）", Color(0xFF00B050)),
    ColorCand("④ 00C853（亮饱和绿）", Color(0xFF00C853)),
    ColorCand("⑤ 3CB371（柔和中绿）", Color(0xFF3CB371)),
    ColorCand("⑥ 1DB954（清新流行绿）", Color(0xFF1DB954)),
)

@Composable
fun FontComparePage() {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color.White).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("数字字体对比（同花顺扒出）", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            Text("重点看 6 和 9 的尾巴方向。哪套最像同花顺，就选哪套",
                fontSize = 11.sp, color = Color(0xFF888888))
            Spacer(Modifier.height(4.dp))
        }
        items(NUM_CANDS) { c ->
            val fam = c.font ?: FontFamily.Default
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(c.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                Spacer(Modifier.height(3.dp))
                // 大号数字（看 6/9 形态）
                Text(
                    text = "6  9",
                    fontSize = 40.sp,
                    color = Color(0xFFF5222D),
                    fontFamily = fam
                )
                // 全部数字
                Text(
                    text = "0123456789",
                    fontSize = 26.sp,
                    color = Color(0xFF222222),
                    fontFamily = fam
                )
                // 实际行情串
                Text(
                    text = "3952.30  +0.35%",
                    fontSize = 22.sp,
                    color = Color(0xFFF5222D),
                    fontFamily = fam
                )
                HorizontalDivider(Modifier.padding(top = 6.dp), color = Color(0xFFEEEEEE))
            }
        }
        item {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFFE8E8E8))
            Spacer(Modifier.height(10.dp))
            Text("↓ 下跌绿色对比（挑一个告诉我编号）", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            Text("看哪个最像同花顺的跌绿、又不偏黑。用同花顺金融体渲染。",
                fontSize = 11.sp, color = Color(0xFF888888))
            Spacer(Modifier.height(4.dp))
        }
        items(DOWN_CANDS) { c ->
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 左侧色块
                    Box(
                        Modifier
                            .size(28.dp)
                            .background(c.color)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(c.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                }
                Spacer(Modifier.height(3.dp))
                // 用该颜色 + 金融字体渲染真实行情串（跌）
                Text(
                    text = "3963.73  -12.85  -0.32%",
                    fontSize = 24.sp,
                    color = c.color,
                    fontFamily = FontFamily(Font(R.font.ths_regular))
                )
                Text(
                    text = "深证成指  9876.54  -1.28%",
                    fontSize = 15.sp,
                    color = c.color,
                    fontFamily = FontFamily(Font(R.font.ths_regular))
                )
                HorizontalDivider(Modifier.padding(top = 6.dp), color = Color(0xFFEEEEEE))
            }
        }
        item {
            Spacer(Modifier.height(16.dp))
            Text("说明：THS 字体是从「趣股道」APK 里扒出来的，字符集只有数字和符号（就是当金额字体用的）。",
                fontSize = 11.sp, color = Color(0xFF888888))
        }
    }
}