(function (window, $) {
    "use strict";

    var richFields = ["description", "input", "output", "hint"];

    function textareaFor(name) {
        return $("textarea[name='" + name + "']");
    }

    function editorFor(name) {
        var textarea = textareaFor(name)[0];
        if (!textarea || !window.KindEditor || !KindEditor.instances) {
            return null;
        }

        for (var i = 0; i < KindEditor.instances.length; i++) {
            var editor = KindEditor.instances[i];
            if (editor && editor.srcElement &&
                (editor.srcElement[0] === textarea || editor.srcElement === textarea || editor.element === textarea)) {
                return editor;
            }
        }
        return null;
    }

    function stripWrapper(value) {
        value = value || "";
        var match = value.match(/^\s*<span\b[^>]*class\s*=\s*["'][^"']*\bmd\b[^"']*["'][^>]*>([\s\S]*)<\/span>\s*$/i);
        return match ? match[1] : value;
    }

    function normalize(value) {
        var content = stripWrapper(value || "");
        if (!content.trim()) {
            return "";
        }
        return '<span class="md">' + content + "</span>";
    }

    function contentFor(name) {
        var textarea = textareaFor(name);
        if (!textarea.length) {
            return "";
        }

        var editor = editorFor(name);
        if (editor && typeof editor.sync === "function") {
            editor.sync();
        }
        return stripWrapper(textarea.val() || "");
    }

    function setContent(name, value) {
        var content = stripWrapper(value || "");
        var textarea = textareaFor(name);
        if (!textarea.length) {
            return;
        }

        textarea.val(content);
        var editor = editorFor(name);
        if (editor && typeof editor.html === "function") {
            editor.html(content);
        }
    }

    function prepareEditors() {
        richFields.forEach(function (name) {
            var textarea = textareaFor(name);
            if (!textarea.length) {
                return;
            }

            var content = contentFor(name);
            textarea.val(content);

            var editor = editorFor(name);
            if (editor && typeof editor.html === "function") {
                editor.html(content);
            }
        });
    }

    function normalizeForm(form) {
        richFields.forEach(function (name) {
            var textarea = textareaFor(name);
            if (!textarea.length || (form && !$.contains(form, textarea[0]) && form !== textarea[0].form)) {
                return;
            }
            textarea.val(normalize(contentFor(name)));
        });
    }

    window.adminRichText = {
        get: contentFor,
        markup: function (name) {
            return normalize(contentFor(name));
        },
        normalize: normalize,
        set: setContent,
        prepare: prepareEditors
    };

    $(function () {
        // kindeditor.php registers its ready handler before this file is loaded,
        // so the editor instances are available here.
        prepareEditors();
        $("form.oj-admin-form").on("submit.adminRichText", function () {
            normalizeForm(this);
        });
    });
}(window, jQuery));
