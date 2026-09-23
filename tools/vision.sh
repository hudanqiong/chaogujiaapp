#!/bin/bash
# 视觉通道: 调 VL 模型看图片并回答问题
# 用法: vision.sh <图片路径> "<问题>"
# 环境变量: VISION_KEY (必填), VISION_PROVIDER=zhipu|ali (默认zhipu), VISION_MODEL (默认glm-4v-flash)

set -u
IMG="$1"
Q="${2:-请详细描述这张图片的排版、颜色、布局。}"
PROV="${VISION_PROVIDER:-zhipu}"
KEY="${VISION_KEY:-}"

if [ -z "$KEY" ]; then echo "ERROR: 未设置 VISION_KEY 环境变量"; exit 1; fi
if [ ! -f "$IMG" ]; then echo "ERROR: 图片不存在: $IMG"; exit 1; fi

# 图片转 base64
B64=$(base64 -w0 "$IMG")
EXT=$(echo "$IMG" | awk -F. '{print tolower($NF)}')
case "$EXT" in jpg|jpeg) MIME="image/jpeg";; png) MIME="image/png";; webp) MIME="image/webp";; *) MIME="image/png";; esac

if [ "$PROV" = "zhipu" ]; then
  MODEL="${VISION_MODEL:-glm-4v-flash}"
  URL="https://open.bigmodel.cn/api/paas/v4/chat/completions"
else
  MODEL="${VISION_MODEL:-qwen-vl-plus}"
  URL="https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
fi

# 组装 JSON (python 避免转义)
PAYLOAD=$(python3 -c "
import json,sys,sos
img_b64=open('/dev/stdin').read().strip()
mime='$MIME'
q='''$Q'''
print(json.dumps({
  'model':'$MODEL',
  'messages':[{'role':'user','content':[
     {'type':'text','text':q},
     {'type':'image_url','image_url':{'url':f'data:{mime};base64,'+img_b64}}
  ]}],
  'temperature':0.1
},ensure_ascii=False))
" <<< "$B64")

curl -s --max-time 90 "$URL" \
  -H "Authorization: Bearer $KEY" \
  -H "Content-Type: application/json" \
  -d "$PAYLOAD" | python3 -c "
import json,sys
try:
  d=json.load(sys.stdin)
  if 'choices' in d:
    print(d['choices'][0]['message']['content'])
  else:
    print('[API返回异常]', json.dumps(d,ensure_ascii=False)[:800])
except Exception as e:
  print('[解析失败]',e)
"
