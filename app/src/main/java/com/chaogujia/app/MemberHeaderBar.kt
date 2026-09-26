package com.chaogujia.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 顶栏数字字体（同花顺金融体，与详情页/搜索页保持一致） */
private val HSpNumFont = FontFamily(Font(R.font.ths_regular))

/**
 * 红条固定总高度：所有页面（首页/自选/行情/交易/资讯）统一使用。
 * 红条高度写死，不再由内容撑高 —— 这样图标怎么调都不影响整体高度。
 */
private val BarHeight = 74.dp

/**
 * 共用红色顶栏（5 个 Tab 页面共用，高度固定一致）
 *
 * 内容按当前页面切换：
 *   - index（首页）   ：头像 + 昵称 + 等级 + 到期时间
 *   - watch（自选）   ：图标1 + "同花顺自选"(居中) + 图标2 + 图标3
 *   - 其他页面        ：空占位（保持高度一致）
 */
@Composable
fun MemberHeaderBar(
    current: String = "index",
    username: String = "",
    isMember: Boolean = false,
    levelName: String = "普通用户",
    expireAt: String = "",
    onOpenSearch: () -> Unit = {},
    // ===== 详情页模式（current == "detail"）专用参数 =====
    detailName: String = "",
    detailCode: String = "",
    detailCanPrev: Boolean = false,
    detailCanNext: Boolean = false,
    onDetailBack: () -> Unit = {},
    onDetailPrev: () -> Unit = {},
    onDetailNext: () -> Unit = {},
    // ===== 交易页模式（current == "trade"）专用参数：账户类型切换按钮 =====
    tradeTab: String = "A股",
    onTradeTabSelect: (String) -> Unit = {}
) {
    // ★ 只有详情页是 edge-to-edge（状态栏透明、由内容绘制），
    //   此时红条背景必须铺满到屏幕最顶（含状态栏区域），内容再下移该高度；
    //   否则状态栏会露出系统色(纯 #F03036)，与红条渐变形成“上下两截不同红”的 bug。
    //   其他页面未开 edge-to-edge，红条保持原高度、由系统画状态栏，不受影响。
    val edgeToEdge = current == "detail"
    val statusInset =
        if (edgeToEdge) WindowInsets.statusBars.asPaddingValues().calculateTopPadding() else 0.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(BarHeight + statusInset)   // 详情页 = 74dp + 状态栏；其他页 = 74dp
            .background(
                Brush.verticalGradient(
                    listOf(Color(Config.COLOR_PRIMARY), Color(0xFFD0202A))
                )
            )
            .padding(top = statusInset),       // 详情页内容下移；其他页为 0
        contentAlignment = Alignment.Center             // 内容整体在红条内居中
    ) {
        when (current) {
            // 首页：会员信息
            "index" -> MemberInfoRow(username, isMember, levelName, expireAt)

            // 自选页：同花顺自选 + 三图标
            "watch" -> WatchHeaderRow(onOpenSearch)
            // ★ 交易页：红条内直接放「账户类型切换按钮」(A股/基金/期货/黄金/模拟)
            "trade" -> TradeHeaderRow(cur = tradeTab, onSelect = onTradeTabSelect)

            // ★ 详情页：唯一红条 [←返回] ◀名称/代码▶ [图二][图三]
            "detail" -> DetailHeaderRow(
                name = detailName,
                code = detailCode,
                canPrev = detailCanPrev,
                canNext = detailCanNext,
                onBack = onDetailBack,
                onPrev = onDetailPrev,
                onNext = onDetailNext,
                onOpenSearch = onOpenSearch
            )

            // 其他页面：什么都不放（红条高度已写死，依然与其他页一致）
            else -> Unit
        }
    }
}

/**
 * 详情页顶栏（唯一红条）：
 *   [← 返回上一步]  ◀ 股票名/代码 ▶  [图二 ic_watch_mid][图三 ic_watch_right → 搜索]
 *
 * 注意：这是全 App 唯一的一条详情红条。
 *      原先详情页内部自己画的第二条红条已删除，功能全部合并到这里。
 *      - 左上角 ← ：返回上一步（历史回退，非固定回自选页）
 *      - ◀ ▶    ：切换上/下一只股票（同一页内切换，不改变层级）
 *      - 图二   ：仅展示
 *      - 图三   ：点开全屏搜索页
 */
private val D_ICON_BIG = 25.dp
private val D_ICON_SMALL = 24.dp

@Composable
private fun DetailHeaderRow(
    name: String,
    code: String,
    canPrev: Boolean,
    canNext: Boolean,
    onBack: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onOpenSearch: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        // 左侧：返回箭头（返回上一步）
        Text(
            text = "←",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .clickable { onBack() }
        )

        // 中间：◀ 名称/代码 ▶
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "◀",
                color = if (canPrev) Color.White else Color.White.copy(alpha = 0.35f),
                fontSize = 14.sp,
                modifier = Modifier
                    .clickable(enabled = canPrev) { onPrev() }
                    .padding(horizontal = 6.dp)
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = name,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = code,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    fontFamily = HSpNumFont,
                    maxLines = 1
                )
            }
            Text(
                text = "▶",
                color = if (canNext) Color.White else Color.White.copy(alpha = 0.35f),
                fontSize = 14.sp,
                modifier = Modifier
                    .clickable(enabled = canNext) { onNext() }
                    .padding(horizontal = 6.dp)
            )
        }

        // 右侧：图二 + 图三（与自选页位置一致）
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.ic_watch_mid),
                contentDescription = null,
                modifier = Modifier.height(D_ICON_SMALL),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.width(6.dp))
            Image(
                painter = painterResource(R.drawable.ic_watch_right),
                contentDescription = "搜索",
                modifier = Modifier
                    .size(D_ICON_BIG)
                    .clickable { onOpenSearch() },
                contentScale = ContentScale.FillBounds
            )
        }
    }
}

/** 首页：会员信息行（头像 + 昵称 + 等级 + 到期时间） */
@Composable
private fun MemberInfoRow(
    username: String,
    isMember: Boolean,
    levelName: String,
    expireAt: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 头像：用户名首字圆形
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = username.take(1).ifEmpty { "股" },
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = username.ifEmpty { "股友" },
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(5.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.22f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (isMember) levelName else "普通用户",
                    color = Color.White,
                    fontSize = 11.sp
                )
            }
        }
        if (expireAt.isNotEmpty()) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "到期时间",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 10.sp
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = expireAt,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * 自选页顶栏：图标1(最左) + "同花顺自选"(居中) + 图标2、3(靠右)
 *
 * - 三个图标 **中线对齐**（排在同一条水平线上）
 * - 图标尺寸在这里单独调（ICON_BIG / ICON_SMALL）
 * - 红条总高度由 BarHeight 写死，不受图标大小影响
 * - 图标仅展示，不绑定点击
 */
private val ICON_BIG = 25.dp      // 图1、图3
private val ICON_SMALL = 24.dp    // 图2

@Composable
private fun WatchHeaderRow(onOpenSearch: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 13.dp),   // ★整体下移(缩小下边距)
        contentAlignment = Alignment.Center
    ) {
        // 图标1：靠最左侧
        Image(
            painter = painterResource(R.drawable.ic_watch_left),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(ICON_BIG),
            contentScale = ContentScale.FillBounds
        )

        // 居中文字
        Text(
            text = "同花顺自选",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center)
        )

        // 图标2、3：靠右
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically   // ★ 中线对齐
        ) {
            Image(
                painter = painterResource(R.drawable.ic_watch_mid),
                contentDescription = null,
                modifier = Modifier.height(ICON_SMALL),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.width(6.dp))
            Image(
                painter = painterResource(R.drawable.ic_watch_right),
                contentDescription = "搜索",
                modifier = Modifier
                    .size(ICON_BIG)
                    .clickable { onOpenSearch() },   // ★ 点最右图标 → 全屏搜索页
                contentScale = ContentScale.FillBounds
            )
        }
    }
}

/** 交易页顶栏内容：账户类型切换按钮（A股/基金/期货/黄金/模拟），居中一排，红底白字 */
private val TRADE_TABS = listOf("A股", "基金", "期货", "黄金", "模拟")

@Composable
private fun TradeHeaderRow(cur: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),            // ★ 整排下移：距红条顶部 12dp（沉到红条下半区）
        horizontalArrangement = Arrangement.Center,   // ★ 整排水平居中
        verticalAlignment = Alignment.CenterVertically
    ) {
        TRADE_TABS.forEachIndexed { i, t ->
            val selected = t == cur
            if (i > 0) Spacer(Modifier.width(8.dp))   // ★ 按钮之间的小间距
            Text(
                text = t,
                color = if (selected) Color.White else Color.White.copy(alpha = 0.9f),
                fontSize = 16.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelect(t) }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }
    }
}