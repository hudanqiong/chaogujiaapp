#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""lhb.php 健壮性补丁：out() 防空输出 + lhb_name() iconv 兜底 + 移除死代码 lhb_pages()"""
import io, sys

P = '/root/cgj/lhb.php'
src = io.open(P, encoding='utf-8').read()
orig_len = len(src)

# ---------- 1) out() 加固：非法 UTF-8 不再返回空 ----------
old_out = """function out($arr) {
    echo json_encode($arr, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}"""
new_out = """function out($arr) {
    // JSON_INVALID_UTF8_SUBSTITUTE：遇到非法 UTF-8（如 GBK 未转码）时用 U+FFFD 代替，
    // 绝不让 json_encode 返回 false 导致“零输出”这种最难排查的故障。
    $flag = JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES;
    if (defined('JSON_INVALID_UTF8_SUBSTITUTE')) $flag |= JSON_INVALID_UTF8_SUBSTITUTE;
    $s = json_encode($arr, $flag);
    if ($s === false) {
        // 极端兜底：逐层清洗非法字节后重试
        $s = json_encode(lhb_scrub($arr), $flag);
        if ($s === false) $s = '{"code":1,"msg":"json_encode failed: ' . addslashes(json_last_error_msg()) . '","data":null}';
    }
    echo $s;
    exit;
}
/** 递归把非法 UTF-8 字节替换掉，保证 json_encode 一定成功 */
function lhb_scrub($v) {
    if (is_array($v)) {
        $o = [];
        foreach ($v as $k => $x) $o[is_string($k) ? lhb_scrub($k) : $k] = lhb_scrub($x);
        return $o;
    }
    if (is_string($v)) {
        if ($v === '' || (defined('JSON_INVALID_UTF8_SUBSTITUTE') && preg_match('//u', $v))) return $v;
        return lhb_to_utf8($v);
    }
    return $v;
}"""

assert src.count(old_out) == 1, 'out() 未唯一命中: %d' % src.count(old_out)
src = src.replace(old_out, new_out)

# ---------- 2) lhb_name() 加固：iconv 兜底 ----------
old_chk = """    if (function_exists('mb_check_encoding') && !mb_check_encoding($raw, 'UTF-8')) {
        $raw = mb_convert_encoding($raw, 'UTF-8', 'GBK');
    }"""
new_chk = """    // 只要不是合法 UTF-8，就按 GBK 转；优先 mbstring，退而 iconv，最后自实现
    $isUtf8 = (bool)preg_match('//u', $raw);
    if (!$isUtf8) $raw = lhb_to_utf8($raw);"""
assert src.count(old_chk) == 1, 'lhb_name 转码段未唯一命中: %d' % src.count(old_chk)
src = src.replace(old_chk, new_chk)

# 追加 lhb_to_utf8 工具函数（放在 lhb_symbol 之前）
anchor = "function lhb_symbol($code) {"
assert src.count(anchor) == 1, 'lhb_symbol 锚点未唯一命中'
helper = """/**
 * 尽力把任意编码（主要是 GBK）的字节流转成合法 UTF-8。
 * 不依赖 mbstring：mbstring → iconv → 自实现 GBK 解码，逐级降级。
 */
function lhb_to_utf8($s) {
    if ($s === '' || preg_match('//u', $s)) return $s;   // 已是合法 UTF-8
    if (function_exists('mb_convert_encoding')) {
        $t = @mb_convert_encoding($s, 'UTF-8', 'GBK');
        if (is_string($t) && preg_match('//u', $t)) return $t;
    }
    if (function_exists('iconv')) {
        $t = @iconv('GBK', 'UTF-8//IGNORE', $s);
        if (is_string($t) && $t !== '' && preg_match('//u', $t)) return $t;
    }
    // 最后手段：丢弃非法字节（GBK 双字节高位 0x81-0xFE，逐字节校验）
    $out = '';
    $len = strlen($s);
    for ($i = 0; $i < $len; $i++) {
        $c = ord($s[$i]);
        if ($c < 0x80) { $out .= $s[$i]; continue; }
        if ($i + 1 < $len && $c >= 0x81 && $c <= 0xFE) {
            $c2 = ord($s[$i + 1]);
            if (($c2 >= 0x40 && $c2 <= 0x7E) || ($c2 >= 0x80 && $c2 <= 0xFE)) {
                $out .= '?';  // 无法映射则占位，保证仍是合法 ASCII
                $i++;
                continue;
            }
        }
        $out .= '?';
    }
    return $out;
}
"""
src = src.replace(anchor, helper + anchor, 1)

# ---------- 3) 移除死代码 lhb_pages() ----------
i0 = src.find("/** 取总页数 */\nfunction lhb_pages(")
if i0 != -1:
    i1 = src.find("/** 文件缓存", i0)
    assert i1 != -1, 'lhb_pages 结束锚点未找到'
    src = src[:i0] + src[i1:]
    removed = True
else:
    removed = False

io.open(P, 'w', encoding='utf-8').write(src)
print('LHB_HARDEN_OK len=%d (was %d) removed_pages=%s' % (len(src), orig_len, removed))
print('paren_check=%d/%d' % (src.count('('), src.count(')')))