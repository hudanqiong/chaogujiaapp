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
    detailTags: List<String> = emptyList(),
    detailCanPrev: Boolean = false,
    detailCanNext: Boolean = false,
    onDetailBack: () -> Unit = {},
    onDetailPrev: () -> Unit = {},
    onDetailNext: () -> Unit = {},
    // ===== 交易页模式（current == "trade"）专用参数：账户类型切换按钮 =====
    tradeTab: String = "A股",
    onTradeTabSelect: (String) -> Unit = {},
    // ===== 持仓页模式（current == "position"）专用参数：第 34 轮新增 =====
    // 红条内容：[<返回] + 两行文字(证券名/资金账户) + ▼ + 方框箭头 + 放大镜 + 刷新
    positionName: String = "",        // 上行：证券名称（大字粗体白）
    positionAccount: String = "",     // 下行：资金账户（小字）
    onPositionBack: () -> Unit = {},  // 点 [<] 返回交易页
    onPositionSwitch: () -> Unit = {},// 点 ▼ 切换账户
    onPositionSquare: () -> Unit = {},// 第 4 格（方框+箭头）
    onPositionSearch: () -> Unit = {},// 放大镜
    onPositionRefresh: () -> Unit = {}, // 刷新
    // ===== 资讯页紧凑模式（current == "news"）：红条压矮到仅盖状态栏，
    //       腾出空间给网页自带的频道切换栏，与红条拼成一体。其他页保持 false 零影响。
    compact: Boolean = false
) {
    // ★ 只有详情页是 edge-to-edge（状态栏透明、由内容绘制），
    //   此时红条背景必须铺满到屏幕最顶（含状态栏区域），内容再下移该高度；
    //   否则状态栏会露出系统色(纯 #F03036)，与红条渐变形成“上下两截不同红”的 bug。
    //   其他页面未开 edge-to-edge，红条保持原高度、由系统画状态栏，不受影响。
    val edgeToEdge = current == "detail"
    val statusInset =
        if (edgeToEdge) WindowInsets.statusBars.asPaddingValues().calculateTopPadding() else 0.dp
    // ★ 资讯页紧凑：红条清零内容、高度压到 28dp（仅盖住状态栏区域）
    val barHeight = if (compact) 28.dp else BarHeight
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(barHeight + statusInset)   // 详情页 = 74dp + 状态栏；资讯紧凑 = 28dp；其他页 = 74dp
            .background(
                Brush.verticalGradient(
                    listOf(Color(Config.COLOR_PRIMARY), Color(0xFFD0202A))
                )
            )
            .padding(top = statusInset),       // 详情页内容下移；其他页为 0
        contentAlignment = Alignment.Center             // 内容整体在红条内居中
    ) {
        when {
            // ★ 资讯紧凑模式：红条只作为状态栏背景色块，不渲染任何内容
            compact -> Unit
            current == "index" -> MemberInfoRow(username, isMember, levelName, expireAt)

            // 自选页：同花顺自选 + 三图标
            current == "watch" -> WatchHeaderRow(onOpenSearch)
            // ★ 交易页：红条内直接放「账户类型切换按钮」(A股/基金/期货/黄金/模拟)
            current == "trade" -> TradeHeaderRow(cur = tradeTab, onSelect = onTradeTabSelect)

            // ★ 持仓页（第 34 轮）：[<返回] + 两行文字 + ▼ + 方框箭头 + 放大镜 + 刷新
            current == "position" -> PositionHeaderRow(
                name = positionName,
                account = positionAccount,
                onBack = onPositionBack,
                onSwitch = onPositionSwitch,
                onSquare = onPositionSquare,
                onSearch = onPositionSearch,
                onRefresh = onPositionRefresh
            )

            // ★ 详情页：唯一红条 [←返回] ◀名称/代码▶ [图二][图三]
            current == "detail" -> DetailHeaderRow(
                name = detailName,
                code = detailCode,
                tags = detailTags,
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
    tags: List<String> = emptyList(),
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
        // 左侧：返回箭头（返回上一步）— 使用自定义图片图标
        Image(
            painter = painterResource(R.drawable.ic_detail_back),
            contentDescription = "返回",
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(D_ICON_SMALL)
                .clickable { onBack() },
            contentScale = ContentScale.Fit
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = code,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontFamily = HSpNumFont,
                        maxLines = 1
                    )
                    if (tags.isNotEmpty()) {
                        Spacer(Modifier.width(4.dp))
                        DetailHeaderTagRow(tags = tags, maxShow = 2, fontSize = 9f)
                    }
                }
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

/**
 * 持仓页顶栏内容（第 34 轮新增，current == "position"）。
 *
 * 从左到右：
 *   [<返回]  两行文字(证券名称 / 资金账户)  ▼   |   方框箭头  放大镜  刷新
 *   - <   ：返回交易页
 *   - ▼   ：切换资金账户
 *   - 第4格(方框+箭头)：ic_pos_sqarrow.png 用 v8 原样，右上角那团多余实心块
 *                        用红条同色小方块在 App 层遮挡掉（不重画图标，只遮挡）。
 *   - 放大镜 / 刷新：各自回调
 */
@Composable
private fun PositionHeaderRow(
    name: String,
    account: String,
    onBack: () -> Unit,
    onSwitch: () -> Unit,
    onSquare: () -> Unit,
    onSearch: () -> Unit,
    onRefresh: () -> Unit
) {
    // ★ 尺寸定稿（第 34 轮真机反馈）：
    //   屏幕宽 360dp。右侧三图标总宽务必＜1/3 屏宽，
    //   保证：① 能整排塞进右边 ② 与中间文字之间留出间距 ③ 右端留一点边距。
    //   36 + 1 + 38 + 1 + 38 = 114dp（约屏宽 32%），右侧尚余约 8~10dp。
    val backSize = 55.dp
    val squareSize = 36.dp
    val bigIconSize = 38.dp
    // ★ 第 34 轮字号分级（按真机反馈）：
    //   证券名称：加大一档
    //   切换三角：比证券名称字体略小（联动）
    //   资金账户：小一号，且改用价格字体（ths_regular）
    val nameFont = 18.sp
    val accountFont = 11.sp
    // 切换三角：用字符 ▼，字号比证券名称略小
    val triFont = 14.sp
    // 右侧三图标之间的间距：一根牙签直径（约 1dp）
    val pickGap = 1.dp
    // 红条本身是「垂直渐变」，遮罩若用纯色必有色差；
    // 因此遮罩直接复用同一条渐变，取右上角区域裁切，做到与红条无缝。
    val barBrush = Brush.verticalGradient(
        listOf(Color(Config.COLOR_PRIMARY), Color(0xFFD0202A))
    )

    // ★ 第 34 轮真机反馈（三段式，位置固定）：
    //   左  = 返回图标（贴左）
    //   中  = 证券名 / 账户号 / 切换三角（红条正中央，三者整体居中）
    //   右  = 方框 + 放大镜 + 刷新（贴右，挨成一簇）
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // ==== 左：返回（贴左，垂直居中）====
        Image(
            painter = painterResource(R.drawable.ic_pos_back),
            contentDescription = "返回",
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(backSize)
                .clickable { onBack() },
            contentScale = ContentScale.Fit
        )

        // ==== 中：证券名 / 账户号 / 三角 ====
        // ★ 第 34 轮：整块（证券名 + 资金账户 + 三角）都可点击切换，
        //   命中区域覆盖三者的并集；点击无任何特效（indication = null）。
        Row(
            modifier = Modifier
                .align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = name,
                    color = Color.White,
                    fontSize = nameFont,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = account,
                    color = Color.White,
                    fontSize = accountFont,
                    fontFamily = HSpNumFont,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(pickGap))
            // ★ 第 34 轮：切换三角改为「白色实心倒三角字符 ▼」。
            //   位置：放在两行（名称/账户）中间的右侧，且相对两行「上下居中」。
            //   做法：外层 Box 显式 align(CenterVertically) 锁定垂直居中；
            //        用 lineHeight 收紧行高，并用 offset 做视觉基线补偿。
            //   点击：不再单独 clickable，由父 Row 统一处理（含特效抑制），
            //        三角本身只负责显示。
            Box(
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .size(20.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSwitch() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "▼",
                    color = Color.White,
                    fontSize = triFont,
                    fontWeight = FontWeight.Bold,
                    lineHeight = triFont,
                    modifier = Modifier.offset(y = 3.dp)
                )
            }
        }

        // ==== 右：方框 + 放大镜 + 刷新（贴右，挨成一簇）====
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(squareSize)
                    .clickable { onSquare() }
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_pos_sqarrow),
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Fit
                )
                // ★ 遮挡 v8 图标「右上角连在箭头前端的多余实心块」：
                //   90° 直角「帽子」，只盖右上角拐角那一小块，保证线条完整不断。
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .fillMaxWidth(0.34f)
                        .fillMaxHeight(0.34f)
                        .background(barBrush)
                )
            }
            Spacer(Modifier.width(pickGap))
            Image(
                painter = painterResource(R.drawable.ic_pos_search),
                contentDescription = "搜索",
                modifier = Modifier
                    .size(bigIconSize)
                    .clickable { onSearch() },
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.width(pickGap))
            Image(
                painter = painterResource(R.drawable.ic_pos_refresh),
                contentDescription = "刷新",
                modifier = Modifier
                    .size(bigIconSize)
                    .clickable { onRefresh() },
                contentScale = ContentScale.Fit
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
/**
 * 详情页顶栏标签行（唯一红条上的标签）。
 * 视觉与详情页内部标签一致：白半透明圆角小块；主色标签（融/创/新/次新/T+0/场内）底色更亮。
 * 红条上不滚动，最多显示 maxShow 个。
 */
@Composable
private fun DetailHeaderTagRow(tags: List<String>, maxShow: Int = 2, fontSize: Float = 9f) {
    val show = tags.filter { it != "场内" }
    if (show.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        show.take(if (maxShow <= 0) show.size else maxShow).forEach { t ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(Config.COLOR_TAG_BG))
                    .padding(horizontal = 3.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = t,
                    color = Color(Config.COLOR_TAG_FG),
                    fontSize = fontSize.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
            Spacer(Modifier.width(3.dp))
        }
    }
}