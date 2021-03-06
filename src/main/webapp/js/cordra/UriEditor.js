(function(){
"use strict";

var window = window || self;

function UriEditor(textInput, editor) {
    var self = this;
    var link = null;

    function constructor() {
        textInput.on('input', updateLink);
        var div = $('<div/>');
        link = $('<a target="_blank"></a>');
        textInput.after(div);
        div.append(link);
        editor.jsoneditor.watch(editor.path, updateLink);
    }

    function updateLink() {
        link.attr('href', textInput.val());
        link.text(textInput.val());
    }

    function enable() {
        textInput.show();
    }
    self.enable = enable;

    function disable() {
        textInput.hide();
    }
    self.disable = disable;


    constructor();
}

window.UriEditor = UriEditor;

})();
