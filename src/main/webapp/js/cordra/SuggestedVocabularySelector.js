(function(){
"use strict";

var window = window || self;

function SuggestedVocabularySelector(textInput, editor) {

    var self = this;

    function constructor() {
        var items = SchemaUtil.getDeepCordraSchemaProperty(editor.schema, 'type', 'suggestedVocabulary');
        var combo = new cnri.ui.ComboBox(textInput, items);
        textInput.on('change', onChange);
    }

    function onChange() {
        var value = textInput.val();
        textInput.val(editor.getValue());
        editor.setValue(value);
    }

    function enable() {

    }
    self.enable = enable;

    function disable() {
        // consider disable in ComboBox
    }
    self.disable = disable;

    constructor();
}

window.SuggestedVocabularySelector = SuggestedVocabularySelector;

})();
