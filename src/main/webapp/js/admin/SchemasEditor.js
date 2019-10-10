function SchemasEditor(
    containerDiv,
    schemasParam,
    schemaIdsParam,
    disabledParam
) {
    var self = this;

    var schemasToolBar = null;
    var schemas = null;
    var schemaIds = null;
    var schemaEditor = null;
    var toolBarDiv = null;
    var schemaEditorDiv = null;
    var disabled = disabledParam;

    function constructor() {
        var headerRow = $('<div class="row object-header"></div>');
        containerDiv.append(headerRow);

        var objectHeader = $('<div class="heading col-md-6"></div>');
        var objectIdHeading = $('<h3 class="editorTitle">Types</h3>');
        objectHeader.append(objectIdHeading);
        headerRow.append(objectHeader);

        toolBarDiv = $(
            '<div id="schema-tool-bar" class="object-editor-toolbar col-md-6 pull-right"></div>'
        );
        headerRow.append(toolBarDiv);

        var description = $(
            '<p class="schemas-header">Click on "Add" button above (to the right) to add new types. You can also load types in bulk using the "Load from File" button above. A set of sample types are provided as files in the Cordra distribution. Feel free to load those types for experimenting with Cordra. Refer to the documentation for more details about those samples.</p>'
        );
        containerDiv.append(description);

        var schemasTotalHeader = $(
            '<div id="schemas-total-header" class="col-md-12 nopadding schemas-header"></div>'
        );
        containerDiv.append(schemasTotalHeader);

        var schemasList = $(
            '<div id="schemas-list" class="schemas-scroll col-md-12"></div>'
        );
        containerDiv.append(schemasList);

        schemasToolBar = new SchemasToolBar(
            containerDiv,
            schemasParam,
            disabled,
            createNewSchema,
            showSchemaEditorFor
        );

        schemaEditorDiv = $(
            '<div id="schema-editor" class="schema-editor col-md-12"></div>'
        );
        containerDiv.append(schemaEditorDiv);

        schemas = schemasParam;
        schemaIds = schemaIdsParam;
    }

    function refresh(newSchemas, newSchemaIds) {
        if (newSchemas) {
            schemas = newSchemas;
            schemaIds = newSchemaIds;
        }
        schemasToolBar.refresh(newSchemas);
    }
    self.refresh = refresh;

    function enable() {
        schemasToolBar.enable();
        if (schemaEditor) {
            schemaEditor.enable();
        }
    }
    self.enable = enable;

    function disable() {
        schemasToolBar.disable();
        if (schemaEditor) {
            schemaEditor.disable();
        }
    }
    self.disable = disable;

    function showSchemaEditorFor(objectType, disabled) {
        if (schemaEditor) schemaEditor.destroy();
        schemaEditorDiv.empty();
        if (objectType) {
            var schemaId = getIdForSchema(objectType);
            APP.getObject(
                schemaId,
                function (schemaCordraObject) {
                    schemaEditor = new SchemaEditor(
                        schemaEditorDiv,
                        schemaCordraObject,
                        objectType,
                        disabled
                    );
                    schemaEditorDiv.show();
                },
                function (resp) {
                    console.log(JSON.stringify(resp));
                }
            );
        }
    }
    self.showSchemaEditorFor = showSchemaEditorFor;

    function getIdForSchema(type) {
        var id = null;
        Object.keys(schemaIds).forEach(function (key) {
            if (schemaIds[key] === type) id = key;
        });
        return id;
    }

    function createNewSchema(objectType, template, js) {
        if (schemas[objectType]) {
            APP.notifications.alertError(
                "The type " + objectType + " already exists."
            );
        } else {
            var schemaCordraObject = {
                type: "Schema",
                content: {
                    name: objectType
                }
            };
            if (js) {
                schemaCordraObject.content.javascript = js;
            }
            if (template) {
                schemaCordraObject.content.schema = template;
            } else {
                schemaCordraObject.content.schema = getDefaultSchema();
            }

            if (schemaEditor) schemaEditor.destroy();
            schemaEditorDiv.empty();
            schemaEditor = new SchemaEditor(
                schemaEditorDiv,
                schemaCordraObject,
                objectType
            );
            schemaEditor.toggleCancelDeleteControls();
            schemaEditorDiv.show();
        }
    }
    self.createNewSchema = createNewSchema;

    function getDefaultSchema() {
        return {
            type: "object",
            required: ["name", "description"],
            properties: {
                identifier: {
                    type: "string",
                    cordra: {
                        type: {
                            autoGeneratedField: "handle"
                        }
                    }
                },
                name: {
                    type: "string",
                    maxLength: 128,
                    title: "Name",
                    cordra: {
                        preview: {
                            showInPreview: true,
                            isPrimary: true
                        }
                    }
                },
                description: {
                    type: "string",
                    format: "textarea",
                    maxLength: 2048,
                    title: "Description",
                    cordra: {
                        preview: {
                            showInPreview: true,
                            excludeTitle: true
                        }
                    }
                }
            }
        };
    }

    constructor();
}
