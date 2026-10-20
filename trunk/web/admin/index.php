<?php
require_once("../include/db_info.inc.php");
require_once("../include/setlang.php");

$admin_roles = array(
    'administrator',
    'contest_creator',
    'user_adder',
    'password_setter',
    'problem_editor'
);
$has_admin_access = false;
foreach ($admin_roles as $admin_role) {
    if (isset($_SESSION[$OJ_NAME.'_'.$admin_role])) {
        $has_admin_access = true;
        break;
    }
}
if (!$has_admin_access) {
    header("Location: ../loginpage.php");
    exit;
}

$show_title = $OJ_NAME.$MSG_ADMIN;
require_once("../template/$OJ_TEMPLATE/header.php");
?>
<style>
    #main.oj-admin-page {
        width: 100%;
        max-width: none;
        box-sizing: border-box;
        padding: 24px 24px 0;
    }
    .oj-admin-layout {
        display: grid;
        grid-template-columns: minmax(230px, 270px) minmax(0, 1fr);
        gap: 20px;
        align-items: stretch;
    }
    .oj-admin-menu,
    .oj-admin-content {
        min-width: 0;
        overflow: hidden;
        border: 1px solid #e2e8f0;
        border-radius: 12px;
        background: #fff;
        box-shadow: 0 8px 24px rgba(15, 23, 42, .08);
    }
    .oj-admin-menu {
        background: #0f172a;
    }
    .oj-admin-panel-title {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 12px;
        min-height: 58px;
        padding: 0 18px;
        color: #fff;
        background: linear-gradient(135deg, #1e293b, #0f172a);
        font-weight: 700;
    }
    .oj-admin-panel-title i {
        margin-right: 7px;
    }
    .oj-admin-panel-title a {
        color: #fdba74;
        font-size: 12px;
        font-weight: 500;
        white-space: nowrap;
    }
    .oj-admin-menu iframe {
        display: block;
        width: 100%;
        height: calc(100vh - 190px);
        min-height: 720px;
        border: 0;
    }
    .oj-admin-content iframe {
        display: block;
        width: 100%;
        height: calc(100vh - 190px);
        min-height: 720px;
        border: 0;
        background: #fff;
    }
    @media (max-width: 900px) {
        #main.oj-admin-page {
            padding: 16px 12px 0;
        }
        .oj-admin-layout {
            grid-template-columns: 1fr;
        }
        .oj-admin-menu iframe {
            height: auto;
            min-height: 420px;
            max-height: 58vh;
        }
        .oj-admin-content iframe {
            height: 720px;
            min-height: 720px;
        }
    }
</style>
<link rel="stylesheet" href="admin-theme.css?v=7">
<script>
    document.getElementById('main').classList.add('oj-admin-page');
</script>
<div class="oj-admin-layout">
    <aside class="oj-admin-menu">
        <div class="oj-admin-panel-title">
            <span><i class="settings icon"></i><?php echo $MSG_ADMIN; ?></span>
            <a href="../" title="返回普通用户界面">返回用户界面</a>
        </div>
        <iframe src="menu.php?embedded=1" title="管理员菜单"></iframe>
    </aside>
    <section class="oj-admin-content">
        <iframe name="main" src="help.php" title="管理员内容"></iframe>
    </section>
</div>
<?php require_once("../template/$OJ_TEMPLATE/footer.php"); ?>
