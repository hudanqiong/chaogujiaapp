<?php
/**
 * lhb.php — 龙虎榜（炒股家 App）
 * 对应同花顺/东财 龙虎榜页：①每日龙虎榜列表 ②个股上榜记录 ③个股某日买卖席位
 *
 * 数据源（服务器出口实测可用，HTTP 200）：
 *   东方财富 datacenter-web.eastmoney.com/api/data/v1/get
 *     每日龙虎榜列表 → reportName=RPT_DAILYBILLBOARD_DETAILSNEW
 *     个股买入席位   → reportName=RPT_BILLBOARD_DAILYDETAILSBUY
 *     个股卖出席位   → reportName=RPT_BILLBOARD_DAILYDETAILSSELL
 *   注：该接口 sortColumns 只认部分列，传错列会返回
 *       {"success":false,"message":"XXX排序列不存在"}，故默认不排序，
 *       统一在 PHP 侧排序，避免依赖未公开的排序列白名单。
 *
 * 参数：
 *   action  daily  每日龙虎榜列表（默认）
 *   date    YYYY-MM-DD，daily 用；缺省取最近一个有数据的交易日
 *   page    页码，从 1 开始，默认 1
 *   size    每页条数，1~100，默认 50
 *   code    6 位股票代码，detail 用；可带 sh/sz 前缀，会被剥离
 *   sort    net|change|ratio  daily 排序字段，默认 net（净买额降序）
 *
 *   action  detail 个股龙虎榜记录（日期 + 上榜原因 + 当日买/卖/净额）
 *   action  seat   个股某日买卖席位明细（买 5 席 + 卖 5 席）
 *
 * 口径说明：
 *   detail 的 buyAmt/sellAmt 为「当日买/卖席位金额合计」，已合并买入表与卖出表；
 *   seat 只取各侧前 5 席，故 sumBuy/sumSell 是「前五席位」合计，非当日全额。
 *   名称字段：东财 BUY/SELL 表不含股票名称，改用腾讯 qt.gtimg.cn 兜底。
 *
 * 输出：
 *   { code:0, msg:'ok', data:{ ... } }   成功
 *   { code:1, msg:'...', data:null }     失败
 *
 * daily  → data:{ date, page, size, pages, total, list:[{code,name,price,changeRate,
 *                  turnover,buyAmt,sellAmt,netAmt,amount,explain,changeType,
 *                  buySeat,sellSeat,d1,d2,d5,d10 }] }
 * detail → data:{ code,name, list:[{date,explain,changeRate,price,amount,buyAmt,
 *                  sellAmt,netAmt,buyRatio,sellRatio,buySeat,sellSeat}] }
 * seat   → data:{ code,name,date, buy:[{dept,buy,sell,net,ratio}],
 *                  sell:[{dept,buy,sell,net,ratio}], explain }
 */
header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Headers: *');
header('Access-Control-Allow-Methods: GET,POST,OPTIONS');
if (isset($_SERVER['REQUEST_METHOD']) && $_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit;
}

function out($arr) {
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
}
function fail($msg) {
    out(['code' => 1, 'msg' => $msg, 'data' => null]);
}

/** 剥掉 sh/sz/bj 前缀与所有非数字，取 6 位纯代码 */
function lhb_code($code) {
    $code = preg_replace('/\D/', '', (string)$code);
    return strlen($code) >= 6 ? substr($code, -6) : $code;
}
/** 6 位代码 → 东财 SECUCODE（600887.SH / 000001.SZ / 4|8 → BJ） */
function lhb_secucode($code) {
    $c = lhb_code($code);
    if ($c === '') return '';
    $h = $c[0];
    if ($h === '6' || $h === '5' || $h === '9') return $c . '.SH';
    if ($h === '4' || $h === '8') return $c . '.BJ';
    return $c . '.SZ';
}
/** YYYY-MM-DD / YYYYMMDD 归一为 YYYY-MM-DD；非法返回 '' */
function lhb_date($d) {
    $d = preg_replace('/\D/', '', (string)$d);
    if (strlen($d) !== 8) return '';
    return substr($d, 0, 4) . '-' . substr($d, 4, 2) . '-' . substr($d, 6, 2);
}
/** '2026-08-17 00:00:00' → '2026-08-17' */
function lhb_day($s) {
    $s = (string)$s;
    $p = strpos($s, ' ');
    return $p === false ? $s : substr($s, 0, $p);
}
/** 数值转 float，null/空 → null；保留原精度交给前端格式化 */
function lhb_num($v) {
    if ($v === null || $v === '') return null;
    return (float)$v;
}

/**
 * 统一 HTTP GET：UA/Referer + 超时 + HTTP 200 校验。
 * 东财返回 UTF-8 JSON，无需转码。
 */
function lhb_get($url, $timeout = 10) {
    $ch = curl_init();
    curl_setopt_array($ch, [
        CURLOPT_URL            => $url,
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_FOLLOWLOCATION => true,
        CURLOPT_MAXREDIRS      => 3,
        CURLOPT_TIMEOUT        => $timeout,
        CURLOPT_CONNECTTIMEOUT => 5,
        CURLOPT_SSL_VERIFYPEER => false,
        CURLOPT_SSL_VERIFYHOST => false,
        CURLOPT_ENCODING       => '',
        CURLOPT_HTTPHEADER     => [
            'User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36',
            'Referer: https://data.eastmoney.com/stock/tradedetail.html',
            'Accept: */*',
        ],
    ]);
    $body = curl_exec($ch);
    $code = (int)curl_getinfo($ch, CURLINFO_HTTP_CODE);
    curl_close($ch);
    if ($body === false || $code !== 200 || $body === '') return '';
    return $body;
}

/** 调东财 datacenter 接口，返回 data 数组（无数据返回 []） */
function lhb_query($report, $filter, $page = 1, $size = 50, $sortColumns = '', $sortTypes = '') {
    $url = 'https://datacenter-web.eastmoney.com/api/data/v1/get?'
         . 'reportName=' . urlencode($report)
         . '&columns=ALL'
         . '&filter=' . urlencode($filter)
         . '&pageNumber=' . (int)$page
         . '&pageSize=' . (int)$size
         . '&source=WEB&client=WEB';
    if ($sortColumns !== '') {
        $url .= '&sortColumns=' . urlencode($sortColumns) . '&sortTypes=' . urlencode($sortTypes !== '' ? $sortTypes : '-1');
    }
    $raw = lhb_get($url);
    if ($raw === '') return null;                 // 网络失败
    $j = json_decode($raw, true);
    if (!is_array($j)) return null;               // 解析失败
    if (empty($j['success'])) {
        // 9201 = 返回数据为空（正常空结果，不算错误）
        if (isset($j['code']) && (int)$j['code'] === 9201) return ['__empty' => true];
        return null;
    }
    return isset($j['result']['data']) && is_array($j['result']['data']) ? $j['result']['data'] : ['__empty' => true];
}
/** 文件缓存（TTL 内直接用；失败时降级旧缓存） */
function lhb_cached($key, $ttl, $fetcher) {
    $dir = sys_get_temp_dir() . '/stock_lhb_cache';
    if (!is_dir($dir)) @mkdir($dir, 0777, true);
    $file = $dir . '/' . md5($key) . '.json';
    if (is_file($file) && (time() - filemtime($file)) < $ttl) {
        $s = @file_get_contents($file);
        if ($s !== false && $s !== '') {
            $v = json_decode($s, true);
            if (is_array($v)) return $v;
        }
    }
    $v = $fetcher();
    if (is_array($v) && !isset($v['__error'])) {
        @file_put_contents($file, json_encode($v, JSON_UNESCAPED_UNICODE));
        return $v;
    }
    // 抓取失败 → 降级到过期旧缓存（有总比没有好）
    if (is_file($file)) {
        $s = @file_get_contents($file);
        if ($s !== false && $s !== '') {
            $old = json_decode($s, true);
            if (is_array($old)) return $old;
        }
    }
    return ['__error' => true];
}

/** 取最近一个有龙虎榜数据的交易日（从今天往前最多回看 15 天） */
function lhb_latest_date() {
    $probe = function ($d) {
        $f = "(TRADE_DATE='" . $d . "')";
        $r = lhb_query('RPT_DAILYBILLBOARD_DETAILSNEW', $f, 1, 1);
        return is_array($r) && !isset($r['__error']);
    };
    // 命中缓存就不重复探测
    $ck = 'lhb_latest_date';
    $dir = sys_get_temp_dir() . '/stock_lhb_cache';
    if (!is_dir($dir)) @mkdir($dir, 0777, true);
    $file = $dir . '/' . md5($ck) . '.json';
    if (is_file($file) && (time() - filemtime($file)) < 900) {
        $s = @file_get_contents($file);
        if ($s !== false && preg_match('/^\d{4}-\d{2}-\d{2}$/', $s)) return $s;
    }
    for ($i = 0; $i <= 15; $i++) {
        $d = date('Y-m-d', strtotime('-' . $i . ' day'));
        if ($i === 0) continue;                   // 当天盘中通常还没出榜，从昨天起
        if ($probe($d)) {
            @file_put_contents($file, $d);
            return $d;
        }
    }
    return date('Y-m-d', strtotime('-1 day'));
}

/** 6 位代码 → 腾讯符号（sh600887 / sz000001 / bj430047） */
/**
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
    // 只要不是合法 UTF-8，就按 GBK 转；优先 mbstring，退而 iconv，最后自实现
    $isUtf8 = (bool)preg_match('//u', $raw);
    if (!$isUtf8) $raw = lhb_to_utf8($raw);
    // v_sh600887="1~伊利股份~600887~..."
    if (preg_match('/="([^"]*)"/', $raw, $m)) {
        $f = explode('~', $m[1]);
        if (isset($f[1]) && $f[1] !== '') return $f[1];
    }
    return '';
}

/** 单行 → 每日龙虎榜条目 */
function lhb_map_daily($r) {
    return [
        'code'       => isset($r['SECURITY_CODE']) ? $r['SECURITY_CODE'] : '',
        'name'       => isset($r['SECURITY_NAME_ABBR']) ? $r['SECURITY_NAME_ABBR'] : '',
        'price'      => lhb_num(isset($r['CLOSE_PRICE']) ? $r['CLOSE_PRICE'] : null),
        'changeRate' => lhb_num(isset($r['CHANGE_RATE']) ? $r['CHANGE_RATE'] : null),
        'turnover'   => lhb_num(isset($r['TURNOVERRATE']) ? $r['TURNOVERRATE'] : null),
        'buyAmt'     => lhb_num(isset($r['BILLBOARD_BUY_AMT']) ? $r['BILLBOARD_BUY_AMT'] : null),
        'sellAmt'    => lhb_num(isset($r['BILLBOARD_SELL_AMT']) ? $r['BILLBOARD_SELL_AMT'] : null),
        'netAmt'     => lhb_num(isset($r['BILLBOARD_NET_AMT']) ? $r['BILLBOARD_NET_AMT'] : null),
        'amount'     => lhb_num(isset($r['ACCUM_AMOUNT']) ? $r['ACCUM_AMOUNT'] : null),
        'dealRatio'  => lhb_num(isset($r['DEAL_AMOUNT_RATIO']) ? $r['DEAL_AMOUNT_RATIO'] : null),
        'netRatio'   => lhb_num(isset($r['DEAL_NET_RATIO']) ? $r['DEAL_NET_RATIO'] : null),
        'explain'    => isset($r['EXPLANATION']) ? $r['EXPLANATION'] : '',
        'explainTag' => isset($r['EXPLAIN']) ? $r['EXPLAIN'] : '',
        'changeType' => isset($r['CHANGE_TYPE']) ? $r['CHANGE_TYPE'] : '',
        'buySeat'    => isset($r['BUY_SEAT']) ? $r['BUY_SEAT'] : '',
        'sellSeat'   => isset($r['SELL_SEAT']) ? $r['SELL_SEAT'] : '',
        'd1'         => lhb_num(isset($r['D1_CLOSE_ADJCHRATE']) ? $r['D1_CLOSE_ADJCHRATE'] : null),
        'd2'         => lhb_num(isset($r['D2_CLOSE_ADJCHRATE']) ? $r['D2_CLOSE_ADJCHRATE'] : null),
        'd5'         => lhb_num(isset($r['D5_CLOSE_ADJCHRATE']) ? $r['D5_CLOSE_ADJCHRATE'] : null),
        'd10'        => lhb_num(isset($r['D10_CLOSE_ADJCHRATE']) ? $r['D10_CLOSE_ADJCHRATE'] : null),
    ];
}
/** 单行 → 个股上榜记录条目 */
function lhb_map_detail($r) {
    return [
        'date'       => lhb_day(isset($r['TRADE_DATE']) ? $r['TRADE_DATE'] : ''),
        'explain'    => isset($r['EXPLANATION']) ? $r['EXPLANATION'] : '',
        'changeRate' => lhb_num(isset($r['CHANGE_RATE']) ? $r['CHANGE_RATE'] : null),
        'price'      => lhb_num(isset($r['CLOSE_PRICE']) ? $r['CLOSE_PRICE'] : null),
        'amount'     => lhb_num(isset($r['ACCUM_AMOUNT']) ? $r['ACCUM_AMOUNT'] : null),
        'volume'     => lhb_num(isset($r['ACCUM_VOLUME']) ? $r['ACCUM_VOLUME'] : null),
        'buyAmt'     => lhb_num(isset($r['BUY']) ? $r['BUY'] : null),
        'sellAmt'    => lhb_num(isset($r['SELL']) ? $r['SELL'] : null),
        'netAmt'     => lhb_num(isset($r['NET']) ? $r['NET'] : null),
        'buyRatio'   => lhb_num(isset($r['TOTAL_BUYRIO']) ? $r['TOTAL_BUYRIO'] : null),
        'sellRatio'  => lhb_num(isset($r['TOTAL_SELLRIO']) ? $r['TOTAL_SELLRIO'] : null),
        'dept'       => isset($r['OPERATEDEPT_NAME']) ? $r['OPERATEDEPT_NAME'] : '',
        'changeType' => isset($r['CHANGE_TYPE']) ? $r['CHANGE_TYPE'] : '',
    ];
}
/** 单行 → 席位条目 */
function lhb_map_seat($r) {
    return [
        'dept'       => isset($r['OPERATEDEPT_NAME']) ? $r['OPERATEDEPT_NAME'] : '',
        'buy'        => lhb_num(isset($r['BUY']) ? $r['BUY'] : null),
        'sell'       => lhb_num(isset($r['SELL']) ? $r['SELL'] : null),
        'net'        => lhb_num(isset($r['NET']) ? $r['NET'] : null),
        'buyRatio'   => lhb_num(isset($r['TOTAL_BUYRIO']) ? $r['TOTAL_BUYRIO'] : null),
        'sellRatio'  => lhb_num(isset($r['TOTAL_SELLRIO']) ? $r['TOTAL_SELLRIO'] : null),
        'changeRate' => lhb_num(isset($r['CHANGE_RATE']) ? $r['CHANGE_RATE'] : null),
        'price'      => lhb_num(isset($r['CLOSE_PRICE']) ? $r['CLOSE_PRICE'] : null),
        'explain'    => isset($r['EXPLANATION']) ? $r['EXPLANATION'] : '',
    ];
}

/* ============================ 路由 ============================ */
$action = isset($_GET['action']) ? strtolower(trim($_GET['action'])) : 'daily';
$page   = isset($_GET['page']) ? max(1, (int)$_GET['page']) : 1;
$size   = isset($_GET['size']) ? (int)$_GET['size'] : 50;
if ($size < 1) $size = 1;
if ($size > 100) $size = 100;

/* ---------------------- action=daily ---------------------- */
if ($action === 'daily' || $action === '') {
    $date = lhb_date(isset($_GET['date']) ? $_GET['date'] : '');
    if ($date === '') $date = lhb_latest_date();
    $filter = "(TRADE_DATE='" . $date . "')";
    $tk = 'lhb_daily_' . $date . '_' . $page . '_' . $size . '_' . (isset($_GET['sort']) ? $_GET['sort'] : 'net');

    $data = lhb_cached($tk, 300, function () use ($filter, $page, $size) {
        // 不传 sortColumns（白名单不稳），取全量页后在 PHP 侧排序
        $rows = lhb_query('RPT_DAILYBILLBOARD_DETAILSNEW', $filter, 1, 500);
        if (!is_array($rows)) return ['__error' => true];
        if (isset($rows['__empty'])) return ['list' => [], 'total' => 0];
        $out = [];
        foreach ($rows as $r) {
            $m = lhb_map_daily($r);
            if ($m['code'] === '') continue;
            $out[] = $m;
        }
        return ['list' => $out, 'total' => count($out)];
    });
    if (is_array($data) && isset($data['__error'])) fail('龙虎榜数据源请求失败，请稍后重试');

    $list = isset($data['list']) && is_array($data['list']) ? $data['list'] : [];
    // PHP 侧排序
    $sort = isset($_GET['sort']) ? strtolower(trim($_GET['sort'])) : 'net';
    if ($sort === 'change') {
        usort($list, function ($a, $b) { return ($b['changeRate'] <=> $a['changeRate']); });
    } elseif ($sort === 'ratio') {
        usort($list, function ($a, $b) { return ($b['dealRatio'] <=> $a['dealRatio']); });
    } elseif ($sort === 'norm') {
        usort($list, function ($a, $b) { return ($a['netAmt'] <=> $b['netAmt']); });
    } else { // net 默认：净买额降序
        usort($list, function ($a, $b) {
            $x = $a['netAmt'] === null ? -1e18 : $a['netAmt'];
            $y = $b['netAmt'] === null ? -1e18 : $b['netAmt'];
            return ($y <=> $x);
        });
    }
    $total = count($list);
    $slice = array_slice($list, ($page - 1) * $size, $size);

    out(['code' => 0, 'msg' => 'ok', 'data' => [
        'date'  => $date,
        'page'  => $page,
        'size'  => $size,
        'pages' => $size > 0 ? (int)ceil($total / $size) : 0,
        'total' => $total,
        'sort'  => $sort,
        'list'  => $slice,
    ]]);
}

/* ---------------------- action=detail ---------------------- */
if ($action === 'detail') {
    $code = lhb_code(isset($_GET['code']) ? $_GET['code'] : '');
    if ($code === '' || strlen($code) !== 6) fail('缺少或非法参数 code（6 位股票代码）');
    $secu = lhb_secucode($code);
    $tk = 'lhb_detail_' . $code . '_' . $page . '_' . $size;

    $data = lhb_cached($tk, 600, function () use ($secu, $code) {
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
    });
    if (is_array($data) && isset($data['__error'])) fail('个股龙虎榜数据源请求失败，请稍后重试');

    $list = isset($data['list']) && is_array($data['list']) ? $data['list'] : [];
    $total = count($list);
    $slice = array_slice($list, ($page - 1) * $size, $size);

    out(['code' => 0, 'msg' => 'ok', 'data' => [
        'code'  => $code,
        'name'  => isset($data['name']) ? $data['name'] : '',
        'page'  => $page,
        'size'  => $size,
        'pages' => $size > 0 ? (int)ceil($total / $size) : 0,
        'total' => $total,
        'list'  => $slice,
    ]]);
}

/* ---------------------- action=seat ---------------------- */
if ($action === 'seat') {
    $code = lhb_code(isset($_GET['code']) ? $_GET['code'] : '');
    if ($code === '' || strlen($code) !== 6) fail('缺少或非法参数 code（6 位股票代码）');
    $date = lhb_date(isset($_GET['date']) ? $_GET['date'] : '');
    if ($date === '') fail('缺少参数 date（YYYY-MM-DD）');
    $secu = lhb_secucode($code);
    $tk = 'lhb_seat_' . $code . '_' . $date;

    $data = lhb_cached($tk, 600, function () use ($secu, $date) {
        $fltBuy  = '(SECUCODE="' . $secu . '")(TRADE_DATE=\'' . $date . '\')';
        $rb = lhb_query('RPT_BILLBOARD_DAILYDETAILSBUY', $fltBuy, 1, 50);
        $rs = lhb_query('RPT_BILLBOARD_DAILYDETAILSSELL', $fltBuy, 1, 50);
        if (!is_array($rb) && !is_array($rs)) return ['__error' => true];

        $name = '';
        $explain = '';
        $buy = [];
        if (is_array($rb) && !isset($rb['__empty'])) {
            foreach ($rb as $r) {
                if ($name === '' && !empty($r['SECURITY_NAME_ABBR'])) $name = $r['SECURITY_NAME_ABBR'];
                $m = lhb_map_seat($r);
                if ($m['dept'] === '') continue;
                $buy[] = ['dept' => $m['dept'], 'buy' => $m['buy'], 'sell' => $m['sell'],
                          'net' => $m['net'], 'ratio' => $m['buyRatio']];
                if ($explain === '' && $m['explain'] !== '') $explain = $m['explain'];
            }
        }
        $sell = [];
        if (is_array($rs) && !isset($rs['__empty'])) {
            foreach ($rs as $r) {
                if ($name === '' && !empty($r['SECURITY_NAME_ABBR'])) $name = $r['SECURITY_NAME_ABBR'];
                $m = lhb_map_seat($r);
                if ($m['dept'] === '') continue;
                $sell[] = ['dept' => $m['dept'], 'buy' => $m['buy'], 'sell' => $m['sell'],
                           'net' => $m['net'], 'ratio' => $m['sellRatio']];
                if ($explain === '' && $m['explain'] !== '') $explain = $m['explain'];
            }
        }
        // 买入席按买入额降序（前 5），卖出席按卖出额降序（前 5）
        usort($buy,  function ($a, $b) { return (($b['buy']  === null ? -1e18 : $b['buy'])  <=> ($a['buy']  === null ? -1e18 : $a['buy'])); });
        usort($sell, function ($a, $b) { return (($b['sell'] === null ? -1e18 : $b['sell']) <=> ($a['sell'] === null ? -1e18 : $a['sell'])); });
        $buy  = array_slice($buy, 0, 5);
        $sell = array_slice($sell, 0, 5);

        $sumBuy = 0.0; foreach ($buy as $x) { if ($x['buy'] !== null) $sumBuy += $x['buy']; }
        $sumSell = 0.0; foreach ($sell as $x) { if ($x['sell'] !== null) $sumSell += $x['sell']; }

        return [
            'name'     => $name,
            'explain'  => $explain,
            'buy'      => $buy,
            'sell'     => $sell,
            'sumBuy'   => round($sumBuy, 2),
            'sumSell'  => round($sumSell, 2),
            'sumNet'   => round($sumBuy - $sumSell, 2),
        ];
    });
    if (is_array($data) && empty($data['name'])) {
        $data['name'] = lhb_name($code);
    }
    if (is_array($data) && isset($data['__error'])) fail('席位数据源请求失败，请稍后重试');

    out(['code' => 0, 'msg' => 'ok', 'data' => [
        'code'    => $code,
        'name'    => isset($data['name']) ? $data['name'] : '',
        'date'    => $date,
        'explain' => isset($data['explain']) ? $data['explain'] : '',
        'buy'     => isset($data['buy']) ? $data['buy'] : [],
        'sell'    => isset($data['sell']) ? $data['sell'] : [],
        'sumBuy'  => isset($data['sumBuy']) ? $data['sumBuy'] : 0,
        'sumSell' => isset($data['sumSell']) ? $data['sumSell'] : 0,
        'sumNet'  => isset($data['sumNet']) ? $data['sumNet'] : 0,
    ]]);
}

fail('未知 action：' . $action . '（可用：daily / detail / seat）');
