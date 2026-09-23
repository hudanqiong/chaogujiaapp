#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""视觉通道: 调 VL 模型看图并回答。用法: vl_call.py <图片路径> <问题> [provider] [model]"""
import base64, json, sys, os, urllib.request

def main():
    if len(sys.argv) < 3:
        print("用法: vl_call.py <图片路径> <问题> [provider=zhipu|ali] [model]")
        sys.exit(1)
    img_path, question = sys.argv[1], sys.argv[2]
    provider = sys.argv[3] if len(sys.argv) > 3 else os.environ.get("VISION_PROVIDER", "zhipu")
    key = os.environ.get("VISION_KEY", "")
    if not key:
        print("ERROR: 未设置 VISION_KEY")
        sys.exit(1)
    if not os.path.isfile(img_path):
        print(f"ERROR: 图片不存在: {img_path}")
        sys.exit(1)

    with open(img_path, "rb") as f:
        b64 = base64.b64encode(f.read()).decode()
    ext = img_path.rsplit(".", 1)[-1].lower()
    mime = {"jpg": "image/jpeg", "jpeg": "image/jpeg", "png": "image/png", "webp": "image/webp"}.get(ext, "image/png")

    if provider == "zhipu":
        model = sys.argv[4] if len(sys.argv) > 4 else os.environ.get("VISION_MODEL", "glm-4v-flash")
        url = "https://open.bigmodel.cn/api/paas/v4/chat/completions"
    else:
        model = sys.argv[4] if len(sys.argv) > 4 else os.environ.get("VISION_MODEL", "qwen-vl-plus")
        url = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"

    payload = {
        "model": model,
        "messages": [{"role": "user", "content": [
            {"type": "text", "text": question},
            {"type": "image_url", "image_url": {"url": f"data:{mime};base64,{b64}"}}
        ]}],
        "temperature": 0.1,
    }
    data = json.dumps(payload, ensure_ascii=False).encode()
    req = urllib.request.Request(url, data=data, headers={
        "Authorization": "Bearer " + key,
        "Content-Type": "application/json",
    })
    try:
        with urllib.request.urlopen(req, timeout=90) as resp:
            result = json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        print("[HTTP错误]", e.code, e.read().decode()[:500])
        sys.exit(2)
    except Exception as e:
        print("[请求失败]", repr(e))
        sys.exit(3)

    if "choices" in result:
        print(result["choices"][0]["message"]["content"])
    else:
        print("[API返回异常]", json.dumps(result, ensure_ascii=False)[:800])

if __name__ == "__main__":
    main()
