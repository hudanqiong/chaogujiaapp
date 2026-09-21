package com.chaogujia.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.runtime.CompositionLocalProvider

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
    val drawIcon: @Composable (Boolean) -> Unit
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChaoGuJiaApp() {
    val tabs = listOf(
        TabItem("index", "首页", { sel -> TabIcon(sel, R.drawable.tab_home, R.drawable.tab_home_checked) }),
        TabItem("quote", "行情", { sel -> TabIcon(sel, R.drawable.tab_quote, R.drawable.tab_quote_checked) }),
        TabItem("watch", "自选", { sel -> TabIcon(sel, R.drawable.tab_watch, R.drawable.tab_watch_checked) }),
        TabItem("trade", "交易", { sel -> TabIcon(sel, R.drawable.tab_trade, R.drawable.tab_trade_checked) }),
        TabItem("news", "资讯", { sel -> TabIcon(sel, R.drawable.tab_news, R.drawable.tab_news_checked) })
    )
    var current by remember { mutableStateOf("index") }

    Scaffold(
        topBar = {
            // 顶部红色区域（5 个页面共用）：从屏幕最顶铺下来，含状态栏，内容区 60dp
            // 内容已清空，待重新布局
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
            NavigationBar(containerColor = Color(0xFFF5F5F5)) {
                // 关闭点击时的涟漪水波：把 LocalIndication 置为 null，
                // Compose 1.6 的 clickable 在 indication 为 null 时不绘制任何按压反馈
                CompositionLocalProvider(LocalIndication provides null) {
                    tabs.forEach { t ->
                        val selected = current == t.key
                        val color = if (selected) Color(Config.COLOR_PRIMARY) else Color(Config.COLOR_TEXT_MAIN)
                        NavigationBarItem(
                            selected = selected,
                            onClick = { current = t.key },
                            icon = { t.drawIcon(selected) },
                            // 去掉选中时图标背后的胶囊/圆圈指示器，点亮只改图标和文字颜色
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.Unspecified,
                                selectedTextColor = Color.Unspecified,
                                indicatorColor = Color.Transparent,
                                unselectedIconColor = Color.Unspecified,
                                unselectedTextColor = Color.Unspecified
                            ),
                            label = {
                                Text(
                                    text = t.label,
                                    color = color,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
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
                "index" -> EmptyScreen()
                "quote" -> EmptyScreen()
                "watch" -> EmptyScreen()
                "trade" -> EmptyScreen()
                "news" -> EmptyScreen()
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