function ObjectAclEditor(containerDiv, acl, objectId, types, allowEdits) {
    var self = this;
    var editor = null;

    function constructor() {
        var actionToolBar = $(
            '<div class="object-editor-toolbar col-md-12 pull-right"></div>'
        );
        containerDiv.append(actionToolBar);

        var writer = allowEdits;
        var reader = !allowEdits;
        var empty = isEmpty(acl);

        if (writer) {
            var saveAclButton = $(
                '<button class="btn btn-sm btn-primary"><i class="fa fa-check"></i></button>'
            );
            actionToolBar.append(saveAclButton);
            saveAclButton.on("click", onSaveAclClick);

            var saveAclButtonSpan = $("<span></span>");
            saveAclButton.append(saveAclButtonSpan);
            saveAclButtonSpan.text("Save ACL");
        }

        editorDiv = $('<div class="col-md-12"></div>');
        containerDiv.append(editorDiv);

        if (writer || (reader && !empty)) {
            var schema = getAclJsonSchema();
            if (types) {
                schema.properties.readers.items.cordra.type.handleReference.types = types;
                schema.properties.writers.items.cordra.type.handleReference.types = types;
            }

            var options = {
                theme: "bootstrap3",
                iconlib: "fontawesome4",
                schema: schema,
                startval: acl,
                disable_edit_json: true,
                disable_properties: true,
                //                required_by_default : true,
                disable_collapse: true,
                disabled: false
            };
            JSONEditor.defaults.options.iconlib = "bootstrap3";
            JSONEditor.defaults.editors.object.options.disable_properties = true;
            JSONEditor.defaults.editors.object.options.disable_edit_json = true;
            JSONEditor.defaults.editors.object.options.disable_collapse = false;

            editor = new JSONEditor(editorDiv[0], options);
            editor.on("change", onChange);
            if (reader) {
                editor.disable();
                editorDiv.addClass("hidden-buttons");
                editorDiv.addClass("view-mode");
            }
        } else {
            editorDiv.append("<p>No access control list to show.</p>");
        }
    }

    function isEmpty(acl) {
        if (acl == undefined) return true;
        if (acl.writers && acl.writers.length > 0) return false;
        if (acl.readers && acl.readers.length > 0) return false;
        return true;
    }

    function onChange() { }

    function onSaveAclClick() {
        var newAcl = editor.getValue();
        APP.saveAclForCurrentObject(newAcl, onSaveAclSuccess, onSaveAclFail);
    }

    function onSaveAclSuccess(res) { }

    function onSaveAclFail(res) {
        console.log("acl save fail: " + res.status + " " + res.statusText);
    }

    function getAclJsonSchema() {
        var aclJsonSchema = {
            type: "object",
            title: "Access Control List",
            required: [],
            properties: {
                readers: {
                    type: "array",
                    format: "table",
                    title: "Readers",
                    uniqueItems: true,
                    items: {
                        type: "string",
                        title: "Reader",
                        cordra: {
                            type: {
                                handleReference: {
                                    types: ["User", "Group"]
                                }
                            }
                        }
                    }
                },
                writers: {
                    type: "array",
                    format: "table",
                    title: "Writers",
                    uniqueItems: true,
                    items: {
                        type: "string",
                        title: "Writer",
                        cordra: {
                            type: {
                                handleReference: {
                                    types: ["User", "Group"]
                                }
                            }
                        }
                    }
                }
            }
        };
        return aclJsonSchema;
    }

    function destroy() {
        if (editor) editor.destroy();
    }
    self.destroy = destroy;

    constructor();
}
