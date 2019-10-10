(function () {
    "use strict";

    var window = window || self;

    function ObjectEditor(containerDiv, options) {
        var schema = $.extend(true, {}, options.schema); //The editor might modify the schema when expanding $ref so we need to deep clone it here
        var type = options.type;
        var objectJson = options.objectJson;
        var objectId = options.objectId;
        var relationshipsButtonText = options.relationshipsButtonText;
        var disabled = options.disabled;
        var allowEdits = options.allowEdits;
        var allowClone = options.allowClone;
        var contentPlusMeta = options.contentPlusMeta;

        var self = this;
        var editor = null;
        var objectEditorDiv = null;
        var editorDiv = null;
        var toolBarDiv = null;
        var tabControls = null;
        var editJsonDiv = null;
        var editJsonTextDiv = null;
        var jsonEditorOnline = null;
        var aclEditorDiv = null;
        var aclEditorChildDiv = null;
        var aclEditor = null;
        //    var relationshipsEditor = null;
        //    var relationshipsEditorDiv = null;

        var relationshipsGraph = null;
        var relationshipsGraphDiv = null;

        var versionsEditorDiv = null;
        var versionsEditor = null;

        var suffixInput = null;
        var handleInput = null;
        var allowAdvancedView = true;
        var advancedDiv = null;
        var fullCordraObjectViewer = null;
        var bottomToolBarDiv = null;
        var saveButtonBottom = null;
        var revertButtonBottom = null;
        var deleteButtonBottom = null;
        var saveButton = null;
        var payloadsEditorDiv = null;
        var payloadsEditor = null;

        var bottomProgressDiv = null;
        var bottomProgressBar = null;

        var editJsonLink = null;
        var editFormLink = null;
        var viewFormLink = null;
        var viewJsonLink = null;
        var editToggleButton = null;

        function constructor() {
            if (!allowEdits) {
                disabled = true;
            }

            var headerRow = $('<div class="row object-header"></div>');
            containerDiv.append(headerRow);

            var objectHeader = $('<div class="heading col-md-12"></div>');
            var objectIdHeading = $('<h3 class="editorTitle"></h3>');
            objectHeader.append(objectIdHeading);

            if (objectId != null) {
                var objectHeadingText = getObjectHeadingText();
                objectIdHeading.text("Object Id: " + objectId);
                objectIdHeading.text(objectHeadingText);
            } else {
                objectIdHeading.text("New");
            }

            if (!APP.getUiConfig().hideTypeInObjectEditor) {
                var typeText = $("<p></p>");
                typeText.text("Type: " + type);
                objectHeader.append(typeText);
            }

            headerRow.append(objectHeader);

            if (contentPlusMeta.metadata.isVersion) {
                var newerVersionText = $(
                    "<p>There is a newer version of this object </p>"
                );
                containerDiv.append(newerVersionText);
                var versionOf = contentPlusMeta.metadata.versionOf;
                // was ../ under classic
                var link = $('<a style="display:inline-block" target="_blank">')
                    .attr("href", "objects/" + versionOf)
                    .text(versionOf);
                link.attr("data-handle", versionOf);
                link.on("click", onNewerVersionClick);
                newerVersionText.append(link);
            }

            if (objectId == null) {
                var uiConfig = APP.getUiConfig();
                if (uiConfig.allowUserToSpecifyHandleOnCreate) {
                    createHandleInput();
                } else if (uiConfig.allowUserToSpecifySuffixOnCreate) {
                    createSuffixInput();
                }
            }

            var tabPanel = $('<div class="tab">');
            containerDiv.append(tabPanel);

            tabControls = $('<ul class="nav nav-tabs"></ul>');
            tabPanel.append(tabControls);
            createTabs();

            var tabContent = $('<div class="tab-content tabs"></div>');
            tabPanel.append(tabContent);

            versionsEditorDiv = $(
                '<div class="versionsEditor col-md-12 tab-pane" id="versionsEditor"></div>'
            );
            tabContent.append(versionsEditorDiv);

            aclEditorDiv = $(
                '<div class="aclEditor col-md-12 tab-pane" id="aclEditor"></div>'
            );
            tabContent.append(aclEditorDiv);

            relationshipsGraphDiv = $(
                '<div class="relationshipsEditor col-md-12 tab-pane" id="relationshipsEditor"></div>'
            );
            tabContent.append(relationshipsGraphDiv);

            objectEditorDiv = $(
                '<div class="objectEditor col-md-12 tab-pane active" id="objectEditor"></div>'
            );
            tabContent.append(objectEditorDiv);

            advancedDiv = $(
                '<div class="advancedEditor col-md-12 tab-pane" id="advancedEditor"></div>'
            );
            tabContent.append(advancedDiv);

            toolBarDiv = $(
                '<div class="object-editor-toolbar col-md-12 pull-right"></div>'
            );
            objectEditorDiv.append(toolBarDiv);

            createToolBar();

            createEditJsonDiv();

            editorDiv = $('<div class="editor col-md-12 nopadding"></div>');
            objectEditorDiv.append(editorDiv);

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
                disabled: disabled
            };
            JSONEditor.defaults.options.iconlib = "bootstrap3";
            JSONEditor.defaults.editors.object.options.disable_properties = true;
            JSONEditor.defaults.editors.object.options.disable_edit_json = true;
            JSONEditor.defaults.editors.object.options.disable_collapse = false;

            editor = new JSONEditor(editorDiv[0], options);
            editor.on("change", onChange);
            if (disabled) {
                editor.disable();
                editorDiv.addClass("hidden-buttons");
                editorDiv.addClass("view-mode");
            }

            if (objectId != null) {
                APP.getRelationships(
                    objectId,
                    onGotRelationshipsSuccess,
                    onGotRelationshipsError
                );
            }

            var editorTitle = $("div[data-schemapath=root] > h3 > span:first-child");
            if (editorTitle.text() === "root") {
                editorTitle.parent().hide();
            }

            payloadsEditorDiv = $('<div class="payloadEditor col-md-12"></div>');
            objectEditorDiv.append(payloadsEditorDiv);

            if (
                !disabled ||
                (contentPlusMeta.payloads && contentPlusMeta.payloads.length > 0)
            ) {
                payloadsEditor = new PayloadsEditor(
                    payloadsEditorDiv,
                    contentPlusMeta.payloads,
                    disabled
                );
            } else {
                payloadsEditorDiv.hide();
            }

            if (!disabled) {
                if (allowEdits) {
                    bottomToolBarDiv = $(
                        '<div class="object-editor-toolbar col-md-offset-6 col-md-6 pull-right"></div>'
                    );
                    objectEditorDiv.append(bottomToolBarDiv);
                    createBottomToolBar();

                    bottomProgressDiv = $("<div></div>");
                    containerDiv.append(bottomProgressDiv);
                }
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

        function createTabs() {
            if (objectId != null) {
                var objectTab = $(
                    '<li class="active"><a data-toggle="tab" href="#objectEditor">Object</a></li>'
                );
                tabControls.append(objectTab);
                var aclTab = $(
                    '<li class=""><a data-toggle="tab" href="#aclEditor">ACL</a></li>'
                );
                tabControls.append(aclTab);
                aclTab.click(onShowACL);

                var versionsTab = $(
                    '<li class=""><a data-toggle="tab" href="#versionsEditor">Versions</a></li>'
                );
                tabControls.append(versionsTab);
                versionsTab.click(onShowVersions);

                var advancedTab = $(
                    '<li class=""><a data-toggle="tab" href="#advancedEditor">DO View / Details</a></li>'
                );
                tabControls.append(advancedTab);
                advancedTab.click(onShowAdvanced);

                var relativesTab = $(
                    '<li class=""><a data-toggle="tab" href="#relationshipsEditor">Relatives</a></li>'
                );
                tabControls.append(relativesTab);
                relativesTab.click(onShowRelationships);
            }
        }

        function createToolBar() {
            if (disabled) {
                if (allowEdits) {
                    var editButton = $(
                        '<button class="btn btn-sm btn-primary" data-loading-text="Editing..."><i class="fa fa-edit"></i></button>'
                    );
                    toolBarDiv.append(editButton);
                    editButton.on("click", APP.editCurrentObject);

                    var editButtonSpan = $("<span><span>");
                    editButton.append(editButtonSpan);
                    editButtonSpan.text("Edit");
                }
                var objectBreadCrumbs = $('<nav aria-label="breadcrumb"></nav>');
                toolBarDiv.append(objectBreadCrumbs);
                var breadCrumbOl = $('<ol class="breadcrumb"></ol>');
                objectBreadCrumbs.append(breadCrumbOl);

                viewFormLink = $(
                    '<li class="breadcrumb-item nodivider" aria-current="page" style="display:none"></li>'
                );
                var link = $('<a><i class="fa fa-list-alt"></i>Form</a>');
                viewFormLink.append(link);
                link.on("click", onFormButtonClick);
                breadCrumbOl.append(viewFormLink);

                var jsonForm = $(
                    '<form style="display:none" method="POST" target="_blank"/>'
                );
                // was ../ under classic
                jsonForm.attr("action", "objects/" + objectId);
                var accessTokenInput = $('<input type="hidden" name="access_token"/>');
                APP.getAccessToken().then(function (accessToken) {
                    accessTokenInput.val(accessToken);
                });
                jsonForm.append(accessTokenInput);

                viewJsonLink = $(
                    '<li class="breadcrumb-item" aria-current="page"></li>'
                );
                breadCrumbOl.append(viewJsonLink);

                var link = $('<a><i class="fa fa-external-link-alt"></i>JSON</a>');
                viewJsonLink.append(link);
                viewJsonLink.on("click", function (event) {
                    event.preventDefault();
                    jsonForm.trigger("submit");
                });
                breadCrumbOl.append(jsonForm);
            } else {
                if (objectId != null) {
                    var deleteButton = $(
                        '<button class="btn btn-sm btn-danger"><i class="fa fa-trash"></i></button>'
                    );
                    toolBarDiv.append(deleteButton);
                    deleteButton.on("click", onDelete);

                    var deleteButtonSpan = $("<span><span>");
                    deleteButton.append(deleteButtonSpan);
                    deleteButtonSpan.text("Delete");

                    var revertButton = $(
                        '<button class="btn btn-sm btn-warning"><i class="fa fa-undo"></i></button>'
                    );
                    toolBarDiv.append(revertButton);
                    revertButton.on("click", onRevert);

                    var revertButtonSpan = $("<span><span>");
                    revertButton.append(revertButtonSpan);
                    revertButtonSpan.text("Revert");
                }

                saveButton = $(
                    '<button class="btn btn-sm btn-success" data-loading-text="Saving..."><i class="fa fa-save"></i></button>'
                );
                toolBarDiv.append(saveButton);
                saveButton.on("click", save);

                var saveButtonSpan = $("<span><span>");
                saveButton.append(saveButtonSpan);
                saveButtonSpan.text("Save");

                var objectBreadCrumbs = $('<nav aria-label="breadcrumb"></nav>');
                toolBarDiv.append(objectBreadCrumbs);
                var breadCrumbOl = $('<ol class="breadcrumb"></ol>');
                objectBreadCrumbs.append(breadCrumbOl);

                editFormLink = $(
                    '<li class="breadcrumb-item" aria-current="page" style="display:none"></li>'
                );
                var link = $('<a><i class="fa fa-edit"></i>Edit as Form</a>');
                editFormLink.append(link);
                link.on("click", onEditForm);
                breadCrumbOl.append(editFormLink);

                editJsonLink = $(
                    '<li class="breadcrumb-item nodivider" aria-current="page"></li>'
                );
                var link = $('<a><i class="fa fa-stream"></i>Edit as JSON</a>');
                editJsonLink.append(link);
                link.on("click", onEditJson);
                breadCrumbOl.append(editJsonLink);
            }
        }

        function createBottomToolBar() {
            //Revert and delete button should not be shown if creating a new object
            if (objectId != null) {
                deleteButtonBottom = $(
                    '<button class="btn btn-sm btn-danger"><i class="fa fa-trash"></i></button>'
                );
                bottomToolBarDiv.append(deleteButtonBottom);
                deleteButtonBottom.on("click", onDelete);

                var deleteButtonBottomSpan = $("<span><span>");
                deleteButtonBottom.append(deleteButtonBottomSpan);
                deleteButtonBottomSpan.text("Delete");

                revertButtonBottom = $(
                    '<button class="btn btn-sm btn-warning"><i class="fa fa-undo"></i></button>'
                );
                bottomToolBarDiv.append(revertButtonBottom);
                revertButtonBottom.on("click", onRevert);

                var revertButtonBottomSpan = $("<span><span>");
                revertButtonBottom.append(revertButtonBottomSpan);
                revertButtonBottomSpan.text("Revert");
            }

            saveButtonBottom = $(
                '<button class="btn btn-sm btn-success" data-loading-text="Saving..."><i class="fa fa-save"></i></button>'
            );
            bottomToolBarDiv.append(saveButtonBottom);
            saveButtonBottom.on("click", save);

            var saveButtonBottomSpan = $("<span><span>");
            saveButtonBottom.append(saveButtonBottomSpan);
            saveButtonBottomSpan.text("Save");
        }

        function onNewerVersionClick(e) {
            e.preventDefault();
            var link = $(this);
            var handle = link.attr("data-handle");
            APP.resolveHandle(handle);
        }

        function createHandleInput() {
            var handleDiv = $('<div class="handleEditor"></div>');
            containerDiv.append(handleDiv);
            var handleLabel = $('<label class="control-label">Handle</label>');
            handleDiv.append(handleLabel);

            var suffixAndPrefixDiv = $("<div></div>");
            handleDiv.append(suffixAndPrefixDiv);

            //        var prefixLabel = $('<span style="display: inline-block"></span>');
            //        prefixLabel.text(APP.getPrefix() + "/");
            //        prefixLabel.append("&nbsp;");
            //        suffixAndPrefixDiv.append(prefixLabel);

            handleInput = $(
                '<input type="text" style="display: inline-block; width: auto" class="form-control" placeholder="Handle (optional)" />'
            );
            suffixAndPrefixDiv.append(handleInput);
        }

        function createSuffixInput() {
            var handleDiv = $('<div class="handleEditor"></div>');
            containerDiv.append(handleDiv);
            var handleLabel = $('<label class="control-label">Handle</label>');
            handleDiv.append(handleLabel);

            var suffixAndPrefixDiv = $("<div></div>");
            handleDiv.append(suffixAndPrefixDiv);

            var prefixLabel = $('<span style="display: inline-block"></span>');
            prefixLabel.text(APP.getPrefix() + "/");
            prefixLabel.append("&nbsp;");
            suffixAndPrefixDiv.append(prefixLabel);

            suffixInput = $(
                '<input type="text" style="display: inline-block; width: auto" class="form-control" placeholder="Suffix (optional)" />'
            );
            suffixAndPrefixDiv.append(suffixInput);
        }

        function onFormButtonClick() {
            editorDiv.show();
            payloadsEditorDiv.show();
            advancedDiv.hide();
            viewDOLink.show();
            viewFormLink.hide();
        }

        //    function onReferrersButtonClick() {
        //        var referrersQuery = "internal.pointsAt:" + objectId;
        //        APP.performSearchWidgetSearch(referrersQuery);
        //    }

        function onShowACL() {
            APP.getAclForCurrentObject(onGotAclSuccess);
        }

        function onShowVersions() {
            APP.getVersionsFor(objectId, onGotVersionsSuccess);
        }

        function onShowAdvanced() {
            advancedDiv.empty();
            fullCordraObjectViewer = new FullCordraObjectViewer(advancedDiv, contentPlusMeta);
        }

        function onShowRelationships() {
            showRelationshipsGraph(objectId);
        }

        function onGotVersionsSuccess(versions) {
            //if (versionsEditor) versionsEditor.destroy();
            versionsEditorDiv.empty();
            versionsEditor = new ObjectVersions(
                versionsEditorDiv,
                versions,
                objectId,
                allowEdits
            );
        }

        function onGotAclSuccess(res) {
            if (aclEditor) aclEditor.destroy();
            aclEditorDiv.empty();
            var uiConfig = APP.getUiConfig();
            aclEditor = new ObjectAclEditor(
                aclEditorDiv,
                res,
                objectId,
                uiConfig.aclUiSearchTypes,
                allowEdits
            );
        }

        function createEditJsonDiv() {
            editJsonDiv = $('<div class="editJsonEditor col-md-12" style="display:none;"></div>');
            editJsonDiv.attr("data-view", "");
            objectEditorDiv.append(editJsonDiv);

            var editJsonTextDiv = $('<div style="height: 500px; display:block; width:100%;"></div>');
            editJsonDiv.append(editJsonTextDiv);

            var container = editJsonTextDiv[0];
            var options = {
                ace: ace,
                theme: "ace/theme/textmate",
                mode: "code",
                modes: ["code", "tree"], // allowed modes
                onError: function (err) {
                    APP.notifications.alertError(err.toString());
                }
            };
            jsonEditorOnline = new JsonEditorOnline(container, options);
        }

        function onGotRelationshipsSuccess(res) {
            if (disabled) {
                // var showRelationshipsButton = $(
                //   '<li><a class="dropdown-item"><i class="fa fa-bezier-curve"></i>Visualize Relatives</a></li>'
                // );
                // otherActionsMenu.append(showRelationshipsButton);
                // showRelationshipsButton.on("click", onShowRelationshipsClick);
            }
        }

        function onGotRelationshipsError(res) {
            console.log(res);
        }

        function showRelationshipsGraph(objectId) {
            relationshipsGraphDiv.empty();
            //relationshipsGraphDiv.show();
            if (relationshipsGraph) relationshipsGraph.destroy();
            relationshipsGraph = new RelationshipsGraph(
                relationshipsGraphDiv,
                objectId
            );
        }

        function hideRelationshipsGraph() {
            if (relationshipsGraph) relationshipsGraph.destroy();
            relationshipsGraphDiv.empty();
            //relationshipsGraphDiv.hide();
            relationshipsGraph = null;
        }

        function resizeRelationshipsGraph() {
            if (relationshipsGraph) {
                relationshipsGraph.onResize();
            }
        }
        self.resizeRelationshipsGraph = resizeRelationshipsGraph;

        function onCloseClick() {
            APP.clearFragment();
        }

        function destroy() {
            if (aclEditor) aclEditor.destroy();
            hideRelationshipsGraph();
            editor.destroy();
            jsonEditorOnline.destroy();
        }
        self.destroy = destroy;

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

        function getJsonFromEditor() {
            var jsonObject = editor.getValue();
            ensureFileInputsAreEmptyString(jsonObject);
            return jsonObject;
        }
        self.getJsonFromEditor = getJsonFromEditor;

        function getContentPlusMeta() {
            var contentPlusMetaCopy = jQuery.extend(true, {}, contentPlusMeta);
            contentPlusMetaCopy.content = getJsonFromEditor();
            return contentPlusMetaCopy;
        }
        self.getContentPlusMeta = getContentPlusMeta;

        function getType() {
            return type;
        }
        self.getType = getType;

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

        function onEditForm() {
            var jsonObject = jsonEditorOnline.get();
            editor.setValue(jsonObject);
            editJsonDiv.attr("data-view", "FORM");
            APP.editCurrentObject();
            editJsonLink.show();
            editFormLink.hide();
            editorDiv.show();
            payloadsEditorDiv.show();
            editJsonDiv.hide();
        }

        function onEditJson() {
            var jsonObject = editor.getValue();
            jsonEditorOnline.set(jsonObject);
            editJsonDiv.attr("data-view", "JSON");
            editJsonLink.hide();
            editFormLink.show();
            editorDiv.hide();
            payloadsEditorDiv.hide();
            editJsonDiv.show();
        }

        function save() {
            saveButtonBottom
                .children(":first-child")
                .replaceWith('<i class="fa fa-spinner"></i>');
            saveButtonBottom
                .children(":nth-child(2)")
                .replaceWith("<span>Loading</span>");
            saveButton
                .children(":first-child")
                .replaceWith('<i class="fa fa-spinner"></i>');
            saveButton.children(":nth-child(2)").replaceWith("<span>Loading</span>");
            bottomProgressDiv.empty();
            bottomProgressBar = new UploadProgressBar(bottomProgressDiv);

            var cordraObject = buildCordraObject();
            if (objectId == null) {
                var handle = null;
                var suffix = null;
                if (APP.getUiConfig().allowUserToSpecifyHandleOnCreate) {
                    handle = handleInput.val();
                    if (handle === "") {
                        handle = null;
                    }
                } else if (APP.getUiConfig().allowUserToSpecifySuffixOnCreate) {
                    suffix = suffixInput.val();
                    if (suffix === "") {
                        suffix = null;
                    }
                }
                if (handle) cordraObject.id = handle;
                cordraObject.type = type;
                APP.createObject(cordraObject, suffix, onSaveError, progressCallback);
            } else {
                cordraObject.id = objectId;
                APP.saveObject(cordraObject, onSaveError, progressCallback);
            }
        }

        function progressCallback(event) {
            var fractionComplete = event.loaded / event.total;
            var percentComplete = Math.floor(fractionComplete * 100);
            bottomProgressBar.setStatus(percentComplete, event.loaded, event.total);
        }

        function onSaveError() {
            saveButtonBottom
                .children(":first-child")
                .replaceWith('<i class="fa fa-save"></i>');
            saveButtonBottom
                .children(":nth-child(2)")
                .replaceWith("<span>Save</span>");
            saveButton
                .children(":first-child")
                .replaceWith('<i class="fa fa-save"></i>');
            saveButton.children(":nth-child(2)").replaceWith("<span>Save</span>");
            bottomProgressDiv.empty();
        }

        function onDelete() {
            APP.deleteObject(objectId);
        }

        function onRevert() {
            APP.resolveHandle(objectId);
        }

        function onClone() {
            APP.cloneCurrentObject();
        }

        //    function onShowRelationshipsClick() {
        //      APP.showRelationshipsGraph(objectId);
        //    }

        function buildCordraObject() {
            //determine if we need to consider changes from JSON editor
            var isJSONEditorInView = editJsonDiv.attr("data-view") === "JSON";
            if (isJSONEditorInView) {
                var jsonObject = jsonEditorOnline.get();
                editor.setValue(jsonObject);
            }

            var cordraObject = {
                content: getJsonFromEditor()
            };
            payloadsEditor.appendCordraObject(cordraObject);
            return cordraObject;
        }

        function getObjectId() {
            return objectId;
        }
        self.getObjectId = getObjectId;

        constructor();
    }
    window.ObjectEditor = ObjectEditor;
})();
