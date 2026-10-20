<?php
$cache_time = 10;
$OJ_CACHE_SHARE = false;
header('Cache-Control: private, no-store, no-cache, must-revalidate, max-age=0');
header('Pragma: no-cache');
require_once('include/cache_start.php');
require_once('include/db_info.inc.php');
require_once('include/setlang.php');
require_once("include/const.inc.php");
require_once("include/my_func.inc.php");
require_once("include/avatar.php");
require_once("include/memcache.php");
require_once("include/iplocation.php");
// check user
$user = $_GET['user'];
if (!is_valid_user_name($user)) {
    echo "No such User!";
    exit(0);
}
$iplocation = new IpLocation();

function extractPlistBlocks($inputString)
{
    // 定义正则表达式模式
    $pattern = '/\[plist=[^\]]*\][^[]*?\[\/plist\]/';

    // 使用 preg_match_all 函数查找所有匹配项
    preg_match_all($pattern, $inputString, $matches);

    return $matches[0];
}

function extractPlistData($inputString)
{
    // 定义正则表达式模式
    $pattern = '/\[plist=([^]]*)\](.*?)\[\/plist\]/s';

    // 使用 preg_match 函数查找匹配项
    if (preg_match($pattern, $inputString, $matches)) {
        // 返回匹配到的 example1 和 内容1
        return [
            'name' => $matches[2],
            'list' => str_replace("&#44;", ",", trim($matches[1]))
        ];
    }

    return null;
}

$sql = "select content from news where content like '%[plist=%' and defunct='N' ";
// 示例输入字符串
$news = array_column(mysql_query_cache($sql), 'content');
$news = array_unique($news);
$plista = array();
$bible = array();
foreach ($news as $plists) {
// 提取 plist 块
    $plistBlocks = extractPlistBlocks($plists);
    foreach ($plistBlocks as $plistB) {
        $plist = extractPlistData($plistB);
//		print_r($plist);
//		echo "<br>";
        if (!empty($pid)) array_push($plista, $plist);
    }
// 输出结果
    //$plista=array_merge($plist,$plistBlocks);
}
foreach ($plista as $plist) {
    $name = $plist["name"];
    $list = explode(",", $plist['list']);
    foreach ($list as $pid) {
        array_push($bible, $pid);
    }
}
$bible = array_unique($bible);

if (!empty($bible)) {
    $bible = mysql_query_cache("select problem_id,title from problem where problem_id in (" . implode(",", $bible) . ")");
    // 提取 id 列作为键
    $keys = array_column($bible, 'problem_id');

    // 提取 name 列作为值
    $values = array_column($bible, 'title');

    // 使用 array_combine 将键和值组合成一个映射
    $bible = array_combine($keys, $values);
    // var_dump($ptitle);

//	print_r($bible);
}
$now = date('Y-m-d H:i', time());
$exceptions = array();
if (isset($OJ_NOIP_KEYWORD) && $OJ_NOIP_KEYWORD && !isset($_SESSION[$OJ_NAME . "_administrator"])) {  // && !isset($_SESSION[$OJ_NAME."_administrator"])   管理员不受限

    $sql = "select contest_id from contest c where  c.start_time<'$now' and c.end_time>'$now' and ( c.title like '%$OJ_NOIP_KEYWORD%' or ((c.contest_type & 20) >0 and end_time>now() ) )";
    $row = pdo_query($sql);
    if (count($row) > 0) {
        $exceptions = array_column($row, 'contest_id');
    }
}
if (!empty($exceptions)) {
    $not_in_noip = " and contest_id not in (" . implode(",", $exceptions) . ") ";
} else {
    $not_in_noip = "";
}
$view_title = $user . "@" . $OJ_NAME;
if (isset($_SESSION[$OJ_NAME . '_' . 'administrator']))
    $sql = "select * FROM `users` WHERE `user_id`=? ";
else
    $sql = "select * FROM `users` WHERE `user_id`=? and user_id not in ($OJ_RANK_HIDDEN) ";
$result = mysql_query_cache($sql, $user);
$row_cnt = count($result);
if ($row_cnt == 0) {
    $view_errors = "No such User!";
    require("template/" . $OJ_TEMPLATE . "/error.php");
    exit(0);
}

$row = $result[0];
$school = $row['school'];
$group_name = $row['group_name'];
if (empty($group_name)) {
    $mapped_special = trim((string) getMappedSpecial($user));
    $group_name = $mapped_special === '' ? '' : '[' . $mapped_special . ']';
}
$email = $row['email'];
$avatar_url = hustoj_avatar_url($user);
$nick = $row['nick'];
$starred = $row['starred'];
if (!$starred && starred($user)) {
    mysql_query_cache("update users set starred=1 where user_id=?", $user);
    $starred = 1;
}

// Social profile data: one-way follows (which naturally allows mutual follows)
// and public messages left on this user's profile.
$current_user = isset($_SESSION[$OJ_NAME . '_user_id']) ? $_SESSION[$OJ_NAME . '_user_id'] : '';
$social_notice = null;
if (isset($_SESSION[$OJ_NAME . '_social_notice'])) {
    $social_notice = $_SESSION[$OJ_NAME . '_social_notice'];
    unset($_SESSION[$OJ_NAME . '_social_notice']);
}

$social_tables_ready = false;
$follower_count = 0;
$following_count = 0;
$message_count = 0;
$is_following = false;
$is_followed_by = false;
$followers = array();
$following_users = array();
$profile_messages = array();

$social_stats = pdo_query(
    "SELECT
        (SELECT COUNT(*) FROM user_follow f INNER JOIN users u ON u.user_id=f.follower_id WHERE f.followed_id=? AND u.defunct='N') AS follower_count,
        (SELECT COUNT(*) FROM user_follow f INNER JOIN users u ON u.user_id=f.followed_id WHERE f.follower_id=? AND u.defunct='N') AS following_count,
        (SELECT COUNT(*) FROM user_message m INNER JOIN users u ON u.user_id=m.author_id WHERE m.profile_user_id=? AND m.defunct='N' AND u.defunct='N') AS message_count",
    $user,
    $user,
    $user
);
if (is_array($social_stats) && isset($social_stats[0])) {
    $social_tables_ready = true;
    $follower_count = intval($social_stats[0]['follower_count']);
    $following_count = intval($social_stats[0]['following_count']);
    $message_count = intval($social_stats[0]['message_count']);

    $followers = pdo_query(
        "SELECT f.follower_id AS user_id, u.nick
         FROM user_follow f INNER JOIN users u ON u.user_id=f.follower_id
         WHERE f.followed_id=? AND u.defunct='N'
         ORDER BY f.in_date DESC LIMIT 12",
        $user
    );
    $following_users = pdo_query(
        "SELECT f.followed_id AS user_id, u.nick
         FROM user_follow f INNER JOIN users u ON u.user_id=f.followed_id
         WHERE f.follower_id=? AND u.defunct='N'
         ORDER BY f.in_date DESC LIMIT 12",
        $user
    );
    $profile_messages = pdo_query(
        "SELECT m.message_id, m.author_id, m.content, m.in_date, u.nick
         FROM user_message m INNER JOIN users u ON u.user_id=m.author_id
         WHERE m.profile_user_id=? AND m.defunct='N' AND u.defunct='N'
         ORDER BY m.message_id DESC LIMIT 50",
        $user
    );

    if (!is_array($followers)) $followers = array();
    if (!is_array($following_users)) $following_users = array();
    if (!is_array($profile_messages)) $profile_messages = array();

    if ($current_user !== '' && $current_user !== $user) {
        $follow_state = pdo_query(
            'SELECT follower_id, followed_id FROM user_follow WHERE (follower_id=? AND followed_id=?) OR (follower_id=? AND followed_id=?)',
            $current_user,
            $user,
            $user,
            $current_user
        );
        if (is_array($follow_state)) {
            foreach ($follow_state as $follow_row) {
                if ($follow_row['follower_id'] === $current_user) $is_following = true;
                if ($follow_row['follower_id'] === $user) $is_followed_by = true;
            }
        }
    }
}


// count solved
$sql = "select count(DISTINCT problem_id) as `ac` FROM `solution` WHERE `user_id`=? AND `result`=4 and problem_id>0 $not_in_noip ";
$result = mysql_query_cache($sql, $user);
$row = $result[0];
$AC = $row['ac'];

// count submission
$sql = "select count(DISTINCT problem_id) as `Submit` FROM `solution` WHERE `user_id`=? and  problem_id>0  $not_in_noip ";
$result = mysql_query_cache($sql, $user);
$row = $result[0];
$Submit = $row['Submit'];

// update solved 
$sql = "UPDATE `users` SET `solved`='" . strval($AC) . "',`submit`='" . strval($Submit) . "' WHERE `user_id`=?";
$result = mysql_query_cache($sql, $user);
$sql = "select count(*) as `Rank` FROM `users` WHERE `solved`>? and defunct='N' and user_id not in (" . $OJ_RANK_HIDDEN . ") ";
$result = mysql_query_cache($sql, $AC);
$row = $result[0];
$Rank = intval($row[0]) + 1;

// load coin data (recalculate from first-ac problems to ensure consistency)
$sql = "UPDATE users SET coin_earned = COALESCE((SELECT SUM(coin) FROM problem WHERE problem_id IN (SELECT DISTINCT problem_id FROM solution WHERE user_id=users.user_id AND result=4 AND first_time=1 AND problem_id>0)), 0) WHERE user_id=?";
pdo_query($sql, $user);

$sql = "select `coin_earned`, `coin_bonus`, `coin_spent` FROM `users` WHERE `user_id`=?";
$result = mysql_query_cache($sql, $user);
$row = $result[0];
$coin_earned = intval($row['coin_earned']);
$coin_bonus = intval($row['coin_bonus']);
$coin_spent = intval($row['coin_spent']);
$coin_balance = $coin_earned + $coin_bonus - $coin_spent;

if (isset($_SESSION[$OJ_NAME . '_' . 'administrator'])) {
    $sql = "select user_id,password,ip,`time` FROM `loginlog` WHERE `user_id`=? order by `time` desc LIMIT 0,50";
    $view_userinfo = mysql_query_cache($sql, $user);
}
$sql = "select result,count(1) FROM solution WHERE `user_id`=? AND result>=4 $not_in_noip group by result order by result";
$result = mysql_query_cache($sql, $user);
$view_userstat = array();
$i = 0;
foreach ($result as $row) {
    $view_userstat[$i++] = $row;
}


$sql = "select UNIX_TIMESTAMP(date(in_date))*1000 md,count(1) c FROM `solution` where  `user_id`=? $not_in_noip group by md order by md desc ";
$result = mysql_query_cache($sql, $user);//mysql_escape_string($sql));
$chart_data_all = array();
//echo $sql;

foreach ($result as $row) {
    $chart_data_all[$row['md']] = $row['c'];
}

$sql = "select UNIX_TIMESTAMP(date(in_date))*1000 md,count(1) c FROM `solution` where  `user_id`=? and result=4 $not_in_noip group by md order by md desc ";
$result = mysql_query_cache($sql, $user);//mysql_escape_string($sql));
$chart_data_ac = array();
//echo $sql;

foreach ($result as $row) {
    $chart_data_ac[$row['md']] = $row['c'];
}

$acc_arr = array();
if (isset($_SESSION[$OJ_NAME . '_' . 'user_id'])) {
    $sql = "select distinct `problem_id` FROM `solution` WHERE `user_id`=? AND `result`=4 $not_in_noip";
    if (isset($pids) && $pids != "") $sql .= " and problem_id in ($pids)";
    $result = mysql_query_cache($sql, $_SESSION[$OJ_NAME . '_' . 'user_id']);
    foreach ($result as $row)
        $acc_arr[$row[0]] = true;
}


/////////////////////////Template
require("template/" . $OJ_TEMPLATE . "/userinfo.php");
/////////////////////////Common foot
if (file_exists('./include/cache_end.php'))
    require_once('./include/cache_end.php');
?>

