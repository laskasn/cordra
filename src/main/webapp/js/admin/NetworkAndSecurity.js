function NetworkAndSecurity(containerDiv, disabled) {
    var self = this;
    var oldAdminPasswordInput = null;
    var newAdminPasswordInput = null;
    var unauthenticatedDiv = null;
    var authenitcatedContainerDiv = null;
    var saveButton = null;
    var newKeysButton = null;

    function constructor() {
        var headerRow = $('<div class="row object-header"></div>');
        containerDiv.append(headerRow);

        var objectHeader = $('<div class="heading col-md-6"></div>');
        var objectIdHeading = $('<h3 class="editorTitle">Security</h3>');
        objectHeader.append(objectIdHeading);
        headerRow.append(objectHeader);

        toolBarDiv = $(
            '<div class="object-editor-toolbar col-md-6 pull-right"></div>'
        );
        headerRow.append(toolBarDiv);

        saveButton = $(
            '<button class="btn btn-sm btn-primary pull-right"><i class="fa fa-save"></i></button>'
        );
        toolBarDiv.append(saveButton);
        saveButton.on("click", saveAdminPassword);

        var saveButtonSpan = $("<span></span>");
        saveButton.append(saveButtonSpan);
        saveButtonSpan.text("Save");

        unauthenticatedDiv = $(
            '<div style="display:none" class="col-md-12"></div>'
        );
        containerDiv.append(unauthenticatedDiv);

        var notAdminMessage = $(
            "<p>You need to be authenticated as admin in order to modify these settings.</p>"
        );
        unauthenticatedDiv.append(notAdminMessage);

        authenitcatedContainerDiv = $(
            '<div style="display:none" class="col-md-12"></div>'
        );
        containerDiv.append(authenitcatedContainerDiv);
        buildAdminPasswordEditor();

        if (!disabled) {
            authenitcatedContainerDiv.show();
            saveButton.show();
        } else {
            unauthenticatedDiv.show();
            saveButton.hide();
        }
    }

    function onCloseClick() {
        APP.clearFragment();
    }

    function enable() {
        authenitcatedContainerDiv.show();
        saveButton.show();
        unauthenticatedDiv.hide();
    }
    self.enable = enable;

    function disable() {
        authenitcatedContainerDiv.hide();
        saveButton.hide();
        unauthenticatedDiv.show();
    }
    self.disable = disable;

    function buildAdminPasswordEditor() {
        var adminPasswordDiv = $("<div></div>");
        authenitcatedContainerDiv.append(adminPasswordDiv);

        var form = $('<form class="form-horizontal"></form>');
        adminPasswordDiv.append(form);

        var oldPassGroup = $('<div class="form-group row"></div>');
        form.append(oldPassGroup);

        var oldPassLabel = $(
            '<label for="oldAdminPasswordInput" class="col-sm-2 control-label">Old Admin Password</label>'
        );
        oldPassGroup.append(oldPassLabel);

        var oldPassDiv = $('<div class="col-sm-10"></div>');
        oldPassGroup.append(oldPassDiv);

        oldAdminPasswordInput = $(
            '<input type="password" class="form-control" id="oldAdminPasswordInput" ></input>'
        );
        oldPassDiv.append(oldAdminPasswordInput);

        var newPassGroup = $('<div class="form-group row"></div>');
        form.append(newPassGroup);

        var newPassLabel = $(
            '<label for="adminPasswordInput" class="col-sm-2 control-label">Admin Password</label>'
        );
        newPassGroup.append(newPassLabel);

        var newPassDiv = $('<div class="col-sm-10"></div>');
        newPassGroup.append(newPassDiv);

        newAdminPasswordInput = $(
            '<input type="password" class="form-control" id="adminPasswordInput" ></input>'
        );
        newPassDiv.append(newAdminPasswordInput);
        newAdminPasswordInput.val();
    }

    function saveAdminPassword() {
        var oldPassword = oldAdminPasswordInput.val();
        var newPassword = newAdminPasswordInput.val();
        oldAdminPasswordInput.val("");
        newAdminPasswordInput.val("");
        var options = {
            username: "admin",
            password: oldPassword
        };
        APP.saveAdminPassword(newPassword, options);
    }

    constructor();
}
