package com.chaogujia.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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
private val COLOR_UP = Color(0xFFE53935)    // 红（涨）
private val COLOR_DOWN = Color(0xFF2E7D32)  // 绿（跌）
private val COLOR_FLAT = Color(0xFF999999)  // 平
private val COLOR_TEXT = Color(0xFF222222)
private val COLOR_SUB = Color(0xFF888888)

private fun colorOf(pct: Double): Color =
    if (pct > 0.000001) COLOR_UP else if (pct < -0.000001) COLOR_DOWN else COLOR_FLAT

@Composable
fun IndexBar() {
    // 顶部当前选中指数
    var secid by remember { mutableStateOf("1.000001") }
    var quote by remember { mutableStateOf<IndexQuote?>(null) }
    // 指数清单（右侧方块用）
    var list by remember { mutableStateOf<List<IndexItemInfo>>(emptyList()) }
    // 清单每个指数的实时行情（code -> 涨跌幅%），用于右侧方块显示
    var boxQuotes by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    // 是否展开详情面板
    var expanded by remember { mutableStateOf(false) }

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

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
        Column(Modifier.fillMaxWidth().background(Color.White)) {

            // ================= 顶部两排版工具条 =================
            TopBar(
                quote = quote,
                expanded = expanded,
                onToggle = { expanded = !expanded }
            )

            // ================= 展开：详情大面板 =================
            if (expanded) {
                IndexDetailPanel(
                    secid = secid,
                    list = list,
                    boxQuotes = boxQuotes,
                    onPick = { sid ->
                        secid = sid
                        expanded = false
                    }
                )
            }
        }
    }
}

/* ---------------- 顶部两排版 ---------------- */

@Composable
private fun TopBar(quote: IndexQuote?, expanded: Boolean, onToggle: () -> Unit) {
    val pct = quote?.changePct ?: 0.0
    val c = colorOf(pct)
    val priceText = quote?.let { fmt(it.price) } ?: "--"
    val chgText = quote?.let { (if (it.change >= 0) "+" else "") + fmt(it.change) } ?: "--"
    val pctText = quote?.let { (if (it.changePct >= 0) "+" else "") + fmt(it.changePct) + "%" } ?: "--"
    val nameText = quote?.name ?: "上证指数"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .clickable { onToggle() }
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            // 第一排：点数（大粗红） + 涨跌点（小红）
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = priceText,
                    color = c,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = chgText,
                    color = c,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
            // 第二排：名称（黑） + 涨跌幅%（红） + ▼
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = nameText, color = COLOR_TEXT, fontSize = 12.sp)
                Spacer(Modifier.width(6.dp))
                Text(text = pctText, color = c, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (expanded) "▲" else "▼",
                    color = COLOR_SUB,
                    fontSize = 10.sp
                )
            }
        }
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
                    Text(fmt(d.price), color = c, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        (if (d.change >= 0) "+" else "") + fmt(d.change),
                        color = c, fontSize = 13.sp, modifier = Modifier.padding(bottom = 3.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        (if (d.changePct >= 0) "+" else "") + fmt(d.changePct) + "%",
                        color = c, fontSize = 13.sp, modifier = Modifier.padding(bottom = 3.dp)
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
                InfoRow("平家数", d.flatCount.toString(), COLOR_SUB)
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
            fontSize = 9.sp
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
            Text("09:30", color = COLOR_SUB, fontSize = 9.sp)
            Text("11:30/13:00", color = COLOR_SUB, fontSize = 9.sp)
            Text("15:00", color = COLOR_SUB, fontSize = 9.sp)
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
        Text(value, color = valueColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
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