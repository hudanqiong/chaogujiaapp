package com.chaogujia.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 交易页（持仓）—— 第二十一轮
 * 入口：底部「交易」Tab（无 token 时由底部导航统一拦截并弹登录）。
 * 数据：Api.account() + Api.positions()。
 */
private val TP_RED = Color(0xFFE23B3B)     // 涨
private val TP_GREEN = Color(0xFF12A05C)   // 跌
private val TP_GRAY = Color(0xFF888888)

private fun tpMoney(v: Double): String =
    String.format(Locale.CHINA, "%,.2f", v)

private fun tpPct(v: Double): String =
    String.format(Locale.CHINA, "%+.2f%%", v)

private fun tpColor(v: Double): Color = when {
    v > 1e-9 -> TP_RED
    v < -1e-9 -> TP_GREEN
    else -> TP_GRAY
}

@Composable
fun TradePage(
    token: String,
    onOpenDetail: (list: List<StockItem>, index: Int) -> Unit = { _, _ -> }
) {
    var loading by remember { mutableStateOf(true) }
    var account by remember { mutableStateOf(AccountInfo()) }
    var positions by remember { mutableStateOf<List<PositionItem>>(emptyList()) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        loading = true
        try {
            val acc = withContext(Dispatchers.IO) { Api.account(token) }
            val pos = withContext(Dispatchers.IO) { Api.positions(token) }
            account = acc
            positions = pos
        } catch (e: Exception) {
            // 保持上次数据
        } finally {
            loading = false
        }
    }

    // 进入页面自动加载
    LaunchedEffect(token) { load() }

    // 持仓 → StockItem 列表（用于点进详情，详情页红条要显示名称/代码）
    val stockList = remember(positions) {
        positions.map {
            StockItem(
                code = it.code,
                name = it.name,
                price = it.price,
                change = 0.0,
                changePct = 0.0
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        // ===== 账户总览卡片 =====
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "总资产",
                    color = TP_GRAY,
                    fontSize = 13.sp
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (loading) "加载中…" else "¥ " + tpMoney(account.total),
                    color = Color(0xFF222222),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                StatCell("可用资金", tpMoney(account.cash), Modifier.weight(1f))
                StatCell("持仓市值", tpMoney(account.marketValue), Modifier.weight(1f))
                StatCell(
                    "总盈亏",
                    tpMoney(account.totalPn),
                    Modifier.weight(1f),
                    valueColor = tpColor(account.totalPn)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                StatCell("初始资金", tpMoney(account.initial), Modifier.weight(1f))
                StatCell(
                    "盈亏比例",
                    if (account.initial > 0) tpPct(account.totalPnPct) else "—",
                    Modifier.weight(1f),
                    valueColor = tpColor(account.totalPn)
                )
                StatCell(
                    "浮动盈亏",
                    tpMoney(account.floatPn),
                    Modifier.weight(1f),
                    valueColor = tpColor(account.floatPn)
                )
            }
        }

        // ===== 持仓标题栏 =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "我的持仓",
                color = Color(0xFF222222),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "（${positions.size}）",
                color = TP_GRAY,
                fontSize = 13.sp
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (loading) "刷新中…" else "刷新",
                color = if (loading) TP_GRAY else Color(Config.COLOR_PRIMARY),
                fontSize = 13.sp,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { if (!loading) scope.launch { load() } }
            )
        }

        // ===== 持仓列表 =====
        if (positions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (loading) "加载中…" else "暂无持仓",
                    color = TP_GRAY,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.White)
            ) {
                items(positions.size) { idx ->
                    val p = positions[idx]
                    PositionRow(
                        p = p,
                        onClick = {
                            if (idx in stockList.indices) onOpenDetail(stockList, idx)
                        }
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(Color(Config.COLOR_DIVIDER))
                            .padding(start = 16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color(0xFF222222)
) {
    Column(modifier = modifier) {
        Text(text = label, color = TP_GRAY, fontSize = 12.sp)
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            color = valueColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun PositionRow(p: PositionItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左：名称 + 代码 + 持仓量/成本
        Column(modifier = Modifier.weight(1.4f)) {
            Text(
                text = p.name.ifEmpty { p.code },
                color = Color(0xFF222222),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = p.code,
                color = TP_GRAY,
                fontSize = 11.sp,
                maxLines = 1
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "持 ${p.quantity}  成本 ${String.format(Locale.CHINA, "%.2f", p.avgCost)}",
                color = TP_GRAY,
                fontSize = 11.sp,
                maxLines = 1
            )
        }
        // 中：现价 + 涨跌
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = String.format(Locale.CHINA, "%.2f", p.price),
                color = tpColor(p.price - p.avgCost),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "市值 " + tpMoney(p.marketValue),
                color = TP_GRAY,
                fontSize = 11.sp
            )
        }
        // 右：浮动盈亏
        Column(
            modifier = Modifier.weight(1.1f),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = (if (p.pnl > 1e-9) "+" else "") + tpMoney(p.pnl),
                color = tpColor(p.pnl),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.End
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = tpPct(p.pnlPct),
                color = tpColor(p.pnl),
                fontSize = 11.sp
            )
        }
    }
}
