function SchemaEditor(containerDiv, schemaCordraObject, type, disabled) {
    var self = this;
    var editorDiv = null;
    var javascriptDiv = null;
    var toolBarDiv = null;
    var bottomToolBarDiv = null;
    var editor = null;
    var previewDiv = null;
    var saveButton = null;
    var deleteButton = null;
    var cancelButton = null;
    var editButton = null;
    var previewButton = null;
    var saveButtonBottom = null;
    var deleteButtonBottom = null;
    var cancelButtonBottom = null;

    var scriptEditorDiv = null;
    var scriptEditor = null;

    function constructor() {
        var headerRow = $('<div class="row object-header"></div>');
        containerDiv.append(headerRow);

        var objectHeader = $('<div class="heading col-md-12"></div>');
        headerRow.append(objectHeader);
        var objectIdHeading = $(
            '<h3 class="editorTitle">Object Type: ' + type + "</h3>"
        );
        objectHeader.append(objectIdHeading);

        var typeText = $("<p>Type: Schema</p>");
        objectHeader.append(typeText);

        toolBarDiv = $(
            '<div class="object-editor-toolbar col-md-12 pull-right"></div>'
        );
        headerRow.append(toolBarDiv);

        createToolBar();

        editorDiv = $(
            '<div class="col-md-12 nopadding schema-editor"  style="height:500px;"></div>'
        );
        containerDiv.append(editorDiv);

        var container = editorDiv[0];
        var options = {
            ace: ace,
            theme: "ace/theme/textmate",
            mode: "code",
            modes: ["code", "tree"], // allowed modes
            onError: function (err) {
                alert(err.toString());
            }
        };
        var schema = schemaCordraObject.content.schema;
        editor = new JsonEditorOnline(container, options, schema);
        if (disabled) {
            APP.disableJsonEditorOnline(editor);
        }
        previewDiv = $('<div class="col-md-12 nopadding"></div>');
        containerDiv.append(previewDiv);

        javascriptDiv = $('<div class="col-md-12 nopadding js-editor"></div>');
        containerDiv.append(javascriptDiv);
        buildScriptEditor(schemaCordraObject, javascriptDiv);

        bottomToolBarDiv = $(
            '<div class="object-editor-toolbar col-md-offset-6 col-md-6 pull-right nopadding"></div>'
        );
        containerDiv.append(bottomToolBarDiv);
        createBottomToolBar();
    }

    function buildScriptEditor(schemaCordraObject, containerDiv) {
        containerDiv.append($("<p></p>"));
        var label = $("<label></label>");
        label.text("JavaScript");
        containerDiv.append(label);

        scriptEditorDiv = $(
            '<div id="handleMintingJavaScriptEditor" class="ace_editor"></div>'
        );
        containerDiv.append(scriptEditorDiv);

        scriptEditor = ace.edit("handleMintingJavaScriptEditor");
        scriptEditor.setTheme("ace/theme/textmate");
        scriptEditor.getSession().setMode("ace/mode/javascript");
        scriptEditor.setOptions({
            maxLines: Infinity,
            minLines: 10
        });
        scriptEditor.$blockScrolling = Infinity;
        if (schemaCordraObject.content.javascript) {
            scriptEditor.setValue(schemaCordraObject.content.javascript, -1);
        }
    }

    function createToolBar() {
        previewButton = $(
            '<button class="btn btn-sm btn-primary"><i class="fa fa-eye"></i></button>'
        );
        toolBarDiv.append(previewButton);
        previewButton.on("click", previewClick);

        var previewButtonSpan = $("<span><span>");
        previewButton.append(previewButtonSpan);
        previewButtonSpan.text("Preview UI");

        editButton = $(
            '<button class="btn btn-sm btn-primary"><i class="fa fa-edit"></i></button>'
        );
        toolBarDiv.append(editButton);
        editButton.on("click", onEditClick);

        var editButtonSpan = $("<span><span>");
        editButton.append(editButtonSpan);
        editButtonSpan.text("Edit");
        editButton.hide();

        deleteButton = $(
            '<button class="btn btn-sm btn-danger"><i class="fa fa-trash"></i></button>'
        );

        toolBarDiv.append(deleteButton);
        deleteButton.on("click", deleteSchema);

        var deleteButtonSpan = $("<span><span>");
        deleteButton.append(deleteButtonSpan);
        deleteButtonSpan.text("Delete");

        cancelButton = $(
            '<button class="btn btn-sm btn-danger"><i class="fa fa-trash"></i></button>'
        );

        toolBarDiv.append(cancelButton);
        cancelButton.on("click", APP.closeSchemaEditor);

        var cancelButtonSpan = $("<span><span>");
        cancelButton.append(cancelButtonSpan);
        cancelButtonSpan.text("Cancel");
        cancelButton.hide();

        saveButton = $(
            '<button class="btn btn-sm btn-success" data-loading-text="Saving..."><i class="fa fa-save"></i></button>'
        );

        toolBarDiv.append(saveButton);
        saveButton.on("click", save);

        var saveButtonSpan = $("<span><span>");
        saveButton.append(saveButtonSpan);
        saveButtonSpan.text("Save");
    }

    function createBottomToolBar() {
        deleteButtonBottom = $(
            '<button class="btn btn-sm btn-danger"><i class="fa fa-trash"></i></button>'
        );

        bottomToolBarDiv.append(deleteButtonBottom);
        deleteButtonBottom.on("click", deleteSchema);

        var deleteButtonBottomSpan = $("<span><span>");
        deleteButtonBottom.append(deleteButtonBottomSpan);
        deleteButtonBottomSpan.text("Delete");

        saveButtonBottom = $(
            '<button class="btn btn-sm btn-success" data-loading-text="Saving..."><i class="fa fa-save"></i></button>'
        );

        bottomToolBarDiv.append(saveButtonBottom);
        saveButtonBottom.on("click", save);

        var saveButtonBottomSpan = $("<span><span>");
        saveButtonBottom.append(saveButtonBottomSpan);
        saveButtonBottomSpan.text("Save");
    }

    function toggleToolBarControls() {
        previewButton.toggle();
        saveButton.toggle();
        deleteButton.toggle();
        saveButtonBottom.toggle();
        deleteButtonBottom.toggle();
        editButton.toggle();
    }

    function toggleCancelDeleteControls() {
        cancelButton.toggle();
        deleteButton.toggle();
    }
    self.toggleCancelDeleteControls = toggleCancelDeleteControls;

    function enable() {
        toggleToolBarControls();
        APP.enableJsonEditorOnline(editor);
    }
    self.enable = enable;

    function disable() {
        toggleToolBarControls();
        APP.disableJsonEditorOnline(editor);
    }
    self.disable = disable;

    function destroy() {
        editor.destroy();
    }
    self.destroy = destroy;

    function save() {
        var schema = "";
        try {
            schema = editor.get();
            schemaCordraObject.content.schema = schema;
            var js = scriptEditor.getValue();
            schemaCordraObject.content.javascript = js;
            APP.saveSchema(schemaCordraObject, type);
            APP.closeSchemaEditor();
        } catch (e) {
            console.log(e);
            APP.notifications.alertError(
                "Type " + type + " failed to save. Check schema syntax."
            );
        }
    }

    function deleteSchema() {
        var dialog = new ModalYesNoDialog(
            "Are you sure you want to delete this type?",
            function () {
                yesDeleteCallback(type);
            },
            null,
            self
        );
        dialog.show();
    }

    function yesDeleteCallback(type) {
        APP.deleteSchema(type);
    }

    function closeClick() {
        APP.closeSchemaEditor();
    }

    function onEditClick() {
        toggleToolBarControls();
        editorDiv.show();
        javascriptDiv.show();
        previewDiv.hide();
    }

    function previewClick() {
        previewDiv.empty();
        var schema = editor.get();
        var previewEditor = PreviewObjectEditor(
            previewDiv,
            schema,
            type,
            null,
            "example/id"
        );
        toggleToolBarControls();
        editorDiv.hide();
        javascriptDiv.hide();
        previewDiv.show();
    }

    constructor();
}
