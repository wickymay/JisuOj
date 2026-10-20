<?php

if (!function_exists('admin_strip_md_wrapper')) {
    /**
     * Remove the wrapper used by the public problem page while keeping the
     * actual rich-text content visible in the admin editor.
     */
    function admin_strip_md_wrapper($value)
    {
        $value = (string)$value;
        $pattern = '/^\s*<span\b[^>]*class\s*=\s*["\'][^"\']*\bmd\b[^"\']*["\'][^>]*>([\s\S]*)<\/span>\s*$/i';
        if (preg_match($pattern, $value, $matches)) {
            return $matches[1];
        }
        return $value;
    }
}

if (!function_exists('admin_normalize_rich_text')) {
    /**
     * Keep the database format predictable without exposing the wrapper to
     * administrators while they edit a problem.
     */
    function admin_normalize_rich_text($value)
    {
        $content = admin_strip_md_wrapper($value);
        if (trim($content) === '') {
            return '';
        }
        return '<span class="md">' . $content . '</span>';
    }
}

