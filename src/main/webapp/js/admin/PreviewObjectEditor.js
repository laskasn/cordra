(function () {
    "use strict";

    var window = window || self;

    function PreviewObjectEditor(
        containerDiv,
        schema,
        type,
        objectJson,
        objectId
    ) {
        var self = this;
        var editor = null;
        var objectIdHeading = null;
        var editorDiv = null;
        var suffixDiv = null;
        var suffixInput = null;

        function constructor() {
            objectIdHeading = $("<h3></h3>");
            containerDiv.append(objectIdHeading);
            if (objectId != null) {
                var objectHeadingText = getObjectHeadingText();
                objectIdHeading.text("Object Id: " + objectId);
                objectIdHeading.text(objectHeadingText);
            }
            editorDiv = $("<div></div>");
            containerDiv.append(editorDiv);

            fixPropertyOrder(schema);
            var options = {
                theme: "bootstrap3",
                iconlib: "fontawesome4",
                schema: schema,
                startval: objectJson,
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

            var editorTitle = $("div[data-schemapath=root] > h3 > span:first-child");
            if (editorTitle.text() === "root") {
                editorTitle.parent().hide();
            }
        }

        function fixPropertyOrder(schema) {
            if (!isObject(schema)) return;
            for (var key in schema) {
                fixPropertyOrder(schema[key]);
            }
            if (isObject(schema.properties)) {
                var count = 1;
                for (var prop in schema.properties) {
                    var propSchema = schema.properties[prop];
                    if (isObject(propSchema)) {
                        propSchema.propertyOrder = count;
                        count++;
                    }
                }
            }
        }

        function isObject(obj) {
            return obj && typeof obj === "object";
        }

        function getObjectHeadingText() {
            var searchResult = {
                id: objectId,
                json: objectJson,
                type: type
            };
            var previewData = ObjectPreviewUtil.getPreviewData(searchResult);
            var objectHeadingText = null;
            for (var jsonPointer in previewData) {
                var thisPreviewData = previewData[jsonPointer];
                var prettifiedPreviewData = ObjectPreviewUtil.prettifyPreviewJson(
                    thisPreviewData.previewJson
                );
                if (!prettifiedPreviewData) continue;
                if (thisPreviewData.isPrimary) {
                    objectHeadingText = prettifiedPreviewData;
                    break;
                }
            }
            if (objectHeadingText == null) {
                objectHeadingText = "Object Id: " + objectId;
            }
            return objectHeadingText;
        }

        function onChange() {
            fixButtonGroupCss();
        }

        function fixButtonGroupCss() {
            $(".cordra-round-left").removeClass("cordra-round-left");
            $(".cordra-round-right").removeClass("cordra-round-right");
            editorDiv.find(".btn-group").each(function (_, div) {
                var $div = $(div);
                var firstChild = $div.children().first();
                if (!firstChild.is(":visible")) {
                    var firstDisplayedChild = $div.children(":visible").first();
                    firstDisplayedChild.addClass("cordra-round-left");
                }
                var lastChild = $div.children().last();
                if (!lastChild.is(":visible")) {
                    var lastDisplayedChild = $div.children(":visible").last();
                    lastDisplayedChild.addClass("cordra-round-right");
                }
            });
        }

        function ensureFileInputsAreEmptyString(jsonObject) {
            var allFileInputs = getAllFileInputs();
            allFileInputs.each(function (index, element) {
                var jsonPointer = JsonUtil.jsonPointerForElement(element);
                JsonUtil.replaceJsonAtPointer(jsonObject, jsonPointer, "");
            });
        }

        function getAllFileInputs() {
            return containerDiv.find("input[type=file]");
        }

        constructor();
    }
    window.PreviewObjectEditor = PreviewObjectEditor;
})();
