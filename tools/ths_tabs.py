import re, sys
f = sys.argv[1] if len(sys.argv)>1 else '/sdcard/Download/ths_tabs/cur.xml'
s = open(f, encoding='utf-8').read()
want = ['看点','资讯','盘口','资金','社区']
print('=== 标签实时坐标 ===')
for m in re.finditer(r'<node[^>]*?text="([^"]+)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s):
    t = m.group(1)
    x1,y1,x2,y2 = map(int, m.groups()[1:])
    if t in want or '简况' in t:
        print('%-12s 中心=(%d,%d)  bounds=[%d,%d][%d,%d]' % (t,(x1+x2)//2,(y1+y2)//2,x1,y1,x2,y2))
print()
print('=== 屏幕上所有可点文字(前40) ===')
n=0
for m in re.finditer(r'<node[^>]*?text="([^"]+)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s):
    t=m.group(1); x1,y1,x2,y2=map(int,m.groups()[1:])
    print('%-20s (%d,%d)'%(t[:20],(x1+x2)//2,(y1+y2)//2)); n+=1
    if n>=40: break
