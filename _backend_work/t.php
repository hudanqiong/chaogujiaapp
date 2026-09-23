<?php
require '/var/www/html/stock_api/config.php';
$uid = 1;
// 找或建分组
$gid = db()->query("SELECT id FROM watch_group WHERE user_id=$uid ORDER BY sort ASC, id ASC LIMIT 1")->fetchColumn();
if (!$gid) {
    db()->exec("INSERT INTO watch_group(user_id,name,sort) VALUES($uid,'测试',0)");
    $gid = db()->lastInsertId();
}
echo "gid=$gid\n";
// 清掉测试股后插入两只
db()->exec("DELETE FROM watch_stock WHERE user_id=$uid AND code IN ('600519','000001')");
$st = db()->prepare("INSERT INTO watch_stock(user_id,group_id,code,name,sort) VALUES(?,?,?,?,?)");
$st->execute([$uid,$gid,'600519','贵州茅台',0]);
$st->execute([$uid,$gid,'000001','平安银行',1]);
echo "inserted\n";
