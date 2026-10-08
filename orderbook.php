<?php
/**
 * orderbook.php — 五档盘口 + 成交明细（炒股家 App）
 * 对应同花顺分时页右侧栏：上 = 五档买卖盘，下 = 逐笔成交明细
 *
 * 数据源（服务器出口实测可用）：
 *   五档盘口 → 腾讯 https://qt.gtimg.cn/q=sh600887      (GBK, '~' 分隔)
 *   逐笔成交 → 腾讯 https://stock.gtimg.cn/data/index.php?appn=detail&action=data&c=sh600887&p=1
 *
 * 参数：
 *   code  股票代码，如 600887 / 000001
 *   p     逐笔页码，从 1 开始（每页固定 70 条，上限约 60 页），默认 1
 *   n     返回逐笔条数，1~70，默认 30
 *
 * 输出：
 *   { code:0, msg:'ok', data:{ code,name,price,prevClose,open,volume,outer,inner,
 *                              bids:[{price,vol}×5], asks:[{price,vol}×5],
 *                              deals:[{no,time,price,change,vol,amount,dir}], ts } }
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
    echo json_encode($arr, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}

/** 代码 → 腾讯符号前缀：6/5/9 → sh，0/3/1/2 → sz，4/8 → bj */
function ob_symbol($code) {
    $code = preg_replace('/\D/', '', (string)$code);
    if ($code === '') return '';
    $h = $code[0];
    if ($h === '6' || $h === '5' || $h === '9') return 'sh' . $code;
    if ($h === '4' || $h === '8') return 'bj' . $code;
    return 'sz' . $code;
}

/** 统一 HTTP GET：UA/Referer + 超时 + HTTP 200 校验 + GBK→UTF-8 */
function ob_get($url, $referer = '', $timeout = 8) {
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
            'Referer: ' . ($referer !== '' ? $referer : 'https://gu.qq.com/'),
            'Accept: */*',
        ],
    ]);
    $body = curl_exec($ch);
    $code = (int)curl_getinfo($ch, CURLINFO_HTTP_CODE);
    curl_close($ch);
    if ($body === false || $code !== 200 || $body === '') return '';
    // 腾讯接口是 GBK，需要转 UTF-8（名称字段会乱码）
    if (function_exists('mb_convert_encoding') && !mb_check_encoding($body, 'UTF-8')) {
        $body = mb_convert_encoding($body, 'UTF-8', 'GBK');
    }
    return $body;
}

/** 文件缓存（TTL 内直接用；失败时降级到旧缓存） */
function ob_cached($key, $ttl, $fetcher) {
    $dir = sys_get_temp_dir() . '/stock_orderbook_cache';
    if (!is_dir($dir)) @mkdir($dir, 0777, true);
    $file = $dir . '/' . md5($key) . '.json';
    if (is_file($file) && (time() - filemtime($file)) < $ttl) {
        $raw = @file_get_contents($file);
        if ($raw !== false) {
            $j = json_decode($raw, true);
            if ($j !== null) return $j;
        }
    }
    $fresh = null;
    try { $fresh = $fetcher(); } catch (Exception $e) { $fresh = null; }
    if ($fresh === null || $fresh === false) {
        // 失败：降级用旧缓存（放宽到 60 秒）
        if (is_file($file)) {
            $raw = @file_get_contents($file);
            if ($raw !== false) {
                $j = json_decode($raw, true);
                if ($j !== null) return $j;
            }
        }
        return null;
    }
    @file_put_contents($file, json_encode($fresh, JSON_UNESCAPED_UNICODE));
    return $fresh;
}

/**
 * 解析腾讯五档盘口
 * v_sh600887="1~伊利股份~600887~26.92~26.77~26.77~411393~232955~178438~
 *             26.92~8~26.91~942~26.90~957~26.89~133~26.88~392~
 *             26.93~20~26.94~36~26.95~40~26.96~693~26.97~1021~..."
 * 买1~5 价 9/11/13/15/17，量 10/12/14/16/18
 * 卖1~5 价 19/21/23/25/27，量 20/22/24/26/28
 */
function ob_fetch_orderbook($sym) {
    $url = 'https://qt.gtimg.cn/q=' . $sym;
    $body = ob_get($url, 'https://gu.qq.com/', 8);
    if ($body === '') return null;
    if (!preg_match('/="([^"]*)"/', $body, $m)) return null;
    $f = explode('~', $m[1]);
    if (count($f) < 30) return null;

    $num = function ($i) use ($f) {
        return isset($f[$i]) && is_numeric($f[$i]) ? (float)$f[$i] : 0.0;
    };

    $bids = [];
    $asks = [];
    // 买1~5：价 9,11,13,15,17 / 量 10,12,14,16,18
    for ($i = 0; $i < 5; $i++) {
        $pi = 9 + $i * 2;
        $vi = 10 + $i * 2;
        $bids[] = ['price' => $num($pi), 'vol' => $num($vi)];
    }
    // 卖1~5：价 19,21,23,25,27 / 量 20,22,24,26,28
    for ($i = 0; $i < 5; $i++) {
        $pi = 19 + $i * 2;
        $vi = 20 + $i * 2;
        $asks[] = ['price' => $num($pi), 'vol' => $num($vi)];
    }

    return [
        'code'      => isset($f[2]) ? $f[2] : '',
        'name'      => isset($f[1]) ? $f[1] : '',
        'price'     => $num(3),
        'prevClose' => $num(4),
        'open'      => $num(5),
        'volume'    => $num(6),
        'outer'     => $num(7),   // 外盘（主动买）
        'inner'     => $num(8),   // 内盘（主动卖）
        'bids'      => $bids,
        'asks'      => $asks,
    ];
}

/**
 * 解析腾讯逐笔成交
 * v_detail_data_sh600887=[页码,"序号/时间/价格/涨跌/成交量(手)/成交额(元)/方向|..."]
 * 例：70/09:33:27/26.80/0.00/573/1536243/B
 * 方向 B=买 S=卖 M=中性
 */
function ob_fetch_deals($sym, $page, $n) {
    $page = max(1, (int)$page);
    $url = 'https://stock.gtimg.cn/data/index.php?appn=detail&action=data&c=' . $sym . '&p=' . $page;
    $body = ob_get($url, 'https://gu.qq.com/', 8);
    if ($body === '') return [];
    // 格式：v_detail_data_sh600887=[页码,"序号/时间/价格/涨跌/量(手)/额(元)/方向|..."]
    // 注意这里没有 '="'，是 '=['，故不能用 "=" 匹配
    if (!preg_match('/=\[\s*(\d+)\s*,\s*"([^"]*)"/', $body, $m2)) return [];
    $list = $m2[2];
    if ($list === '') return [];

    $rows = explode('|', $list);
    $out = [];
    foreach ($rows as $r) {
        $r = trim($r);
        if ($r === '') continue;
        $c = explode('/', $r);
        if (count($c) < 7) continue;
        $vol    = (float)$c[4];   // 手
        $amount = (float)$c[5];   // 元
        $dir    = isset($c[6]) ? strtoupper(trim($c[6])) : 'M';
        if ($dir !== 'B' && $dir !== 'S') $dir = 'M';
        $out[] = [
            'no'     => (int)$c[0],
            'time'   => $c[1],
            'price'  => (float)$c[2],
            'change' => (float)$c[3],
            'vol'    => $vol,
            'amount' => $amount,
            'dir'    => $dir,
        ];
        if ($n > 0 && count($out) >= $n) break;
    }
    return $out;
}

// ---------------- 主流程 ----------------
$code = isset($_GET['code']) ? $_GET['code'] : (isset($_POST['code']) ? $_POST['code'] : '');
$page = isset($_GET['p'])    ? (int)$_GET['p'] : 1;
$n    = isset($_GET['n'])    ? (int)$_GET['n'] : 30;

if ($n < 1)  $n = 1;
if ($n > 70) $n = 70;

$sym = ob_symbol($code);
if ($sym === '') out(['code' => 1, 'msg' => '参数 code 不能为空', 'data' => null]);

// 五档：1 秒缓存
$ob = ob_cached('ob_' . $sym, 1, function () use ($sym) {
    return ob_fetch_orderbook($sym);
});

// 逐笔：1 秒缓存（按 页+条数 缓存）
$deals = ob_cached('deals_' . $sym . '_' . $page . '_' . $n, 1, function () use ($sym, $page, $n) {
    return ob_fetch_deals($sym, $page, $n);
});
if (!is_array($deals)) $deals = [];

if ($ob === null) {
    // 盘口失败但仍可能拿到逐笔 —— 兜底返回部分数据
    if (!empty($deals)) {
        out([
            'code' => 0,
            'msg'  => 'ok(盘口暂不可用)',
            'data' => [
                'code'      => preg_replace('/^(sh|sz|bj)/', '', $sym),
                'name'      => '',
                'price'     => 0,
                'prevClose' => 0,
                'open'      => 0,
                'volume'    => 0,
                'outer'     => 0,
                'inner'     => 0,
                'bids'      => [],
                'asks'      => [],
                'deals'     => $deals,
                'ts'        => time(),
            ],
        ]);
    }
    out(['code' => 1, 'msg' => '行情获取失败，请稍后重试', 'data' => null]);
}

$data = [
    'code'      => $ob['code'],
    'name'      => $ob['name'],
    'price'     => $ob['price'],
    'prevClose' => $ob['prevClose'],
    'open'      => $ob['open'],
    'volume'    => $ob['volume'],
    'outer'     => $ob['outer'],
    'inner'     => $ob['inner'],
    'bids'      => $ob['bids'],
    'asks'      => $ob['asks'],
    'deals'     => $deals,
    'ts'        => time(),
];

out(['code' => 0, 'msg' => 'ok', 'data' => $data]);
