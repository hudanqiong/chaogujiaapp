package com.chaogujia.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 独立持仓页（PositionPage）—— 第 34 轮新增
 *
 * 入口：交易页（TradePage）「持仓」按钮上抛 onOpenPosition → MainActivity 打开本页。
 * 不挂在底部导航上；返回由顶部红条的 [<] 按钮负责（MainActivity.BackHandler 亦可）。
 *
 * 页面结构（自上而下）：
 *   ① 顶部红条：由 MainActivity 统一渲染 MemberHeaderBar(current = "position")，
 *      内含 [<返回] + 两行文字(证券名/资金账户) + ▼ + 方框箭头 + 放大镜 + 刷新。
 *      本页不画红条。
 *   ② 5 个切换按钮：买入 / 卖出 / 撤单 / 持仓 / 查询（固定不滚动，5 等分）。
 *      未选中深灰、选中红 + 红色下划线（宽度 = 按钮宽度）；默认选中「持仓」。
 *   ③ 内容区：本轮先空占位（列表数据 / 统计数字留待后续步骤）。
 */
private val PP_RED = Color(0xFFE23B3B)      // 选中色 / 下划线（与红条同族）
private val PP_GRAY = Color(0xFF555555)     // 未选中文字
private val PP_BG = Color(0xFFF5F5F5)       // 页面灰底

/** 5 个切换按钮（顺序固定） */
private val POS_TABS = listOf("买入", "卖出", "撤单", "持仓", "查询")

@Composable
fun PositionPage(
    token: String = "",
    /** 默认选中项：按用户要求默认「持仓」 */
    defaultTab: String = "持仓",
    /** 回吐当前资金账户（上行=券商名，下行=账号），供红条两行文字显示 */
    onAccountChange: (String, String) -> Unit = { _, _ -> }
) {
    var curTab by remember { mutableStateOf(defaultTab) }

    // ★ 进页时拉一次「我的券商账户」，取第一个回吐给红条显示。
    //   失败静默忽略（红条文字留空），不影响页面其它部分。
    LaunchedEffect(token) {
        if (token.isNotEmpty()) {
            try {
                val list = Api.brokerList(token)
                val first = list.firstOrNull()
                if (first != null) {
                    onAccountChange(first.broker, first.account)
                }
            } catch (_: Throwable) {
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PP_BG)
    ) {
        // ===== ② 5 个切换按钮（固定不滚动）=====
        PosTabBar(cur = curTab, onSelect = { curTab = it })

        // ===== ③ 内容区：本轮先空占位 =====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            // 列表数据 / 统计数字留待后续步骤；此处仅占位（保持空，不误导）
        }
    }
}

/**
 * 顶部 5 切换按钮栏：
 *   - Row 内 5 等分（weight(1f)），固定不滚动
 *   - 未选中：深灰文字；选中：红色文字 + 红色下划线（宽度 = 按钮宽度）
 *   - 默认选中「持仓」
 */
@Composable
private fun PosTabBar(cur: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        POS_TABS.forEach { t ->
            val selected = t == cur
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelect(t) },
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = t,
                    color = if (selected) PP_RED else PP_GRAY,
                    fontSize = 15.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1
                )
                Spacer(Modifier.height(5.dp))
                // 下划线：仅选中项显示，宽度 = 按钮宽度（即 weight 分到的整格宽）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                        .background(if (selected) PP_RED else Color.Transparent)
                )
            }
        }
    }
}