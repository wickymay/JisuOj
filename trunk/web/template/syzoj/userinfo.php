<?php $show_title="$MSG_USERINFO - $OJ_NAME"; ?>
<?php include("template/$OJ_TEMPLATE/header.php");?>
<style>
#avatar_container:before {
    display: none;
}
.profile-social-summary {
    display: flex;
    justify-content: space-around;
    gap: 12px;
    margin-bottom: 12px;
    text-align: center;
}
.profile-social-summary a {
    color: inherit;
}
.profile-social-summary strong {
    display: block;
    font-size: 1.15em;
}
.profile-message-content {
    white-space: pre-wrap;
    overflow-wrap: anywhere;
    margin-top: 6px;
}
.profile-message-actions {
    float: right;
}
.profile-message-actions form {
    display: inline;
}
</style>
<?php 
    $calsed = '锦鲤';
    $calledid = -1;
    $acneed = [10,20,30,50,80,100,200,300,500,800,1000];
    $accall = ["萌新","小小牛","小牛","小犇","中牛","中犇","大牛","大犇","神牛","神犇"];
    for ($i = count($accall);$i > 0; $i--) {
        if ($AC < $acneed[$i]) {$calsed = $accall[$i - 1];$calledid=$i-1;}
    }
/*
    for ($i=0;$i<=11;++$i){
    	$ped[$i]=0;
    }
    $sql="select * FROM `solution` WHERE `user_id`=?";
    $result = mysql_query_cache($sql, $user);
    foreach ($result as $row) {
    	++$ped[$row['result']];
    }
*/
?>

<div class="padding">
<?php if (is_array($social_notice) && isset($social_notice['message'])) { ?>
<div class="ui <?php echo $social_notice['type'] === 'success' ? 'positive' : 'negative'; ?> message">
    <?php echo htmlentities($social_notice['message'], ENT_QUOTES, 'UTF-8'); ?>
</div>
<?php } ?>
<div class="ui grid">
    <div class="row">
        <div class="five wide column">
            <div class="ui card" style="width: 100%; " id="user_card">
                <div class="image" id="avatar_container">
					<div class="avatar-profile-row">
			<?php $default = ""; $grav_url = "https://www.gravatar.com/avatar/" . md5( strtolower( trim( $email ) ) ) . "?d=" . urlencode( $default ) . "&s=500"; ?>
		<?php
		    if ($avatar_url != "") {
				$grav_url = $avatar_url;
		    } else {
		    // 如果email填写的是qq邮箱，取QQ头像显示
                    $qq=stripos($email,"@qq.com");
                    if($qq>0){
                         $qq=urlencode(substr($email,0,$qq));
                         $grav_url="https://q1.qlogo.cn/g?b=qq&nk=$qq&s=5";
                    };
		    }
                ?>

						<img src="<?php echo htmlentities($grav_url, ENT_QUOTES, "UTF-8"); ?>" alt="<?php echo htmlentities($nick, ENT_QUOTES, "UTF-8"); ?>的头像">
						<?php if (isset($_SESSION[$OJ_NAME . '_user_id']) && $_SESSION[$OJ_NAME . '_user_id'] === $user) { ?>
						<div class="profile-edit-actions">
							<a class="ui mini basic button profile-edit-button avatar-edit-button" href="modifypage.php">修改头像</a>
							<a class="ui mini basic button profile-edit-button" href="modifypage.php">修改注册信息</a>
						</div>
						<?php } ?>
					</div>
                </div>
                <div class="content">
                    <div class="header"><?php echo $nick?></div>
                    <div class="meta">
                        <a class="group"><?php echo $school?></a>
			<?php if (trim((string) $group_name) !== '') { ?><a class="group"><?php echo $group_name?></a><?php } ?>
                    </div>
                </div>
                 <table class="table table-hover" style="width:100%">
                <tbody>
                    <tr>
                        <th width=20%>等级</th>
                        <td width=20%><?php echo $calsed;?></td>
                        <td width=55%>距离 <?php echo $accall[$calledid+1];?> 还需AC <?php echo $acneed[$calledid+1]-$AC;?>题</td>
                    </tr>
                </tbody>
              </table>
                <div class="extra content">
                    <a><i class="check icon"></i>通过 <?php echo $AC ?> 题</a>
                    <a style="float: right; "><i class="star icon <?php if($starred) echo "active"?>" title='用同名账户给hustoj项目加星，可以点亮此星' ></i>排名 <?php echo $Rank ?></a>
                    <?php if (isset($coin_balance)) { ?>
                    <div style="margin-top:8px;font-size:12px">
                        <span><i class="circle icon"></i><?php echo $MSG_COIN_EARNED ?>: <?php echo $coin_earned ?></span>
                        <span style="margin-left:12px"><i class="gift icon"></i><?php echo $MSG_COIN_BONUS ?>: <?php echo $coin_bonus ?></span>
                        <span style="margin-left:12px"><i class="minus circle icon"></i><?php echo $MSG_COIN_SPENT ?>: <?php echo $coin_spent ?></span>
                        <span style="margin-left:12px;font-weight:bold"><i class="money icon"></i><?php echo $MSG_COIN_BALANCE ?>: <?php echo $coin_balance ?></span>
                    </div>
                    <?php } ?>
                    <?php if ($social_tables_ready) { ?>
                    <div class="ui divider"></div>
                    <div class="profile-social-summary">
                        <a href="#profile-messages"><strong><?php echo $message_count; ?></strong>留言</a>
                    </div>
                    <?php if ($current_user !== '' && $current_user !== $user) { ?>
                    <form class="ui form" method="post" action="user_social.php">
                        <input type="hidden" name="action" value="<?php echo $is_following ? 'unfollow' : 'follow'; ?>">
                        <input type="hidden" name="target_user" value="<?php echo htmlentities($user, ENT_QUOTES, 'UTF-8'); ?>">
                        <?php require('./include/set_post_key.php'); ?>
                        <button type="submit" class="ui fluid <?php echo $is_following ? 'basic' : 'primary'; ?> button">
                            <i class="<?php echo $is_following ? 'user times' : 'user plus'; ?> icon"></i>
                            <?php echo $is_following ? '取消关注' : '关注'; ?>
                            <?php if ($is_followed_by) echo ' · 对方已关注你'; ?>
                        </button>
                    </form>
                    <?php } ?>
                    <?php } else if (isset($_SESSION[$OJ_NAME . '_administrator'])) { ?>
                    <div class="ui tiny warning message">请先在后台执行“更新数据库”以启用关注和留言。</div>
                    <?php } ?>
                     <?php if ($email != "") { ?>
                            <div style="margin-top:10px;margin-bottom:10px">
                                <a href="mailto:<?php echo "Hello" ?>?body=CSPOJ">
                                    <i class="icon large envelope"></i>
                                    <span style="display:inline-block; vertical-align:middle"><?php echo $email?></span>
                                </a>
                            </div>
                        <?php } ?>
                    
                </div>
            </div>

            <?php if ($social_tables_ready) { ?>
            <div class="profile-message-panel" id="profile-messages">
                <h4 class="ui top attached block header">留言板 <span class="ui small circular label"><?php echo $message_count; ?></span></h4>
                <div class="ui bottom attached segment">
                    <?php if ($current_user !== '' && $current_user !== $user) { ?>
                    <form class="ui reply form" method="post" action="user_social.php">
                        <input type="hidden" name="action" value="message_add">
                        <input type="hidden" name="target_user" value="<?php echo htmlentities($user, ENT_QUOTES, 'UTF-8'); ?>">
                        <?php require('./include/set_post_key.php'); ?>
                        <div class="field">
                            <textarea name="content" maxlength="500" rows="3" required placeholder="给 <?php echo htmlentities($nick, ENT_QUOTES, 'UTF-8'); ?> 留言（最多 500 字）"></textarea>
                        </div>
                        <button type="submit" class="ui primary button"><i class="edit icon"></i>发布留言</button>
                    </form>
                    <div class="ui divider"></div>
                    <?php } else if ($current_user === '') { ?>
                    <div class="ui info message"><a href="loginpage.php">登录</a>后可以给该用户留言。</div>
                    <?php } ?>

                    <?php if (empty($profile_messages)) { ?>
                        <div class="ui grey text">还没有留言。</div>
                    <?php } else { ?>
                    <div class="ui comments" style="max-width:none">
                        <?php foreach ($profile_messages as $profile_message) {
                            $message_nick = hustoj_user_display_name_html($profile_message['author_id'], $profile_message['nick']);
                            $can_delete_message = $current_user === $profile_message['author_id']
                                || $current_user === $user
                                || isset($_SESSION[$OJ_NAME . '_administrator']);
                        ?>
                        <div class="comment">
                            <div class="content">
                                <a class="author" href="userinfo.php?user=<?php echo rawurlencode($profile_message['author_id']); ?>"><?php echo $message_nick; ?></a>
                                <div class="metadata"><span class="date"><?php echo htmlentities($profile_message['in_date'], ENT_QUOTES, 'UTF-8'); ?></span></div>
                                <?php if ($can_delete_message) { ?>
                                <span class="profile-message-actions">
                                    <form method="post" action="user_social.php" onsubmit="return confirm('确定删除这条留言吗？');">
                                        <input type="hidden" name="action" value="message_delete">
                                        <input type="hidden" name="target_user" value="<?php echo htmlentities($user, ENT_QUOTES, 'UTF-8'); ?>">
                                        <input type="hidden" name="message_id" value="<?php echo intval($profile_message['message_id']); ?>">
                                        <?php require('./include/set_post_key.php'); ?>
                                        <button type="submit" class="ui mini basic red button">删除</button>
                                    </form>
                                </span>
                                <?php } ?>
                                <div class="text profile-message-content"><?php echo htmlentities($profile_message['content'], ENT_QUOTES, 'UTF-8'); ?></div>
                            </div>
                        </div>
                        <?php } ?>
                    </div>
                    <?php if ($message_count > count($profile_messages)) { ?><div class="ui tiny grey text">仅显示最近 50 条留言</div><?php } ?>
                    <?php } ?>
                </div>
            </div>
            <?php } ?>

        </div>
         <div class="eleven wide column">
                <div class="ui grid" style="padding-left: 20px;">
                    <div class="row">
                        <div class="column">
			<h4 class="ui top attached block header"><?php echo $MSG_SUBMIT.$MSG_STATISTICS ?></h4>
                            <div class="ui bottom attached segment">
                                <div id="sub_date_chart" style="width:100%;height:210px"></div>
				<a href="/status.php?user_id=<?php echo $user?>"><i class="search icon"></i><?php echo $MSG_SUBMIT.$MSG_LIST ?></a>
                            </div>
                        </div>
                    </div>
                    <div class="row">
                        <div class="column">
			<h4 class="ui top attached block header"><?php echo $MSG_CONTEST?></h4>
                            <div class="ui bottom attached segment">
				<table class='striped table ui'>
				<tr>
					<th><?php echo $MSG_NUM ?></th>
					<th><?php echo $MSG_TITLE ?></th>
					<th><?php echo $MSG_START_TIME?></th>
					<th><?php echo $MSG_SUBMIT ?></th>
					<th><?php echo $MSG_AC ?></th>
				</tr>
			<?php
  $sql="select c.contest_id,c.title,c.start_time,rt.tried,rt.ac  from contest c
                            inner join (select contest_id,count(distinct(problem_id)) as tried, count(distinct(if(result=4,problem_id,null))) as ac from solution
                                where user_id=? and contest_id>0
 group by contest_id order by contest_id) rt on c.contest_id=rt.contest_id and  not (c.title like '%$OJ_NOIP_KEYWORD%' or  ((c.contest_type & 20) >0 and end_time>now() )) ;";

				$contests=pdo_query($sql,$user);
					$total=$total_ac=$cnt=0;
					foreach($contests as $row){
					        echo "<tr><td>".++$cnt."</td>";
						$id=0;
					        for($i=0;$i<count($row)/2;$i++){
							if($id==0){
								$id=$row[$i];
								continue;
							}
					                echo "<td>";
					                if($i==1) echo "<a href='contestrank.php?cid=$id#".htmlentities($user)."' target=_blank>".$row[$i]."</a>";
							else echo "\t".$row[$i];
							if($i==3) $total+=$row[$i];
                                                        if($i==4) $total_ac+=$row[$i];
					                echo "</td>";
					        }
					        echo "</tr>";
					}
				?>
				 <tr>
                                        <th></th>
                                        <th></th>
                                        <th></th>
                                        <th><?php echo $total ?></th>
                                        <th><?php echo $total_ac ?></th>
                                </tr>
                                    </table>
                            </div>
                        </div>
                    </div>
                    <div class="row">
                        <div class="column">
			<h4 class="ui top attached block header"><?php echo $MSG_STATISTICS?></h4>
                            <div class="ui bottom attached segment">
                                <div class="ui grid">
                                    <div class="row">
                                        <div id="pie_chart_legend" class="six wide column"></div>
                                        <div class="ten wide column">
                                            <canvas id="pie_chart"></canvas>
                                            
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                  
                    <div class="row">
                        <div class="column">
                            <h4 class="ui top attached block header">
                                通过的题目

                            </h4>
                            <div class="ui bottom attached segment">
                                <script language='javascript'>
                                    function p(id, c) {
                                        if(c>0)document.write("<a title=\"U've Passed!\" href=problem.php?id=" + id + " class=\"ui green basic label\" id=\"show-problem-id\">" + id + " </a>");
                                        else document.write("<a title=\"U've Not Passed Yet!\" href=problem.php?id=" + id + " class=\"ui red basic label\" id=\"show-problem-id\">" + id + " </a>");
                                    }
                                    function ptot(len) {
                                        document.write("<div style='text-align:right;margin-bottom:10px'><div class='ui green small horizontal statistic'><div class='value'>" + len + "</div><div class='label'>AC</div></div></div>")
                                    }
                                    <?php
				    $ac=array();
                                    $sql = "select `problem_id`,count(1) from solution where `user_id`=? and result=4 and problem_id != 0 $not_in_noip group by `problem_id` ORDER BY `problem_id` ASC";
                                    if ($ret = mysql_query_cache($sql, $user)) {
                                        $len = count($ret);
                                        echo "ptot($len);";
                                        foreach ($ret as $row){
                                            if (isset($acc_arr[$row['problem_id']]))
                                                echo "p($row[0],$row[1]);";
                                            else
                                                echo "p($row[0],0);";
					    array_push($ac,$row[0]);
                                        }
                                    }
                                    ?>
                                </script>
                            </div>

                        </div>
                    </div>
		    <div class="row">
                        <div class="column">
                            <h4 class="ui top attached block header">未通过的题目</h4>
                            <div class="ui bottom attached segment">
                                <script language='javascript'>
                                    function p(id, c) {
                                        document.write("<a href=problem.php?id=" + id + " class=\"ui basic label\" id=\"show-problem-id\">" + id +
                                            " </a>");
                                    }
                                    <?php
                                    $sql = "select `sol`.`problem_id`, count(1) from solution sol where `sol`.`user_id`=? and `sol`.`result`!=4 and sol.problem_id != 0  $not_in_noip group by `sol`.`problem_id` ORDER BY `sol`.`problem_id` ASC";
                                    if ($result = mysql_query_cache($sql, $user)) {
                                        foreach ($result as $row)
                                            if(!in_array($row[0],$ac))echo "p($row[0],$row[1]);";
                                    }
                                    ?>
                                </script>
                            </div>
                        </div>
                    </div>
    
		    <div class="row">
                        <div class="column">
                            <h4 class="ui top attached block header">系统题单</h4>
                            <div class="ui bottom attached segment">
<?php 
echo "<table class='ui striped table '>";
foreach($plista as $plist){
	echo "<tr>";
	$name=$plist["name"];
	echo "<td>$name</td>";
	$list=explode(",",$plist['list']);
	foreach($list as $pid){
		if (in_array($pid,$ac)) $color="green"; else $color="red";
	 	echo "<td class='ui $color basic label'><a href=problem.php?id=$pid>".$bible[$pid]."</a></td>";
	}
	echo "</tr>";
}
echo "</table>";
?>
                            </div>
                        </div>
                    </div>
            </div>
        </div>
    </div>
</div>
</div>

<script>
    $(function() {
        $('#user_card .image').dimmer({
            on: 'hover'
        });
        var pie = new Chart(document.getElementById('pie_chart').getContext('2d'), {
            aspectRatio: 1,
            type: 'pie',
            data: {
                datasets: [{
                    data: [
                        <?php
                        foreach ($view_userstat as $row) {
                            echo $row[1] . ",\n";
                        }
                        ?>
                    ],
                    backgroundColor: [
                        "#32CD32",
                        "#FA8072",
                        "#DC143C",
                        "#FF9912",
                        "#8A2BE2",
                        "#4169E1",
                        "#DB7093",
                        "#082E54",
                        "#FFFF00",
                    ]
                }],
                labels: [
                    <?php
                    foreach ($view_userstat as $row) {
                        echo "\"" . $jresult[$row[0]] . "\",\n";
                    }
                    ?>
                ]
            },
            options: {
                responsive: true,
                legend: {
                    display: false
                },
                legendCallback: function(chart) {
                    var text = [];
                    text.push(
                        '<ul style="list-style: none; padding-left: 20px; margin-top: 0; " class="' +
                        chart.id + '-legend">');

                    var data = chart.data;
                    var datasets = data.datasets;
                    var labels = data.labels;

                    if (datasets.length) {
                        for (var i = 0; i < datasets[0].data.length; ++i) {
                            text.push(
                                '<li style="font-size: 15px; color: #666; margin:10px 20px"><span style="width: 12px; height: 12px; display: inline-block; border-radius: 50%; margin-right: 5px; background-color: ' +
                                datasets[0].backgroundColor[i] + '; "></span>');
                            if (labels[i]) {
                                text.push(labels[i]);
                                text.push(' : ' + datasets[0].data[i]);
                            }
                            text.push('</li>');
                        }
                    }

                    text.push('</ul>');
                    return text.join('');
                }
            },
        });
        document.getElementById('pie_chart_legend').innerHTML = pie.generateLegend();
    });
</script>

<?php 
$sub_data = [];
$max_count = 0;
$sql = "select DATE(in_date),count(*) FROM solution WHERE user_id=? AND  in_date >= DATE_SUB(CURDATE(),INTERVAL 1 YEAR) AND result < 13 $not_in_noip GROUP BY DATE(in_date);";
$ret = mysql_query_cache($sql, $user);
foreach ($ret as $row) {
    array_push($sub_data, [$row[0], (int)$row[1]]);
    $max_count = max($max_count, (int)$row[1]);
}
// $max_count = ceil($max_count / 100) * 100;
date_default_timezone_set('Asia/Shanghai');
$today = date('Y-m-d', time());
$beg_time = date('Y-m-d', strtotime("-6 month"));
// echo json_encode($sub_data, false);
?>
<script  src="<?php echo $OJ_CDN_URL.$path_fix."template/$OJ_TEMPLATE"?>/js/echarts.min.js"></script>
<!--<script src="https://cdn.staticfile.org/echarts/5.1.2/echarts.min.js"></script>-->

<script type="text/javascript">
    var chartDom = document.getElementById('sub_date_chart');
    var myChart = echarts.init(chartDom);
    var option;
    var today = new Date();

    function getSubmissionChartPalette() {
        var isDark = document.documentElement.getAttribute('data-theme') === 'dark';
        return isDark ? {
            text: '#b8c8dc',
            emptyCell: '#17243a',
            border: '#314866',
            range: ['#245c4a', '#34d399'],
            tooltipBackground: '#0f1b31',
            tooltipBorder: '#3a5275',
            tooltipText: '#e6edf7'
        } : {
            text: '#475569',
            emptyCell: '#f1f5f9',
            border: '#d8e1ec',
            range: ['#9edbad', '#16875f'],
            tooltipBackground: '#ffffff',
            tooltipBorder: '#cbd5e1',
            tooltipText: '#243447'
        };
    }

    var chartPalette = getSubmissionChartPalette();
    console.log(today.getFullYear() + '-' + today.getMonth() + '-' + today.getDay());
    option = {
        textStyle: {
            color: chartPalette.text
        },
        title: {
            top: 30,
            left: 'center',
            textStyle: {
                color: chartPalette.text
            },
        },
        tooltip: {
            backgroundColor: chartPalette.tooltipBackground,
            borderColor: chartPalette.tooltipBorder,
            textStyle: {
                color: chartPalette.tooltipText
            },
            formatter: function(params) {
                return params.value[0] + '<br>提交数：' + params.value[1];
            }
        },
        visualMap: {
            min: 0,
            max: <?php echo max(1, $max_count) ?>,
            show: false,
            type: 'piecewise',
            orient: 'horizontal',
            left: 'center',
            top: 10,
            inRange: {
                color: chartPalette.range
            }
        },
        calendar: {
            top: 30,
            left: 40,
            right: 30,
            cellSize: [20, 20],
            range: ['<?php echo $beg_time ?>', '<?php echo $today ?>'],
            itemStyle: {
                color: chartPalette.emptyCell,
                borderColor: chartPalette.border,
                borderWidth: 1
            },
            lineStyle: {
                color: '#D10E00',
                width: 1,
                opacity: 1
            },
            yearLabel: {
                show: false
            },
            dayLabel: {
                firstDay: 1,
                nameMap: 'cn',
                margin: '8px',
                color: chartPalette.text
            },
            monthLabel: {
                nameMap: 'cn',
                margin: 15,
                fontSize: 14,
                color: chartPalette.text
            },
            splitLine: {
                show: false
            },
            
        },
        series: {
            name: '提交次数',
            type: 'heatmap',
            coordinateSystem: 'calendar',
            data: <?php echo json_encode($sub_data, false); ?>,
        }
    };

    option && myChart.setOption(option);

    function applySubmissionChartTheme() {
        var palette = getSubmissionChartPalette();
        myChart.setOption({
            textStyle: { color: palette.text },
            title: { textStyle: { color: palette.text } },
            tooltip: {
                backgroundColor: palette.tooltipBackground,
                borderColor: palette.tooltipBorder,
                textStyle: { color: palette.tooltipText }
            },
            visualMap: { inRange: { color: palette.range } },
            calendar: {
                itemStyle: {
                    color: palette.emptyCell,
                    borderColor: palette.border,
                    borderWidth: 1
                },
                dayLabel: { color: palette.text },
                monthLabel: { color: palette.text }
            }
        });
    }

    new MutationObserver(function(mutations) {
        for (var i = 0; i < mutations.length; i++) {
            if (mutations[i].attributeName === 'data-theme') {
                applySubmissionChartTheme();
                break;
            }
        }
    }).observe(document.documentElement, {
        attributes: true,
        attributeFilter: ['data-theme']
    });

    window.addEventListener('resize', function() {
        myChart.resize();
    });
</script>

<?php include("template/$OJ_TEMPLATE/footer.php");?>
