<?php
require_once('./include/db_info.inc.php');
require_once('./include/my_func.inc.php');

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    http_response_code(405);
    header('Allow: POST');
    exit('Method Not Allowed');
}

if (empty($_SESSION[$OJ_NAME . '_user_id'])) {
    header('Location: loginpage.php');
    exit();
}

require_once('./include/check_post_key.php');

$current_user = $_SESSION[$OJ_NAME . '_user_id'];
$target_user = isset($_POST['target_user']) ? trim($_POST['target_user']) : '';
$action = isset($_POST['action']) ? $_POST['action'] : '';

function social_redirect($target_user, $type, $message)
{
    global $OJ_NAME;
    $_SESSION[$OJ_NAME . '_social_notice'] = array(
        'type' => $type,
        'message' => $message
    );
    header('Location: userinfo.php?user=' . rawurlencode($target_user) . '&social_updated=' . rawurlencode(uniqid('', true)));
    exit();
}

if (!is_valid_user_name($target_user)) {
    social_redirect($current_user, 'error', '用户不存在。');
}

$target_rows = pdo_query(
    "SELECT user_id FROM users WHERE user_id=? AND defunct='N' LIMIT 1",
    $target_user
);
if (!is_array($target_rows) || count($target_rows) !== 1) {
    social_redirect($current_user, 'error', '用户不存在或已被停用。');
}

if ($action === 'follow') {
    if ($current_user === $target_user) {
        social_redirect($target_user, 'error', '不能关注自己。');
    }

    $result = pdo_query(
        'INSERT IGNORE INTO user_follow (follower_id, followed_id, in_date) VALUES (?, ?, NOW())',
        $current_user,
        $target_user
    );
    social_redirect(
        $target_user,
        $result === -1 ? 'error' : 'success',
        $result === -1 ? '关注失败，请确认数据库已经升级。' : '关注成功。'
    );
}

if ($action === 'unfollow') {
    $result = pdo_query(
        'DELETE FROM user_follow WHERE follower_id=? AND followed_id=?',
        $current_user,
        $target_user
    );
    social_redirect(
        $target_user,
        $result === -1 ? 'error' : 'success',
        $result === -1 ? '取消关注失败，请稍后重试。' : '已取消关注。'
    );
}

if ($action === 'message_add') {
    if ($current_user === $target_user) {
        social_redirect($target_user, 'error', '不能给自己留言。');
    }

    $content = isset($_POST['content']) ? trim(str_replace("\0", '', $_POST['content'])) : '';
    $content_length = function_exists('mb_strlen') ? mb_strlen($content, 'UTF-8') : strlen($content);
    if ($content_length < 1 || $content_length > 500) {
        social_redirect($target_user, 'error', '留言内容须为 1 至 500 个字符。');
    }
    if (has_bad_words($content)) {
        social_redirect($target_user, 'error', '留言中包含不适合发布的内容。');
    }

    $recent_messages = pdo_query(
        'SELECT message_id FROM user_message WHERE author_id=? AND in_date>DATE_SUB(NOW(), INTERVAL 10 SECOND) LIMIT 1',
        $current_user
    );
    if ($recent_messages === -1) {
        social_redirect($target_user, 'error', '留言失败，请确认数据库已经升级。');
    }
    if (!empty($recent_messages)) {
        social_redirect($target_user, 'error', '留言太频繁，请 10 秒后再试。');
    }

    $result = pdo_query(
        "INSERT INTO user_message (profile_user_id, author_id, content, in_date, ip, defunct) VALUES (?, ?, ?, NOW(), ?, 'N')",
        $target_user,
        $current_user,
        $content,
        isset($ip) ? substr($ip, 0, 46) : ''
    );
    social_redirect(
        $target_user,
        $result === -1 ? 'error' : 'success',
        $result === -1 ? '留言失败，请稍后重试。' : '留言已发布。'
    );
}

if ($action === 'message_delete') {
    $message_id = isset($_POST['message_id']) ? intval($_POST['message_id']) : 0;
    $message_rows = pdo_query(
        "SELECT message_id, profile_user_id, author_id FROM user_message WHERE message_id=? AND defunct='N' LIMIT 1",
        $message_id
    );
    if (!is_array($message_rows) || count($message_rows) !== 1) {
        social_redirect($target_user, 'error', '留言不存在或已经删除。');
    }

    $message = $message_rows[0];
    $target_user = $message['profile_user_id'];
    $can_delete = $current_user === $message['author_id']
        || $current_user === $message['profile_user_id']
        || isset($_SESSION[$OJ_NAME . '_administrator']);
    if (!$can_delete) {
        social_redirect($target_user, 'error', '你没有权限删除这条留言。');
    }

    $result = pdo_query("UPDATE user_message SET defunct='Y' WHERE message_id=?", $message_id);
    social_redirect(
        $target_user,
        $result === -1 ? 'error' : 'success',
        $result === -1 ? '删除留言失败，请稍后重试。' : '留言已删除。'
    );
}

social_redirect($target_user, 'error', '未知操作。');
?>
