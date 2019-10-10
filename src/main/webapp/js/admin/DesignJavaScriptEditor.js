function DesignJavaScriptEditor(containerDiv, designJavaScript, disabled) {
    var self = this;
    var editor = null;
    var saveButton = null;
    var scriptEditorDiv = null;
    var scriptEditor = null;

    function constructor() {
        var headerRow = $('<div class="row object-header"></div>');
        containerDiv.append(headerRow);

        var objectHeader = $('<div class="heading col-md-6"></div>');
        var objectIdHeading = $('<h3 class="editorTitle">Design JavaScript</h3>');
        objectHeader.append(objectIdHeading);
        headerRow.append(objectHeader);

        toolBarDiv = $(
            '<div class="object-editor-toolbar col-md-6 pull-right"></div>'
        );
        headerRow.append(toolBarDiv);

        if (disabled) {
            saveButton = $(
                '<button class="btn btn-sm btn-primary" style="display:none"><i class="fa fa-save"></i></button>'
            );
        } else {
            saveButton = $(
                '<button class="btn btn-sm btn-primary"><i class="fa fa-save"></i></button>'
            );
        }
        toolBarDiv.append(saveButton);
        saveButton.on("click", save);

        var saveButtonSpan = $("<span></span>");
        saveButton.append(saveButtonSpan);
        saveButtonSpan.text("Save");

        var description = $(
            '<p>Edit the "javascript" property of the design object. This allows for the creation of a "generateId" function that can be used to programmatically generate the handles on objects for create requests.</p>'
        );
        containerDiv.append(description);

        var scriptEditorDivContainer = $('<div class="col-sm-10"/>');
        scriptEditorDiv = $(
            '<div id="designJavaScriptEditor" class="ace_editor"></div>'
        );
        scriptEditorDivContainer.append(scriptEditorDiv);
        containerDiv.append(scriptEditorDivContainer);

        scriptEditor = ace.edit("designJavaScriptEditor");
        scriptEditor.setTheme("ace/theme/textmate");
        scriptEditor.getSession().setMode("ace/mode/javascript");
        scriptEditor.setOptions({
            maxLines: Infinity,
            minLines: 10
        });
        scriptEditor.$blockScrolling = Infinity;
        if (designJavaScript) {
            scriptEditor.setValue(designJavaScript, -1);
        }
    }

    function onCloseClick() {
        APP.clearFragment();
    }

    function disable() {
        saveButton.hide();
    }
    self.disable = disable;

    function enable() {
        saveButton.show();
    }
    self.enable = enable;

    function destroy() {
        scriptEditor.destroy();
    }
    self.destroy = destroy;

    function save() {
        var js = scriptEditor.getValue();
        APP.saveDesignJavaScript(js);
    }

    constructor();
}
