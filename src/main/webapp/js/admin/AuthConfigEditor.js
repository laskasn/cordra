function AuthConfigEditor(containerDiv, authConfig, disabled) {
    var self = this;
    var editor = null;
    var toolBarDiv = null;
    var saveButton = null;

    function constructor() {
        var headerRow = $('<div class="row object-header"></div>');
        containerDiv.append(headerRow);

        var objectHeader = $('<div class="heading col-md-6"></div>');
        var objectIdHeading = $('<h3 class="editorTitle">Authorization</h3>');
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
            "<p>This structure lets you configure who can create, read, and write to what objects by default. Note that defaults can be overwritten by the objects directly. Defaults can be configured to apply across all objects and/or specific types. ACLs can use the reserved keywords: public, authenticated, creator, self and admin.</p>"
        );
        containerDiv.append(description);

        var editorDiv = $('<div style="height:500px;" class="col-md-12"></div>');
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
        if (!authConfig) {
            authConfig = {};
        }

        editor = new JsonEditorOnline(container, options, authConfig);
        if (disabled) {
            APP.disableJsonEditorOnline(editor);
        }
    }

    function onCloseClick() {
        APP.clearFragment();
    }

    function disable() {
        saveButton.hide();
        APP.disableJsonEditorOnline(editor);
    }
    self.disable = disable;

    function enable() {
        saveButton.show();
        APP.enableJsonEditorOnline(editor);
    }
    self.enable = enable;

    function destroy() {
        editor.destroy();
    }
    self.destroy = destroy;

    function save() {
        var authConfigUpdate = editor.get();
        APP.saveAuthConfig(authConfigUpdate);
    }

    constructor();
}
