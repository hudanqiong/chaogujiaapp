# -*- coding: utf-8 -*-
# patch_header_icons.py
# 把 IndexBar.kt 表头固定列「名称 / 代码」文字改为两个 24dp 图标按钮
# 规则：selectedGroup != -2（非持仓股）显示图标；持仓股仍显示原文字
import io, sys

FP = '/root/cgj/app/src/main/java/com/chaogujia/app/IndexBar.kt'

OLD = '''                    // 上层：固定列「名称 / 代码」，带白底，盖住滑过来的统计列
                    Box(
                        Modifier
                            .align(Alignment.CenterStart)
                            .width(fixedW.dp)
                            .fillMaxHeight()
                            .background(Color.White)
                    ) {
                        Text(
                            text = "名称 / 代码",
                            color = COLOR_TEXT,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 16.dp)
                        )
                    }'''

NEW = '''                    // 上层：固定列「名称 / 代码」区，带白底，盖住滑过来的统计列
                    // 非持仓股分组：显示两个 24dp 图标（①表头管理页 ②列表排序）
                    // 持仓股分组(-2)：暂仍显示原文字，后续单独实现
                    Box(
                        Modifier
                            .align(Alignment.CenterStart)
                            .width(fixedW.dp)
                            .fillMaxHeight()
                            .background(Color.White)
                    ) {
                        if (selectedGroup != -2) {
                            Row(
                                modifier = Modifier.align(Alignment.CenterStart),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_watch_add),
                                    contentDescription = "编辑表头",
                                    modifier = Modifier
                                        .padding(start = 10.dp)
                                        .size(24.dp)
                                        .clickable {
                                            // TODO: 进入「表头管理页」（交互口径待定）
                                        }
                                )
                                Image(
                                    painter = painterResource(id = R.drawable.ic_watch_share),
                                    contentDescription = "排序",
                                    modifier = Modifier
                                        .padding(start = 16.dp)
                                        .size(24.dp)
                                        .clickable {
                                            // 排序：暂不接逻辑（空按钮）
                                        }
                                )
                            }
                        } else {
                            Text(
                                text = "名称 / 代码",
                                color = COLOR_TEXT,
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 16.dp)
                            )
                        }
                    }'''

src = io.open(FP, 'r', encoding='utf-8').read()

if src.count(OLD) != 1:
    print('ERROR: OLD 匹配数 =', src.count(OLD))
    sys.exit(1)

out = src.replace(OLD, NEW, 1)
io.open(FP, 'w', encoding='utf-8').write(out)

# 校验
chk = io.open(FP, 'r', encoding='utf-8').read()
print('OK: patch applied')
print('ic_watch_add 出现次数 =', chk.count('ic_watch_add'))
print('ic_watch_share 出现次数 =', chk.count('ic_watch_share'))
print('selectedGroup != -2 出现次数 =', chk.count('selectedGroup != -2'))
