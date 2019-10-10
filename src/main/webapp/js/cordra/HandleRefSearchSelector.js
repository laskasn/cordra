(function () {
    "use strict";

    var window = window || self;

    function HandleRefSearchSelector(textInput, editor) {
        var self = this;
        var types = null;
        var excludeTypes = null;
        var prepend = null;
        var lastQuery = 0;
        var pendingQueryTimeoutId = null;
        var lastResults = null;
        var link = null;

        function constructor() {
            types = SchemaUtil.getDeepCordraSchemaProperty(
                editor.schema,
                "type",
                "handleReference",
                "types"
            );
            excludeTypes = SchemaUtil.getDeepCordraSchemaProperty(
                editor.schema,
                "type",
                "handleReference",
                "excludeTypes"
            );
            prepend = SchemaUtil.getDeepCordraSchemaProperty(
                editor.schema,
                "type",
                "handleReference",
                "prepend"
            );
            if (
                !prepend &&
                SchemaUtil.getDeepCordraSchemaProperty(
                    editor.schema,
                    "type",
                    "handleReference",
                    "prependHandleMintingConfigPrefix"
                )
            ) {
                var prefix = APP.getPrefix();
                if (prefix) prepend = ensureSlash(prefix);
            }
            if (!prepend) prepend = "";
            textInput.attr("placeholder", placeholderForTypes(types));
            textInput.on("keyup", onChange);
            textInput.popover({
                content: popoverContent,
                placement: "bottom",
                html: true,
                trigger: "manual"
            });
            textInput.on("shown.bs.popover", onShownPopover);
            textInput.on("blur", onBlur);
            link = $("<a></a>");
            $(textInput).after(link);
            editor.jsoneditor.watch(editor.path, getTargetObject);
        }

        function ensureSlash(prefix) {
            if (prefix.length === 0) return "/";
            if (prefix.substring(prefix.length - 1) === "/") {
                return prefix;
            }
            return prefix + "/";
        }

        function popoverContent() {
            if (lastResults == null) return "No results.";
            var outerDiv = $('<div class="container"></div>');
            var div = $('<div class="search-results-list col-md-12"></div>');
            outerDiv.append(div);
            var results = lastResults.results;
            if (results.length === 0) return "No results.";
            for (var i = 0; i < results.length; i++) {
                var id = results[i].id;
                if (prepend && !startsWith(id, prepend)) continue;
                var resultsDiv = ObjectPreviewUtil.elementForSuggestion(results[i]);
                div.append(resultsDiv);
            }
            return outerDiv.html();
        }

        function startsWith(str, prepend) {
            return str.lastIndexOf(prepend, 0) === 0;
        }

        function onShownPopover() {
            textInput
                .next(".popover")
                .find("a")
                .on("click", function (e) {
                    onPopoverItemClick(e, $(this));
                });
            textInput
                .next(".popover")
                .find(".search-result")
                .on("click", function (e) {
                    onPopoverItemClick(e, $(this));
                });
        }

        function onPopoverItemClick(e, element) {
            e.preventDefault();
            textInput.val("");
            var newValue = element.attr("data-handle");
            if (prepend && startsWith(newValue, prepend)) {
                newValue = newValue.substring(prepend.length);
            }
            editor.setValue(newValue);
            textInput.val(newValue);
            textInput.popover("hide");
        }

        function getTargetObject() {
            link.text("");
            link.attr("href", "#");
            var targetObjectId = prepend + textInput.val();
            if (targetObjectId) {
                resolveHandle(targetObjectId);
            }
        }

        function resolveHandle(targetObjectId) {
            APP.getObject(
                targetObjectId,
                onGotTargetObjectObject,
                onGotTargetObjectError
            );
        }

        function onGotTargetObjectError(res) {
            console.log(res);
        }

        function onGotTargetObjectObject(res) {
            var objectId = res.id;
            var type = res.type;
            renderTargetObjectPreview(res.content, type, objectId);
        }

        function renderTargetObjectPreview(targetObject, type, objectId) {
            var targetObjectSearchResult = {
                id: objectId,
                type: type,
                json: targetObject
            };
            var previewData = ObjectPreviewUtil.getPreviewData(
                targetObjectSearchResult
            );
            var linkText = objectId;
            for (var jsonPointer in previewData) {
                var previewDataItem = previewData[jsonPointer];
                if (previewDataItem.isPrimary) {
                    linkText = previewDataItem.title + ": " + previewDataItem.previewJson;
                }
            }
            link.text(linkText);
            link.attr("href", "#objects/" + objectId);
        }

        function onBlur() {
            textInput.popover("hide");
        }

        function onChange() {
            var now = Date.now();
            if (now - lastQuery >= 500) {
                doQuery();
            } else {
                if (pendingQueryTimeoutId !== null) clearTimeout(pendingQueryTimeoutId);
                pendingQueryTimeoutId = setTimeout(doQuery, 500 - (now - lastQuery));
            }
        }

        function doQuery() {
            var text = textInput.val();
            if (text === "") {
                return;
            }
            var query = "";
            if (types) {
                if (typeof types === "string" || types.length > 0) {
                    query = queryForTypes(types) + " ";
                }
            }
            if (excludeTypes) {
                if (typeof excludeTypes === "string" || excludeTypes.length > 0) {
                    query += "-" + queryForTypes(excludeTypes) + " ";
                }
            }
            query += "+internal.all:(" + text + ")";
            APP.search(query, 0, 10, null, onSuccess, onError);

            lastQuery = Date.now();
            pendingQueryTimeoutId = null;
        }

        function onSuccess(response) {
            lastResults = response;
            textInput.popover("show");
        }

        function onError(response) {
            console.log(response);
        }

        function placeholderForTypes(types) {
            if (excludeTypes) {
                if (typeof excludeTypes === "string") {
                    return "Any type except " + excludeTypes;
                } else if (excludeTypes.length > 0) {
                    var res = "Any type except ";
                    for (var k = 0; k < excludeTypes.length; k++) {
                        if (k > 0) res += ", ";
                        res += excludeTypes[k];
                    }
                    return res;
                }
            }
            if (!types) {
                return "Any type";
            }
            if (typeof types === "string") {
                return types;
            } else {
                if (types.length == 0) {
                    return "Any type"
                }
                var res = "";
                for (var i = 0; i < types.length; i++) {
                    if (i > 0) res += ", ";
                    res += types[i];
                }
                return res;
            }
        }

        function queryForTypes(types) {
            if (typeof types === "string") {
                return "+(type:" + types + ")";
            }
            if (types.length == 0) {
                return "";
            }
            var query = "+(";
            for (var i = 0; i < types.length; i++) {
                if (i > 0) query += " ";
                query += "type:" + types[i];
            }
            query += ")";
            return query;
        }

        constructor();
    }

    window.HandleRefSearchSelector = HandleRefSearchSelector;
})();
