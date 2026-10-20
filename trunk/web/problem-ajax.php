<?php
header("Cache-Control: no-cache, must-revalidate"); // HTTP/1.1
header("Expires: Sat, 26 Jul 1997 05:00:00 GMT"); // Date in the past

////////////////////////////Common head
$cache_time = 2;
$OJ_CACHE_SHARE = false;
require_once('./include/db_info.inc.php');
require_once('./include/setlang.php');
require_once("./include/const.inc.php");
require_once("./include/memcache.php");
require_once("./include/my_func.inc.php");

$pid = 0;
// check the top arg

if (isset($_GET['pid'])) {
    $pid = intval($_GET['pid']);
}

// 查询命题人的昵称；user_id 只作为稳定的链接参数使用。
$sql = "SELECT p.user_id, u.nick
        FROM privilege p LEFT JOIN users u ON u.user_id=p.user_id
        WHERE p.rightstr=? LIMIT 1";
$result = mysql_query_cache($sql, "p" . $pid);


if ($result) {
    $row = $result[0];
    echo "<a href='userinfo.php?user=" . rawurlencode($row['user_id']) . "'>"
        . hustoj_user_display_name_html($row['user_id'], $row['nick']) . "</a>";
} else {
    echo "$MSG_IMPORTED";
}

