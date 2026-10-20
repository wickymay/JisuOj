<?php

function hustoj_avatar_dir()
{
    return dirname(__DIR__) . "/upload/avatar";
}

function hustoj_avatar_stem($user_id)
{
    return "avatar_" . hash("sha256", (string) $user_id);
}

function hustoj_avatar_extensions()
{
    return array("jpg", "jpeg", "png", "gif", "webp");
}

function hustoj_avatar_url($user_id)
{
    $dir = hustoj_avatar_dir();
    $stem = hustoj_avatar_stem($user_id);

    foreach (hustoj_avatar_extensions() as $extension) {
        $path = $dir . "/" . $stem . "." . $extension;
        if (is_file($path)) {
            return "upload/avatar/" . $stem . "." . $extension . "?v=" . (int) @filemtime($path);
        }
    }

    return "";
}

function hustoj_remove_avatar($user_id)
{
    $dir = hustoj_avatar_dir();
    $stem = hustoj_avatar_stem($user_id);

    foreach (hustoj_avatar_extensions() as $extension) {
        $path = $dir . "/" . $stem . "." . $extension;
        if (is_file($path)) @unlink($path);
    }
}

function hustoj_save_avatar($user_id, $upload, &$error)
{
    $error = "";
    if (!is_array($upload) || !isset($upload["error"])) {
        $error = "头像上传失败，请重新选择图片。";
        return false;
    }

    if ($upload["error"] !== UPLOAD_ERR_OK) {
        $error = "头像上传失败，请选择不超过 2MB 的图片。";
        return false;
    }

    if (empty($upload["tmp_name"]) || !is_uploaded_file($upload["tmp_name"])) {
        $error = "头像上传失败，请重新选择图片。";
        return false;
    }

    if ((int) $upload["size"] <= 0 || (int) $upload["size"] > 2 * 1024 * 1024) {
        $error = "头像不能超过 2MB。";
        return false;
    }

    $image_info = @getimagesize($upload["tmp_name"]);
    $mime_to_extension = array(
        "image/jpeg" => "jpg",
        "image/png" => "png",
        "image/gif" => "gif",
        "image/webp" => "webp"
    );
    $mime = is_array($image_info) && isset($image_info["mime"])
        ? strtolower($image_info["mime"])
        : "";

    if (!isset($mime_to_extension[$mime])) {
        $error = "头像格式不支持，请上传 JPG、PNG、GIF 或 WEBP 图片。";
        return false;
    }

    if ((int) $image_info[0] > 2048 || (int) $image_info[1] > 2048) {
        $error = "头像尺寸不能超过 2048×2048。";
        return false;
    }

    $dir = hustoj_avatar_dir();
    if (!is_dir($dir) && !@mkdir($dir, 0755, true)) {
        $error = "头像目录不可写，请联系管理员。";
        return false;
    }
    if (!is_writable($dir)) {
        $error = "头像目录不可写，请联系管理员。";
        return false;
    }

    $stem = hustoj_avatar_stem($user_id);
    $extension = $mime_to_extension[$mime];
    $filename = $stem . "." . $extension;
    $target = $dir . "/" . $filename;
    $temporary = $dir . "/." . $filename . "." . uniqid("tmp_", true);

    if (!@move_uploaded_file($upload["tmp_name"], $temporary)) {
        $error = "头像保存失败，请稍后重试。";
        return false;
    }

    if (!@rename($temporary, $target)) {
        @unlink($temporary);
        $error = "头像保存失败，请稍后重试。";
        return false;
    }

    foreach (hustoj_avatar_extensions() as $old_extension) {
        $old_path = $dir . "/" . $stem . "." . $old_extension;
        if ($old_path !== $target && is_file($old_path)) @unlink($old_path);
    }

    return true;
}
