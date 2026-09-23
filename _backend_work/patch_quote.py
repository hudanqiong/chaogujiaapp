# -*- coding: utf-8 -*-
import re

path = '/var/www/html/stock_api/quote.php'
src = open(path, encoding='utf-8').read()

# 1) 新增：集合竞价快照（分时第一根）解析函数，插入 quote_fetch_tencent 之前
helper = '''
// ------------------------------------------------------------
//  集合竞价快照（9:25 定格，盘后不再变）
//  数据源：腾讯分时第一根 "0930 价格 累计量 累计成交额"
//         → 第一根即集合竞价撮合结果，固定不变
//  缓存策略：缓存到「当日收盘后仍有效」——若当前时间在 9:25 之后，
//           缓存当日有效（一天只拉一次）；9:25 之前缓存 15 秒。
//  ------------------------------------------------------------
function quote_auction_snapshot($code) {
    $sym = quote_tx_symbol($code);
    $cf  = quote_cache_dir() . '/auc_' . md5($sym) . '_' . date('Ymd') . '.json';
    if (is_file($cf)) {
        $age = time() - filemtime($cf);
        $now = (int)date('Hi');
        // 9:25 之后：当日缓存一直复用（一天只拉一次）；9:25 之前：15 秒过期
        $ttl = ($now >= 925) ? 99999 : 15;
        if ($age < $ttl) {
            $j = json_decode(@file_get_contents($cf), true);
            if (is_array($j)) return $j;
        }
    }
    $raw = quote_get('https://ifzq.gtimg.cn/appstock/app/minute/query?code=' . $sym, 'https://gu.qq.com/');
    if ($raw === false) return null;
    $j = json_decode($raw, true);
    $d = $j['data'][$sym]['data']['data'] ?? null;
    if (!is_array($d) || !count($d)) return null;
    // 第一根： "0930 1255.03 63 7906688.70"
    $p = preg_split('/\\s+/', trim($d[0]));
    if (count($p) < 4) return null;
    $aucPrice  = (float)$p[1];   // 竞价价格（= 开盘价）
    $aucAmount = (float)$p[3];   // 竞价成交额（元）
    if ($aucPrice <= 0) return null;
    $out = ['aucPrice' => $aucPrice, 'aucAmount' => $aucAmount];
    @file_put_contents($cf, json_encode($out));
    return $out;
}
'''

anchor = 'function quote_fetch_tencent($codes) {'
assert anchor in src, 'anchor not found'
src = src.replace(anchor, helper + '\n' + anchor, 1)

# 2) 在 quote_fetch_tencent 的 out 组装块加字段
old = """        $out[$code] = [
            'name'      => $name,
            'price'     => $price,
            'prevClose' => $prev,
            'change'    => $chg,
            'changePct' => $pct,
        ];"""
new = """        // 成交额（万元，字段 [37]）
        $amount = (isset($f[37]) && is_numeric($f[37])) ? (float)$f[37] : 0.0;
        $amount = $amount * 10000.0; // 万元 → 元
        // 集合竞价快照（9:25 定格）：竞价涨幅% + 竞价金额
        $aucPct = 0.0; $aucAmount = 0.0;
        $auc = quote_auction_snapshot($code);
        if ($auc) {
            $aucAmount = $auc['aucAmount'];
            $aucPct = ($prev != 0) ? (($auc['aucPrice'] - $prev) / $prev * 100) : 0.0;
        }
        $out[$code] = [
            'name'      => $name,
            'price'     => $price,
            'prevClose' => $prev,
            'change'    => $chg,
            'changePct' => $pct,
            'amount'    => $amount,
            'aucPct'    => round($aucPct, 2),
            'aucAmount' => $aucAmount,
        ];"""
assert old in src, 'tencent out block not found'
src = src.replace(old, new, 1)

open(path, 'w', encoding='utf-8').write(src)
print('quote.php patched OK')
