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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale

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
    // token / 用户名：启动时从本地读回（免重复登录）
    var token by remember { mutableStateOf(prefs.getString("token", "") ?: "") }
    var uname by remember { mutableStateOf(prefs.getString("username", "") ?: "") }
    // 登录弹层开关 + 登录成功后要跳转的目标 Tab
    var showLogin by remember { mutableStateOf(false) }
    var targetTab by remember { mutableStateOf("index") }

    Scaffold(
        topBar = {
            // 顶部红色区域（5 个页面共用）：从屏幕最顶铺下来，含状态栏，内容区 60dp
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(Config.COLOR_PRIMARY))
                    .statusBarsPadding()
            ) {
                Spacer(Modifier.height(60.dp))
            }
        },
        bottomBar = {
            // 自定义底部导航：clickable(indication = null) 彻底去掉水波
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
                                    // 只有"行情"不需要登录，其他点击都要登录
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
                "index" -> HomeScreen(token, uname)
                "quote" -> EmptyScreen()
                "watch" -> EmptyScreen()
                "trade" -> EmptyScreen()
                "news" -> EmptyScreen()
            }

            // 登录弹层（覆盖在内容之上）
            if (showLogin) {
                LoginScreen(
                    onSuccess = { tk, un ->
                        token = tk
                        uname = un
                        prefs.edit().putString("token", tk).putString("username", un).apply()
                        showLogin = false
                        current = targetTab        // 登录成功 → 进入目标页
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

// 底部导航图标：选中显示点亮版，未选中显示普通版（图片来自 res/drawable）
@Composable
fun TabIcon(selected: Boolean, normalRes: Int, checkedRes: Int) {
    Image(
        painter = painterResource(if (selected) checkedRes else normalRes),
        contentDescription = null,
        modifier = Modifier.size(24.dp),
        contentScale = ContentScale.Fit
    )
}