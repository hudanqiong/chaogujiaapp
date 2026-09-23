#!/usr/bin/env python3
# 精准提取"竖直柱体"的颜色：柱体 = 某列上连续>15px 的同色段
from PIL import Image
from collections import Counter

im = Image.open("/sdcard/Download/ths.png").convert("RGB")
W, H = im.size
px = im.load()

def is_green(p):
    r, g, b = p
    mx, mn = max(p), min(p)
    sat = (mx - mn) / mx if mx else 0
    return g >= r and g >= b and sat > 0.4 and g > 90

# 找竖直绿柱：逐列扫描，连续绿色段 >= 20px
vert_green = Counter()
for x in range(W):
    run = 0
    runcol = None
    for y in range(H):
        p = px[x, y]
        if is_green(p):
            run += 1
            runcol = p
        else:
            if run >= 20 and runcol:
                vert_green[runcol] += 1
            run = 0
            runcol = None

print("=== 竖直绿柱体颜色 top15（这些才是真正的柱子）===")
for (r, g, b), n in vert_green.most_common(15):
    print(f"   #{r:02X}{g:02X}{b:02X}  rgb({r},{g},{b})  n={n}")

# 同样的方法抓"竖直红柱"做对照
def is_red(p):
    r, g, b = p
    mx, mn = max(p), min(p)
    sat = (mx - mn) / mx if mx else 0
    return r >= g and r >= b and sat > 0.4 and r > 90

vert_red = Counter()
for x in range(W):
    run = 0
    runcol = None
    for y in range(H):
        p = px[x, y]
        if is_red(p):
            run += 1
            runcol = p
        else:
            if run >= 20 and runcol:
                vert_red[runcol] += 1
            run = 0
            runcol = None

print("\n=== 竖直红柱体颜色 top10（对照）===")
for (r, g, b), n in vert_red.most_common(10):
    print(f"   #{r:02X}{g:02X}{b:02X}  rgb({r},{g},{b})  n={n}")