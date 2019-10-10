function SchemasToolBar(
    containerDiv,
    schemasParam,
    disabledParam,
    createNewSchema,
    showSchemaEditorFor
) {
    var self = this;
    var newSchemaObjectTypeInput = null;
    var fileToLoad = null;
    var deleteCheckbox = null;

    var addSchemaDiv = null;
    var loadFileDiv = null;
    var templates = null;
    var templateSelect = null;
    var exampleJavaScript = null;
    var jsCheckBox = null;
    var table = null;
    var disabled = disabledParam;
    var schemaToolBar = null;
    var schemasTotalHeader = null;
    var schemasList = null;

    function constructor() {
        fetch("schemaTemplates")
            .then(getResponseJson)
            .then(constructorContinuation);
    }

    function getResponseJson(response) {
        return response.json();
    }

    function constructorContinuation(templatesResponse) {
        templates = templatesResponse.templates;
        exampleJavaScript = templatesResponse.exampleJavaScript;

        schemaToolBar = containerDiv.find("#schema-tool-bar");

        if (disabled) {
            schemaToolBar.hide();
        } else {
            schemaToolBar.show();
        }

        schemasTotalHeader = containerDiv.find("#schemas-total-header");

        schemasList = containerDiv.find("#schemas-list");
        buildSchemaAdder();
        buildFileLoader();
        buildSchemasTable(schemasParam);
    }

    function buildSchemasTable(schemas) {
        var schemaNamesArray = getSchemaNames(schemas);
        var numberOfSchemas = schemaNamesArray.length;
        var totalNumberOfSchemas = $("<p></p>");
        var text = "";

        if (numberOfSchemas === 0) {
            text = "There are no types to show.";
        } else {
            if (numberOfSchemas > 1) {
                text =
                    "There are " +
                    numberOfSchemas +
                    " types in the system, the names of which are shown below.";
            } else {
                text = "There is 1 type in the system.";
            }
            text +=
                " Click on a name below to view or edit the schema and/or JavaScript associated with that type.";
        }

        totalNumberOfSchemas.text(text);

        schemasTotalHeader.append(totalNumberOfSchemas);

        var header = $('<div class="header col-md-12"></div>');
        schemasList.append(header);

        var row = $('<div class="col-md-12 header-content"></div>');
        schemasList.append(row);
        var name = $('<div class="col-md-12">Type Name</div>');
        row.append(name);
        addSchemas(schemaNamesArray);
    }

    function addSchemas(schemaNamesArray) {
        for (var i = 0; i < schemaNamesArray.length; i++) {
            var objectType = schemaNamesArray[i];
            var row = $('<div class="col-md-12 content"></div>');
            schemasList.append(row);
            row.data("objectType", objectType);
            var objectType = $(
                '<div class="schema-object col-md-12">' + objectType + "</div>"
            );
            row.append(objectType);
            row.on("click", onSchemaRowClicked);
        }
    }

    function getSchemaNames(schemas) {
        var schemaNamesArray = [];
        for (var objectType in schemas) {
            if ("Schema" === objectType || "CordraDesign" === objectType) {
                continue;
            } else {
                schemaNamesArray.push(objectType);
            }
        }
        schemaNamesArray.sort();
        return schemaNamesArray;
    }

    function onSchemaRowClicked(e) {
        setAllRowsInactive();
        var clickedSchemaLi = $(this);
        clickedSchemaLi.addClass("info");
        var objectType = clickedSchemaLi.data("objectType");
        showSchemaEditorFor(objectType, disabled);
        $("html, body").animate(
            {
                scrollTop: $("#schema-editor").offset().top
            },
            1000
        );
    }

    function setAllRowsInactive() {
        schemasList.find(".info").each(function () {
            $(this).removeClass("info");
        });
    }

    function buildSchemaAdder() {
        var addButton = $(
            '<button class="btn btn-sm btn-primary"><i class="fa fa-plus"></i></button>'
        );
        schemaToolBar.append(addButton);
        addButton.on("click", onAddClicked);

        var addButtonSpan = $("<span></span>");
        addButton.append(addButtonSpan);
        addButtonSpan.text("Add");

        buildAddSchemaDialog();
    }

    function buildAddSchemaDialog() {
        addSchemaDiv = $('<div class="modal fade" tabindex="-1"></div>');

        var modalDialog = $('<div class="modal-dialog"></div>');
        addSchemaDiv.append(modalDialog);

        var modalContent = $('<div class="modal-content"></div>');
        modalDialog.append(modalContent);

        var modalHeader = $('<div class="modal-header"></div>');
        modalContent.append(modalHeader);
        //        var closeButton = $('<button type="button" class="close" data-dismiss="modal" aria-hidden="true">&times;</button>');
        //        modalHeader.append(closeButton);

        var title = $('<h4 class="modal-title">Add Type</h4>');
        modalHeader.append(title);

        var modalBody = $('<div class="modal-body"></div>');
        modalContent.append(modalBody);

        var addForm = $('<form class="form-horizontal" role="form"></form>');
        addForm.on("submit", function (e) {
            return false;
        });
        modalBody.append(addForm);

        var nameGroup = $('<div class="form-group"></div>');
        addForm.append(nameGroup);

        var nameLabel = $(
            '<label for="newSchemaNameInput" class="col-sm-2 control-label">Name</label>'
        );
        nameGroup.append(nameLabel);

        var nameCol = $('<div class="col-sm-10">');
        nameGroup.append(nameCol);

        newSchemaObjectTypeInput = $(
            '<input id="newSchemaNameInput" type="text" class="form-control input-sm" placeholder="Type name">'
        );
        nameCol.append(newSchemaObjectTypeInput);

        var templateGroup = $('<div class="form-group"></div>');
        addForm.append(templateGroup);

        var templateLabel = $(
            '<label for="templateSelect" class="col-sm-2 control-label">Template</label>'
        );
        templateGroup.append(templateLabel);

        var templateCol = $('<div class="col-sm-10">');
        templateGroup.append(templateCol);

        templateSelect = $(
            '<select id="templateSelect" class="form-control"></select>'
        );
        templateCol.append(templateSelect);

        for (var templateName in templates) {
            var option = $(
                '<option value="' + templateName + '">' + templateName + "</option>"
            );
            templateSelect.append(option);
        }

        var jsCheckGroup = $('<div class="form-group"></div>');
        addForm.append(jsCheckGroup);

        var jsCheckColumn = $('<div class="col-sm-offset-2 col-sm-10">');
        jsCheckGroup.append(jsCheckColumn);

        var jsCheckDiv = $('<div class="checkbox">');
        jsCheckColumn.append(jsCheckDiv);

        var jsLabel = $("<label>Include example JavaScript</label>");
        jsCheckDiv.append(jsLabel);

        jsCheckBox = $('<input type="checkbox">');
        jsLabel.prepend(jsCheckBox);

        addSchemaDiv.on("shown.bs.modal", function () {
            newSchemaObjectTypeInput.trigger("focus");
        });

        var modalFooter = $('<div class="modal-footer"></div>');
        modalContent.append(modalFooter);

        var cancelButton = $(
            '<button type="button" class="btn btn-sm btn-warning" style="min-width: 130px;" >Cancel</button>'
        );
        modalFooter.append(cancelButton);
        cancelButton.on("click", onCancelButtonClick);

        var addDoneButton = $(
            '<button type="button" class="btn btn-sm btn-primary" style="min-width: 130px;" >Add</button>'
        );
        modalFooter.append(addDoneButton);
        addDoneButton.on("click", onAddDoneButtonClick);
    }

    function buildFileLoader() {
        var fileLoaderButton = $(
            '<button id="loadFromFileButton" class="btn btn-sm btn-secondary"><i class="fa fa-file"></i></button>'
        );
        var progressIcon = $(
            '<img id="objectLoadingGif" src="/img/load.gif" style="display: none; padding-left: 5px;" />'
        );
        schemaToolBar.append(fileLoaderButton);
        schemaToolBar.append(progressIcon);

        var fileLoaderButtonSpan = $("<span></span>");
        fileLoaderButton.append(fileLoaderButtonSpan);
        fileLoaderButtonSpan.text("Load from File");

        fileLoaderButton.on("click", onFileLoadClicked);
        buildFileLoaderDialog();
    }

    function buildFileLoaderDialog() {
        loadFileDiv = $('<div class="modal fade" tabindex="-1"></div>');

        var modalDialog = $('<div class="modal-dialog"></div>');
        loadFileDiv.append(modalDialog);

        var modalContent = $('<div class="modal-content"></div>');
        modalDialog.append(modalContent);

        var modalHeader = $('<div class="modal-header"></div>');
        modalContent.append(modalHeader);

        var title = $('<h4 class="modal-title">Load files</h4>');
        modalHeader.append(title);

        var subTitle = $(
            "<h5>Load files that correspond to types (schemas and JavaScript). You may also load digital objects that are not types.</h5>"
        );
        title.append(subTitle);

        var modalBody = $('<div class="modal-body"></div>');
        modalContent.append(modalBody);

        var modalForm = $('<form class="form-horizontal" role="form"></form>');
        modalForm.on("submit", function () {
            return false;
        });
        modalBody.append(modalForm);

        var fileGroup = $('<div class="form-group"></div>');
        modalForm.append(fileGroup);

        var nameLabel = $('<label class="col-sm-2 control-label">File</label>');
        fileGroup.append(nameLabel);

        var nameCol = $('<div class="col-sm-10">');
        fileGroup.append(nameCol);

        var selectedName = $('<p id="uploadFileName" class="form-control"></p>');
        nameCol.append(selectedName);

        var selectedLabel = $(
            '<label class="btn btn-sm btn-primary">Browse...</label>'
        );
        nameCol.append(selectedLabel);

        fileToLoad = $(
            '<input id="fileToLoad" type="file" style="display: none;">'
        );
        fileToLoad.on("change", function () {
            var file = $(this).prop("files")[0];
            if (file) {
                $("#uploadFileName").text(file.name);
            }
        });
        selectedLabel.append(fileToLoad);

        var deleteGroup = $('<div class="form-group"></div>');
        modalForm.append(deleteGroup);

        var deleteLabel = $(
            '<label for="deleteCheckbox" class="col-sm-2 control-label"> </label>'
        );
        deleteGroup.append(deleteLabel);

        var deleteCol = $('<div class="col-sm-10">');
        deleteGroup.append(deleteCol);

        var deleteCheckLabel = $('<label class="control-label">');
        deleteCol.append(deleteCheckLabel);

        deleteCheckbox = $(
            '<input id="deleteCheckbox" type="checkbox" class="input">'
        );
        deleteCheckLabel.append(deleteCheckbox);

        var deleteCheckSpan = $(
            '<span class="control-label"> Delete ALL digital objects including types? </span>'
        );
        deleteCheckLabel.append(deleteCheckSpan);

        loadFileDiv.on("shown.bs.modal", function () {
            fileToLoad.trigger("focus");
        });

        var modalFooter = $('<div class="modal-footer"></div>');
        modalContent.append(modalFooter);

        var cancelButton = $(
            '<button type="button" class="btn btn-sm btn-warning" style="min-width: 130px;" >Cancel</button>'
        );
        modalFooter.append(cancelButton);
        cancelButton.on("click", onLoadCancelButtonClick);

        var loadDoneButton = $(
            '<button type="button" class="btn btn-sm btn-primary" style="min-width: 130px;" >Load</button>'
        );
        modalFooter.append(loadDoneButton);
        loadDoneButton.on("click", onLoadDoneButtonClick);
    }

    function enable() {
        disabled = false;
        if (schemaToolBar) schemaToolBar.show();
    }
    self.enable = enable;

    function disable() {
        disabled = true;
        if (schemaToolBar) schemaToolBar.hide();
    }
    self.disable = disable;

    function onCancelButtonClick(e) {
        e.preventDefault();
        newSchemaObjectTypeInput.val("");
        addSchemaDiv.modal("hide");
    }

    function onAddDoneButtonClick(e) {
        e.preventDefault();
        var objectType = newSchemaObjectTypeInput.val();
        if (objectType === "") {
            APP.notifications.alertError("Type name is a required.");
        } else {
            var templateName = templateSelect.val();
            var template = templates[templateName];
            var copyOfTemplate = {};
            $.extend(copyOfTemplate, template);

            var isIncludeExampleJs = jsCheckBox.is(":checked");
            var js = null;
            if (isIncludeExampleJs) {
                js = exampleJavaScript;
            }
            createNewSchema(objectType, copyOfTemplate, js);
            newSchemaObjectTypeInput.val("");
            addSchemaDiv.modal("hide");
        }
    }

    function onAddClicked(e) {
        e.preventDefault();
        addSchemaDiv.modal({ keyboard: true });
    }

    function onLoadCancelButtonClick(e) {
        e.preventDefault();
        fileToLoad.val(null);
        deleteCheckbox.prop("checked", false);
        loadFileDiv.modal("hide");
    }

    function onLoadDoneButtonClick(e) {
        e.preventDefault();
        var file = fileToLoad.prop("files")[0];
        var doDelete = deleteCheckbox.is(":checked");
        if (!file) {
            APP.notifications.alertError(
                'Please select a file before clicking the "Load" button.'
            );
        } else {
            var reader = new FileReader();
            reader.readAsText(file);
            reader.onload = function () {
                APP.loadObjects(JSON.parse(reader.result), doDelete);
                fileToLoad.val(null);
                deleteCheckbox.prop("checked", false);
                loadFileDiv.modal("hide");
            };
        }
    }

    function onFileLoadClicked(e) {
        APP.closeSchemaEditor();
        e.preventDefault();
        loadFileDiv.modal({ keyboard: true });
    }

    function refresh(newSchemas) {
        if (newSchemas) {
            refreshSchemas(newSchemas);
        }
    }
    self.refresh = refresh;

    function refreshSchemas(schemas) {
        schemasList.empty();
        schemasTotalHeader.empty();
        buildSchemasTable(schemas);
    }

    constructor();
}
