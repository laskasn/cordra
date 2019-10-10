(function () {
    "use strict";

    function elementForSearchResult(searchResult, onHandleClick, uiConfig) {
        var searchResultsConfig = {};
        if (uiConfig) {
            if (uiConfig.searchResults) {
                searchResultsConfig = uiConfig.searchResults;
            }
        }
        var handleString = searchResult.id;
        var searchResultDiv = $('<div class="search-result col-md-12"/>');

        var previewData = getPreviewData(searchResult);

        var headerRow = $('<div class="header row"/>');
        searchResultDiv.append(headerRow);

        var header = $('<h4 class="col-md-12 col-sm-12 col-xs-12"></h4>');
        headerRow.append(header);

        // was ../ under classic
        var link = $('<a class="list-handles-link" target="_blank">')
            .attr("href", "#objects/" + handleString)
            .text(handleString);
        link.attr("data-handle", handleString);

        var metadataDiv = $('<div class="info row"></div>');
        for (var jsonPointer in previewData) {
            var thisPreviewData = previewData[jsonPointer];
            var prettifiedPreviewData = prettifyPreviewJson(
                thisPreviewData.previewJson
            );
            if (!prettifiedPreviewData) continue;

            if (thisPreviewData.isPrimary) {
                // was ../ under classic
                link = $('<a class="list-handles-link" target="_blank">')
                    .attr("href", "#objects/" + handleString)
                    .text(prettifiedPreviewData);
                link.attr("data-handle", handleString);
            } else {
                var metadataBodyDiv = $('<div class="info-body col-md-12"/>');
                if (thisPreviewData.excludeTitle) {
                    if (thisPreviewData.isUri) {
                        var link = $('<a target="_blank"></a>');
                        link.text(prettifiedPreviewData);
                        link.attr("href", prettifiedPreviewData);
                        metadataBodyDiv.append(link);
                    } else {
                        metadataBodyDiv.text(prettifiedPreviewData);
                    }
                } else {
                    if (thisPreviewData.isUri) {
                        var link = $('<a target="_blank"></a>');
                        link.text(prettifiedPreviewData);
                        link.attr("href", prettifiedPreviewData);
                        metadataBodyDiv.text(thisPreviewData.title + ": ");
                        metadataBodyDiv.append(link);
                    } else {
                        var infoHeaderSpan = $('<span class="info-header"><span>').text(
                            thisPreviewData.title
                        );
                        var infoContentSpan = $('<span class="info-content"></span>').text(
                            prettifiedPreviewData
                        );
                        metadataBodyDiv.append(infoHeaderSpan).append(infoContentSpan);
                    }
                }
                metadataDiv.append(metadataBodyDiv);
            }
        }

        header.prepend(link);

        if (onHandleClick != null) {
            link.on("click", onHandleClick);
        }

        var basicMetadata = $('<div class="metadata row"></div>');

        var objectIdSpan = $(
            '<div class="col-md-12 col-sm-12 col-xs-12" title="Object ID" data-original-title="ObjectID"></div>'
        );
        objectIdSpan.append('<i class="fa fa-bullseye"></i>');
        var objectIdLink = $('<a class="list-handles-link" target="_blank">')
            .attr("href", "#objects/" + handleString)
            .text(handleString);
        objectIdLink.attr("data-handle", handleString);
        objectIdSpan.append(objectIdLink);

        if (onHandleClick != null) {
            objectIdLink.on("click", onHandleClick);
        }

        basicMetadata.append(objectIdSpan);

        // if (searchResult.payloads) {
        //   if (searchResult.payloads.length > 0) {
        //     var attachmentsSpan = $(
        //       '<div class="col-md-12 col-sm-12 col-xs-12" title="Number of Payloads" data-original-title="Number of Payloads"></div>'
        //     );
        //     attachmentsSpan.append('<i class="fa fa-paperclip"></i>');
        //     var payloadsHelperText =
        //       searchResult.payloads.length === 1 ? " payload" : " payloads";
        //     attachmentsSpan.append(
        //       searchResult.payloads.length + payloadsHelperText
        //     );
        //     basicMetadata.append(attachmentsSpan);
        //   }
        // }

        //If there are multiple schemas in Cordra include the type in the search results
        if (APP.getSchemaCount() > 1) {
            var schema = APP.getSchema(searchResult.type);
            var typeTitle = searchResult.type;
            if (schema && schema.title) {
                typeTitle = schema.title;
            }
            if (searchResultsConfig.includeType) {
                var objectTypeDiv = $(
                    '<div class="col-md-12 col-sm-12 col-xs-12" class="top" title="Object Type" data-original-title="Object Type"></span>'
                );
                objectTypeDiv.append('<i class="fa fa-file-alt"></i>');
                objectTypeDiv.append(typeTitle + " type");
                basicMetadata.append(objectTypeDiv);
            }
        }

        if (searchResult.metadata.createdOn) {
            if (searchResultsConfig.includeCreatedDate) {
                var creationTimeDiv = $(
                    '<div class="col-md-12 col-sm-12 col-xs-12" title="Object Creation Time" data-original-title="Object Creation Time"><i class="fa fa-calendar"></i></span>'
                );
                creationTimeDiv.append(
                    new Date(searchResult.metadata.createdOn).toISOString()
                );
                basicMetadata.append(creationTimeDiv);
            }
        }
        if (searchResult.metadata.modifiedOn) {
            if (searchResultsConfig.includeModifiedDate) {
                var modifiedTimeDiv = $(
                    '<div class="col-md-3 col-sm-6 col-xs-12" title="Object Last Modified" data-original-title="Object Last Modified"><i class="fa fa-calendar-plus"></i></span>'
                );
                modifiedTimeDiv.append(
                    new Date(searchResult.metadata.modifiedOn).toISOString()
                );
                basicMetadata.append(modifiedTimeDiv);
            }
        }

        searchResultDiv.append(basicMetadata);

        if (metadataDiv.children().length > 0) searchResultDiv.append(metadataDiv);
        return searchResultDiv;
    }

    function elementForSuggestion(searchResult) {
        var handleString = searchResult.id;
        var searchResultDiv = $('<div class="search-result col-md-12"/>');
        searchResultDiv.attr("data-handle", handleString);
        var previewData = getPreviewData(searchResult);
        var headerRow = $('<div class="header row"/>');
        searchResultDiv.append(headerRow);
        var header = $('<h4 class="col-md-12"></h4>');
        headerRow.append(header);
        // was ../ under classic
        var link = $('<a class="list-handles-link" target="_blank">')
            .attr("href", "#objects/" + handleString)
            .text(handleString);
        link.attr("data-handle", handleString);

        for (var jsonPointer in previewData) {
            var thisPreviewData = previewData[jsonPointer];
            var prettifiedPreviewData = prettifyPreviewJson(
                thisPreviewData.previewJson
            );
            if (!prettifiedPreviewData) continue;

            if (thisPreviewData.isPrimary) {
                // was ../ under classic
                link = $('<a class="list-handles-link" target="_blank">')
                    .attr("href", "#objects/" + handleString)
                    .text(prettifiedPreviewData);
                link.attr("data-handle", handleString);
            }
        }
        header.prepend(link);
        var objectIdSpan = $(
            '<div class="header-id col-md-12" title="Object ID" data-original-title="ObjectID"></div>'
        );
        var objectIdLink = $('<a class="list-handles-link" target="_blank">')
            .attr("href", "#objects/" + handleString)
            .text(handleString);
        objectIdLink.attr("data-handle", handleString);
        objectIdSpan.append(objectIdLink);

        headerRow.append(objectIdSpan);
        return searchResultDiv;
    }

    function prettifyPreviewJson(previewJson, maxLength) {
        var result = null;
        if (typeof previewJson === "string") {
            result = previewJson;
        } else {
            result = JSON.stringify(previewJson);
        }
        if (maxLength != null && maxLength != undefined) {
            if (result.length > maxLength) {
                result = result.substring(0, maxLength) + "...";
            }
        }
        return result;
    }

    function getPreviewData(searchResult) {
        var res = {};
        var schema = APP.getSchema(searchResult.type);
        if (!schema) return res;
        var content = searchResult.content;
        if (!content) content = searchResult.json; // old-style search result
        var pointerToSchemaMap = SchemaExtractorFactory.get().extract(
            content,
            schema
        );
        var foundPrimary = false;
        for (var jsonPointer in pointerToSchemaMap) {
            var subSchema = pointerToSchemaMap[jsonPointer];
            var previewNode = SchemaUtil.getDeepCordraSchemaProperty(
                subSchema,
                "preview"
            );
            if (!previewNode) continue;
            var showInPreview = previewNode["showInPreview"];
            var isPrimary = previewNode["isPrimary"];
            var excludeTitle = previewNode["excludeTitle"];
            if (!showInPreview) continue;
            var title = subSchema["title"];
            if (!title) title = jsonPointer;
            var previewJson = JsonUtil.getJsonAtPointer(content, jsonPointer);
            var data = { title: title, previewJson: previewJson };
            if (subSchema.format === "uri") {
                data.isUri = true;
            }
            if (isPrimary && !foundPrimary) {
                data.isPrimary = true;
                foundPrimary = true;
            }
            if (excludeTitle) {
                data.excludeTitle = true;
            }
            res[jsonPointer] = data;
        }
        return res;
    }

    var ObjectPreviewUtil = {};
    ObjectPreviewUtil.elementForSearchResult = elementForSearchResult;
    ObjectPreviewUtil.elementForSuggestion = elementForSuggestion;
    ObjectPreviewUtil.getPreviewData = getPreviewData;
    ObjectPreviewUtil.prettifyPreviewJson = prettifyPreviewJson;
    window.ObjectPreviewUtil = ObjectPreviewUtil;
})();
