function AuthenticatorWidget(
    containerDiv,
    onAuthenticationStateChangeCallback,
    isActiveSession,
    usernameParam,
    userIdParam,
    typesPermittedToCreateParam,
    allowLogin
) {
    var self = this;

    var signInButton = null;
    var signOutLink = null;
    var authenticatedLabel = null;

    var authenticatedDiv = null;
    var authenticateDiv = null;

    var privateKeyAuthenticateDiv = null;
    var secretKeyAuthenticateDiv = null;

    var handleInput = null;

    var fileReaderDiv = null;
    var fileReader = null;
    var privateKeyPassPhraseInput = null;
    var privateKeyAuthenticateButton = null;
    var privateKeyBytes = null;
    var isEncryptedKey = false;

    var usernameInput = null;
    var passwordInput = null;

    var authenticateButton = null;
    var newPasswordContainer = null;
    var newPasswordInput = null;

    var userInfo = {
        userId: null,
        username: null
    };

    var HEARTBEAT_TIMEOUT = 1000 * 30;

    var heartbeatTimer = null;
    var isHeartbeatInFlight = false;
    var ignoreNextHeartbeatResponse = false;
    var typesPermittedToCreate = typesPermittedToCreateParam;

    var dialogNotifications = null;

    function constructor() {
        if (allowLogin) {
            signInButton = $(
                '<button type="button" class="btn btn-primary btn-sm"><i class="fa fa-user"></i>Sign In</button>'
            );
            signInButton.on("click", onSignInClick);
        } else {
            signInButton = $(
                '<button type="button" class="btn btn-primary btn-sm" disabled></i>Login only allowed over HTTPS</button>'
            );
        }
        containerDiv.append(signInButton);

        authenticatedDiv = $(
            '<div class="authenticatedDiv" style="display:none;"></div>'
        );
        containerDiv.append(authenticatedDiv);
        var signOutForm = $('<form class="form-inline"></form>');
        authenticatedDiv.append(signOutForm);
        var signOutGroup = $('<div class="control-group"></div>');
        signOutForm.append(signOutGroup);

        authenticatedLabel = $(
            '<span class="help-inline" style="color:white; cursor: pointer;"></span>'
        );
        signOutGroup.append(authenticatedLabel);
        signOutGroup.append(" ");
        signOutLink = $('<a class="sign-out-link">[Sign Out]</a>');
        signOutGroup.append(signOutLink);
        signOutLink.on("click", onSignOutLinkClick);

        buildAuthenticateDialog();
        if (isActiveSession) {
            userInfo.userId = userIdParam;
            userInfo.username = usernameParam;
            setAuthenticated();
        }
    }

    function buildAuthenticateDialog() {
        authenticateDiv = $('<div class="modal fade" tabindex="-1"></div>');

        var modalDialog = $(
            '<div class="modal-dialog" style="width: 428px;"></div>'
        );
        authenticateDiv.append(modalDialog);

        var modalContent = $('<div class="modal-content"></div>');
        modalDialog.append(modalContent);

        var modalHeader = $('<div class="modal-header"></div>');
        modalContent.append(modalHeader);
        var closeButton = $(
            '<button type="button" class="close" data-dismiss="modal" aria-hidden="true">&times;</button>'
        );
        modalHeader.append(closeButton);

        var title = $('<h4 class="modal-title">Authenticate</h4>');
        modalHeader.append(title);

        var modalBody = $('<div class="modal-body"></div>');
        modalContent.append(modalBody);

        var dialogNotificationsDiv = $("<div></div>");
        modalContent.append(dialogNotificationsDiv);
        dialogNotifications = new Notifications(dialogNotificationsDiv);

        var authenticateModeSelectDiv = $(
            '<div class="tabbable tab authenticateModeSelect"></div>'
        );
        modalBody.append(authenticateModeSelectDiv);
        var tabNav = $(
            '<ul id="authTab" class="nav nav-tabs" style="margin-bottom: 5px;">'
        );
        var privateKeyNav = $(
            '<li><a href="#privateKeyAuth" data-toggle="tab">Private Key</a></li>'
        );
        var secretKeyNav = $(
            '<li class="active"><a href="#secretKeyAuth" data-toggle="tab">Password</a></li>'
        );
        tabNav.append(secretKeyNav);
        tabNav.append(privateKeyNav);
        authenticateModeSelectDiv.append(tabNav);

        var tabContentDiv = $(
            '<div id="authenticateTabContent" class="tab-content">'
        );
        authenticateModeSelectDiv.append(tabContentDiv);

        privateKeyAuthenticateDiv = $(
            '<div class="tab-pane fade " id="privateKeyAuth"></div>'
        );
        secretKeyAuthenticateDiv = $(
            '<div class="tab-pane fade in active" id="secretKeyAuth"></div>'
        );

        tabContentDiv.append(secretKeyAuthenticateDiv);
        tabContentDiv.append(privateKeyAuthenticateDiv);

        //private key
        var privateKeyAuthenticateForm = $("<form>");
        privateKeyAuthenticateDiv.append(privateKeyAuthenticateForm);
        var handleFormGroup = $('<div class="form-group">');
        privateKeyAuthenticateForm.append(handleFormGroup);
        handleInput = $(
            '<input id="authHandleInput" type="text" class="form-control input-sm" style="min-width: 150px;" placeholder="Username/Handle">'
        );
        handleFormGroup.append(handleInput);

        var keyFileFormGroup = $('<div class="form-group">');
        privateKeyAuthenticateForm.append(keyFileFormGroup);
        fileReaderDiv = $('<div class="form-inline"></div>');
        keyFileFormGroup.append(fileReaderDiv);
        fileReader = new ClientSideFileWidget(
            fileReaderDiv,
            onPrivateKeySelected,
            false,
            "Select private key"
        );
        keyFileFormGroup.append(" ");

        var decoyTextInput = $('<input type="text" style="display:none"/>');
        fileReaderDiv.append(decoyTextInput);
        privateKeyPassPhraseInput = $(
            '<input type="password" class="form-control input-sm" style="display:none;" placeholder="Passphrase">'
        );
        fileReaderDiv.append(" ");
        fileReaderDiv.append(privateKeyPassPhraseInput);
        privateKeyPassPhraseInput.on("keypress", function (event) {
            if (event.which === 13) {
                event.preventDefault();
                onPrivateKeyAuthenticateButtonClick();
            }
        });

        privateKeyAuthenticateButton = $(
            '<button type="button" class="btn btn-sm btn-primary" style="min-width: 130px;" data-loading-text="Authenticating...">Authenticate</button>'
        );
        privateKeyAuthenticateForm.append(privateKeyAuthenticateButton);
        privateKeyAuthenticateButton.on(
            "click",
            onPrivateKeyAuthenticateButtonClick
        );

        //username and password
        var passwordAuthenticateForm = $("<form>");
        secretKeyAuthenticateDiv.append(passwordAuthenticateForm);
        var usernameFormGroup = $('<div class="form-group">');
        passwordAuthenticateForm.append(usernameFormGroup);
        usernameInput = $(
            '<input type="text" class="form-control input-sm username-input" style="min-width: 150px;" placeholder="Username">'
        );
        usernameFormGroup.append(usernameInput);

        newPasswordContainer = $('<form style="display:none;"></form>');
        modalBody.append(newPasswordContainer);
        var newPasswordForGroup = $('<div class="form-group"></div>');
        newPasswordContainer.append(newPasswordForGroup);
        var newPasswordMessage = $(
            "<label>Server requires a new password be set.</label>"
        );
        newPasswordForGroup.append(newPasswordMessage);
        newPasswordInput = $(
            '<input type="password" class="form-control input-sm" placeholder="New Password">'
        );
        newPasswordForGroup.append(newPasswordInput);

        authenticateDiv.on("shown.bs.modal", function () {
            usernameInput.trigger("focus");
        });

        var passwordFormGroup = $('<div class="form-group">');
        passwordAuthenticateForm.append(passwordFormGroup);

        passwordInput = $(
            '<input type="password" class="form-control input-sm" placeholder="Password">'
        );
        passwordInput.on("keypress", function (event) {
            if (event.which === 13) {
                event.preventDefault();
                onAuthenticateButtonClick(event);
            }
        });
        passwordFormGroup.append(passwordInput);
        authenticateButton = $(
            '<button type="button" class="btn btn-sm btn-primary" style="min-width: 130px;" data-loading-text="Authenticating...">Authenticate</button>'
        );
        passwordAuthenticateForm.append(authenticateButton);
        authenticateButton.on("click", onAuthenticateButtonClick);
    }

    function onSignInClick() {
        usernameInput.trigger("focus");
        usernameInput.val("");
        passwordInput.val("");
        newPasswordInput.val("");
        newPasswordContainer.hide();
        authenticateDiv.modal({ keyboard: true });
    }

    function onAuthenticateButtonClick(e) {
        e.preventDefault();
        var password = passwordInput.val();

        var username = usernameInput.val();
        if (username === "") {
            dialogNotifications.alertError("Missing username.");
            return;
        }
        if (password === "") {
            dialogNotifications.alertError("Missing password.");
            return;
        }

        var options = {
            username: username,
            password: password
        };

        if (isHeartbeatInFlight) {
            ignoreNextHeartbeatResponse = true;
        }

        if ($(newPasswordContainer).is(":visible")) {
            var newPassword = newPasswordInput.val();
            if (newPassword === "") {
                dialogNotifications.alertError("Missing password.");
                return;
            }
            APP.changePassword(newPassword, options)
                .then(function () {
                    var newOptions = {
                        username: username,
                        password: newPassword
                    };
                    return APP.authenticate(newOptions);
                })
                .then(function (resp) {
                    APP.storeCordraOptions({ username: username });
                    return APP.getAuthenticationStatus(true)
                    .then(function (statusResp) {
                        onAuthenticateSuccess(statusResp);
                    });
                })
                .catch(onAuthenticateError);
        } else {
            APP.authenticate(options)
                .then(function (resp) {
                    APP.storeCordraOptions({ username: username });
                    return APP.getAuthenticationStatus(true)
                    .then(function (statusResp) {
                        onAuthenticateSuccess(statusResp);
                    });
                })
                .catch(onAuthenticateError);
        }
    }

    function onPrivateKeyAuthenticateButtonClick() {
        var handle = handleInput.val();
        if (handle === "") {
            dialogNotifications.alertError(
                "You must specify the handle containing your public key to authenticate."
            );
            return;
        }
        if (privateKeyBytes === null) {
            dialogNotifications.alertError(
                "You must select a private key file to authenticate."
            );
            return;
        }
        if (isEncryptedKey) {
            var passPhrase = getPassPhrase();
            if (passPhrase === "") {
                dialogNotifications.alertError(
                    "The selected private key requires a passphrase to decrypt it."
                );
                return;
            }
            try {
                cnri.util.EncryptionUtil.decryptPrivateKeyAes(
                    privateKeyBytes,
                    passPhrase
                )
                    .then(function (keyBytesBuffer) {
                        var keyBytes = new Uint8Array(keyBytesBuffer);
                        privateKeyAuthenticateForKeyBytes(handle, keyBytes);
                    })
                    .catch(function (error) {
                        dialogNotifications.alertError("Invalid private key file.");
                        console.log(error);
                    });
            } catch (error) {
                dialogNotifications.alertError("Invalid private key file.");
                console.log(error);
            }
        } else {
            privateKeyAuthenticateForKeyBytes(handle, privateKeyBytes);
        }
    }

    function privateKeyAuthenticateForKeyBytes(handle, keyBytes) {
        dialogNotifications.clear();
        var key = parsePrivateKeyFile(keyBytes);
        if (key == null) {
            return;
        }
        privateKeyAuthenticateButton.button("loading");
        privateKeyAuthenticate(handle, key);
    }

    function privateKeyAuthenticate(handle, privateKey) {
        var options = {
            userId: handle,
            privateKey: privateKey
        };
        APP.authenticate(options)
            .then(function (resp) {
                APP.storeCordraOptions({ userId: handle });
                return APP.getAuthenticationStatus(true)
                .then(function (statusResp) {
                    onAuthenticateSuccess(statusResp);
                });
            })
            .catch(onAuthenticateError);
    }

    function getIsActiveSession() {
        return isActiveSession;
    }
    self.getIsActiveSession = getIsActiveSession;

    function onSignOutLinkClick(e) {
        e.preventDefault();
        signOut();
    }

    function signOut() {
        if (isHeartbeatInFlight) {
            ignoreNextHeartbeatResponse = true;
        }
        APP.signOut()
            .then(function (resp) {
                APP.storeCordraOptions({});
                return APP.getAuthenticationStatus(true)
                .then(function (statusResp) {
                    onSignOutSuccess(statusResp);
                });
            })
            .catch(function (resp) {
                APP.storeCordraOptions({});
                onSignOutError(resp);
            });
    }

    function onSignOutSuccess(response) {
        setTypesPermittedToCreate(response.typesPermittedToCreate);
        APP.notifications.clear();
        setUiToStateUnauthenticated();
    }

    function setUiToStateUnauthenticated() {
        if (!isActiveSession) return;
        isActiveSession = false;
        authenticatedLabel.text("");
        signInButton.show();
        authenticatedDiv.hide();
        userInfo.userId = null;
        userInfo.username = null;
        if (fileReader) {
            fileReader.clear();
        }
        clearTimeout(heartbeatTimer);
        onAuthenticationStateChangeCallback(isActiveSession);
    }
    self.setUiToStateUnauthenticated = setUiToStateUnauthenticated;

    function onSignOutError() {
        setUiToStateUnauthenticated();
    }

    function setAuthenticated() {
        isActiveSession = true;
        APP.notifications.clear();
        dialogNotifications.clear();
        usernameInput.val("");
        passwordInput.val("");
        privateKeyPassPhraseInput.val("");
        signInButton.hide();
        authenticateButton.button("reset");
        privateKeyAuthenticateButton.button("reset");
        authenticatedLabel.show();
        if (userInfo.username) {
            authenticatedLabel.text(userInfo.username);
        } else {
            authenticatedLabel.text(userInfo.userId);
        }

        if (userInfo.username !== "admin") {
            authenticatedLabel.on("click", onUsernameLabelClick);
        }
        authenticateDiv.modal("hide");
        authenticateDiv.hide();
        authenticatedDiv.show();
        heartbeatTimer = setTimeout(heartbeat, HEARTBEAT_TIMEOUT);
    }

    function onUsernameLabelClick(e) {
        e.preventDefault();
        APP.resolveHandle(userInfo.userId);
    }

    function onAuthenticateSuccess(response) {
        userInfo.userId = response.userId;
        userInfo.username = response.username;
        typesPermittedToCreate = response.typesPermittedToCreate;
        setAuthenticated();
        onAuthenticationStateChangeCallback(isActiveSession);
    }

    function getTypesPermittedToCreate() {
        return typesPermittedToCreate;
    }
    self.getTypesPermittedToCreate = getTypesPermittedToCreate;

    function setTypesPermittedToCreate(types) {
        typesPermittedToCreate = types;
    }
    self.setTypesPermittedToCreate = setTypesPermittedToCreate;

    function onAuthenticateError(response) {
        var msg;
        if (secretKeyAuthenticateDiv.is(":visible")) {
            msg = "The username or password you entered is incorrect";
        } else {
            msg = "The authentication has failed";
        }
        if (response.body) {
            msg = response.message;
            if (response.body.passwordChangeRequired) {
                newPasswordContainer.show();
            }
        }
        dialogNotifications.alertError(msg);
        authenticateButton.button("reset");
        privateKeyAuthenticateButton.button("reset");
    }

    function getCurrentUserId() {
        return userInfo.userId;
    }
    self.getCurrentUserId = getCurrentUserId;

    function heartbeat() {
        isHeartbeatInFlight = true;
        APP.getAuthenticationStatus()
            .then(function (resp) {
                isHeartbeatInFlight = false;
                if (ignoreNextHeartbeatResponse) {
                    ignoreNextHeartbeatResponse = false;
                    return;
                }
                if (resp.active) {
                    if (!isActiveSession || resp.userId !== userInfo.userId) {
                        retrieveFullAuthenticationStatus();
                    }
                } else {
                    if (isActiveSession) {
                        signOut();
                    }
                }
            })
            .catch(function () {
                isHeartbeatInFlight = false;
                if (ignoreNextHeartbeatResponse) {
                    ignoreNextHeartbeatResponse = false;
                    return;
                }
                if (isActiveSession) {
                    signOut();
                }
                APP.notifications.alertError("The repository could not be reached.");
            });
        heartbeatTimer = setTimeout(heartbeat, HEARTBEAT_TIMEOUT);
    }

    function retrieveFullAuthenticationStatus() {
        APP.getAuthenticationStatus(true)
            .then(onAuthenticateSuccess)
            .catch(function () {
                if (isActiveSession) {
                    signOut();
                }
                APP.notifications.alertError("The repository could not be reached.");
            });
    }

    function onPrivateKeySelected(keyBytes) {
        dialogNotifications.clear();
        isEncryptedKey = false;
        if (!keyBytes) {
            privateKeyBytes = null;
            privateKeyPassPhraseInput.hide();
            return;
        }
        if (cnri.util.EncryptionUtil.requiresSecretKey(keyBytes)) {
            isEncryptedKey = true;
            privateKeyPassPhraseInput.show(400);
        } else {
            privateKeyPassPhraseInput.hide();
        }
        privateKeyBytes = keyBytes;
    }

    function getPassPhrase() {
        return privateKeyPassPhraseInput.val();
    }

    function parsePrivateKeyFile(keyBytes) {
        var key = null;
        try {
            var offset = 4;
            if (isEncryptedKey) offset = 0;
            key = cnri.util.EncryptionUtil.getPrivateKeyFromBytes(keyBytes, offset);
            dialogNotifications.clear();
        } catch (err) {
            dialogNotifications.alertError(
                "Selected file could not be parsed as a private key."
            );
        }
        return key;
    }

    constructor();
}
