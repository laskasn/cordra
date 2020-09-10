(function () {
    "use strict";

    var window = window || self;

    function CordraApp() {
        var self = this;
        var schemas = null;
        var schemaIds = null;
        var cordra = null;

        var uiConfig = null;
        var handleMintingPrefix = null;
        var toolBar = null;
        var notifications = null;
        var searchWidget = null;
        var objectId = null;
        var editor = null;

        var toolBarDiv = null;
        var editorDiv = null;
        var searchDiv = null;
        var notificationsDiv = null;
        //    var relationshipsGraphDiv = null;
        var htmlContentDiv = null;

        //    var relationshipsGraph = null;

        var navBar = null;

        var authConfig;

        var subscriptionBanner = null;

        var design = null;

        var externalLoginConfig = null
        
        // Admin sections
        var schemasDiv = null;
        var schemasEditor = null;
        var authWidget = null;
        var uiConfigEditor = null;
        var uiConfigDiv = null;
        var authConfigDiv = null;
        var authConfigEditor = null;
        var handleMintingConfigDiv = null;
        var handleMintingConfigEditor = null;
        var designJavaScriptDiv = null;
        var designJavaScriptEditor = null;
        var networkAndSecurityDiv = null;
        var networkAndSecurity = null;
        var sections = {};


        function constructor() {

            let storedOptions = getStoredCordraOptions();

            // was ../ under classic
            cordra = new cnri.CordraClient(".", storedOptions);

            editorDiv = $("#editor");
            searchDiv = $("#search");
            notificationsDiv = $("#notifications");
            //      relationshipsGraphDiv = $("#relationships");
            htmlContentDiv = $("#htmlContent");

            // admin sections
            schemasDiv = $("#schemas");
            uiConfigDiv = $("#ui");
            authConfigDiv = $("#authConfig");
            handleMintingConfigDiv = $("#handleRecords");
            networkAndSecurityDiv = $("#networkAndSecurity");
            designJavaScriptDiv = $("#designJavaScript");

            sections["schemas"] = schemasDiv;
            sections["ui"] = uiConfigDiv;
            sections["authConfig"] = authConfigDiv;
            sections["handleRecords"] = handleMintingConfigDiv;
            sections["networkAndSecurity"] = networkAndSecurityDiv;
            sections["designJavaScript"] = designJavaScriptDiv;


            if(storedOptions.keycloakConfig != null){
                cordra.authenticate(storedOptions)
                  .then((authResult) => {
                      if(authResult!=null){
                        cordra.defaultOptions.token = cordra.keycloakClient.token;
                        storeCordraOptions(cordra.defaultOptions);
                        //adding here because we need to load UI after the authentication is completed
                        buildCordraAuthHeadersReturnDetails();
                      }
                  })
                  .catch( (e) => console.log(e));
            }
            else {
                buildCordraAuthHeadersReturnDetails();
            }

            $(window).on("resize", onResize);
        }



        function buildCordraAuthHeadersReturnDetails(){

          cordra.buildAuthHeadersReturnDetails().then(function (headersObj) {

              if (headersObj.unauthenticated) {
                  storeCordraOptions({});
                  return fetch("initData")
                  .then(cnri.CordraClient.checkForErrors);
              } else if (!headersObj.isStoredToken) {
                  return fetch("initData", {
                      headers: headersObj.headers
                  })
                  .then(cnri.CordraClient.checkForErrors);
              } else {
                  return fetch("initData", {
                      headers: headersObj.headers
                  })
                  .then(cnri.CordraClient.checkForErrors)
                  .catch(function (error) {
                      if (error.status !== 401) return Promise.reject(error);
                      if(getStoredCordraOptions().keycloakConfig == null)  storeCordraOptions({});
                      return fetch("initData")
                      .then(cnri.CordraClient.checkForErrors);
                  })
              }
          })
          .then(getResponseJson)
          .then(adaptByKeycloakState)
          .then(onGotInitData);

        }

        function getExternalLoginConfig(){
        	return externalLoginConfig;
        }
        self.getExternalLoginConfig = getExternalLoginConfig;
        

        function getStoredCordraOptions() {
            const storedOptions = JSON.parse(localStorage.getItem("cordraOptions")) || {};
            //console.log("Retrieved the options: ", storedOptions);
            return storedOptions;
        }
        self.getStoredCordraOptions = getStoredCordraOptions;


        function retrieveCordraOptions() {
            const storedOptions = JSON.parse(localStorage.getItem("cordraOptions")) || {};
            cordra.defaultOptions = storedOptions || {};
            return storedOptions;
        }
        self.retrieveCordraOptions = retrieveCordraOptions;

        function storeCordraOptions(options) {
            localStorage.setItem("cordraOptions", JSON.stringify(options))
            cordra.defaultOptions = options;
            //console.log("Stored the options: ", cordra.defaultOptions)
        }
        self.storeCordraOptions = storeCordraOptions;
        
        function getResponseJson(response) {
            return response.json();
        }

        function onResize() {
            if (editor) {
                editor.resizeRelationshipsGraph();
            }

            //      if (relationshipsGraph) {
            //        relationshipsGraph.onResize();
            //      }
        }


        function adaptByKeycloakState(response) {

          let storedOptions = getStoredCordraOptions();

          if(storedOptions.token!=null && storedOptions.keycloakConfig!=null){
            var parsedToken = JSON.parse(atob(storedOptions.token.split('.')[1]));
            response['name'] = parsedToken.name;
            response['username'] = parsedToken.preferred_username;
            response['userId'] = parsedToken.sub;
            response['isActiveSession'] = true;
          }

          return response;
        }




        function onGotInitData(response) {
        	externalLoginConfig = response.externalLoginConfig;
            design = response.design;
            uiConfig = response.design.uiConfig;
            schemas = response.design.schemas;
            schemaIds = response.design.schemaIds;

            authConfig = response.design.authConfig;
            handleMintingPrefix = response.design.handleMintingConfig.prefix;
            subscriptionBanner = new SubscriptionBanner(
                $("#subscriptionBanner"),
                handleMintingPrefix
            );

            $(".navbar-brand").text(uiConfig.title);
            $("title").text(uiConfig.title);

            var navBarElement = $("#navBar");
            navBar = new NavBar(navBarElement, uiConfig.navBarLinks, schemas);

            notifications = new Notifications(notificationsDiv);
            self.notifications = notifications;
            searchWidget = new SearchWidget(
                searchDiv,
                false,
                uiConfig.numTypesForCreateDropdown
            );

            var allowLogin = checkIfLoginAllowed(response.design.allowInsecureAuthentication);
            authWidget = new AuthenticatorWidget(
                $("#authenticateDiv"),
                onAuthenticationStateChange,
                response.isActiveSession,
                response.user,
                response.userId,
                response.typesPermittedToCreate,
                allowLogin
            );

            var isAdminDisabled = !(
                response.isActiveSession && response.username === "admin"
            );
            buildAdminWidgets(isAdminDisabled);

            onAuthenticationStateChange();

            // At this point, the editor and objectId are still null, so onAuthenticationStateChange will call handleNewWindowLocation for us
            // handleNewWindowLocation();
            //window.onpopstate = handleOnpopstate;
            window.onhashchange = handleOnhashchange;
        }

        function checkIfLoginAllowed(allowInsecureLogin) {
            if (allowInsecureLogin) return true;
            return location.protocol === 'https:';
        }

        function buildAdminWidgets(isAdminDisabled) {
            schemasEditor = new SchemasEditor(
                schemasDiv,
                design.schemas,
                design.schemaIds,
                isAdminDisabled
            );
            uiConfigEditor = new UiConfigEditor(
                uiConfigDiv,
                design.uiConfig,
                isAdminDisabled
            );
            authConfigEditor = new AuthConfigEditor(
                authConfigDiv,
                design.authConfig,
                isAdminDisabled
            );
            handleMintingConfigEditor = new HandleMintingConfigEditor(
                handleMintingConfigDiv,
                design.handleMintingConfig,
                isAdminDisabled
            );
            networkAndSecurity = new NetworkAndSecurity(
                networkAndSecurityDiv,
                isAdminDisabled
            );
            designJavaScriptEditor = new DesignJavaScriptEditor(
                designJavaScriptDiv,
                design.javascript,
                isAdminDisabled
            );
        }

        function onAuthenticationStateChange() {
            var userId = authWidget.getCurrentUserId();
            if (userId === "admin") {
                enableAdminControls();
            } else {
                disableAdminControls();
            }
            if (authConfig) {
                var types = authWidget.getTypesPermittedToCreate();
                searchWidget.setAllowCreateTypes(types);
            }
            if (editor != null && objectId != null) {
                var currentObjectId = objectId;
                var showSearch = false;
                hideObjectEditor(showSearch);
                resolveHandle(currentObjectId, false);
            } else {
                handleNewWindowLocation();
            }
        }

        function disableAdminControls() {
            navBar.hideAdminMenu();
            uiConfigEditor.disable();
            authConfigEditor.disable();
            handleMintingConfigEditor.disable();
            networkAndSecurity.disable();
            schemasEditor.disable();
            designJavaScriptEditor.disable();
        }

        function enableAdminControls() {
            navBar.showAdminMenu();
            uiConfigEditor.enable();
            authConfigEditor.enable();
            handleMintingConfigEditor.enable();
            networkAndSecurity.enable();
            schemasEditor.enable();
            designJavaScriptEditor.enable();
        }

        function handleOnpopstate() {
            handleNewWindowLocation();
        }

        function handleOnhashchange() {
            handleNewWindowLocation();
        }

        function handleNewWindowLocation() {
            htmlContentDiv.hide();
            searchWidget.hideResults();
            hideObjectEditor(false);
            hideAllAdminSections();

            var fragment = window.location.hash.substr(1);
            if (fragment != null && fragment !== "") {
                if (startsWith(fragment, "objects/")) {
                    if (startsWith(fragment, "objects/?query=")) {
                        var params = getParamsFromFragment(fragment);
                        var fragmentQuery = params.query;
                        var sortFields = params.sortFields;
                        searchWidget.search(fragmentQuery, sortFields);
                    } else {
                        var fragmentObjectId = getObjectIdFromFragment(fragment);
                        if (fragmentObjectId != null && fragmentObjectId !== "") {
                            if (fragmentObjectId !== objectId) {
                                resolveHandle(fragmentObjectId);
                            }
                        }
                    }
                } else if (startsWith(fragment, "urls/")) {
                    var url = getUrlFromFragment(fragment);
                    if (url != null && url !== "") {
                        var options = {
                            type: "url",
                            url: url
                        };
                        showHtmlPageFor(options);
                    }
                } else if (startsWith(fragment, "create/")) {
                    var prefix = "create/";
                    var type = decodeURIComponent(fragment.substring(prefix.length));
                    createNewObject(type);
                } else if (sections[fragment]) {
                    sections[fragment].show();
                }
            } else if (
                window.location.href.indexOf("#") === -1 &&
                uiConfig.initialFragment
            ) {
                window.location.hash = uiConfig.initialFragment;
            }
        }

        function hideAllAdminSections() {
            for (var id in sections) {
                sections[id].hide();
            }
        }

        function encodeURIComponentPreserveSlash(s) {
            return encodeURIComponent(s).replace(/%2F/gi, '/');
        }

        function setCreateInFragment(type) {
            var fragment = "create/" + encodeURIComponentPreserveSlash(type);
            window.location.hash = fragment;
        }
        self.setCreateInFragment = setCreateInFragment;

        function setObjectIdInFragment(objectId) {
            var fragment = "objects/" + encodeURIComponentPreserveSlash(objectId);
            window.location.hash = fragment;
        }
        self.setObjectIdInFragment = setObjectIdInFragment;

        function setQueryInFragment(query, sortFields) {
            var fragment = "objects/?query=" + encodeURIComponentPreserveSlash(query);
            if (sortFields) {
                fragment += "&sortFields=" + encodeURIComponentPreserveSlash(sortFields);
            }
            window.location.hash = fragment;
        }
        self.setQueryInFragment = setQueryInFragment;

        function clearFragment() {
            window.location.hash = "";
        }
        self.clearFragment = clearFragment;

        function performSearchWidgetSearch(query, sortFields) {
            hideHtmlContent();
            setQueryInFragment(query, sortFields);
        }
        self.performSearchWidgetSearch = performSearchWidgetSearch;

        function hideObjectEditor(showSearch) {
            objectId = null;
            //      hideRelationshipsGraph();
            if (editor) editor.destroy();
            editorDiv.empty();
            editorDiv.hide();
            editor = null;
        }
        self.hideObjectEditor = hideObjectEditor;

        function hideHtmlContent() {
            htmlContentDiv.hide();
        }
        self.hideHtmlContent = hideHtmlContent;

        function showHtmlPageFor(options) {
            //      hideRelationshipsGraph();
            objectId = null;
            if (editor) editor.destroy();
            editorDiv.empty();
            editorDiv.hide();
            htmlContentDiv.show();
            new HtmlPageViewer(htmlContentDiv, options);
        }

        function getUiConfig() {
            return uiConfig;
        }
        self.getUiConfig = getUiConfig;

        function getPrefix() {
            return handleMintingPrefix;
        }
        self.getPrefix = getPrefix;

        function getSchema(type) {
            return schemas[type];
        }
        self.getSchema = getSchema;

        function getSchemaCount() {
            return Object.keys(schemas).length;
        }
        self.getSchemaCount = getSchemaCount;

        function createNewObject(type) {
            editorDiv.empty();
            //      hideRelationshipsGraph();
            objectId = null;
            var allowEdits = true;

            var contentPlusMeta = {
                id: null,
                type: type,
                content: {},
                metadata: {}
            };

            var options = {
                contentPlusMeta: contentPlusMeta,
                schema: schemas[type],
                type: type,
                objectJson: {},
                objectId: null,
                relationshipsButtonText: uiConfig.relationshipsButtonText,
                disabled: false,
                allowEdits: allowEdits
            };
            if (editor) editor.destroy();
            editor = new ObjectEditor(editorDiv, options);
            editorDiv.show();
        }
        self.createNewObject = createNewObject;

        function isUserAllowedToEdit() {
            return authWidget.getIsActiveSession();
        }

        function resolveHandle(objectId, retainGraph) {
            var options = Object.assign(
                { includeResponseContext: true },
                cordra.defaultOptions
            );
            cordra
                .get(objectId, options)
                .then(function (response) {
                    onGotObject(response, retainGraph);
                })
                .catch(onErrorResponse);
        }
        self.resolveHandle = resolveHandle;

        // Just gets an object by id, does not start editing that object
        function getObject(objectId, successCallback, errorCallback) {
            cordra
                .get(objectId)
                .then(successCallback)
                .catch(errorCallback);
        }
        self.getObject = getObject;

        function getPayloadContent(
            objectId,
            payloadName,
            successCallBack,
            errorCallback
        ) {
            cordra
                .getPayload(objectId, payloadName)
                .then(successCallBack)
                .catch(errorCallback);
        }
        self.getPayloadContent = getPayloadContent;

        function getAclForCurrentObject(onGotAclSuccess) {
            cordra
                .getAclForObject(objectId)
                .then(onGotAclSuccess)
                .catch(onErrorResponse);
        }
        self.getAclForCurrentObject = getAclForCurrentObject;

        function saveAclForCurrentObject(newAcl, onSuccess, onFail) {
            notifications.clear();
            cordra
                .updateAclForObject(objectId, newAcl)
                .then(function (res) {
                    notifications.alertSuccess("ACL for Object " + objectId + " saved.");
                    onSuccess(res);
                })
                .catch(function (response) {
                    onErrorResponse(response, onFail);
                });
        }
        self.saveAclForCurrentObject = saveAclForCurrentObject;

        function onGotObject(contentPlusMeta, retainGraph) {
            notifications.clear();
            editorDiv.empty();
            editorDiv.show();
            objectId = contentPlusMeta.id;
            var type = contentPlusMeta.type;
            var permission;
            if (contentPlusMeta.responseContext) {
                permission = contentPlusMeta.responseContext.permission;
                delete contentPlusMeta.responseContext;
            }

            setObjectIdInFragment(objectId);
            //      if (retainGraph) {
            //        relationshipsGraph.setNewTargetObject(objectId);
            //      } else {
            //        hideRelationshipsGraph();
            //      }
            hideHtmlContent();
            var allowEdits = false;
            if (permission === "WRITE") {
                allowEdits = true;
            }

            var userId = authWidget.getCurrentUserId();
            var allowClone = isAllowedToCreate(userId, type);

            var schema = getSchema(type);
            var content = contentPlusMeta.content;
            var options = {
                contentPlusMeta: contentPlusMeta,
                schema: schema,
                type: type,
                objectJson: content,
                objectId: objectId,
                relationshipsButtonText: uiConfig.relationshipsButtonText,
                disabled: true,
                allowEdits: allowEdits,
                allowClone: allowClone
            };
            if (editor) editor.destroy();
            editor = new ObjectEditor(editorDiv, options);
        }

        // This method does not fiddle the UI in any way.
        function search(query, pageNum, pageSize, sortFields, onSuccess, onError) {
            if (!pageNum) pageNum = 0;
            if (!pageSize) pageSize = -1;
            var params = {
                pageNum: pageNum,
                pageSize: pageSize,
                sortFields: getSortFieldsFromString(sortFields)
            };
            cordra
                .search(query, params)
                .then(onSuccess)
                .catch(onError);
        }
        self.search = search;

        function getSortFieldsFromString(sortFieldsString) {
            var sortFields = [];
            if (sortFieldsString) {
                var fieldStrings = sortFieldsString.split(",");
                fieldStrings.forEach(function (value) {
                    var terms = value.split(" ");
                    var reverse = false;
                    if (terms.length > 1) {
                        if (terms[1].toUpperCase() === "DESC") reverse = true;
                    }
                    sortFields.push({
                        name: terms[0],
                        reverse: reverse
                    });
                });
            }
            return sortFields;
        }
        self.getSortFieldsFromString = getSortFieldsFromString;

        //    function showRelationshipsGraph(objectId) {
        //      relationshipsGraphDiv.empty();
        //      relationshipsGraphDiv.show();
        //      if (relationshipsGraph) relationshipsGraph.destroy();
        //      relationshipsGraph = new RelationshipsGraph(
        //        relationshipsGraphDiv,
        //        objectId
        //      );
        //    }
        //    self.showRelationshipsGraph = showRelationshipsGraph;
        //
        //    function hideRelationshipsGraph() {
        //      if (relationshipsGraph) relationshipsGraph.destroy();
        //      relationshipsGraphDiv.empty();
        //      relationshipsGraphDiv.hide();
        //      relationshipsGraph = null;
        //    }
        //    self.hideRelationshipsGraph = hideRelationshipsGraph;

        function getRelationships(
            objectId,
            successCallback,
            errorCallback,
            outboundOnly
        ) {
            var uri = "relationships/" + objectId;
            if (outboundOnly) {
                uri = uri + "?outboundOnly=true";
            }
            cordra.retryAfterTokenFailure(cordra.defaultOptions, function (headers) {
                return fetch(uri, {
                    headers: headers
                })
                .then(cnri.CordraClient.checkForErrors);
            })
            .then(getResponseJson)
            .then(successCallback)
            .catch(errorCallback);
        }
        self.getRelationships = getRelationships;

        function deleteObject(objectId) {
            var dialog = new ModalYesNoDialog(
                "Are you sure you want to delete this object?",
                function () {
                    yesDeleteCallback(objectId, schemaIds);
                },
                noDeleteCallback,
                self
            );
            dialog.show();
        }
        self.deleteObject = deleteObject;

        function yesDeleteCallback(objectId, schemaIds) {
            var isSchema = Object.keys(schemaIds).indexOf(objectId) > -1;
            cordra
                .delete(objectId)
                .then(function () {
                    //          hideRelationshipsGraph();
                    if (isSchema) refreshSchemasInUI();
                    if (editor) editor.destroy();
                    editorDiv.empty();
                    editor = null;
                    clearFragment();
                    notifications.alertSuccess("Object " + objectId + " deleted.");
                })
                .catch(function (response) {
                    onErrorResponse(response);
                });
        }

        function noDeleteCallback() {
            //no-op
        }

        function cloneCurrentObject() {
            var currentObjectJson = editor.getJsonFromEditor();
            var currentObjectType = editor.getType();
            var newObject = ObjectCloner.clone(
                currentObjectJson,
                currentObjectType,
                schemas[currentObjectType]
            );

            editorDiv.empty();
            clearFragment();
            //      hideRelationshipsGraph();
            objectId = null;
            //var allowEdits = isUserAllowedToEdit();

            var contentPlusMeta = {
                id: null,
                type: currentObjectType,
                content: newObject,
                metadata: {}
            };

            var options = {
                contentPlusMeta: contentPlusMeta,
                schema: schemas[currentObjectType],
                type: currentObjectType,
                objectJson: newObject,
                objectId: null,
                relationshipsButtonText: uiConfig.relationshipsButtonText,
                disabled: false,
                allowEdits: true
            };
            if (editor) editor.destroy();
            editor = new ObjectEditor(editorDiv, options);
            editorDiv.show();
        }
        self.cloneCurrentObject = cloneCurrentObject;

        function saveObject(cordraObject, errorCallback, progressCallback) {
            notifications.clear();
            var options = Object.assign(
                { includeResponseContext: true },
                cordra.defaultOptions
            );
            cordra
                .update(cordraObject, progressCallback, options)
                .then(onSaveSuccess)
                .catch(function (response) {
                    onErrorResponse(response, errorCallback);
                });
        }
        self.saveObject = saveObject;

        function onSaveSuccess(contentPlusMeta) {
            editorDiv.empty();
            objectId = contentPlusMeta.id;
            var type = contentPlusMeta.type;
            var permission;
            if (contentPlusMeta.responseContext) {
                permission = contentPlusMeta.responseContext.permission;
                delete contentPlusMeta.responseContext;
            }
            setObjectIdInFragment(objectId);
            if (type === "Schema") refreshSchemasInUI();

            var allowEdits = false;
            if (permission === "WRITE") {
                allowEdits = true;
            }

            var userId = authWidget.getCurrentUserId();
            var allowClone = isAllowedToCreate(userId, type);

            var content = contentPlusMeta.content;
            var options = {
                contentPlusMeta: contentPlusMeta,
                schema: schemas[type],
                type: type,
                objectJson: content,
                objectId: objectId,
                relationshipsButtonText: uiConfig.relationshipsButtonText,
                disabled: true,
                allowEdits: allowEdits,
                allowClone: allowClone
            };
            if (editor) editor.destroy();
            editor = new ObjectEditor(editorDiv, options);
            notifications.alertSuccess("Object " + objectId + " saved.");
        }

        function publishVersion(objectId, onSuccess, onFail) {
            notifications.clear();
            cordra
                .publishVersion(objectId)
                .then(function (res) {
                    notifications.alertSuccess("Version published with id " + res.id);
                    if (onSuccess) {
                        onSuccess(res);
                    }
                })
                .catch(function (response) {
                    onErrorResponse(response, onFail);
                });
        }
        self.publishVersion = publishVersion;

        function getVersionsFor(objectId, onSuccess, onFail) {
            cordra
                .getVersionsFor(objectId)
                .then(onSuccess)
                .catch(function (response) {
                    onErrorResponse(response, onFail);
                });
        }
        self.getVersionsFor = getVersionsFor;

        function createObject(
            cordraObject,
            suffix,
            errorCallback,
            progressCallback
        ) {
            notifications.clear();
            var options = Object.assign(
                { suffix: suffix },
                cordra.defaultOptions
            );
            cordra
                .create(cordraObject, progressCallback, options)
                .then(onCreateSuccess)
                .catch(function (response) {
                    onErrorResponse(response, errorCallback);
                });
        }
        self.createObject = createObject;

        function onCreateSuccess(contentPlusMeta) {
            editorDiv.empty();
            objectId = contentPlusMeta.id;
            var type = contentPlusMeta.type;
            setObjectIdInFragment(objectId);
            if (type === "Schema") refreshSchemasInUI();

            var content = contentPlusMeta.content;
            var options = {
                contentPlusMeta: contentPlusMeta,
                schema: schemas[type],
                type: type,
                objectJson: content,
                objectId: objectId,
                relationshipsButtonText: uiConfig.relationshipsButtonText,
                disabled: true,
                allowEdits: true
            };
            if (editor) editor.destroy();
            editor = new ObjectEditor(editorDiv, options);
            notifications.alertSuccess("Object " + objectId + " saved.");
        }

        function hideAllSectionsExcept(sectionId) {
            for (var id in sections) {
                if (id === sectionId) {
                    sections[id].show();
                } else {
                    sections[id].hide();
                }
            }
            window.scrollTo(0, 0);
        }

        function getFirstSchemaAndType(schemas) {
            var result = null;
            for (var type in schemas) {
                var schema = schemas[type];
                result = {
                    schema: schema,
                    type: type
                };
                break;
            }
            return result;
        }

        function getSchema(type) {
            return design.schemas[type];
        }
        self.getSchema = getSchema;

        function getSchemaCount() {
            return Object.keys(design.schemas).length;
        }
        self.getSchemaCount = getSchemaCount;

        function saveUiConfig(uiConfig) {
            notifications.clear();
            if (!authWidget.getIsActiveSession()) {
                notifications.alertError("Not authenticated.");
                return;
            }
            cordra
                .updateObjectProperty("design", "/uiConfig", uiConfig)
                .then(function () {
                    $(".navbar-brand").text(uiConfig.title);
                    $("title").text(uiConfig.title);
                    notifications.alertSuccess("UiConfig saved.");
                })
                .catch(onErrorResponse);
        }
        self.saveUiConfig = saveUiConfig;

        function saveDesignJavaScript(designJavaScript) {
            notifications.clear();
            if (!authWidget.getIsActiveSession()) {
                notifications.alertError("Not authenticated.");
                return;
            }
            cordra
                .updateObjectProperty("design", "/javascript", designJavaScript)
                .then(function () {
                    notifications.alertSuccess("Design JavaScript saved.");
                })
                .catch(onErrorResponse);
        }
        self.saveDesignJavaScript = saveDesignJavaScript;

        function saveAdminPassword(password, options) {
            notifications.clear();
            if (!authWidget.getIsActiveSession()) {
                notifications.alertError("Not authenticated.");
                return;
            }
            cordra
                .changeAdminPassword(password, options)
                .then(function () {
                    notifications.alertSuccess("Admin password saved.");
                })
                .then(function () {
                    if (options.username !== 'admin') return;
                    var newOptions = {
                        username: 'admin',
                        password: password
                    };
                    return APP.authenticate(newOptions);
                })
                .catch(onErrorResponse);
        }
        self.saveAdminPassword = saveAdminPassword;

        function saveHandleMintingConfig(handleMintingConfig) {
            notifications.clear();
            if (!authWidget.getIsActiveSession()) {
                notifications.alertError("Not authenticated.");
                return;
            }
            cordra
                .updateObjectProperty(
                    "design",
                    "/handleMintingConfig",
                    handleMintingConfig
                )
                .then(function () {
                    notifications.alertSuccess("Handle minting config saved.");
                })
                .catch(onErrorResponse);
        }
        self.saveHandleMintingConfig = saveHandleMintingConfig;

        function updateAllHandles(successCallback) {
            notifications.clear();
            if (!authWidget.getIsActiveSession()) {
                notifications.alertError("Not authenticated.");
                return;
            }
            cordra
                .updateAllHandles()
                .then(function () {
                    notifications.alertSuccess("Update in progress");
                    if (successCallback) successCallback();
                })
                .catch(onErrorResponse);
        }
        self.updateAllHandles = updateAllHandles;

        function getHandleUpdateStatus(successCallback) {
            notifications.clear();
            cordra
                .getHandleUpdateStatus()
                .then(function (res) {
                    if (successCallback) successCallback(res);
                })
                .catch(onErrorResponse);
        }
        self.getHandleUpdateStatus = getHandleUpdateStatus;

        function saveAuthConfig(authConfig) {
            notifications.clear();
            if (!authWidget.getIsActiveSession()) {
                notifications.alertError("Not authenticated.");
                return;
            }
            cordra
                .updateObjectProperty("design", "/authConfig", authConfig)
                .then(function () {
                    notifications.alertSuccess("Auth config saved.");
                })
                .catch(onErrorResponse);
        }
        self.saveAuthConfig = saveAuthConfig;

        function refreshSchemasInUI() {
            cordra.retryAfterTokenFailure(cordra.defaultOptions, function (headers) {
                return fetch("initData", {
                    headers: headers
                })
                .then(cnri.CordraClient.checkForErrors);
            })
            .then(getResponseJson)
            .then(function (response) {
                design.schemas = response.design.schemas;
                schemas = response.design.schemas;
                schemaIds = response.design.schemaIds;
                if (schemasEditor) {
                    schemasEditor.refresh(schemas, schemaIds);
                    // schemasEditor.setSelected(null);
                    // schemasEditor.showSchemaEditorFor(null);
                }
                var types = response.typesPermittedToCreate;
                authWidget.setTypesPermittedToCreate(types);
                searchWidget.setAllowCreateTypes(types);
                navBar.refresh(schemas);
            });
        }

        function loadObjects(objects, deleteCurrentObjects) {
            notifications.clear();
            if (!authWidget.getIsActiveSession()) {
                notifications.alertError("Not authenticated.");
                return;
            }
            $("#objectLoadingGif").show();
            $("#loadFromFileButton").prop("disabled", true);
            cordra
                .uploadObjects(objects, deleteCurrentObjects)
                .then(function () {
                    $("#objectLoadingGif").hide();
                    $("#loadFromFileButton").prop("disabled", false);
                    refreshSchemasInUI();
                    notifications.alertSuccess("Objects loaded.");
                })
                .catch(function (response) {
                    $("#objectLoadingGif").hide();
                    $("#loadFromFileButton").prop("disabled", false);
                    onErrorResponse(response);
                });
        }
        self.loadObjects = loadObjects;

        function getIdForSchema(type) {
            var id = null;
            Object.keys(schemaIds).forEach(function (key) {
                if (schemaIds[key] === type) id = key;
            });
            return id;
        }

        function saveSchema(schemaCordraObject, type) {
            notifications.clear();
            if (!authWidget.getIsActiveSession()) {
                notifications.alertError("Not authenticated.");
                return;
            }
            //        var schemaCordraObject = {
            //            content: {
            //                name: type,
            //                schema: schema
            //            }
            //        };
            //var id = getIdForSchema(type);
            if (schemaCordraObject.id) {
                //schemaCordraObject.id = id;
                //schemaCordraObject.content.identifier = id;
                //cordra.updateObjectProperty(id, "/schema", schema)
                cordra
                    .update(schemaCordraObject)
                    .then(function () {
                        refreshSchemasInUI();
                        notifications.alertSuccess("Schema " + type + " saved.");
                    })
                    .catch(onErrorResponse);
            } else {
                schemaCordraObject.type = "Schema";
                cordra
                    .create(schemaCordraObject)
                    .then(function () {
                        refreshSchemasInUI();
                        notifications.alertSuccess("Schema " + type + " created.");
                    })
                    .catch(onErrorResponse);
            }
        }
        self.saveSchema = saveSchema;

        function deleteSchema(type) {
            notifications.clear();
            if (!authWidget.getIsActiveSession()) {
                notifications.alertError("Not authenticated.");
                return;
            }
            var id = getIdForSchema(type);
            if (id) {
                cordra
                    .delete(id)
                    .then(function (res) {
                        refreshSchemasInUI();
                        notifications.alertSuccess("Schema " + type + " deleted.");
                        closeSchemaEditor();
                    })
                    .catch(onErrorResponse);
            }
        }
        self.deleteSchema = deleteSchema;

        function closeSchemaEditor() {
            schemasEditor.refresh();
            schemasEditor.showSchemaEditorFor(null);
        }
        self.closeSchemaEditor = closeSchemaEditor;

        function editCurrentObject() {
            editorDiv.empty();
            editorDiv.show();
            var type = editor.getType();
            var jsonObject = editor.getJsonFromEditor();

            var contentPlusMeta = editor.getContentPlusMeta();
            var options = {
                contentPlusMeta: contentPlusMeta,
                schema: schemas[type],
                type: type,
                objectJson: jsonObject,
                objectId: objectId,
                relationshipsButtonText: uiConfig.relationshipsButtonText,
                disabled: false,
                allowEdits: true
            };
            if (editor) editor.destroy();
            editor = new ObjectEditor(editorDiv, options);
        }
        self.editCurrentObject = editCurrentObject;

        function getTypesUserCanCreate(userId) {
            return typesPermittedToCreate;
        }

        function isAllowedToCreate(userId, type) {
            if (userId === "admin") return true;
            var acl = null;
            var schemaAcl = authConfig.schemaAcls[type];
            if (schemaAcl && schemaAcl.aclCreate) {
                acl = schemaAcl.aclCreate;
            } else {
                if (authConfig.defaultAcls) {
                    acl = authConfig.defaultAcls.aclCreate;
                }
            }
            if (!acl) return false;
            for (var i = 0; i < acl.length; i++) {
                var permittedId = acl[i];
                if ("public" === permittedId) return true;
                if (userId != null && "authenticated" === permittedId) return true;
                if (userId === permittedId) return true;
            }
            return false;
        }

        function getObjectId() {
            return objectId;
        }
        self.getObjectId = getObjectId;

        function getObjectIdFromFragment(fragment) {
            var path = "objects/";
            return decodeURIComponent(fragment.substring(path.length));
        }

        function getParamsFromFragment(fragment) {
            //        var prefix = "objects/?query=";
            //        return decodeURIComponent(fragment.substring(prefix.length));
            var prefix = "objects/?";
            var queryParamsString = fragment.substring(prefix.length);
            var paramsArray = queryParamsString.split("&");
            var params = {};
            for (var i = 0; i < paramsArray.length; i++) {
                var paramString = paramsArray[i];
                var paramTokens = paramString.split("=");
                params[decodeURIComponent(paramTokens[0])] = decodeURIComponent(paramTokens[1]);
            }
            return params;
        }

        function getUrlFromFragment(fragment) {
            var path = "urls/";
            return fragment.substring(path.length);
        }

        function getAccessToken() {
            return cordra.buildAuthHeadersReturnDetails().then(function (headersObj) {
                var authHeader = headersObj.headers.get("Authorization");
                if (!authHeader) return "";
                if (!startsWith(authHeader, "Bearer ")) return "";
                var bearerToken = authHeader.substring(7);
                if (bearerToken.indexOf(".") >= 0) return "";
                return bearerToken;
            });
        }
        self.getAccessToken = getAccessToken;

        function startsWith(str, prefix) {
            return str.lastIndexOf(prefix, 0) === 0;
        }

        function disableJsonEditorOnline(editor) {
            editor.aceEditor.container.style.backgroundColor = "rgb(238, 238, 238)";
            editor.aceEditor.setReadOnly(true);
        }
        self.disableJsonEditorOnline = disableJsonEditorOnline;

        function enableJsonEditorOnline(editor) {
            editor.aceEditor.container.style.backgroundColor = "";
            editor.aceEditor.setReadOnly(false);
        }
        self.enableJsonEditorOnline = enableJsonEditorOnline;

        function authenticate(options) {
            return cordra.authenticate(options);
        }
        self.authenticate = authenticate;

        function getAuthenticationStatus(full) {
            return cordra.getAuthenticationStatus(full);
        }
        self.getAuthenticationStatus = getAuthenticationStatus;

        function changePassword(newPassword, options) {
            return cordra.changePassword(newPassword, options);
        }
        self.changePassword = changePassword;

        function signOut() {
            return cordra.signOut();
        }
        self.signOut = signOut;

        function onErrorResponse(response, errorCallback) {
            if (!response) {
                notifications.alertError("Something went wrong.");
            } else {
                if (response.status === 401) {
                    storeCordraOptions({});
                    authWidget.setUiToStateUnauthenticated();
                    var message = response.statusText || "Authentication failed";
                    notifications.alertError(message);
                } else if (response.status === 403) {
                    var message = response.statusText || "Forbidden";
                    notifications.alertError(message);
                } else {
                    if (response.message) {
                        notifications.alertError(response.message);
                    } else {
                        response.json()
                            .then(function (json) {
                                notifications.alertError(json.message);
                            });
                    }
                }
            }
            if (errorCallback) errorCallback(response);
        }

        constructor();

    }

    window.CordraApp = CordraApp;
})();
