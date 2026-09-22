package com.chaogujia.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ===== 本页私有小工具（不依赖 IndexBar 的 private 符号） =====
private val SpNumFont = FontFamily(Font(R.font.ths_regular))

private fun spColor(pct: Double): Color = when {
    pct > 0 -> Color(0xFFF5222D)
    pct < 0 -> Color(0xFF00B050)
    else -> Color(0xFF000000)
}

private fun spFmt(v: Double): String = String.format(Locale.CHINA, "%.2f", v)

/**
 * 全屏搜索页：搜索框 + 实时搜索 + 结果列表 + 「加自选」（弹分组选择）
 *
 * 入口：自选页顶部红条最右图标（ic_watch_right）点击弹出。
 * 依赖接口：Api.search(kw) 免登录；Api.addWatch(token, code, gid) 需登录；Api.watchGroups(token)。
 */
@Composable
fun SearchPage(
    token: String,
    onClose: () -> Unit
) {
    var kw by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<StockItem>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    // 正在选分组的那只股票（null 表示没弹分组选择）
    var picking by remember { mutableStateOf<StockItem?>(null) }
    // 操作提示（加入成功/失败）
    var toast by remember { mutableStateOf("") }

    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    // 进页面自动聚焦搜索框 + 先拉一次热门
    LaunchedEffect(Unit) {
        try { focusRequester.requestFocus() } catch (_: Exception) {}
        results = withContext(Dispatchers.IO) { Api.search("") }
    }

    // 关键词变化 → 防抖 300ms 再搜
    LaunchedEffect(kw) {
        searching = true
        delay(300)
        val r = withContext(Dispatchers.IO) { Api.search(kw.trim()) }
        results = r
        searching = false
    }

    // toast 3 秒后自动消失
    LaunchedEffect(toast) {
        if (toast.isNotEmpty()) {
            delay(2000)
            toast = ""
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
    ) {
        // ================= 顶部搜索栏 =================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(Color.White)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 返回
            Text(
                text = "←",
                color = Color(0xFF222222),
                fontSize = 22.sp,
                modifier = Modifier
                    .clickable { onClose() }
                    .padding(end = 10.dp)
            )
            // 搜索框
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(6.dp))   // ★ 方形（小圆角），非胶囊
                    .background(Color(0xFFF2F2F2))
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (kw.isEmpty()) {
                    Text("输入股票代码 / 名称 / 拼音", color = Color(0xFF999999), fontSize = 14.sp)
                }
                BasicTextField(
                    value = kw,
                    onValueChange = { kw = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color(0xFF222222), fontSize = 15.sp),
                    cursorBrush = SolidColor(Color(Config.COLOR_PRIMARY)),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            }
            // 清空
            if (kw.isNotEmpty()) {
                Text(
                    text = "✕",
                    color = Color(0xFF999999),
                    fontSize = 16.sp,
                    modifier = Modifier
                        .clickable { kw = "" }
                        .padding(start = 10.dp)
                )
            } else {
                Spacer(Modifier.width(26.dp))
            }
        }
        HorizontalDivider(thickness = 1.dp, color = Color(0xFFEEEEEE))

        // ================= 结果区 =================
        if (searching && results.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("搜索中…", color = Color(0xFF888888), fontSize = 14.sp)
            }
        } else if (results.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (kw.isEmpty()) "暂无热门数据" else "没有找到「$kw」",
                    color = Color(0xFF888888),
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(results, key = { it.code }) { s ->
                    SearchResultRow(s) { picking = s }
                }
            }
        }
    }

    // ================= 分组选择弹窗 =================
    picking?.let { stock ->
        GroupPickDialog(
            token = token,
            stockName = stock.name,
            onDismiss = { picking = null },
            onPicked = { gid ->
                val st = stock
                picking = null
                scope.launch {
                    val res = withContext(Dispatchers.IO) { Api.addWatch(token, st.code, gid) }
                    toast = if (res.ok) "已加入自选" else (res.msg.ifEmpty { "加入失败" })
                }
            }
        )
    }

    // ================= 提示条 =================
    if (toast.isNotEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 120.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text(toast, color = Color.White, fontSize = 14.sp)
            }
        }
    }
}

/** 搜索结果单行：名称/代码 + 现价 + 涨跌幅 + 「+ 加自选」 */
@Composable
private fun SearchResultRow(s: StockItem, onAdd: () -> Unit) {
    val c = spColor(s.changePct)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(s.name, color = Color(0xFF222222), fontSize = 15.sp)
            Spacer(Modifier.height(2.dp))
            Text(s.code, color = Color(0xFF888888), fontSize = 12.sp, fontFamily = SpNumFont)
        }
        Text(
            text = spFmt(s.price),
            color = c,
            fontSize = 16.sp,
            fontFamily = SpNumFont,
            textAlign = TextAlign.End,
            modifier = Modifier.width(96.dp)
        )
        Box(
            modifier = Modifier.width(72.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = String.format("%+.2f%%", s.changePct),
                color = c,
                fontSize = 13.sp,
                fontFamily = SpNumFont
            )
        }
        // 「+ 加自选」按钮
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color(Config.COLOR_PRIMARY))
                .clickable { onAdd() }
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text("+ 自选", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
    HorizontalDivider(thickness = 1.dp, color = Color(0xFFF0F0F0))
}

/** 分组选择弹窗：列出全部分组，点一个即加入 */
@Composable
private fun GroupPickDialog(
    token: String,
    stockName: String,
    onDismiss: () -> Unit,
    onPicked: (Int) -> Unit
) {
    var groups by remember { mutableStateOf<List<WatchGroup>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        groups = withContext(Dispatchers.IO) { Api.watchGroups(token) }
        loaded = true
    }

    // 半透明遮罩
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x88000000))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White)
                .clickable(enabled = false) {}
                .padding(vertical = 16.dp)
        ) {
            Text(
                text = "加入分组",
                color = Color(0xFF222222),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
            Text(
                text = stockName,
                color = Color(0xFF888888),
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(12.dp))
            if (!loaded) {
                Text(
                    "加载中…",
                    color = Color(0xFF888888),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            } else if (groups.isEmpty()) {
                Text(
                    "还没有分组，请先到「我的-自选分组」新建",
                    color = Color(0xFF888888),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            } else {
                groups.forEach { g ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPicked(g.id) }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📁", fontSize = 15.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(g.name, color = Color(0xFF222222), fontSize = 15.sp)
                    }
                    HorizontalDivider(thickness = 1.dp, color = Color(0xFFF0F0F0))
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDismiss() }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("取消", color = Color(0xFF888888), fontSize = 15.sp)
            }
        }
    }
}