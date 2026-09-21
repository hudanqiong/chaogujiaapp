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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.interaction.MutableInteractionSource
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
                tabs.forEach { t ->
                    val selected = current == t.key
                    val color = if (selected) Color(Config.COLOR_PRIMARY) else Color(Config.COLOR_TEXT_MAIN)
                    // 关闭点击水波涟漪
                    CompositionLocalProvider(LocalIndication provides NoIndication) {
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

// 空指示器：点击时不做任何涟漪/水波效果
private object NoIndication : androidx.compose.foundation.Indication {
    @Composable
    override fun rememberUpdatedInstance(interactionSource: androidx.compose.foundation.interaction.InteractionSource): androidx.compose.foundation.IndicationInstance =
        object : androidx.compose.foundation.IndicationInstance {
            override fun DrawScope.drawIndication() { /* 什么都不画 */ }
        }
}