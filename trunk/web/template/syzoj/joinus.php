<?php $show_title = "加入我们 - JISUOJ"; ?>
<?php include("template/$OJ_TEMPLATE/header.php"); ?>
<link rel="stylesheet" href="<?php echo $path_fix; ?>template/<?php echo $OJ_TEMPLATE; ?>/css/joinus.css?v=<?php echo filemtime(__DIR__ . '/css/joinus.css'); ?>">

<main class="join-us-page">
    <section class="join-us-intro">
        <span class="join-us-kicker">COMMUNITY</span>
        <h1>加入我们</h1>
        <p>加入吉林外国语大学程序设计学习社区，与同学们一起交流题目、分享思路、获取活动通知。</p>
        <div class="join-us-features" aria-label="社区内容">
            <span><i class="comments outline icon"></i> 题目交流</span>
            <span><i class="code icon"></i> 算法讨论</span>
            <span><i class="calendar alternate outline icon"></i> 活动通知</span>
        </div>
    </section>

    <section class="join-us-community" aria-labelledby="join-us-community-title">
        <div class="join-us-section-heading">
            <div>
                <span class="join-us-kicker">GET CONNECTED</span>
                <h2 id="join-us-community-title">选择交流社区</h2>
            </div>
            <p>点击下方入口查看二维码</p>
        </div>

        <div class="qr-carousel">
            <div class="group-qr-dropdowns">
                <div class="group-qr-dropdown">
                    <button class="group-qr-toggle" type="button" aria-expanded="false" aria-controls="qq-group-qr-panel">
                        <span><i class="qq icon"></i> 加入QQ群</span>
                        <i class="angle down icon"></i>
                    </button>
                    <div class="group-qr-panel" id="qq-group-qr-panel" hidden>
                        <img src="<?php echo $path_fix; ?>image/qq-group.png" alt="New Coders答疑灌水群二维码">
                        <div class="group-qr-panel-label">New Coders答疑灌水群</div>
                    </div>
                </div>

                <div class="group-qr-dropdown">
                    <button class="group-qr-toggle" type="button" aria-expanded="false" aria-controls="wechat-group-qr-panel">
                        <span><i class="wechat icon"></i> 加入微信群</span>
                        <i class="angle down icon"></i>
                    </button>
                    <div class="group-qr-panel" id="wechat-group-qr-panel" hidden>
                        <img src="<?php echo $path_fix; ?>image/wechat-group.png" alt="吉外程序设计社团（C++）群二维码">
                        <div class="group-qr-panel-label">吉外程序设计社团（C++）</div>
                    </div>
                </div>
            </div>
        </div>
    </section>
</main>

<script>
(function () {
    const dropdowns = Array.from(document.querySelectorAll('.group-qr-dropdown'));

    function setDropdownState(dropdown, open) {
        const toggle = dropdown.querySelector('.group-qr-toggle');
        const panel = dropdown.querySelector('.group-qr-panel');
        dropdown.classList.toggle('is-open', open);
        toggle.setAttribute('aria-expanded', open ? 'true' : 'false');
        panel.hidden = !open;
    }

    dropdowns.forEach(function (dropdown) {
        const toggle = dropdown.querySelector('.group-qr-toggle');
        toggle.addEventListener('click', function () {
            const shouldOpen = !dropdown.classList.contains('is-open');
            dropdowns.forEach(function (item) {
                setDropdownState(item, item === dropdown && shouldOpen);
            });
        });
    });

    document.addEventListener('click', function (event) {
        if (!event.target.closest('.group-qr-dropdown')) {
            dropdowns.forEach(function (dropdown) {
                setDropdownState(dropdown, false);
            });
        }
    });

    document.addEventListener('keydown', function (event) {
        if (event.key === 'Escape') {
            dropdowns.forEach(function (dropdown) {
                setDropdownState(dropdown, false);
            });
        }
    });
})();
</script>

<?php include("template/$OJ_TEMPLATE/footer.php"); ?>
