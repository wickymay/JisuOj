<?php
        require_once(dirname(__FILE__)."/../../include/memcache.php");
        function checkmail(){  // check if has mail
          global $OJ_NAME;
          $sql="select count(1) cnt FROM `mail` WHERE new_mail=1 AND `to_user`=?";
          $result=pdo_query($sql,$_SESSION[$OJ_NAME.'_'.'user_id']);
          if(empty($result)) return false;
          $row=$result[0];
          //if(intval($row[0])==0) return false;
          $retmsg="<span id=red>(".$row['cnt'].")</span>";
          return $retmsg;
        }

        function get_menu_news() {
            $result = "";
            $sql_news_menu = "select `news_id`,`title` FROM `news` WHERE `menu`=1 AND `title`!='faqs.cn' ORDER BY `importance` ASC,`time` DESC LIMIT 10";
            $sql_news_menu_result = mysql_query_cache( $sql_news_menu );
            if ( $sql_news_menu_result ) {
                foreach ( $sql_news_menu_result as $row ) {
                    $result .= '<a class="item" href="/viewnews.php?id=' . $row['news_id'] . '">' ."<i class='star icon'></i>" . $row['title'] . '</a>';
                }
            }
            return $result;
        }
        $url=basename($_SERVER['REQUEST_URI']);
        $dir=basename(getcwd());
        if($dir=="discuss3" || $dir=="admin" || (isset($_SERVER['SCRIPT_NAME']) && strpos($_SERVER['SCRIPT_NAME'], '/admin/') !== false)) $path_fix="../";
        else $path_fix="";
        if(isset($OJ_NEED_LOGIN)&&$OJ_NEED_LOGIN&&(
                  $url!='loginpage.php'&&
                  $url!='lostpassword.php'&&
                  $url!='lostpassword2.php'&&
                  $url!='registerpage.php'
                  ) && !isset($_SESSION[$OJ_NAME.'_'.'user_id'])){

           header("location:".$path_fix."loginpage.php");
           exit();
        }

        if($OJ_ONLINE){
                require_once($path_fix.'include/online.php');
                $on = new online();
        }

        $sql_news_menu_result_html = "";

        if ($OJ_MENU_NEWS) {
            if ($OJ_REDIS) {
                $redis = new Redis();
                $redis->connect($OJ_REDISSERVER, $OJ_REDISPORT);

                if (isset($OJ_REDISAUTH)) {
                  $redis->auth($OJ_REDISAUTH);
                }
                $redisDataKey = $OJ_REDISQNAME . '_MENU_NEWS_CACHE';
                if ($redis->exists($redisDataKey)) {
                    $sql_news_menu_result_html = $redis->get($redisDataKey);
                } else {
                    $sql_news_menu_result_html = get_menu_news();
                    $redis->set($redisDataKey, $sql_news_menu_result_html);
                    $redis->expire($redisDataKey, 300);
                }

                $redis->close();
            } else {
                $sessionDataKey = $OJ_NAME.'_'."_MENU_NEWS_CACHE";
                if (isset($_SESSION[$sessionDataKey])) {
                    $sql_news_menu_result_html = $_SESSION[$sessionDataKey];
                } else {
                    $sql_news_menu_result_html = get_menu_news();
                    $_SESSION[$sessionDataKey] = $sql_news_menu_result_html;
                }
            }
        }
?>

<!DOCTYPE html>
<html lang="zh-CN">

<head>
    <meta charset="utf-8">
    <meta content="IE=edge" http-equiv="X-UA-Compatible">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title><?php echo $show_title ?></title>
    <script>
        (function () {
            var theme = <?php echo json_encode($OJ_CSS === "dark.css" ? "dark" : "light"); ?>;
            try {
                theme = window.localStorage.getItem("hustoj-theme") || theme;
            } catch (error) {
                // Storage may be unavailable in privacy-restricted browsers.
            }
            document.documentElement.setAttribute("data-theme", theme === "dark" ? "dark" : "light");
        })();
    </script>
    <?php include(dirname(__FILE__)."/css.php");?>
        <style>
@media (max-width: 991px) {
        .mobile-only {
                display:block !important;
        }

        .desktop-only {
            display:none !important;
        }
}

</style>

    <script src="<?php echo $OJ_CDN_URL.$path_fix."template/syzoj/js/"?>jquery.min.js"></script>

<!-- Scripts -->
<script>
    console.log('\n %c HUSTOJ %c https://github.com/zhblue/hustoj %c\n', 'color: #fadfa3; background: #000000; padding:5px 0;', 'background: #fadfa3; padding:5px 0;', '');
    console.log('\n %c Theme By %c Baoshuo ( @renbaoshuo ) %c https://baoshuo.ren %c\n', 'color: #fadfa3; background: #000000; padding:5px 0;', 'background: #fadfa3; padding:5px 0;', 'background: #ffbf33; padding:5px 0;', '');
    console.log('\n GitHub Homepage: https://github.com/zhblue/hustoj \n Document: https://zhblue.github.io/hustoj \n Bug report URL: https://github.com/zhblue/hustoj/issues \n \n%c ★ Please give us a star on GitHub! ★ %c \n', 'color: red;', '')
</script>
</head>

<?php
        if(!isset($_GET['spa'])){
?>
   
<body id="MainBg-C">
    <div id="page-header" class="ui fixed borderless menu" style="position: fixed; height: 49px; z-index:99999">
        <div id="menu" class="ui stackable mobile ui container computer" style="margin-left:auto;margin-right:auto;">
            <a class="header item"  href="/"><span
                    style="font-family: 'Exo 2'; font-size: 1.5em; font-weight: 600; ">JISUOJ</span></a>
            
          <?php
            if(isset($OJ_AI_HTML)&&$OJ_AI_HTML && !isset($OJ_ON_SITE_CONTEST_ID) ) echo $OJ_AI_HTML;
            else echo '<a class="desktop-only item" href="/"><i class="home icon"></i><span class="desktop-only">'.$MSG_HOME.'</span></a>';
            if(file_exists("moodle")){  // 如果存在moodle目录，自动添加链接
              echo '<a class="item" href="moodle"><i class="group icon"></i><span class="desktop-only">Moodle</span></a>';
            }
            if(file_exists("hello")){  // 如果存在hello目录，自动添加链接
              echo '<a class="item" onclick=\'window.open("/hello/index.html", "_blank",
        "width=600,height=850,left=" + (window.screen.width-600)  + ",top=0,toolbar=no,menubar=no,location=no,status=no,resizable=yes");\'><i class="book icon"></i><span class="desktop-only">Hello算法</span></a>';
            }

                
             if( !isset($OJ_ON_SITE_CONTEST_ID) && (!isset($_GET['cid'])||$cid==0) ){
          ?>
            <!-- 问题 -->
            <a class="item <?php if ($url=="problemset.php") echo "active";?>"
                href="<?php echo $path_fix?>problemset.php"><i class="list icon"></i><span class="desktop-only"><?php echo $MSG_PROBLEMS?></span></a>
            <!-- 来源/分类 -->
            <a class="item <?php if ($url=="category.php") echo "active";?>"
                href="<?php echo $path_fix?>category.php"><i class="globe icon"></i><span class="desktop-only"><?php echo $MSG_SOURCE?></span></a>
            <!-- 竞赛/作业 -->
            <a class="item <?php if ($url=="contest.php") echo "active";?>" href="<?php echo $path_fix?>contest.php<?php if(isset($_SESSION[$OJ_NAME."_user_id"])) echo "?my" ?>" ><i
                    class="trophy icon"></i><span class="desktop-only"> <?php echo $MSG_CONTEST?></span></a>
            <!-- 状态 -->
            <a class="item <?php if ($url=="status.php") echo "active";?>" href="<?php echo $path_fix?>status.php"><i
                    class="tasks icon"></i><span class="desktop-only"><?php echo $MSG_STATUS?></span></a>
            <!-- 排名 -->
            <a class="item <?php if ($url=="ranklist.php") echo "active";?> "
                href="<?php echo $path_fix?>ranklist.php"><i class="signal icon"></i><span class="desktop-only"><?php echo $MSG_RANKLIST?></span></a>
            <!--<a class="item <?php //if ($url=="contest.php") echo "active";?>" href="/discussion/global"><i class="comments icon"></i><span class="desktop-only"><?php echo $MSG_BBS?></span></a>-->
            <!-- 近期比赛 -->    
<?php if(isset($OJ_RECENT_CONTEST)&&$OJ_RECENT_CONTEST){    ?>
            <a class="item <?php if ($url=="recent-contest.php") echo "active";?> "
                href="<?php echo $path_fix?>recent-contest.php"><i class="bullhorn icon"></i> <span class="desktop-only"><?php echo $MSG_RECENT_CONTEST?></span></a>
<?php } ?>
            <!-- 常见问答 -->
            <a class="item <?php if ($url=="faqs.php") echo "active";?>" href="<?php echo $path_fix?>faqs.php"><i
                    class="help circle icon"></i><span class="desktop-only"> <?php echo $MSG_FAQ?></span></a>
            <!-- 加入我们 -->
            <a class="item <?php if ($url=="joinus.php") echo "active";?>" href="<?php echo $path_fix?>joinus.php"><i
                    class="users icon"></i><span class="desktop-only"> 加入我们</span></a>
            <!-- 讨论板 -->
              <?php if (isset($OJ_BBS)&& $OJ_BBS){ ?>
                  <a class='item' href="discuss.php"><i class="clipboard icon"></i> <span class="desktop-only"><?php echo $MSG_BBS?></span></a>
              <?php }

            }
                ?>
            <?php if( isset($_GET['cid']) && intval($_GET['cid'])>0 ){
                     $cid=intval($_GET['cid']);
                     if(!isset($OJ_ON_SITE_CONTEST_ID)){   ?>
                            <a id="" class="item" href="<?php echo $path_fix?>contest.php" ><i class="arrow left icon"></i><span class="desktop-only"><?php echo $MSG_CONTEST.$MSG_LIST?></span></a>
            <?php    }      ?>
            <a id="" class="item active" href="<?php echo $path_fix?>contest.php?cid=<?php echo $cid?>" ><i class="list icon"></i><span class="desktop-only"><?php echo $MSG_PROBLEMS.$MSG_LIST?></span></a>
            <a id="" class="item active" href="<?php echo $path_fix?>status.php?cid=<?php echo $cid?>" ><i class="tasks icon"></i><span class="desktop-only"><?php echo $MSG_STATUS.$MSG_LIST?></span></a>
            <a id="" class="item active" href="<?php echo $path_fix?>contestrank.php?cid=<?php echo $cid?>" ><i class="numbered list icon"></i><span class="desktop-only"><?php echo $MSG_RANKLIST?></span></a>
            <a id="" class="item active" href="<?php echo $path_fix?>contestrank-oi.php?cid=<?php echo $cid?>" ><i class="child icon"></i><span class="desktop-only">OI-<?php echo $MSG_RANKLIST?></span></a>
            <?php if (isset($OJ_BBS)&& $OJ_BBS){ ?>
                  <a class='item active' href="discuss.php?cid=<?php echo $cid?>"><i class="clipboard icon"></i> <span class="desktop-only"><?php echo $MSG_BBS?></span></a>
             <?php } ?>

                    <?php if(isset($_SESSION[$OJ_NAME.'_'.'administrator'])||isset($_SESSION[$OJ_NAME.'_'.'contest_creator'])||isset($_SESSION[$OJ_NAME.'_'.'problem_editor'])){ ?>
                            <a id="" class="item active" href="<?php echo $path_fix?>conteststatistics.php?cid=<?php echo $cid?>" ><i class="eye icon"></i><span class="desktop-only"><?php echo $MSG_STATISTICS?></span></a>
                    <?php }  ?>
            <?php }  ?>
            <?php
                if($OJ_MENU_DROPDOWN){
            ?>
            <div class="ui simple dropdown item">
                        <i class="book icon"></i><span class='desktop-only'>学习资料</span><i class="dropdown icon"></i>
                        <div class="menu">
            <?php  } ?>
            <?php echo $sql_news_menu_result_html; ?>
            <?php
            if($OJ_MENU_DROPDOWN){
                ?>
                        </div>
            </div>
            <?php } ?>

            <div class="right menu">
                <?php if(isset($_SESSION[$OJ_NAME.'_'.'user_id'])) {
                   
                ?>
                <?php if (isset($_SESSION[$OJ_NAME.'_'.'administrator']) || isset($_SESSION[$OJ_NAME.'_'.'contest_creator']) || isset($_SESSION[$OJ_NAME.'_'.'user_adder']) || isset($_SESSION[$OJ_NAME.'_'.'password_setter']) || isset($_SESSION[$OJ_NAME.'_'.'problem_editor'])) { ?>
                <a class="item" href="<?php echo $path_fix?>admin/"
                    style="display:inline-flex!important;align-items:center!important;align-self:center!important;height:39px!important;min-height:39px!important;max-height:39px!important;box-sizing:border-box!important;padding:0 14px!important;line-height:1!important;background-color:#f59e0b!important;color:#fff!important;border-radius:4px;margin:0 4px!important;vertical-align:middle;"
                    title="进入管理员界面"><i class="settings icon"></i><span class="desktop-only">管理员</span></a>
                <?php } ?>
                <div class="ui dropdown item user-menu">
                    <button type="button" class="user-menu-trigger" aria-haspopup="true" aria-expanded="false">
                        <span class="user-menu-link">
                        <?php $header_user_name = !empty($_SESSION[$OJ_NAME.'_nick'])
                                  ? $_SESSION[$OJ_NAME.'_nick']
                                  : $_SESSION[$OJ_NAME.'_'.'user_id'];
                              echo htmlentities($header_user_name, ENT_QUOTES, 'UTF-8');
                              if(!empty($_SESSION[$OJ_NAME.'_group_name'])) echo "[".htmlentities($_SESSION[$OJ_NAME.'_group_name'])."]";
                              if($coin>0) echo "💰️$coin";        
                        ?>
                        </span>
                        <i class="dropdown icon" aria-hidden="true"></i>
                    </button>
                    <div class="menu">
                                <a class="item" href="<?php echo $path_fix?>userinfo.php?user=<?php echo rawurlencode($_SESSION[$OJ_NAME.'_'.'user_id'])?>"><i class="user icon"></i>个人主页</a>
                                <a class="item" href="<?php echo $path_fix?>portal.php"><i class="tasks icon"></i><?php echo $MSG_TODO;?></a>
                                <?php if ($OJ_SaaS_ENABLE){ ?>
                                <?php if($_SERVER['HTTP_HOST']==$DOMAIN)
                                        echo  "<a class='item' href='http://".htmlentities($_SESSION[$OJ_NAME.'_'.'user_id']).".$DOMAIN'><i class='globe icon' ></i>MyOJ</a>";?>
                                <?php } ?>
                            <?php if(isset($_SESSION[$OJ_NAME.'_'.'administrator'])||isset($_SESSION[$OJ_NAME.'_'.'contest_creator'])||isset($_SESSION[$OJ_NAME.'_'.'user_adder'])||isset($_SESSION[$OJ_NAME.'_'.'password_setter'])||isset($_SESSION[$OJ_NAME.'_'.'problem_editor'])){ ?>
                            <a class="item" href="<?php echo $path_fix?>admin/"><i class="settings icon"></i><?php echo $MSG_ADMIN;?></a>
                            <?php }
if(isset($_SESSION[$OJ_NAME.'_'.'balloon'])){
  echo "<a class=item href='".$path_fix."balloon.php'><i class='golf ball icon'></i>$MSG_BALLOON</a>";
}
                              if((isset($OJ_EXAM_CONTEST_ID)&&$OJ_EXAM_CONTEST_ID>0)||
                                     (isset($OJ_ON_SITE_CONTEST_ID)&&$OJ_ON_SITE_CONTEST_ID>0)||
                                     (isset($OJ_MAIL)&&!$OJ_MAIL)){
                                      // mail can not use in contest or mail is turned off
                              }else{
                                    $mail=checkmail();
                                    if($mail) echo "<a class='item mail' href=".$path_fix."mail.php><i class='mail icon'></i>$MSG_MAIL$mail</a>";
                              }




                            ?>
        <?php
        if(isset($OJ_PRINTER) && $OJ_PRINTER)
        {
        ?>
          <a  class="item"  href="<?php echo $path_fix?>printer.php">
            <i class="print icon"></i> <?php echo $MSG_PRINTER?>
          </a>
        <?php
        }
        ?>
                            <div class="item theme-submenu user-theme-submenu" tabindex="0" aria-haspopup="true">
                                <div class="theme-submenu-toggle"><i class="adjust icon" aria-hidden="true"></i><span>主题</span><i class="chevron left icon theme-submenu-arrow" aria-hidden="true"></i></div>
                                <div class="left menu theme-submenu-menu" role="menu" aria-label="主题选择">
                                    <button type="button" class="theme-option" data-theme="light" aria-pressed="false" role="menuitem">
                                        <i class="sun icon" aria-hidden="true"></i><span>浅色</span>
                                    </button>
                                    <button type="button" class="theme-option" data-theme="dark" aria-pressed="false" role="menuitem">
                                        <i class="moon icon" aria-hidden="true"></i><span>深色</span>
                                    </button>
                                </div>
                            </div>
                            <a class="item" href="<?php echo $path_fix?>logout.php"><i class="power icon"></i><?php echo $MSG_LOGOUT;?></a>
                    </div>
                </div>
                <?php } else { ?>


                <div class="item">
                    <div class="ui simple dropdown item theme-menu-guest" tabindex="0">
                        <i class="adjust icon" aria-hidden="true"></i><span class="desktop-only">主题</span>
                        <div class="menu">
                            <div class="item theme-submenu" tabindex="0" aria-haspopup="true">
                                <div class="theme-submenu-toggle"><i class="adjust icon" aria-hidden="true"></i><span>主题</span><i class="chevron left icon theme-submenu-arrow" aria-hidden="true"></i></div>
                                <div class="left menu theme-submenu-menu" role="menu" aria-label="主题选择">
                                    <button type="button" class="theme-option" data-theme="light" aria-pressed="false" role="menuitem">
                                        <i class="sun icon" aria-hidden="true"></i><span>浅色</span>
                                    </button>
                                    <button type="button" class="theme-option" data-theme="dark" aria-pressed="false" role="menuitem">
                                        <i class="moon icon" aria-hidden="true"></i><span>深色</span>
                                    </button>
                                </div>
                            </div>
                        </div>
                    </div>
                    <a class="ui button" style="margin-right: 0.5em; " href="<?php echo $path_fix?>loginpage.php">
                       <?php echo $MSG_LOGIN?>
                    </a>
                    <?php if(isset($OJ_REGISTER)&&$OJ_REGISTER ){ ?>
                    <a class="ui primary button" href="<?php echo $path_fix?>registerpage.php">
                       <?php echo $MSG_REGISTER?>
                    </a>
                    <?php } ?>
                </div>
                <?php } ?>
            </div>
        </div>
    </div>
    <div style="margin-top: 49px; ">
        <div id="main" class="ui main container">
<?php } ?>
