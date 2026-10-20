<?php
$cache_time = 30;
$OJ_CACHE_SHARE = false;
$debug = false;
require_once('./include/cache_start.php');
require_once('./include/db_info.inc.php');
require_once('./include/my_func.inc.php');
ini_set("display_errors", "Off");
require_once('./include/setlang.php');
require_once('./include/online.php');
$on = new online();
$view_title = "Welcome To Online Judge";
require_once('./include/iplocation.php');
$location = new IpLocation();
$users = $on->getAll();

$view_online = array();

if (isset($_SESSION[$OJ_NAME . '_' . 'administrator'])) {

    $sql = "SELECT loginlog.user_id,password,loginlog.ip,time,users.nick
            FROM loginlog LEFT JOIN users ON users.user_id=loginlog.user_id";
    $search = $_GET['search'];
    if ($search != '') {
        $sql = $sql . " WHERE loginlog.ip like ? ";
        $search = "%$search%";
    } else {
        $sql = $sql . " where loginlog.user_id<>? ";
        $search = $_SESSION[$OJ_NAME . '_' . 'user_id'];
    }
    $sql = $sql . "  order by `log_id` desc LIMIT 0,50";

    $result = pdo_query($sql, $search);
    $i = 0;
} else {
    $sql = "SELECT loginlog.user_id,password,loginlog.ip,time,users.nick
            FROM loginlog LEFT JOIN users ON users.user_id=loginlog.user_id
            ORDER BY log_id DESC LIMIT 20";
    $result = pdo_query($sql);
}

foreach ($result as $row) {

    $view_online[$i][0] = "<a href='userinfo.php?user=" . rawurlencode($row[0]) . "'>" . hustoj_user_display_name_html($row[0], $row[4]) . "</a>";
    $view_online[$i][1] = htmlentities($row[1], ENT_QUOTES, "UTF-8");
    $view_online[$i][2] = htmlentities($row[2], ENT_QUOTES, "UTF-8");
    $view_online[$i][3] = htmlentities($row[3], ENT_QUOTES, "UTF-8");

    $i++;
}

/////////////////////////Template
require("template/" . $OJ_TEMPLATE . "/online.php");
/////////////////////////Common foot
if (file_exists('./include/cache_end.php'))
    require_once('./include/cache_end.php');
?>
