<?php
require_once("../include/db_info.inc.php");
require_once("admin-header.php");
require_once("../include/my_func.inc.php");
require_once("admin_form_helpers.php");

if (!(isset($_SESSION[$OJ_NAME.'_'.'administrator']) || isset($_SESSION[$OJ_NAME.'_'.'problem_editor']))) {
  exit(1);
}
?>
  <html>
<head>
  <meta http-equiv="Content-Type" content="text/html; charset=utf-8">
  <title>Edit Problem</title>
</head>
<hr>
  
  <?php
echo "<center><h3>"."Edit-".$MSG_PROBLEM."</h3></center>";
include_once("kindeditor.php") ;
?>
<script src="admin-form.js?v=2"></script>


<body class="oj-admin-form-page" leftmargin="30" >
  <div id="main" class="container">
    <?php
    if (isset($_GET['id'])) {
      ;//require_once("../include/check_get_key.php");
        $pid=intval($_GET['id']);
        if(! (isset($_SESSION[$OJ_NAME.'_'.'administrator']) || isset($_SESSION[$OJ_NAME.'_'."p".$pid])) ){
                echo "No Privilege.";
                exit(0);
        }
    ?>

    <form class="oj-admin-form" method=POST action=problem_edit.php>
      <?php
      $sql = "SELECT * FROM `problem` WHERE `problem_id`=?";
      $result = pdo_query($sql,intval($_GET['id']));
      $row = $result[0];
      ?>

      <input type=hidden name=problem_id value='<?php echo $row['problem_id']?>'>
  <div class="oj-admin-preview-dropdown">
    <button type="button" id="preview-toggle" class="btn btn-default oj-admin-preview-toggle" aria-expanded="false" aria-controls="preview">题目预览</button>
  </div>
        &nbsp;&nbsp;
  <div class="ui toggle checkbox">
        <input type="checkbox" id="full-toggle" checked>
        <label for="full-toggle">完整结构</label>
  </div>

      <p align=left>
        <center>
          <h3>
          <?php echo $row['problem_id']?>: <input class="input input-xxlarge" style='width:90%' type=text name=title value='<?php echo htmlentities($row['title'],ENT_QUOTES,"UTF-8")?>'>
          </h3>
        </center>
      </p>
        <p align=left>
          <?php echo $MSG_Time_Limit; ?>
          <input class="input input-mini" type=number min="0.001" max="300" step="0.001" name=time_limit size=20 value="<?php echo $row['time_limit']?>"> sec
          <?php echo $MSG_Memory_Limit; ?>
          <input class="input input-mini" type=number min="1" max="1024" step="1" name=memory_limit size=20 value="<?php echo $row['memory_limit']?>"> MiB
          <?php echo $MSG_COIN; ?>
          <input class="input input-mini" type=number min="0" max="9999" step="1" name=coin size=20 value="<?php echo intval($row['coin'] ?? 1)?>">
        </p>
      <p align=left>
        <?php echo "<h4>".$MSG_Description."</h4>"?>
        <textarea class="kindeditor" rows=33 name=description cols=80><?php echo htmlentities(admin_strip_md_wrapper($row['description']),ENT_QUOTES,"UTF-8")?></textarea><br>
      </p>
<span class="full" >
      <p align=left>
        <?php echo "<h4>".$MSG_Input."</h4>"?>
        <textarea class="kindeditor" rows=13 name=input cols=80><?php echo htmlentities(admin_strip_md_wrapper($row['input']),ENT_QUOTES,"UTF-8")?></textarea><br>
      </p>

      <p align=left>
        <?php echo "<h4>".$MSG_Output."</h4>"?>
        <textarea  class="kindeditor" rows=13 name=output cols=80><?php echo htmlentities(admin_strip_md_wrapper($row['output']),ENT_QUOTES,"UTF-8")?></textarea><br>
      </p>

      <p align=left>
        <?php echo "<h4>".$MSG_Sample_Input."</h4>"?>
        <textarea  class="input input-large" style="width:100%;" rows=13 name=sample_input><?php echo htmlentities($row['sample_input'],ENT_QUOTES,"UTF-8")?></textarea><br><br>
      </p>

      <p align=left>
        <?php echo "<h4>".$MSG_Sample_Output."</h4>"?>
        <textarea  class="input input-large" style="width:100%;" rows=13 name=sample_output><?php echo htmlentities($row['sample_output'],ENT_QUOTES,"UTF-8")?></textarea><br><br>
      </p>

      <p align=left>
        <?php echo "<h4>".$MSG_HINT."</h4>"?>
        <textarea class="kindeditor" rows=13 name=hint cols=80><?php echo htmlentities(admin_strip_md_wrapper($row['hint']),ENT_QUOTES,"UTF-8")?></textarea><br>
      </p>
</span>
      <p>
        <?php echo "<h4>".$MSG_SPJ."</h4>"?>
        <?php echo "(".$MSG_HELP_SPJ.")"?><br>
        <input type=radio name=spj value='0' <?php echo $row['spj']=="0"?"checked":""?> title='Normal Judger'><?php echo $MSG_NJ?><br>
        <input type=radio name=spj value='1' <?php echo $row['spj']=="1"?"checked":""?> title='Special Judger'><?php echo $MSG_SPJ?><br>
        <input type=radio name=spj value='2' <?php echo $row['spj']=="2"?"checked":""?> title='Raw Text Judger' ><?php echo $MSG_RTJ?><br>
		<input type=radio name=spj value='3' <?php echo $row['spj']=="3"?"checked":""?> title='Interactive Judger' ><?php echo $MSG_INTERACT?><br>
      </p>

      <p align=left>
        <?php echo "<h4>".$MSG_SOURCE."</h4>"?>
        <textarea name=source style="width:100%;" rows=1><?php echo htmlentities($row['source'],ENT_QUOTES,"UTF-8")?></textarea><br>

        <?php echo "<h4>".$MSG_REMOTE_OJ."</h4>"?>
        <input name=remote_oj value='<?php echo htmlentities((string)$row['remote_oj'],ENT_QUOTES,"UTF-8")?>' placeholder='<?php echo $MSG_HELP_LOCAL_EMPTY ?>' >
        <input name=remote_id value='<?php echo htmlentities((string)$row['remote_id'],ENT_QUOTES,"UTF-8")?>' placeholder='<?php echo $MSG_HELP_LOCAL_EMPTY ?>' ><br>
      </p>

      <div align=center>
        <?php require_once("../include/set_post_key.php");?>
        <input class="btn btn-success" type=submit value='<?php echo $MSG_SAVE?>' name=submit>
      </div>
    </form>

  </div>

<script src="<?php echo $OJ_CDN_URL."/template/bs3/"?>marked.min.js"></script>
<script>
function untransform() {
    console.log("预览关闭");
    // 移除预览的 iframe
    $("#preview").remove();

  
    // 移除同步事件
    $("input").off('keyup', sync);
    $("textarea").off('keyup', sync);
}
  function transform(){
        if($("#preview").length) return;
        let height=Math.max(420, Math.min(window.innerHeight-120, 760));
	let submitURL="../problem.php?id=<?php echo $pid ?>";
                 $(".oj-admin-preview-dropdown").append("<div id='preview' class='oj-admin-preview-panel' style='visibility:hidden'><iframe id='previewFrame' src='"+submitURL+"&spa' width='100%' height='"+height+"px' ></iframe></div>");
                 $("#previewFrame").on("load", hide);
         $("#submit").remove();
	$("input").keyup(sync);
	$("textarea").keyup(sync);
  }
  function hide(){
	let preview=$("#previewFrame").contents();
	preview.find(".ui.buttons").hide();
	sync();
	window.requestAnimationFrame(function(){
		$("#preview").css("visibility", "visible");
	});
//	preview.find("h1:first").parent().parent().hide();
  }
  function sync(){
	console.log("sync...");
	let preview=$("#previewFrame").contents();
	let title=$("input[name=title]").val();
	preview.find("h1:first").html(title);
	let time=$("input[name=time_limit]").val();
	preview.find("span.ui.label").eq(0).html("<?php echo $MSG_Time_Limit ?>："+time);
	let memory=$("input[name=memory_limit]").val();
	preview.find("span.ui.label").eq(1).html("<?php echo $MSG_Memory_Limit ?>："+memory);
	
	let description=adminRichText.markup("description");
	preview.find("#description").html(description);
	preview.find("#description .md").each(function(){
		if($("#previewFrame")[0] != undefined) $("#previewFrame")[0].contentWindow.MathJax.typeset();
		$(this).html(marked.parse($(this).html()));
	});
  
	let input=adminRichText.markup("input");
	preview.find("#input").html(input);
	preview.find("#input .md").each(function(){
		if($("#previewFrame")[0] != undefined) $("#previewFrame")[0].contentWindow.MathJax.typeset();
		$(this).html(marked.parse($(this).html()));
	});
	let output=adminRichText.markup("output");
	preview.find("#output").html(output);
	preview.find("#output .md").each(function(){
		if($("#previewFrame")[0] != undefined) $("#previewFrame")[0].contentWindow.MathJax.typeset();
		$(this).html(marked.parse($(this).html()));
	});

	let sinput=$("textarea[name='sample_input']").val();
	preview.find("#sinput").html(sinput);
	let soutput=$("textarea[name='sample_output']").val();
	preview.find("#soutput").html(soutput);
	let hint=adminRichText.markup("hint");
	preview.find("#hint").html(hint);
	preview.find("#hint .md").each(function(){
		if($("#previewFrame")[0] != undefined) $("#previewFrame")[0].contentWindow.MathJax.typeset();
		$(this).html(marked.parse($(this).html()));
	});
	if($("#previewFrame")[0] != undefined) $("#previewFrame")[0].contentWindow.MathJax.typeset();
  }
  $(document).ready(function(){
  
            // 预览默认关闭，按需通过按钮打开，避免编辑区与预览区重叠。
            $('#preview-toggle').on('click', function() {
                 let enabled = $(this).attr('aria-expanded') !== 'true';
                 $(this).attr('aria-expanded', enabled ? 'true' : 'false')
                     .toggleClass('active', enabled)
                     .text(enabled ? '关闭题目预览' : '题目预览');
                 if(enabled) {
                     transform();
                 } else {
                     untransform();
                 }
             });
            $('#full-toggle').change(function() {
		$(".full").toggle();
            });
  }); 
</script>
    <?php
    }
    else {
      require_once("../include/check_post_key.php");
      $id = intval($_POST['problem_id']);
      if(! (isset($_SESSION[$OJ_NAME.'_'.'administrator']) || isset($_SESSION[$OJ_NAME.'_'."p".$id])) ){
                echo "No Privilege.";
                exit(0)    ;
      }
      if (!(isset($_SESSION[$OJ_NAME.'_'."p$id"]) || isset($_SESSION[$OJ_NAME.'_'.'administrator']) || isset($_SESSION[$OJ_NAME.'_'.'problem_editor']) )) exit();

      $title = $_POST['title'];
      $title = str_replace(",", "&#44;", $title);

      $time_limit = $_POST['time_limit'];

      $memory_limit = $_POST['memory_limit'];

      $description = admin_normalize_rich_text($_POST['description'] ?? '');
      $input = admin_normalize_rich_text($_POST['input'] ?? '');
      $output = admin_normalize_rich_text($_POST['output'] ?? '');
      $sample_input = normalizeSpaces($_POST['sample_input']);
      $sample_output = normalizeSpaces($_POST['sample_output']);
      $hint = admin_normalize_rich_text($_POST['hint'] ?? '');
      $source = $_POST['source'];
      $remote_oj= $_POST['remote_oj'];
      $remote_id = $_POST['remote_id'];
      $spj = $_POST['spj'];

      $title = ($title);
      $description = ($description);
      $input = ($input);
      $output = ($output);
      $hint = ($hint);
      $basedir = $OJ_DATA."/$id";

      echo "Problem Updated!<br>";

      if ($sample_input && file_exists($basedir."/sample.in")) {
        //mkdir($basedir);
        $fp = @fopen($basedir."/sample.in","w");
        if($fp){
            fputs($fp,preg_replace("(\r\n)","\n",$sample_input));
            fclose($fp);
        }

        $fp = @fopen($basedir."/sample.out","w");
        if($fp){
            fputs($fp,preg_replace("(\r\n)","\n",$sample_output));
            fclose($fp);
        }
      }

      $spj = intval($spj);

      $sql = "UPDATE `problem` SET `title`=?,`time_limit`=?,`memory_limit`=?, `description`=?,`input`=?,`output`=?,`sample_input`=?,`sample_output`=?,`hint`=?,`source`=?,`spj`=?,remote_oj=?,remote_id=?,`in_date`=NOW(),`coin`=? WHERE `problem_id`=?";

      //echo "SQL: " . $sql . "<br>";
      //echo "Params: remote_oj=[$remote_oj] (" . strlen($remote_oj) . "), remote_id=[$remote_id] (" . strlen($remote_id) . ")<br>"; 
      $coin = intval($_POST['coin'] ?? 1);
      @pdo_query($sql,$title,$time_limit,$memory_limit,$description,$input,$output,$sample_input,$sample_output,$hint,$source,$spj,$remote_oj,$remote_id,$coin,$id);
  
      echo "Edit OK!<br>";
      echo "<a href='../problem.php?id=$id'>See The Problem!</a>";
    }
    ?>
</body>
</html>

