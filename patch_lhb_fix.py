# -*- coding: utf-8 -*-
P = 'lhb.php'
src = open(P, encoding='utf-8').read()
orig = src

# ---------- 1) 新增：股票名称查询（腾讯行情，与 orderbook.php 同源） ----------
old_fn = """/** 单行 → 每日龙虎榜条目 */"""
new_fn = """/** 6 位代码 → 腾讯符号（sh600887 / sz000001 / bj430047） */
function lhb_symbol($code) {
    $c = lhb_code($code);
    if ($c === '') return '';
    $h = $c[0];
    if ($h === '6' || $h === '5' || $h === '9') return 'sh' . $c;
    if ($h === '4' || $h === '8') return 'bj' . $c;
    return 'sz' . $c;
}
/** 股票名称：腾讯 qt.gtimg.cn（GBK，'~' 分隔，第 2 个字段为名称） */
function lhb_name($code) {
    $sym = lhb_symbol($code);
    if ($sym === '') return '';
    $raw = lhb_get('https://qt.gtimg.cn/q=' . $sym, 8);
    if ($raw === '') return '';
    if (function_exists('mb_check_encoding') && !mb_check_encoding($raw, 'UTF-8')) {
        $raw = mb_convert_encoding($raw, 'UTF-8', 'GBK');
    }
    // v_sh600887="1~伊利股份~600887~..."
    if (preg_match('/="([^"]*)"/', $raw, $m)) {
        $f = explode('~', $m[1]);
        if (isset($f[1]) && $f[1] !== '') return $f[1];
    }
    return '';
}

/** 单行 → 每日龙虎榜条目 */"""
assert src.count(old_fn) == 1, 'old_fn count=%d' % src.count(old_fn)
src = src.replace(old_fn, new_fn)

# ---------- 2) detail：买/卖两表合并，name 兜底 ----------
old_detail = """    $data = lhb_cached($tk, 600, function () use ($secu) {
        $rows = lhb_query('RPT_BILLBOARD_DAILYDETAILSBUY', '(SECUCODE="' . $secu . '")', 1, 200);
        if (!is_array($rows)) return ['__error' => true];
        if (isset($rows['__empty'])) return ['list' => []];

        // 同一天可能出现多条（不同上榜原因/多个席位）→ 按日期聚合成一条
        $agg = [];
        $name = '';
        foreach ($rows as $r) {
            $m = lhb_map_detail($r);
            $d = $m['date'];
            if ($d === '') continue;
            if ($name === '' && !empty($r['SECURITY_NAME_ABBR'])) $name = $r['SECURITY_NAME_ABBR'];
            if (!isset($agg[$d])) {
                $agg[$d] = [
                    'date'       => $d,
                    'explain'    => $m['explain'],
                    'changeRate' => $m['changeRate'],
                    'price'      => $m['price'],
                    'amount'     => $m['amount'],
                    'volume'     => $m['volume'],
                    'buyAmt'     => 0.0,
                    'sellAmt'    => 0.0,
                    'netAmt'     => 0.0,
                    'buyRatio'   => $m['buyRatio'],
                    'sellRatio'  => $m['sellRatio'],
                    'buySeat'    => 0,
                    'sellSeat'   => 0,
                    'changeType' => $m['changeType'],
                ];
            }
            // 席位金额累加，用于该日买/卖/净额
            if ($m['buyAmt'] !== null)  $agg[$d]['buyAmt']  += $m['buyAmt'];
            if ($m['sellAmt'] !== null) $agg[$d]['sellAmt'] += $m['sellAmt'];
            if ($m['buyAmt'] !== null)  $agg[$d]['buySeat']++;
            if ($m['sellAmt'] !== null) $agg[$d]['sellSeat']++;
            if ($m['explain'] !== '' && strpos($agg[$d]['explain'], $m['explain']) === false) {
                $agg[$d]['explain'] .= ($agg[$d]['explain'] === '' ? '' : '；') . $m['explain'];
            }
        }
        $list = array_values($agg);
        foreach ($list as $i => $v) {
            $list[$i]['netAmt'] = round($v['buyAmt'] - $v['sellAmt'], 2);
            $list[$i]['buyAmt']  = round($v['buyAmt'], 2);
            $list[$i]['sellAmt'] = round($v['sellAmt'], 2);
        }
        usort($list, function ($a, $b) { return strcmp($b['date'], $a['date']); });
        return ['name' => $name, 'list' => $list];
    });"""
new_detail = """    $data = lhb_cached($tk, 600, function () use ($secu, $code) {
        $flt = '(SECUCODE="' . $secu . '")';
        // 买入表只含买入额、卖出表只含卖出额 → 两张表都要取，按日期合并
        $rb = lhb_query('RPT_BILLBOARD_DAILYDETAILSBUY', $flt, 1, 200);
        $rs = lhb_query('RPT_BILLBOARD_DAILYDETAILSSELL', $flt, 1, 200);
        if (!is_array($rb) && !is_array($rs)) return ['__error' => true];

        $sumBuy  = [];
        $sumSell = [];
        $cntBuy  = [];
        $cntSell = [];
        $meta    = [];
        $name    = '';

        /** 累加某一侧的席位金额 */
        $absorb = function ($rows, $side) use (&$sumBuy, &$sumSell, &$cntBuy, &$cntSell, &$meta, &$name) {
            if (!is_array($rows) || isset($rows['__empty'])) return;
            foreach ($rows as $r) {
                if ($name === '' && !empty($r['SECURITY_NAME_ABBR'])) $name = $r['SECURITY_NAME_ABBR'];
                $m = lhb_map_detail($r);
                $d = $m['date'];
                if ($d === '') continue;
                if (!isset($meta[$d])) {
                    $meta[$d] = [
                        'date'       => $d,
                        'explain'    => '',
                        'changeRate' => $m['changeRate'],
                        'price'      => $m['price'],
                        'amount'     => $m['amount'],
                        'volume'     => $m['volume'],
                        'buyRatio'   => null,
                        'sellRatio'  => null,
                        'changeType' => $m['changeType'],
                    ];
                }
                // 同一天可能多条上榜原因，去重拼接
                if ($m['explain'] !== '' && strpos($meta[$d]['explain'], $m['explain']) === false) {
                    $meta[$d]['explain'] .= ($meta[$d]['explain'] === '' ? '' : '；') . $m['explain'];
                }
                if ($side === 'buy') {
                    if ($m['buyAmt'] !== null) {
                        $sumBuy[$d] = (isset($sumBuy[$d]) ? $sumBuy[$d] : 0) + $m['buyAmt'];
                        $cntBuy[$d] = (isset($cntBuy[$d]) ? $cntBuy[$d] : 0) + 1;
                    }
                    if ($meta[$d]['buyRatio'] === null && $m['buyRatio'] !== null) $meta[$d]['buyRatio'] = $m['buyRatio'];
                } else {
                    if ($m['sellAmt'] !== null) {
                        $sumSell[$d] = (isset($sumSell[$d]) ? $sumSell[$d] : 0) + $m['sellAmt'];
                        $cntSell[$d] = (isset($cntSell[$d]) ? $cntSell[$d] : 0) + 1;
                    }
                    if ($meta[$d]['sellRatio'] === null && $m['sellRatio'] !== null) $meta[$d]['sellRatio'] = $m['sellRatio'];
                }
            }
        };
        $absorb($rb, 'buy');
        $absorb($rs, 'sell');

        $list = [];
        foreach ($meta as $d => $v) {
            $b = isset($sumBuy[$d])  ? $sumBuy[$d]  : 0.0;
            $s = isset($sumSell[$d]) ? $sumSell[$d] : 0.0;
            $v['buyAmt']   = round($b, 2);
            $v['sellAmt']  = round($s, 2);
            $v['netAmt']   = round($b - $s, 2);
            $v['buySeat']  = isset($cntBuy[$d])  ? $cntBuy[$d]  : 0;
            $v['sellSeat'] = isset($cntSell[$d]) ? $cntSell[$d] : 0;
            $list[] = $v;
        }
        usort($list, function ($a, $b) { return strcmp($b['date'], $a['date']); });
        if ($name === '') $name = lhb_name($code);
        return ['name' => $name, 'list' => $list];
    });"""
assert src.count(old_detail) == 1, 'old_detail count=%d' % src.count(old_detail)
src = src.replace(old_detail, new_detail)

# ---------- 3) seat：name 兜底 ----------
old_seat = """            'sumNet'   => round($sumBuy - $sumSell, 2),
        ];
    });"""
new_seat = """            'sumNet'   => round($sumBuy - $sumSell, 2),
        ];
    });
    if (is_array($data) && empty($data['name'])) {
        $data['name'] = lhb_name($code);
    }"""
assert src.count(old_seat) == 1, 'old_seat count=%d' % src.count(old_seat)
src = src.replace(old_seat, new_seat)

# 顶部注释补一条口径说明
old_doc = """ *   action  seat   个股某日买卖席位明细（买 5 席 + 卖 5 席）"""
new_doc = """ *   action  seat   个股某日买卖席位明细（买 5 席 + 卖 5 席）
 *
 * 口径说明：
 *   detail 的 buyAmt/sellAmt 为「当日买/卖席位金额合计」，已合并买入表与卖出表；
 *   seat 只取各侧前 5 席，故 sumBuy/sumSell 是「前五席位」合计，非当日全额。
 *   名称字段：东财 BUY/SELL 表不含股票名称，改用腾讯 qt.gtimg.cn 兜底。"""
assert src.count(old_doc) == 1, 'old_doc count=%d' % src.count(old_doc)
src = src.replace(old_doc, new_doc)

open(P, 'w', encoding='utf-8').write(src)
print('LHB_FIX_OK len=%d' % len(src))
print('has lhb_name:', 'function lhb_name' in src)
print('has absorb:', '$absorb($rs' in src)
