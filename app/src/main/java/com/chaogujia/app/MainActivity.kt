package com.chaogujia.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

@Composable
fun ChaoGuJiaApp() {
    val tabs = listOf(
        TabItem("index", "首页", { sel -> IconHome(sel, Modifier.size(24.dp)) }),
        TabItem("quote", "行情", { sel -> IconQuote(sel, Modifier.size(24.dp)) }),
        TabItem("watch", "自选", { sel -> IconWatch(sel, Modifier.size(24.dp)) }),
        TabItem("trade", "交易", { sel -> IconTrade(sel, Modifier.size(24.dp)) }),
        TabItem("news", "资讯", { sel -> IconNews(sel, Modifier.size(24.dp)) })
    )
    var current by remember { mutableStateOf("index") }

    Scaffold(
        topBar = {
            // 顶部红色区域（5 个页面共用）：从屏幕最顶铺下来，含状态栏，内容区约 3 倍状态栏高
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(Config.COLOR_PRIMARY))
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        text = "炒股家",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "🔍 搜索",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFFF5F5F5)) {
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
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.White)
        ) {
            when (current) {
                "index" -> PlaceholderScreen("首页 · 我的账户", "总资产 / 可用资金 / 持仓市值 / 会员信息")
                "quote" -> PlaceholderScreen("行情", "大盘 / A股 / 港股 / 美股 / 基金")
                "watch" -> PlaceholderScreen("自选", "自选股列表")
                "trade" -> PlaceholderScreen("交易", "持仓列表 + 买入卖出")
                "news" -> PlaceholderScreen("资讯", "分类 + 话题 + 资讯流")
            }
        }
    }
}

@Composable
fun PlaceholderScreen(title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(Config.COLOR_TEXT_MAIN))
        Spacer(Modifier.height(8.dp))
        Text(subtitle, fontSize = 14.sp, color = Color(Config.COLOR_TEXT_SUB))
        Spacer(Modifier.height(24.dp))
        Text("炒股家 v1.0 · 骨架版本", fontSize = 12.sp, color = Color(Config.COLOR_TEXT_SUB))
    }
}