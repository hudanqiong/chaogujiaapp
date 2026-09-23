#!/usr/bin/env python3
# 从同花顺截图里抠出"跌绿色"主色
from PIL import Image
from collections import Counter
import sys

path = sys.argv[1] if len(sys.argv) > 1 else "/sdcard/Download/ths.png"
im = Image.open(path).convert("RGB")
W, H = im.size
print(f"size = {W} x {H}")

px = im.load()

# 收集"绿"像素：G 明显大于 R 与 B，且不是灰/黑/白（排除背景）
greens = Counter()
greens_strict = Counter()
total = 0
for y in range(H):
    for x in range(W):
        r, g, b = px[x, y]
        total += 1
        # 绿：g 是主通道，且和 r/b 有差距
        if g > r + 18 and g > b + 18 and g > 60:
            # 量化到 4 的倍数，聚峰
            qr = (r // 4) * 4 + 2
            qg = (g // 4) * 4 + 2
            qb = (b // 4) * 4 + 2
            greens[(qr, qg, qb)] += 1
            # 严格：饱和度更高
            mx, mn = max(r, g, b), min(r, g, b)
            if mx > 0 and (mx - mn) / mx > 0.35:
                greens_strict[(qr, qg, qb)] += 1

print(f"total px = {total}")
print(f"green-ish px = {sum(greens.values())}  ({100*sum(greens.values())/total:.3f}%)")
print(f"green-strict px = {sum(greens_strict.values())}")

print("\n=== Top 20 green-ish (宽松) ===")
for (r, g, b), c in greens.most_common(20):
    print(f"  #{r:02X}{g:02X}{b:02X}  rgb({r},{g},{b})   count={c}")

print("\n=== Top 20 green-strict (高饱和) ===")
for (r, g, b), c in greens_strict.most_common(20):
    print(f"  #{r:02X}{g:02X}{b:02X}  rgb({r},{g},{b})   count={c}")
