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
