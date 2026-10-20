<?php
$show_title = "JISUOJ - 吉林外国语大学程序设计平台";

$escape_home = static function ($value) {
    return htmlentities((string)$value, ENT_QUOTES, 'UTF-8');
};

// 首页只展示平台动态，不把系统框架说明类文章（默认是 HelloWorld!）放进主内容。
$home_news_title = isset($OJ_INDEX_NEWS_TITLE) ? $OJ_INDEX_NEWS_TITLE : 'HelloWorld!';
$home_news_count_rows = mysql_query_cache(
    "SELECT COUNT(*) AS ids FROM news
     WHERE defunct!='Y' AND title!='faqs.$OJ_LANG' AND title!=?",
    $home_news_title
);
$home_news_count = is_array($home_news_count_rows) && !empty($home_news_count_rows) ? intval($home_news_count_rows[0]['ids']) : 0;
$home_news_pages = max(1, (int)ceil($home_news_count / 6));
$home_news_page = max(1, intval(isset($_GET['page']) ? $_GET['page'] : 1));
$home_news_page = min($home_news_page, $home_news_pages);
$home_news_offset = ($home_news_page - 1) * 6;
$home_news = mysql_query_cache(
    "SELECT news_id,title,time FROM news
     WHERE defunct!='Y' AND title!='faqs.$OJ_LANG' AND title!=?
     ORDER BY importance ASC,time DESC LIMIT {$home_news_offset},6",
    $home_news_title
);

$home_contests = mysql_query_cache(
    "SELECT contest_id,title,start_time,end_time FROM contest
     WHERE defunct='N' ORDER BY contest_id DESC LIMIT 5"
);

$home_problems = array();
if (isset($_SESSION[$OJ_NAME . '_user_id'])) {
    $home_user_id = $_SESSION[$OJ_NAME . '_user_id'];
    $home_problems = mysql_query_cache(
        "SELECT p.problem_id,p.title,s.max_in_date
         FROM (
             SELECT problem_id,MAX(in_date) max_in_date
             FROM solution
             WHERE user_id=? AND result>4 AND problem_id>0
             GROUP BY problem_id
         ) s INNER JOIN problem p ON s.problem_id=p.problem_id
         ORDER BY s.max_in_date DESC LIMIT 5",
        $home_user_id
    );
}
?>
<?php include("template/$OJ_TEMPLATE/header.php"); ?>
<link rel="stylesheet" href="<?php echo $path_fix; ?>template/<?php echo $OJ_TEMPLATE; ?>/css/home.css?v=<?php echo filemtime(__DIR__ . '/css/home.css'); ?>">

<div class="jisu-home">
    <section class="jisu-hero" aria-labelledby="jisu-home-title">
        <div class="jisu-hero-particles" aria-hidden="true">
            <span class="jisu-hero-particle"></span>
            <span class="jisu-hero-particle"></span>
            <span class="jisu-hero-particle"></span>
            <span class="jisu-hero-particle"></span>
            <span class="jisu-hero-particle"></span>
            <span class="jisu-hero-particle"></span>
            <span class="jisu-hero-particle"></span>
            <span class="jisu-hero-particle"></span>
            <span class="jisu-hero-particle"></span>
            <span class="jisu-hero-particle"></span>
            <span class="jisu-hero-particle"></span>
            <span class="jisu-hero-particle"></span>
            <span class="jisu-hero-particle"></span>
            <span class="jisu-hero-particle"></span>
            <span class="jisu-hero-particle"></span>
            <span class="jisu-hero-particle"></span>
        </div>
        <div class="jisu-hero-copy">
            <div class="jisu-eyebrow">吉林外国语大学 · PROGRAMMING PLATFORM</div>
            <h1 id="jisu-home-title">JisuOj</h1>
            <p class="jisu-subtitle">吉林外国语大学程序设计平台</p>
            <p class="jisu-description">从第一行代码开始，练习算法、参与竞赛，在真实的评测中持续进步。</p>
            <div class="jisu-hero-actions">
                <a class="jisu-primary-action" href="<?php echo $path_fix; ?>problemset.php">
                    开始练习 <i class="arrow right icon"></i>
                </a>
                <a class="jisu-secondary-action" href="<?php echo $path_fix; ?>contest.php">
                    浏览比赛
                </a>
            </div>
            <div class="jisu-hero-note">
                <span><i class="check circle icon"></i> 在线评测</span>
                <span><i class="layers icon"></i> 分层训练</span>
                <span><i class="trophy icon"></i> 竞赛实践</span>
            </div>
        </div>
        <div class="jisu-hero-visual" aria-hidden="true">
            <div class="jisu-emblem">
                <img class="jisu-emblem-logo" src="<?php echo $path_fix; ?>image/jisu-university-logo.png" alt="吉林外国语大学校徽">
            </div>
            <span class="jisu-hero-index">01</span>
        </div>
    </section>

    <section class="jisu-entry-grid" aria-label="平台入口">
        <a class="jisu-entry-card" href="<?php echo $path_fix; ?>problemset.php">
            <span class="jisu-entry-icon"><i class="list ul icon"></i></span>
            <span class="jisu-entry-content">
                <strong>题库练习</strong>
                <small>按知识点和难度训练</small>
            </span>
            <i class="angle right icon jisu-entry-arrow"></i>
        </a>
        <a class="jisu-entry-card" href="<?php echo $path_fix; ?>contest.php">
            <span class="jisu-entry-icon"><i class="trophy icon"></i></span>
            <span class="jisu-entry-content">
                <strong>竞赛与作业</strong>
                <small>参加比赛，检验学习成果</small>
            </span>
            <i class="angle right icon jisu-entry-arrow"></i>
        </a>
        <a class="jisu-entry-card" href="<?php echo $path_fix; ?>status.php">
            <span class="jisu-entry-icon"><i class="tasks icon"></i></span>
            <span class="jisu-entry-content">
                <strong>提交状态</strong>
                <small>查看评测结果和运行记录</small>
            </span>
            <i class="angle right icon jisu-entry-arrow"></i>
        </a>
    </section>

    <section id="platform-news" class="jisu-home-section">
        <div class="jisu-section-heading">
            <div>
                <span class="jisu-section-kicker">LATEST</span>
                <h2>平台动态</h2>
            </div>
            <?php if ($home_news_pages > 1) { ?>
                <a href="<?php echo $path_fix; ?>index.php?page=<?php echo $home_news_page == $home_news_pages ? 1 : $home_news_page + 1; ?>#platform-news" class="jisu-section-link">
                    <?php echo $home_news_page == $home_news_pages ? '回到最新' : '更多动态'; ?> <i class="arrow right icon"></i>
                </a>
            <?php } ?>
        </div>
        <div class="jisu-news-panel">
            <?php if (is_array($home_news) && !empty($home_news)) { ?>
                <?php foreach ($home_news as $news) { ?>
                    <a class="jisu-news-row" href="<?php echo $path_fix; ?>viewnews.php?id=<?php echo intval($news['news_id']); ?>">
                        <span class="jisu-news-title"><?php echo $escape_home($news['title']); ?></span>
                        <time datetime="<?php echo $escape_home($news['time']); ?>"><?php echo $escape_home(substr($news['time'], 0, 10)); ?></time>
                    </a>
                <?php } ?>
            <?php } else { ?>
                <div class="jisu-empty-state">暂时没有新的平台动态。</div>
            <?php } ?>
        </div>
        <?php if ($home_news_pages > 1) { ?>
            <div class="jisu-news-pagination" aria-label="平台动态分页">
                <a href="<?php echo $path_fix; ?>index.php?page=<?php echo $home_news_page == 1 ? $home_news_pages : $home_news_page - 1; ?>#platform-news">上一页</a>
                <span><?php echo $home_news_page; ?> / <?php echo $home_news_pages; ?></span>
                <a href="<?php echo $path_fix; ?>index.php?page=<?php echo $home_news_page == $home_news_pages ? 1 : $home_news_page + 1; ?>#platform-news">下一页</a>
            </div>
        <?php } ?>
    </section>

    <section class="jisu-content-grid">
        <div class="jisu-home-section">
            <div class="jisu-section-heading">
                <div>
                    <span class="jisu-section-kicker">CONTESTS</span>
                    <h2>近期比赛</h2>
                </div>
                <a href="<?php echo $path_fix; ?>contest.php" class="jisu-section-link">全部比赛 <i class="arrow right icon"></i></a>
            </div>
            <div class="jisu-list-panel">
                <?php if (is_array($home_contests) && !empty($home_contests)) { ?>
                    <?php foreach ($home_contests as $contest) { ?>
                        <a class="jisu-list-row" href="<?php echo $path_fix; ?>contest.php?cid=<?php echo intval($contest['contest_id']); ?>">
                            <span class="jisu-list-main">
                                <strong><?php echo $escape_home($contest['title']); ?></strong>
                                <small><?php echo $escape_home(substr($contest['start_time'], 0, 16)); ?></small>
                            </span>
                            <i class="angle right icon"></i>
                        </a>
                    <?php } ?>
                <?php } else { ?>
                    <div class="jisu-empty-state">近期暂无比赛安排。</div>
                <?php } ?>
            </div>
        </div>

        <div class="jisu-home-section">
            <div class="jisu-section-heading">
                <div>
                    <span class="jisu-section-kicker">PRACTICE</span>
                    <h2><?php echo isset($_SESSION[$OJ_NAME . '_user_id']) ? '继续练习' : '开始练习'; ?></h2>
                </div>
                <a href="<?php echo $path_fix; ?>problemset.php" class="jisu-section-link">进入题库 <i class="arrow right icon"></i></a>
            </div>
            <div class="jisu-list-panel">
                <?php if (is_array($home_problems) && !empty($home_problems)) { ?>
                    <?php foreach ($home_problems as $problem) { ?>
                        <a class="jisu-list-row" href="<?php echo $path_fix; ?>problem.php?id=<?php echo intval($problem['problem_id']); ?>">
                            <span class="jisu-list-main">
                                <strong><?php echo $escape_home($problem['title']); ?></strong>
                                <small>最近提交 <?php echo $escape_home(substr($problem['max_in_date'], 0, 16)); ?></small>
                            </span>
                            <i class="angle right icon"></i>
                        </a>
                    <?php } ?>
                <?php } else { ?>
                    <div class="jisu-practice-empty">
                        <p>选择一道题目，开始你的下一次思考。</p>
                        <a class="jisu-text-action" href="<?php echo $path_fix; ?>problemset.php">浏览题库 <i class="arrow right icon"></i></a>
                    </div>
                <?php } ?>
            </div>
        </div>
    </section>

    <section class="jisu-bottom-banner">
        <div>
            <span class="jisu-section-kicker">LEARN BY DOING</span>
            <h2>让每一次提交，都成为进步的记录。</h2>
        </div>
        <a class="jisu-primary-action" href="<?php echo $path_fix; ?>problemset.php">进入题库 <i class="arrow right icon"></i></a>
    </section>
</div>

<?php include("template/$OJ_TEMPLATE/footer.php"); ?>
