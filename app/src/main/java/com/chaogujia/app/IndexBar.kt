package com.chaogujia.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 自选页工具条（红条正下方，紧贴红条底部）
 *
 * 布局：
 *   ┌──────────────────────────────────────────┐
 *   │ 上证指数 3961.85 +0.30%  │  🔘  🔘  🔘   │
 *   └──────────────────────────────────────────┘
 *   左：大盘指数（真实数据，红涨绿跌，5 秒轮询刷新）
 *   右：几个功能按钮（本轮仅占位，不绑定功能）
 */

/** 涨跌颜色：>=0 红，<0 绿 */
private val COLOR_UP = Color(0xFFE53935)    // 红（涨）
private val COLOR_DOWN = Color(0xFF2E7D32)  // 绿（跌）
private val COLOR_FLAT = Color(0xFF999999)  // 平

@Composable
fun IndexBar() {
    // 指数列表 + 轮询刷新（5 秒）
    var quotes by remember { mutableStateOf<List<IndexQuote>>(emptyList()) }

    LaunchedEffect(Unit) {
        while (true) {
            try {
                quotes = withContext(Dispatchers.IO) { Api.indexQuotes() }
            } catch (e: Exception) {
                // 保持旧数据
            }
            delay(5000)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // —— 左：大盘指数（取第一个：上证指数）——
        val idx = quotes.firstOrNull()
        IndexItem(idx)

        Spacer(Modifier.weight(1f))

        // —— 右：功能按钮（占位，暂不写功能）——
        PlaceholderButton("自选股")
        Spacer(Modifier.width(8.dp))
        PlaceholderButton("排序")
        Spacer(Modifier.width(8.dp))
        PlaceholderButton("编辑")
    }
}

/** 单个指数字块：名称 + 点数 + 涨跌幅（红涨绿跌） */
@Composable
private fun IndexItem(idx: IndexQuote?) {
    if (idx == null) {
        // 还没拿到数据：占位，避免跳动
        Column {
            Text("上证指数", color = Color(0xFF333333), fontSize = 12.sp)
            Text("--      --", color = COLOR_FLAT, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        return
    }

    val up = idx.changePct >= 0
    val flat = idx.changePct == 0.0
    val color = when {
        flat -> COLOR_FLAT
        up -> COLOR_UP
        else -> COLOR_DOWN
    }
    val sign = if (up && !flat) "+" else ""

    Column {
        // 名称
        Text(
            text = idx.name,
            color = Color(0xFF333333),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(2.dp))
        // 点数 + 涨跌幅
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = formatNumber(idx.price),
                color = color,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "$sign${formatPct(idx.changePct)}%",
                color = color,
                fontSize = 12.sp
            )
        }
    }
}

/** 占位功能按钮（仅展示，不绑定点击） */
@Composable
private fun PlaceholderButton(label: String) {
    Box(
        modifier = Modifier
            .background(Color(0xFFF5F5F5), androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* 功能待定 */ }
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text = label, color = Color(0xFF666666), fontSize = 12.sp)
    }
}

/** 数字格式化：3961.85 */
private fun formatNumber(v: Double): String {
    return String.format(java.util.Locale.CHINA, "%.2f", v)
}

/** 涨跌幅格式化：0.30 / -1.20 */
private fun formatPct(v: Double): String {
    return String.format(java.util.Locale.CHINA, "%.2f", Math.abs(v))
}
