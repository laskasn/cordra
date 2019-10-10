function HandleMintingConfigEditor(
    containerDiv,
    handleMintingConfig,
    disabled
) {
    var self = this;
    var haiInput = null;
    var prefixInput = null;
    var baseUriInput = null;
    var advancedToggleButton = null;
    var basicToggleButton = null;
    var editor = null;
    var editorDiv = null;
    var saveButton = null;

    var scriptEditorDiv = null;
    var scriptEditor = null;

    var form = null;
    var isAdvanced = false;

    var updateWell = null;
    var updateProgressDiv = null;
    var updateProgressWidget = null;

    function constructor() {
        var headerRow = $('<div class="row object-header"></div>');
        containerDiv.append(headerRow);

        var objectHeader = $('<div class="heading col-md-6"></div>');
        var objectIdHeading = $('<h3 class="editorTitle">Handle Records</h3>');
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

        var viewsDropDownButtonGroup = $('<div class="btn-group"></div>');
        toolBarDiv.append(viewsDropDownButtonGroup);

        var viewsDropDownButton = $(
            '<button type="button" class="btn btn-primary btn-sm dropdown-toggle" data-toggle="dropdown"><i class="fa fa-eye"></i>Views...<span class="caret"></span></button>'
        );
        viewsDropDownButtonGroup.append(viewsDropDownButton);

        var viewDropDownMenu = $('<ul class="dropdown-menu"></ul>');
        viewsDropDownButtonGroup.append(viewDropDownMenu);

        var basicToggleButton = $(
            '<li><a class="dropdown-item"><i class="fa fa-cube"></i>Default</a></li>'
        );
        viewDropDownMenu.append(basicToggleButton);
        basicToggleButton.on("click", showBasic);

        var advancedToggleButton = $(
            '<li><a class="dropdown-item"><i class="fa fa-stream"></i>JSON</a></li>'
        );
        viewDropDownMenu.append(advancedToggleButton);
        advancedToggleButton.on("click", showAdvanced);

        var description = $(
            "<p>This structure lets you configure Cordra to create handle records for each new object created. " +
            "Note: if you enable handle resolution using cordra-client-handle-storage at your handle server, " +
            "you should not also configure Cordra to create handle records.  You may still want to set the " +
            "prefix value, which determines which prefix Cordra will use by default for new objects.</p>"
        );
        containerDiv.append(description);
        description = $(
            "<p>To have Cordra create handle records, you must configure either a base URI (ending with a slash) " +
            "or JavaScript, as described in the Cordra technical manual.  If using a base URI (the common case), " +
            "the handle records consist of a value redirecting handle resolvers to this Cordra's API and/or user interface. " +
            "Specify the base URI (ending with a slash) where Cordra is accessible in the Internet for this configuration to take effect.</p>"
        );
        containerDiv.append(description);
        description = $(
            "<p>Handle creation also requires configuring a handle admin identity, generally a handle like 20.500.123/repo. " +
            "The handle record for the handle admin identity must contain a public key corresponding to the privatekey in the Cordra data directory. " +
            "The handle admin identity must also be authorized at the handle server where handles will be created. " +
            "This should be preconfigured for a preconfigured Cordra download.</p>"
        );
        containerDiv.append(description);

        form = $('<form class="form-horizontal col-md-12"></form>');
        containerDiv.append(form);

        var group = $('<div class="form-group"></div>');
        form.append(group);

        var prefixLabel = $(
            '<label for="prefixInput" class="col-sm-2 control-label">Prefix</label>'
        );
        group.append(prefixLabel);
        var prefixDiv = $('<div class="col-sm-10"></div>');
        group.append(prefixDiv);
        prefixInput = $(
            '<input class="form-control" id="prefixInput" placeholder="Prefix"></input>'
        );
        prefixDiv.append(prefixInput);
        prefixInput.val(handleMintingConfig.prefix);

        var haiLabel = $(
            '<label for="haiInput" class="col-sm-2 control-label">Handle Admin Identity</label>'
        );
        group.append(haiLabel);
        var haiDiv = $('<div class="col-sm-10"></div>');
        group.append(haiDiv);
        haiInput = $(
            '<input class="form-control" id="haiInput" placeholder="Handle Admin Identity"></input>'
        );
        haiDiv.append(haiInput);
        haiInput.val(handleMintingConfig.handleAdminIdentity);

        var label = $(
            '<label for="baseUriInput" class="col-sm-2 control-label">Base URI</label>'
        );
        group.append(label);

        var div = $('<div class="col-sm-10"></div>');
        group.append(div);

        baseUriInput = $(
            '<input class="form-control" id="baseUriInput" placeholder="Base URI"></input>'
        );
        div.append(baseUriInput);
        baseUriInput.val(handleMintingConfig.baseUri);

        var jsLabel = $('<label class="col-sm-2 control-label">JavaScript</label>');
        group.append(jsLabel);

        var scriptEditorDivContainer = $('<div class="col-sm-10"/>');
        haiInput.css("margin-bottom", "10px");
        prefixInput.css("margin-bottom", "10px");
        baseUriInput.css("margin-bottom", "10px");
        scriptEditorDiv = $(
            '<div id="handleMintingJavaScriptEditor" class="ace_editor"></div>'
        );
        scriptEditorDivContainer.append(scriptEditorDiv);
        group.append(scriptEditorDivContainer);

        scriptEditor = ace.edit("handleMintingJavaScriptEditor");
        scriptEditor.setTheme("ace/theme/textmate");
        scriptEditor.getSession().setMode("ace/mode/javascript");
        scriptEditor.setOptions({
            maxLines: Infinity,
            minLines: 10
        });
        scriptEditor.$blockScrolling = Infinity;
        if (handleMintingConfig.javascript) {
            scriptEditor.setValue(handleMintingConfig.javascript, -1);
        }

        editorDiv = $('<div style="height:500px; display:none; col-md-12"></div>');
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
        editor = new JsonEditorOnline(container, options, handleMintingConfig);
        if (disabled) {
            APP.disableJsonEditorOnline(editor);
        }

        updateWell = $('<div class="col-md-12" style="margin-top: 50px"></div>');
        containerDiv.append(updateWell);

        if (disabled) {
            updateWell.hide();
        }
        var headerRow = $('<div class="row object-header"></div>');
        updateWell.append(headerRow);

        var objectHeader = $('<div class="heading col-md-6"></div>');
        var objectIdHeading = $(
            '<h3 class="editorTitle">Update Handles Records </h3>'
        );
        objectHeader.append(objectIdHeading);
        headerRow.append(objectHeader);

        updateToolBarDiv = $(
            '<div class="object-editor-toolbar col-md-6 pull-right"></div>'
        );
        headerRow.append(updateToolBarDiv);

        var updateHandlesButton = $(
            '<button class="btn btn-sm btn-primary"><i class="fa fa-pen"></i></button>'
        );
        updateToolBarDiv.append(updateHandlesButton);
        updateHandlesButton.on("click", updateHandles);

        var updateHandlesButtonSpan = $("<span></span>");
        updateHandlesButton.append(updateHandlesButtonSpan);
        updateHandlesButtonSpan.text("Update All Handles");

        updateProgressDiv = $('<div style="display:none"></div>');
        updateWell.append(updateProgressDiv);

        updateProgressWidget = new UpdateAllHandlesStatusWidget(updateProgressDiv);

        var updateDescription = $(
            "<p>Once the configuration above is updated, click on the Update All Handles button to update all existing handle records pertaining to this Cordra instance according to the above configuration.</p>"
        );
        updateWell.append(updateDescription);

        if (!disabled) {
            pollUpdateStatus();
        }
    }

    function onCloseClick() {
        APP.clearFragment();
    }

    function enable() {
        saveButton.show();
        APP.enableJsonEditorOnline(editor);
        updateWell.show();
        pollUpdateStatus();
    }
    self.enable = enable;

    function disable() {
        saveButton.hide();
        updateWell.hide();
        APP.disableJsonEditorOnline(editor);
    }
    self.disable = disable;

    function destroy() {
        editor.destroy();
        scriptEditor.destroy();
    }
    self.destroy = destroy;

    function showAdvanced() {
        setEditorValueBasedOnBasic();
        form.hide();
        editorDiv.show();
        isAdvanced = true;
    }

    function showBasic() {
        var handleMintingConfigUpdate = editor.get();
        haiInput.val(handleMintingConfigUpdate.handleAdminIdentity);
        prefixInput.val(handleMintingConfigUpdate.prefix);
        baseUriInput.val(handleMintingConfigUpdate.baseUri);
        if (handleMintingConfigUpdate.javascript) {
            scriptEditor.setValue(handleMintingConfigUpdate.javascript, -1);
        } else {
            scriptEditor.setValue("", -1);
        }
        form.show();
        editorDiv.hide();
        isAdvanced = false;
    }

    function setEditorValueBasedOnBasic() {
        var handleMintingConfigUpdate = editor.get();
        var newHai = haiInput.val();
        if (newHai) {
            handleMintingConfigUpdate.handleAdminIdentity = newHai;
        } else {
            delete handleMintingConfigUpdate.handleAdminIdentity;
        }
        var newPrefix = prefixInput.val();
        if (newPrefix) {
            handleMintingConfigUpdate.prefix = newPrefix;
        } else {
            delete handleMintingConfigUpdate.prefix;
        }
        var newBaseUri = baseUriInput.val();
        if (newBaseUri) {
            handleMintingConfigUpdate.baseUri = newBaseUri;
        } else {
            delete handleMintingConfigUpdate.baseUri;
        }
        var js = scriptEditor.getValue();
        if (js) {
            handleMintingConfigUpdate.javascript = js;
        } else {
            delete handleMintingConfigUpdate.javascript;
        }
        editor.set(handleMintingConfigUpdate);
    }

    function save() {
        if (!isAdvanced) {
            setEditorValueBasedOnBasic();
        }
        APP.saveHandleMintingConfig(editor.get());
    }

    function updateHandles() {
        var dialog = new ModalYesNoDialog(
            "Updating all handles can take a long time. Are you sure you want to start this process?",
            yesUpdateHandles,
            noUpdateHandles,
            self
        );
        dialog.show();
    }

    function yesUpdateHandles() {
        save();
        updateProgressWidget.clear();
        APP.updateAllHandles(pollUpdateStatus);
    }

    function pollUpdateStatus() {
        APP.getHandleUpdateStatus(function (status) {
            if (!status.inProgress && status.total === 0) return;
            updateProgressWidget.setStatus(status);
            updateProgressDiv.show();
            if (status.inProgress) {
                setTimeout(pollUpdateStatus, 100);
            } else {
                //complete
            }
        });
    }

    function noUpdateHandles() {
        //no-op
    }

    constructor();
}
