package com.chaogujia.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.StrokeCap as DrawStrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 首页 = 会员中心。顶部红色由共用 MemberHeaderBar 负责，这里全是白区卡片 */
@Composable
fun HomeScreen(
    token: String? = null,
    username: String = "",
    onLogout: () -> Unit = {},
    onMemberChanged: (MemberInfo) -> Unit = {}
) {
    var account by remember { mutableStateOf(AccountInfo()) }
    var member by remember { mutableStateOf(MemberInfo()) }
    var points by remember { mutableStateOf(0) }
    var subPage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(token) {
        try {
            val a = withContext(Dispatchers.IO) { Api.account(token) }
            val m = withContext(Dispatchers.IO) { Api.member(token) }
            val p = withContext(Dispatchers.IO) { Api.point(token) }
            account = a
            member = m
            points = p.points
            onMemberChanged(m)
        } catch (e: Exception) {
        }
    }

    if (subPage != null) {
        SubPage(
            key = subPage!!,
            token = token,
            username = username,
            account = account,
            member = member,
            points = points,
            onBack = { subPage = null },
            onRefreshMember = { member = it; onMemberChanged(it) },
            onLogout = onLogout
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        HomeAssetCard { subPage = "initial" }
        Spacer(Modifier.height(12.dp))
        HomePointCard(points) { subPage = it }
        Spacer(Modifier.height(12.dp))
        HomeMenuCard { subPage = it }
        Spacer(Modifier.height(12.dp))
        HomeLogoutCard(onLogout)
        Spacer(Modifier.height(12.dp))
        ChevronSizeCompareCard()
        Spacer(Modifier.height(16.dp))
        Text(
            text = "免责声明：本应用所有行情与交易数据均为模拟演示，仅供学习交流使用，不构成任何投资建议。股市有风险，投资需谨慎。",
            color = Color(Config.COLOR_TEXT_SUB),
            fontSize = 11.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Spacer(Modifier.height(20.dp))
    }
}

// ===== 通用组件 =====
@Composable
fun CardBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(14.dp)
    ) { content() }
}

@Composable
fun SmallBtn(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(Config.COLOR_PRIMARY))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun LabelValue(label: String, value: String) {
    Column {
        Text(label, color = Color(Config.COLOR_TEXT_SUB), fontSize = 11.sp)
        Spacer(Modifier.height(2.dp))
        Text(value, color = Color(Config.COLOR_TEXT_MAIN), fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun MenuRow(title: String, sub: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color(Config.COLOR_TEXT_MAIN), fontSize = 15.sp)
            if (sub.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(sub, color = Color(Config.COLOR_TEXT_SUB), fontSize = 11.sp)
            }
        }
        Text("|", color = Color(Config.COLOR_TEXT_SUB), fontSize = 18.sp)
    }
}

@Composable
fun HDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(Config.COLOR_DIVIDER))
    )
}

// ===== 首页卡片 =====
@Composable
private fun HomeAssetCard(onSetInitial: () -> Unit) {
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("资金设置", color = Color(Config.COLOR_TEXT_MAIN), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text("设置你的初始资金", color = Color(Config.COLOR_TEXT_SUB), fontSize = 11.sp)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSetInitial() }
                    .background(Color(Config.COLOR_PRIMARY).copy(alpha = 0.1f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("设置初始资金", color = Color(Config.COLOR_PRIMARY), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun HomePointCard(points: Int, onNav: (String) -> Unit) {
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("我的积分", color = Color(Config.COLOR_TEXT_SUB), fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Text("$points", color = Color(Config.COLOR_PRIMARY), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            SmallBtn("积分明细") { onNav("points") }
            Spacer(Modifier.width(8.dp))
            SmallBtn("兑换会员") { onNav("exchange") }
        }
    }
}

@Composable
private fun HomeMenuCard(onNav: (String) -> Unit) {
    CardBox {
        Column {
            MenuRow("邀请好友", "邀好友开卡得积分") { onNav("invite") }
            HDivider()
            MenuRow("券商账户", "绑定你的券商席位") { onNav("broker") }
            HDivider()
            MenuRow("自选分组设置", "管理自选股分组") { onNav("groups") }
            HDivider()
            MenuRow("添加自选股", "输入代码加入自选") { onNav("addstock") }
            HDivider()
            MenuRow("修改密码", "修改登录密码") { onNav("pwd") }
        }
    }
}

@Composable
private fun HomeLogoutCard(onLogout: () -> Unit) {
    CardBox {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onLogout() }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("退出登录", color = Color(Config.COLOR_PRIMARY), fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ===== 尖角图标尺寸/角度 对比预览（调参用，可删）=====
/**
 * 用 Canvas 画与 StockDetailPage.ChevronIcon 同几何的 V 形尖角，但尺寸可调。
 * scale  = 尖角整体占外框比例（越小越精致）
 * armRatio = 垂直臂 / 水平臂；=1.0 时尖点夹角严格 90°
 */
@Composable
private fun PreviewChevron(
    tpLeft: Boolean,
    boxSize: androidx.compose.ui.unit.Dp,
    scale: Float,
    armRatio: Float = 1f,
    dbl: Boolean = false,
    rot45: Boolean = false,
    strokeW: androidx.compose.ui.unit.Dp = 1.6.dp,
    tint: Color = Color(0xFF222222)
) {
    Canvas(modifier = Modifier.size(boxSize)) {
        val w = size.width
        val h = size.height
        val sw = strokeW.toPx()
        val armW = w * scale * 0.5f
        val armH = h * scale * 0.5f * armRatio
        val gap = w * 0.06f

        fun chevron(cx: Float, cy: Float, tipLeftLocal: Boolean) {
            // V 形尖角: 两条臂在尖点处交汇，尖头朝外
            val tipX  = if (tipLeftLocal) cx - armW else cx + armW
            val backX = if (tipLeftLocal) cx + armW else cx - armW
            val topY = cy - armH
            val botY = cy + armH
            val path = Path().apply {
                moveTo(backX, topY)
                lineTo(tipX, cy)
                lineTo(backX, botY)
            }
            drawPath(
                path, color = tint,
                style = DrawStroke(
                    width = sw,
                    cap = DrawStrokeCap.Round
                )
            )
        }

        val cx = w / 2f; val cy = h / 2f
        if (dbl) {
            val both: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit = {
                chevron(cx - gap, cy, true); chevron(cx + gap, cy, false)
            }
            if (rot45) {
                rotate(45f, Offset(cx, cy)) { both() }
            } else both()
        } else chevron(cx, cy, tpLeft)
    }
}

/** 圆形底 + 尖角的对比项（模拟真实按钮观感） */
@Composable
private fun CompareItem(label: String, box: androidx.compose.ui.unit.Dp, scale: Float, ratio: Float = 1f, dbl: Boolean = false, rot45: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Color(0x66EEEEEE)),
            contentAlignment = Alignment.Center
        ) {
            PreviewChevron(tpLeft = true, boxSize = box, scale = scale, armRatio = ratio, dbl = dbl, rot45 = rot45)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color(0xFF666666), fontSize = 10.sp)
    }
}

@Composable
private fun ChevronSizeCompareCard() {
    CardBox {
        Column {
            Text("尖角图标尺寸对比（挑一个，告诉我编号）", color = Color(0xFF222222), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("尺寸=圆底内占比；90°=尖点夹角严格直角", color = Color(0xFF999999), fontSize = 10.sp)
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CompareItem("A\n30%.30dp", 30.dp, 0.30f)
                CompareItem("B\n42%.30dp", 30.dp, 0.42f)
                CompareItem("C\n54%.30dp", 30.dp, 0.54f)
                CompareItem("D\n66%.30dp", 30.dp, 0.66f)
            }
            Spacer(Modifier.height(12.dp))
            Text("圆底大小对比（同占比 42%）", color = Color(0xFF999999), fontSize = 10.sp)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CompareItem("24dp", 24.dp, 0.42f)
                CompareItem("30dp", 30.dp, 0.42f)
                CompareItem("36dp", 36.dp, 0.42f)
                CompareItem("42dp", 42.dp, 0.42f)
            }
            Spacer(Modifier.height(12.dp))
            Text("形状对比（42% / 30dp）", color = Color(0xFF999999), fontSize = 10.sp)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CompareItem("尖朝左", 30.dp, 0.42f)
                CompareItem("尖朝右", 30.dp, 0.42f)
                CompareItem("双尖45°", 30.dp, 0.42f, dbl = true, rot45 = true)
                CompareItem("双尖0°", 30.dp, 0.42f, dbl = true, rot45 = false)
            }
        }
    }
}
