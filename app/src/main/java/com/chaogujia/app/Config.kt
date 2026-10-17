package com.chaogujia.app

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/** 全局配置：后端接口地址等 */
object Config {
    // 你的服务器接口根地址（末尾要带 /）
    const val BASE_URL = "http://121.43.178.214:8080/stock_api/"
    // 资讯站地址（第十二轮建站，外网可访问）
    const val NEWS_URL = "http://121.43.178.214:8080/news/"
    // 资产分析页（AA 页，WebView 内嵌版；部署在 80 端口）
    const val AA_URL = "http://121.43.178.214/aa/"

    // ---- 第二十七轮：H5 全页面封装（三个 H5 页统一收口，WebView 内嵌）----
    // 资产分析（蓝条首页）
    const val H5_INDEX = "http://121.43.178.214/aa/index.html"
    // 持仓/交易（红条五 tab）
    const val H5_POSITION = "http://121.43.178.214/aa/position.html"
    // 个股详情
    const val H5_DETAIL = "http://121.43.178.214/aa/detail.html"
    // ---- 第 31 轮：首页/行情/自选/交易 四页全部改走 H5 ----
    // 首页（= 会员中心，红条 + 功能卡 + 底部 tab）
    const val H5_HOME = "http://121.43.178.214/aa/home.html"
    // 行情 / 自选（当前共用一个 H5 页）
    const val H5_WATCH = "http://121.43.178.214/aa/watch.html"
    // 交易（红条 + 买卖/持仓/撤单/查询）
    const val H5_TRADE = "http://121.43.178.214/aa/trade.html"
    // 主题色（同花顺红 #F03036）
    const val COLOR_PRIMARY = 0xFFF03036.toInt()
    const val COLOR_TEXT_MAIN = 0xFF222222.toInt()
    const val COLOR_TEXT_SUB = 0xFF888888.toInt()
    const val COLOR_DIVIDER = 0xFFEEEEEE.toInt()
    // ---- 股票标签（融/创/新/次新/深股通/沪股通/京A/L1/L2/T+0/场内）----
    // 主色标签：背景 #E48D48 + 米白字（无边框）
    const val COLOR_TAG_BG = 0xFFE48D48.toInt()
    const val COLOR_TAG_FG = 0xFFFDF6EE.toInt()
    // 灰色标签（T+0/场内）：透明底 + 浅灰字 + 浅灰细边框
    const val COLOR_TAG_GRAY_FG = 0xFFB0B0B0.toInt()
    const val COLOR_TAG_GRAY_BORDER = 0xFFD8D8D8.toInt()

    /**
     * 交易数据版本号（全局刷新信号）。
     *
     * 背景：一键模拟（simulate.php）成功后，交易/持仓/marks 已真实落库，
     * 但详情页的 K 线数据由 LaunchedEffect(current.code, period) 驱动，
     * 仅在「切股票 / 切周期」时重拉。用户生成后停留在原页面不会自动刷新，
     * 于是「刚生成的 BST 标记看不到」——这是使用层假象，而非后端问题。
     *
     * 约定：任何会改动 trades / positions / marks 的操作（一键模拟、下单、
     * 交易记录增删改等）成功后自增本值；K 线详情页监听它并重新拉取 detail。
     */
    var tradeDataVersion by mutableIntStateOf(0)
        private set

    /** 通知全局：交易相关数据已变更，需要刷新（K 线 marks / 持仓 / 资金等）。 */
    fun bumpTradeData() {
        tradeDataVersion += 1
    }
}
