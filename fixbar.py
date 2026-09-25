import io
p="app/src/main/java/com/chaogujia/app/StockDetailPage.kt"
L=io.open(p,encoding="utf-8").read().split("\n")
block="""        // ================= 内容区：图表固定高度 + 下方预留模块（可滚动），底部功能栏钉在屏幕底部 =================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // ---- 图表区（固定高度，下方预留其它功能模块）----
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CHART_AREA_HEIGHT)
                ) {
                    when (period) {
                        "minute" -> MinuteChart(detail, showAuction, symmetricAxis)
                        "fiveday" -> FiveDayChart(detail)
                        else -> KLineChart(detail, selK, maPeriods, showChip = showChip, onMaPeriodsChange = { maPeriods = it }, onSelect = { selK = it })
                    }
                    if (loading && detail == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("加载中…", color = TEXT_SUB, fontSize = 14.sp)
                        }
                    }
                }
                // ---- 底部栏占位（防止滚动内容被钉底的底部栏遮挡）----
                Spacer(Modifier.height(DETAIL_BAR_HEIGHT))
                // TODO: 后续功能模块追加在此处下方
            }
            // ---- 底部功能栏：钉在屏幕最底 ----
            DetailBottomBar(
                concept = concept,
                watched = watched,
                onOrder = { /* TODO 下单 */ },
                onDiagnose = { /* TODO 诊股 */ },
                onToggleWatch = {
                    val code = current?.code ?: return@DetailBottomBar
                    scope.launch {
                        val isW = watched == true
                        val r = withContext(Dispatchers.IO) {
                            if (isW) Api.delWatch(token, code, 0) else Api.addWatch(token, code, 0)
                        }
                        if (r.ok) watched = !isW
                    }
                },
                onMore = { /* TODO 功能菜单 */ },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}"""
newblock=block.split(chr(10))
# 找到图表区起始行和根Column结束行
start=None
for i,l in enumerate(L):
    if "图表区（固定高度" in l:
        start=i
        break
end=None
for i in range(start,len(L)):
    if L[i].strip()=="}" and i>start+30:
        end=i-1
        break
print("START",start+1,"END",end+1)
print(repr(L[start]),repr(L[end]))
L2=L[:start]+newblock+L[end+5:]
io.open(p,"w",encoding="utf-8").write(chr(10).join(L2))
print("REPLACED_OK")
