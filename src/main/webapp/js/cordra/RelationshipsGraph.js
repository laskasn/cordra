(function () {
    "use strict";

    var window = window || self;

    function RelationshipsGraph(containerDiv, objectId) {
        var self = this;

        var defaultColor = {
            border: "#2B7CE9",
            background: "#97C2FC",
            highlight: { border: "#2B7CE9", background: "#D2E5FF" },
            hover: { border: "#2B7CE9", background: "#D2E5FF" }
        };
        var selectedColor = { background: "Silver" };

        var nodes = null;
        var edges = null;
        var network = null;
        var maxPreviewStringLength = 20;

        var instructions = null;
        var referrersLink = null;

        var canvasDiv = null;
        var resizeButton = null;
        var isBig = false;

        var existingEdges = {};
        var existingNodes = {};

        var currentSelectedNode = null;
        var selectedDetails = null;

        var fanOutAllButton = null;
        var fanOutSelectedButton = null;
        var inboundToggleButton = null;
        var outboundOnly = true;

        var undoFanOutButton = null;

        function constructor() {
            var actionToolBar = $('<div class="action-tool-bar pull-right"></div>');
            containerDiv.append(actionToolBar);

            var graphActionsButtonGroup = $('<div class="btn-group"></div>');
            actionToolBar.append(graphActionsButtonGroup);

            var graphActionsDropDownButton = $(
                '<button type="button" class="btn btn-primary btn-sm dropdown-toggle" data-toggle="dropdown"><i class="fa fa-clipboard-list"></i>Graph Actions...<span class="caret"></span></button>'
            );
            graphActionsButtonGroup.append(graphActionsDropDownButton);

            var graphActionsMenu = $('<ul class="dropdown-menu"></ul>');
            graphActionsButtonGroup.append(graphActionsMenu);

            inboundToggleButton = $(
                '<li><a class="dropdown-item"><i class="fa fa-arrow-right"></i>Restart with inbound links</a></li>'
            );
            graphActionsMenu.append(inboundToggleButton);
            inboundToggleButton.on("click", inboundToggleButtonClick);

            fanOutSelectedButton = $(
                '<li><a class="dropdown-item"><i class="fa fa-compress"></i>Fan Out</a></li>'
            );
            graphActionsMenu.append(fanOutSelectedButton);
            fanOutSelectedButton.on("click", onFanOutSelectedClick);

            fanOutAllButton = $(
                '<li><a class="dropdown-item"><i class="fa fa-expand-arrows-alt"></i>Fan Out All</a></li>'
            );
            graphActionsMenu.append(fanOutAllButton);
            fanOutAllButton.on("click", onFanOutAllClick);

            undoFanOutButton = $(
                '<li><a class="dropdown-item"><i class="fa fa-compress-arrows-alt"></i>Undo Fan Out</a></li>'
            );
            graphActionsMenu.append(undoFanOutButton);
            undoFanOutButton.on("click", deleteLastAddedItems);

            //      resizeButton = $(
            //        '<button class="btn btn-sm btn-primary"><span class="glyphicon glyphicon-resize-full"></span></button>'
            //      );
            //      actionToolBar.append(resizeButton);
            //      resizeButton.on("click", onResizeClick);

            //      var resizeButtonSpan = $("<span></span>");
            //      resizeButton.append(resizeButtonSpan);
            //      resizeButtonSpan.text("Resize");

            //      var closeButton = $(
            //        '<button class="btn btn-sm btn-warning"><i class="fa fa-times-circle"></i></button>'
            //      );
            //      actionToolBar.append(closeButton);
            //      closeButton.on("click", onCloseClick);
            //
            //      var closeButtonSpan = $("<span></span>");
            //      closeButton.append(closeButtonSpan);
            //      closeButtonSpan.text("Close");

            instructions = $(
                "<div>Click and drag to manipulate graph.  Double click to load object.</div>"
            );
            containerDiv.append(instructions);

            addReferrersLinkIfNeeded();

            var clearFix = $('<div class="clearfix"></div>');
            containerDiv.append(clearFix);

            canvasDiv = $('<div style="height:730px"></div>');
            containerDiv.append(canvasDiv);
            canvasDiv.css("visibility", "hidden");

            selectedDetails = $('<div style="margin:5px 5px 0px 10px"></div>');
            containerDiv.append(selectedDetails);

            var requestedLevel = 1;
            APP.getRelationships(
                objectId,
                function (response) {
                    addNewRelationshipsToGraph(response, requestedLevel);
                },
                onGotRelationshipsError,
                outboundOnly
            );
        }

        function destroy() {
            if (network) network.destroy();
        }
        self.destroy = destroy;

        function addReferrersLinkIfNeeded() {
            if (referrersLink) {
                referrersLink.remove();
                referrersLink = null;
            }
            var referrersQuery = "internal.pointsAt:" + objectId;
            APP.search(
                referrersQuery,
                0,
                1,
                null,
                function (response) {
                    APP.notifications.clear();
                    if (response.size !== 0) {
                        addReferrersLink();
                    }
                },
                function (response) {
                    response.json().then(function (json) {
                        APP.notifications.alertError(json.message);
                    });
                }
            );
        }

        function addReferrersLink() {
            referrersLink = $("<span/>");
            referrersLink.append("<br/>");
            referrersLink.append("There are objects which refer to this object.  ");
            var link = $('<a href="#">Click here</a>');
            referrersLink.append(link);
            referrersLink.append(" to list them.");
            instructions.append(referrersLink);
            link.on("click", function (e) {
                e.preventDefault();
                var referrersQuery = "internal.pointsAt:" + objectId;
                APP.performSearchWidgetSearch(referrersQuery);
            });
        }

        function getCurrentTopLevel() {
            var topLevel = 0;
            var nodeIds = nodes.getIds();
            for (var i = 0; i < nodeIds.length; i++) {
                var nodeId = nodeIds[i];
                var node = nodes.get(nodeId);
                if (node.level > topLevel) {
                    topLevel = node.level;
                }
            }

            var edgeIds = edges.getIds();
            for (var i = 0; i < edgeIds.length; i++) {
                var edgeId = edgeIds[i];
                var edge = edges.get(edgeId);
                if (edge.level > topLevel) {
                    topLevel = edge.level;
                }
            }
            return topLevel;
        }

        function buildNetwork() {
            //currently unused
            var data = {
                nodes: nodes,
                edges: edges
            };
            var options = {
                physics: {
                    hierarchicalRepulsion: {
                        nodeDistance: 500
                    },
                    stabilization: {
                        enabled: false
                    }
                },
                hierarchicalLayout: {
                    enabled: true,
                    direction: "UD",
                    levelSeparation: 200,
                    nodeSpacing: 300
                },
                nodes: {
                    shape: "box"
                },
                edges: {
                    length: 500
                }
            };

            network = new vis.Network(canvasDiv.get(0), data, options);
            // network.on('select', onSelect);
            network.on("doubleClick", doubleClick);
            network.selectNodes([objectId]);
            currentSelectedNode = objectId;
            displaySelectedNodeData(currentSelectedNode);

            animatedZoomExtent();
        }

        function buildNetworkDynamicLayout() {
            nodes.add({ id: "fakeNode" });
            var data = {
                nodes: nodes,
                edges: edges
            };
            var options = {
                physics: {
                    barnesHut: {
                        gravitationalConstant: -4250,
                        centralGravity: 0.05,
                        springConstant: 0.002,
                        springLength: 500
                    },
                    stabilization: {
                        iterations: 1500
                    }
                },
                nodes: {
                    shape: "box"
                },
                edges: {
                    arrows: "to",
                    length: 500
                }
            };

            network = new vis.Network(canvasDiv.get(0), data, options);
            network.on("select", onSelect);
            network.on("doubleClick", doubleClick);
            network.selectNodes([objectId]);
            currentSelectedNode = objectId;
            displaySelectedNodeData(currentSelectedNode);

            network.on("stabilized", function () {
                canvasDiv.css("visibility", "visible");
            });

            setTimeout(function () {
                nodes.remove(["fakeNode"]);
            }, 1);

            animatedZoomExtent();
        }

        function animatedZoomExtent() {
            var intervalId = setInterval(function () {
                network.fit();
            }, 1000 / 60);

            network.once("stabilized", function () {
                clearInterval(intervalId);
                network.fit({ animation: { duration: 200 } });
            });

            setTimeout(function () {
                clearInterval(intervalId);
            }, 5000);
        }

        function onCloseClick() {
            APP.hideRelationshipsGraph();
        }

        function addNewRelationshipsToGraph(res, requestedLevel, zoomExtent) {
            var nodesToAdd = [];
            for (var i = 0; i < res.nodes.length; i++) {
                var node = res.nodes[i];
                if (existingNodes[node.id]) {
                    continue;
                }
                existingNodes[node.id] = node;
                if (node.id === objectId) {
                    node.level = 0;
                } else {
                    node.level = requestedLevel;
                }

                nodesToAdd.push(node);
                if (node.id === objectId) {
                    node.color = selectedColor;
                }
                var searchResult = res.results[node.id];
                node.searchResult = searchResult;
                if (searchResult != null) {
                    addPreviewDataToNode(node, searchResult);
                }
            }

            addLabelsToEdges(res.edges, res.results);

            var isEdgesAdded = false;
            if (network == null) {
                nodes = new vis.DataSet();
                edges = new vis.DataSet();
                if (res.nodes.length === 2) {
                    setInitialPositionForNodes(res.nodes);
                }
                nodes.add(nodesToAdd);
                isEdgesAdded = addEdges(res.edges, requestedLevel);
                //buildNetwork();
                buildNetworkDynamicLayout();
            } else {
                nodes.add(nodesToAdd);
                isEdgesAdded = addEdges(res.edges, requestedLevel);
                if (zoomExtent) {
                    animatedZoomExtent();
                }
            }
        }

        function doubleClick(properties) {
            var selectedNodes = properties.nodes;
            if (selectedNodes.length > 0) {
                var firstSelectedNodeId = selectedNodes[0];
                var retainGraph = true;
                APP.resolveHandle(firstSelectedNodeId, retainGraph);
            }
        }

        function inboundToggleButtonClick() {
            inboundToggleButton.trigger("blur");
            if (outboundOnly) {
                inboundToggleButton
                    .children(":first-child")
                    .empty()
                    .append(
                        '<i class="fa fa-arrow-left"></i>Restart with outbound links only'
                    );
                outboundOnly = false;
                //resetNetwork();
                resetNetworkByPrune();
            } else {
                inboundToggleButton
                    .children(":first-child")
                    .empty()
                    .append(
                        '<i class="fa fa-arrow-right"></i>Restart with inbound links'
                    );
                outboundOnly = true;
                //resetNetwork();
                resetNetworkByPrune();
            }
        }

        function resetNetworkByPrune() {
            pruneBackToLevel1OutboundOnly();

            if (outboundOnly) {
                //you are done
            } else {
                //get relationships for the root node
                var requestedLevel = 1;
                var zoomExtent = true;
                APP.getRelationships(
                    objectId,
                    function (response) {
                        addNewRelationshipsToGraph(response, requestedLevel, zoomExtent);
                    },
                    onGotRelationshipsError,
                    outboundOnly
                );
            }
        }

        function deleteLastAddedItems() {
            undoFanOutButton.trigger("blur");
            var currentTopLevel = getCurrentTopLevel();
            if (currentTopLevel === 1) return;

            var nodeIds = nodes.getIds();
            var nodesToDelete = [];

            for (var n = 0; n < nodeIds.length; n++) {
                var nodeId = nodeIds[n];
                var node = nodes.get(nodeId);
                if (node.level === currentTopLevel) {
                    nodesToDelete.push(node);
                }
            }

            var linkIds = edges.getIds();
            var linksToDelete = [];

            for (var l = 0; l < linkIds.length; l++) {
                var linkId = linkIds[l];
                var link = edges.get(linkId);
                if (link.level === currentTopLevel) {
                    linksToDelete.push(link);
                }
            }

            removeNodes(nodesToDelete);
            removeLinks(linksToDelete);

            //currentTopLevel = currentTopLevel -1;
        }

        //get the level 0 node
        //find its outbound links
        //find the nodes those links point at.
        //delete all nodes and links not in the above
        function pruneBackToLevel1OutboundOnly() {
            //      currentTopLevel = 1;
            var rootObjectId = objectId;
            var rootNode = nodes.get(rootObjectId);
            var nodesToKeep = [];
            var linksToDelete = [];

            nodesToKeep.push(rootNode);

            var linkIds = edges.getIds();

            for (var i = 0; i < linkIds.length; i++) {
                var linkId = linkIds[i];
                var link = edges.get(linkId);
                if (link.from === rootObjectId) {
                    var nodeToKeep = nodes.get(link.to);
                    nodesToKeep.push(nodeToKeep);
                } else {
                    linksToDelete.push(link);
                }
            }

            var nodesToDelete = [];

            var nodeIds = nodes.getIds();
            for (var n = 0; n < nodeIds.length; n++) {
                var nodeId = nodeIds[n];
                var node = nodes.get(nodeId);
                if (!isKeepNode(node, nodesToKeep)) {
                    nodesToDelete.push(node);
                }
            }

            removeNodes(nodesToDelete);
            removeLinks(linksToDelete);
        }

        function isKeepNode(node, nodesToKeep) {
            for (var n = 0; n < nodesToKeep.length; n++) {
                var nodeToKeep = nodesToKeep[n];
                if (node.id === nodeToKeep.id) {
                    return true;
                }
            }
            return false;
        }

        function resetNetwork() {
            //      currentTopLevel = 1;
            nodes.clear();
            edges.clear();
            existingEdges = {};
            existingNodes = {};
            var requestedLevel = 1;
            var zoomExtent = true;
            APP.getRelationships(
                objectId,
                function (response) {
                    addNewRelationshipsToGraph(response, requestedLevel, zoomExtent);
                },
                onGotRelationshipsError,
                outboundOnly
            );
        }

        function setNewTargetObject(objectIdParam) {
            nodes.clear();
            edges.clear();
            nodes = new vis.DataSet();
            edges = new vis.DataSet();
            network = null;
            canvasDiv.css("visibility", "hidden");

            objectId = objectIdParam;

            inboundToggleButton
                .children(":first-child")
                .empty()
                .append('<i class="fa-arrow-right"></i>Restart with inbound links');

            outboundOnly = true;
            //      currentTopLevel = 1;

            existingEdges = {};
            existingNodes = {};
            var requestedLevel = 1;
            var zoomExtent = true;
            addReferrersLinkIfNeeded();
            APP.getRelationships(
                objectId,
                function (response) {
                    addNewRelationshipsToGraph(response, requestedLevel, zoomExtent);
                },
                onGotRelationshipsError,
                outboundOnly
            );
        }
        self.setNewTargetObject = setNewTargetObject;

        function removeLinks(links) {
            edges.remove(links);
            for (var i = 0; i < links.length; i++) {
                var link = links[i];
                delete link.id;
                delete link.level; // level not part of edgeName
                var edgeName = JSON.stringify(link);
                delete existingEdges[edgeName];
            }
        }

        function removeNodes(nodesToDelete) {
            nodes.remove(nodesToDelete);
            for (var i = 0; i < nodesToDelete.length; i++) {
                var node = nodesToDelete[i];
                if (node.id === currentSelectedNode) currentSelectedNode = null;
                delete existingNodes[node.id];
            }
        }

        function isLinkInbound(link) {
            var fromNode = existingNodes[link.from];
            var toNode = existingNodes[link.to];
            if (toNode.level < fromNode.level) {
                return true;
            } else {
                return false;
            }
        }

        function setInitialPositionForNodes(nodes) {
            nodes[0].y = 200;
            nodes[0].allowedToMoveY = true;

            nodes[1].y = 600;
            nodes[1].allowedToMoveY = true;
        }

        function addLabelsToEdges(edges, searchResultsMap) {
            for (var i = 0; i < edges.length; i++) {
                var edge = edges[i];
                var fromId = edge.from;
                var fromSearchResult = searchResultsMap[fromId];
                if (fromSearchResult != null) {
                    addLabelToEdge(edge, fromSearchResult);
                }
            }
        }

        function addLabelToEdge(edge, fromSearchResult) {
            var jsonPointer = edge.jsonPointer;
            var schema = APP.getSchema(fromSearchResult.type);
            var pointerToSchemaMap = SchemaExtractorFactory.get().extract(
                fromSearchResult.json,
                schema
            );
            var subSchema = pointerToSchemaMap[jsonPointer];
            if (subSchema === undefined) return;
            var handleReferenceNode = SchemaUtil.getDeepCordraSchemaProperty(
                subSchema,
                "type",
                "handleReference"
            );
            if (handleReferenceNode === undefined) return;
            var handleReferenceType = handleReferenceNode["types"];
            if (!handleReferenceType) return;
            var idPointedToByReference = JsonUtil.getJsonAtPointer(
                fromSearchResult.json,
                jsonPointer
            );
            var handleReferencePrepend = handleReferenceNode["prepend"];
            if (
                !handleReferencePrepend &&
                handleReferenceNode["prependHandleMintingConfigPrefix"]
            ) {
                var prefix = APP.getPrefix();
                if (prefix) handleReferencePrepend = ensureSlash(prefix);
            }
            if (handleReferencePrepend)
                idPointedToByReference =
                    handleReferencePrepend + idPointedToByReference;
            if (idPointedToByReference !== edge.to) return;

            var handleReferenceName = handleReferenceNode["name"];
            if (!handleReferenceName) {
                edge.label = jsonPointer;
            } else {
                if (
                    startsWith(handleReferenceName, "{{") &&
                    endsWith(handleReferenceName, "}}")
                ) {
                    var expression = handleReferenceName.substr(
                        2,
                        handleReferenceName.length - 4
                    );
                    var label = getValueForExpression(
                        jsonPointer,
                        expression,
                        fromSearchResult.json
                    );
                    if (label !== "" && label !== null) {
                        edge.label = label;
                    }
                } else {
                    edge.label = handleReferenceName;
                }
            }
        }

        function ensureSlash(prefix) {
            if (prefix.length === 0) return "/";
            if (prefix.substring(prefix.length - 1) === "/") {
                return prefix;
            }
            return prefix + "/";
        }

        function getValueForExpression(jsonPointer, expression, jsonObject) {
            var result = "";
            var segments = jsonPointer.split("/").slice(1);
            if (startsWith(expression, "/")) {
                //treat the expression as a jsonPointer starting at the root
                result = JsonUtil.getJsonAtPointer(jsonObject, expression);
            } else if (startsWith(expression, "..")) {
                var segmentsFromRelativeExpression = expression.split("/").slice(1);
                segments.pop();
                var combinedSegments = segments.concat(segmentsFromRelativeExpression);
                var jsonPointerFromExpression = getJsonPointerFromSegments(
                    combinedSegments
                );
                result = JsonUtil.getJsonAtPointer(
                    jsonObject,
                    jsonPointerFromExpression
                );
            } else {
                //consider the expression to be a jsonPointer starting at the current jsonPointer
                var targetPointer = jsonPointer + "/" + expression;
                result = JsonUtil.getJsonAtPointer(jsonObject, targetPointer);
            }
            if (typeof result !== "string") {
                result = JSON.stringify(result);
            }
            return result;
        }

        function getJsonPointerFromSegments(segments) {
            var jsonPointer = "";
            for (var i = 0; i < segments.length; i++) {
                var segment = segments[i];
                var encodedSegment = JsonUtil.encodeJsonPointerSegment(segment);
                jsonPointer = jsonPointer + "/" + encodedSegment;
            }
            return jsonPointer;
        }

        function addPreviewDataToNode(node, searchResult) {
            var nodeId = node.id;
            if (nodeId.length > 30) {
                nodeId = nodeId.substring(0, 30) + "...";
            }
            node.label = nodeId;

            //      node.label += "\n" + "Level" + ": " + node.level;

            var previewData = ObjectPreviewUtil.getPreviewData(searchResult);
            for (var jsonPointer in previewData) {
                var thisPreviewData = previewData[jsonPointer];
                if (thisPreviewData.isPrimary) {
                    var prettifiedPreviewData = ObjectPreviewUtil.prettifyPreviewJson(
                        thisPreviewData.previewJson,
                        maxPreviewStringLength
                    );
                    if (!prettifiedPreviewData) continue;
                    node.label +=
                        "\n" + thisPreviewData.title + ": " + prettifiedPreviewData;
                }
            }

            //If there are multiple schemas in Cordra include the type
            if (APP.getSchemaCount() > 1) {
                var schema = APP.getSchema(searchResult.type);
                var typeTitle = searchResult.type;
                if (schema && schema.title) {
                    typeTitle = schema.title;
                }
                if (typeTitle) node.label += "\n" + "Type" + ": " + typeTitle;
            }
        }

        function addEdges(edgesToAdd, requestedLevel) {
            var added = false;
            for (var i = 0; i < edgesToAdd.length; i++) {
                var edge = edgesToAdd[i];
                var edgeName = JSON.stringify(edge);
                if (existingEdges[edgeName] === true) {
                    //we already have this edge don't add it
                } else {
                    existingEdges[edgeName] = true;
                    edge.level = requestedLevel;
                    edges.add(edge);
                    added = true;
                }
            }
            return added;
        }

        function onFanOutAllClick() {
            var requestedLevel = getCurrentTopLevel() + 1;
            for (var nodeId in existingNodes) {
                APP.getRelationships(
                    nodeId,
                    function (response) {
                        addNewRelationshipsToGraph(response, requestedLevel);
                    },
                    onGotRelationshipsError,
                    outboundOnly
                );
            }
        }

        function onSelect(properties) {
            var selectedNodes = properties.nodes;
            if (selectedNodes.length > 0) {
                var firstSelectedNode = selectedNodes[0];
                currentSelectedNode = firstSelectedNode;
                displaySelectedNodeData(currentSelectedNode);
            } else {
                selectedDetails
                    .empty()
                    .append($('<ul class="graph-selected-node-preview"></ul>'));
            }
        }

        function displaySelectedNodeData(selectedNodeId) {
            var node = existingNodes[selectedNodeId];
            var searchResult = node.searchResult;
            var previewData = ObjectPreviewUtil.getPreviewData(searchResult);
            var ul = $('<ul class="graph-selected-node-preview"></ul>');
            var placedId = false;
            for (var jsonPointer in previewData) {
                var thisPreviewData = previewData[jsonPointer];
                var prettifiedPreviewData = ObjectPreviewUtil.prettifyPreviewJson(
                    thisPreviewData.previewJson
                );
                if (!prettifiedPreviewData) continue;
                var nodeDetails = $("<li/>");
                if (thisPreviewData.isPrimary) {
                    var b = $("<b/>");
                    nodeDetails.append(b);
                    nodeDetails = b;
                }
                if (thisPreviewData.excludeTitle) {
                    nodeDetails.text(prettifiedPreviewData);
                } else {
                    nodeDetails.text(
                        thisPreviewData.title + ": " + prettifiedPreviewData
                    );
                }
                if (thisPreviewData.isPrimary && !placedId) {
                    ul.prepend($("<li/>").text("Id: " + searchResult.id));
                    ul.prepend(nodeDetails);
                    placedId = true;
                } else {
                    ul.append(nodeDetails);
                }
            }
            if (!placedId) {
                ul.prepend($("<li/>").append($("<b/>").text("Id: " + searchResult.id)));
            }
            //ul.prepend($('<li/>').append($('<b/>').text("level: " + node.level)));
            selectedDetails.empty().append(ul);
            onResize(false);
        }

        function onFanOutSelectedClick() {
            fanOutSelectedButton.trigger("blur");
            if (currentSelectedNode != null) {
                //          currentTopLevel = currentTopLevel +1;
                var requestedLevel = getCurrentTopLevel() + 1;
                APP.getRelationships(
                    currentSelectedNode,
                    function (response) {
                        addNewRelationshipsToGraph(response, requestedLevel);
                    },
                    onGotRelationshipsError,
                    outboundOnly
                );
            }
        }

        function onResizeClick() {
            // prevent button from staying grey after the resize
            resizeButton
                .trigger("blur")
                .hide()
                .show(0);
            if (isBig) {
                containerDiv.removeClass("cordra-big-modal");
                $("body").removeClass("modal-open");
                resizeButton
                    .children(":first-child")
                    .replaceWith('<span class="glyphicon glyphicon-resize-full"></span>');
                canvasDiv.height(770);
                isBig = false;
            } else {
                containerDiv.addClass("cordra-big-modal");
                $("body").addClass("modal-open");
                resizeButton
                    .children(":first-child")
                    .replaceWith(
                        '<span class="glyphicon glyphicon-resize-small"></span>'
                    );
                isBig = true;
            }
            onResize(true);
            animatedZoomExtent();
        }

        function onResize(force) {
            if (isBig) {
                canvasDiv.height(
                    containerDiv.height() - selectedDetails.outerHeight(true) - 52
                );
            }
            if (isBig || force) {
                network.setSize(canvasDiv.width(), canvasDiv.height());
                network.redraw();
            }
        }
        self.onResize = onResize;

        function onGotRelationshipsError(res) {
            console.log(res);
        }

        function startsWith(str, prefix) {
            return str.lastIndexOf(prefix, 0) === 0;
        }

        function endsWith(str, suffix) {
            return str.indexOf(suffix, str.length - suffix.length) !== -1;
        }

        constructor();
    }

    window.RelationshipsGraph = RelationshipsGraph;
})();
