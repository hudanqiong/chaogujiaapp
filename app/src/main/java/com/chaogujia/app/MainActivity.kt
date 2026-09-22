package com.chaogujia.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChaoGuJiaApp()
        }
    }
}

private data class TabItem(
    val key: String,
    val label: String,
    val normalRes: Int,
    val checkedRes: Int
)

@Composable
fun ChaoGuJiaApp() {
    val tabs = listOf(
        TabItem("index", "首页", R.drawable.tab_home, R.drawable.tab_home_checked),
        TabItem("quote", "行情", R.drawable.tab_quote, R.drawable.tab_quote_checked),
        TabItem("watch", "自选", R.drawable.tab_watch, R.drawable.tab_watch_checked),
        TabItem("trade", "交易", R.drawable.tab_trade, R.drawable.tab_trade_checked),
        TabItem("news", "资讯", R.drawable.tab_news, R.drawable.tab_news_checked)
    )

    val ctx = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("chaogujia", Context.MODE_PRIVATE) }

    // 启动默认进"行情"页
    var current by remember { mutableStateOf("quote") }
    var token by remember { mutableStateOf(prefs.getString("token", "") ?: "") }
    var uname by remember { mutableStateOf(prefs.getString("username", "") ?: "") }
    var showLogin by remember { mutableStateOf(false) }
    var targetTab by remember { mutableStateOf("index") }

    // 会员信息（5 页共用，由 MainActivity 统一拉）
    var member by remember { mutableStateOf(MemberInfo()) }

    // token 变化时重新拉会员信息（登录后立刻刷新）
    LaunchedEffect(token) {
        if (token.isNotEmpty()) {
            try {
                member = withContext(Dispatchers.IO) { Api.member(token) }
            } catch (e: Exception) {
                member = MemberInfo()
            }
        } else {
            member = MemberInfo()
        }
    }

    // 退出登录
    fun doLogout() {
        token = ""
        uname = ""
        prefs.edit().remove("token").remove("username").apply()
        current = "quote"
        member = MemberInfo()
    }

    Scaffold(
        topBar = {
            // 红色会员顶栏：5 个 Tab 页面统一渲染，高度一致
            // 内容按页面切换：首页=会员信息，自选页=同花顺自选+三图标，其他=空占位
            MemberHeaderBar(
                current = current,
                username = uname,
                isMember = member.isMember,
                levelName = member.levelName,
                expireAt = member.expireAt
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF5F5F5))
                    .navigationBarsPadding()
            ) {
                tabs.forEach { t ->
                    val selected = current == t.key
                    val color = if (selected) Color(Config.COLOR_PRIMARY) else Color(Config.COLOR_TEXT_MAIN)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    if (t.key != "quote" && token.isEmpty()) {
                                        targetTab = t.key
                                        showLogin = true
                                    } else {
                                        current = t.key
                                    }
                                }
                            )
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        TabIcon(selected, t.normalRes, t.checkedRes)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = t.label,
                            color = color,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.White)
        ) {
            when (current) {
                "index" -> HomeScreen(
                    token = token,
                    username = uname,
                    onLogout = { doLogout() },
                    onMemberChanged = { member = it }
                )
                "quote" -> FontComparePage()
                // 自选页：红条正下方紧贴两排工具条；下方是白色内容区。
                // 点 ▼ 展开的「指数详情面板」悬浮在内容区之上（不挤压内容区高度）。
                "watch" -> Box(Modifier.fillMaxSize().background(Color.White)) {
                    // 纯白背景层（红条与分割线由 IndexBar 内部绘制，详情面板 overlay 悬浮其上）
                    // 叠在最上方：顶部工具条 + 分割线 + （展开时）详情面板
                    IndexBar()
                }
                "trade" -> EmptyScreen()
                "news" -> EmptyScreen()
            }

            if (showLogin) {
                LoginScreen(
                    onSuccess = { tk, un ->
                        token = tk
                        uname = un
                        prefs.edit().putString("token", tk).putString("username", un).apply()
                        showLogin = false
                        current = targetTab
                    },
                    onClose = { showLogin = false }
                )
            }
        }
    }
}

// 页面内容区：暂时留空，等后续接入真实接口后再填充
@Composable
fun EmptyScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    )
}

// 底部导航图标
@Composable
fun TabIcon(selected: Boolean, normalRes: Int, checkedRes: Int) {
    Image(
        painter = painterResource(if (selected) checkedRes else normalRes),
        contentDescription = null,
        modifier = Modifier.size(24.dp),
        contentScale = ContentScale.Fit
    )
}