(function(){
"use strict";

//The Json Editor from josdejong/jsoneditor
//has exactly the same variable name as the one from jdorn/json-editor
//The following is used to rename the first so that both can be used on the same page.

window.JsonEditorOnline = window.JSONEditor;
delete window.JSONEditor;

})();
