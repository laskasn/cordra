(function () {
    "use strict";

    var window = window || self;

    function SearchWidget(containerDiv, allowCreate, numTypesForCreateDropdown) {
        var self = this;
        var searchInput = null;
        var paginationDiv = null;
        var paginationBottomDiv = null;
        var searchBarDiv = null;
        var resultsContainer = null;
        var resultsDiv = null;
        var pageSize = 10;
        var lastQueryResponse = null;
        var retrieveJsonForm = null;
        var createButtonSpan = null;

        function constructor() {
            searchBarDiv = $('<div class="row search-bar"></div>');
            containerDiv.append(searchBarDiv);

            var form = $('<form class="form-inline" role="form"></form>');
            searchBarDiv.append(form);
            form.on("submit", function (e) {
                return false;
            });

            var column = $('<div class="col-md-12 nopadding"/>');
            form.append(column);

            var searchInputGroup = $('<div class="input-group"></div>');
            column.append(searchInputGroup);

            searchInput = $(
                '<input type="text" class="form-control" placeholder="Search">'
            );
            searchInputGroup.append(searchInput);
            searchInput.on("keypress", function (event) {
                if (event.which === 13) {
                    event.preventDefault();
                    onSearchButtonClick();
                }
            });

            var buttonSpan = $(
                '<span class="input-group-btn" style="width:1%"></span>'
            );
            searchInputGroup.append(buttonSpan);

            var searchButton = $(
                '<button class="btn btn-primary cordra-search-button" type="button"><i class="fa fa-search"></i><span>Search<span></button>'
            );
            buttonSpan.append(searchButton);
            searchButton.on("click", onSearchButtonClick);

            if (allowCreate) {
                createButtonSpan = $(
                    '<span class="input-group-btn" style="width:1%"></span>'
                );
            } else {
                createButtonSpan = $(
                    '<span class="input-group-btn" style="width:1%; display:none"></span>'
                );
            }

            searchInputGroup.append(createButtonSpan);

            resultsContainer = $(
                '<div class="row search-results-container-with-controls" style="display:none"><div/>'
            );
            containerDiv.append(resultsContainer);

            var paginationContainerTop = $('<div class="pagination-controls-top"/>');
            resultsContainer.append(paginationContainerTop);

            var paginationContainerDiv = $('<div class="row"/>');
            paginationContainerTop.append(paginationContainerDiv);

            // var closeButton = $(
            //   '<button class="btn btn-sm btn-default pull-right"><span class="glyphicon glyphicon-remove"></span></button>'
            // );
            // paginationContainerDiv.append(closeButton);
            // closeButton.on("click", onCloseClick);

            retrieveJsonForm = $(
                '<form style="display:none" method="POST" target="_blank"/>'
            );
            var accessTokenInput = $('<input type="hidden" name="access_token"/>');
            APP.getAccessToken().then(function (accessToken) {
                accessTokenInput.val(accessToken);
            });
            retrieveJsonForm.append(accessTokenInput);
            paginationContainerDiv.append(retrieveJsonForm);

            paginationDiv = $('<div class="col-md-11"></div>');
            paginationContainerDiv.append(paginationDiv);

            var retrieveJsonLink = $(
                '<div class="col-md-1"><a class="link"><i class="fa fa-external-link-alt"></i><span>JSON</span></a></div>'
            );
            // retrieveJsonButton.css("margin-right", "4px");
            retrieveJsonLink.on("click", function (event) {
                event.preventDefault();
                APP.getAccessToken().then(function (accessToken) {
                    accessTokenInput.val(accessToken);
                    retrieveJsonForm.trigger("submit");
                });
            });
            paginationContainerDiv.append(retrieveJsonLink);

            resultsDiv = $('<div class="search-results-container"></div>');
            resultsContainer.append(resultsDiv);

            var paginationContainerBottomDiv = $("<div/>");
            resultsContainer.append(paginationContainerBottomDiv);

            paginationBottomDiv = $('<div class="pagination-controls-bottom"></div>');
            paginationContainerBottomDiv.append(paginationBottomDiv);
        }

        function notCordraDesign(type) {
            return type !== "CordraDesign";
        }

        function setAllowCreateTypes(types) {
            createButtonSpan.empty();
            if (!types) {
                createButtonSpan.hide();
                return;
            }
            types = types.filter(notCordraDesign);
            if (types.length === 0) {
                createButtonSpan.hide();
                return;
            }
            if (types.length === 1) {
                buildSingleCreateButton(createButtonSpan, types[0]);
            } else {
                if (
                    !numTypesForCreateDropdown ||
                    types.length <= numTypesForCreateDropdown
                ) {
                    buildCreateDropdown(createButtonSpan, types);
                } else {
                    buildCreateTypeahead(createButtonSpan, types);
                }
            }
            createButtonSpan.show();
        }
        self.setAllowCreateTypes = setAllowCreateTypes;

        function setAllowCreate(allowCreateParam) {
            allowCreate = allowCreateParam;
            if (allowCreate) {
                createButtonSpan.show();
            } else {
                createButtonSpan.hide();
            }
        }
        self.setAllowCreate = setAllowCreate;

        function buildSingleCreateButton(form, type) {
            var createButton = $(
                '<button class="btn btn-default cordra-create-button"></button>'
            );
            createButton.data("type", type);
            if (type) {
                createButton.text("Create " + type);
            } else {
                createButton.text("Create");
            }
            form.append(createButton);
            createButton.on("click", onCreateButtonClicked);
        }

        function onCreateButtonClicked(e) {
            e.preventDefault();
            var createButton = $(this);
            var type = createButton.data("type");
            APP.setCreateInFragment(type);
        }

        function substringMatcher(strs) {
            return function findMatches(q, cb) {
                var matches, substrRegex;

                // an array that will be populated with substring matches
                matches = [];

                // regex used to determine if a string contains the substring `q`
                substrRegex = new RegExp(q, "i");

                // iterate through the pool of strings and for any string that
                // contains the substring `q`, add it to the `matches` array
                $.each(strs, function (i, str) {
                    if (substrRegex.test(str)) {
                        matches.push(str);
                    }
                });

                cb(matches);
            };
        }

        function buildCreateTypeahead(form, types) {
            var input = $(
                '<input class="typeahead form-control" type="text" placeholder="Type to create">'
            );
            form.append(input);
            types.sort();
            //        input.typeahead({
            //            name: 'Create',
            //            local: types
            //        });
            input.typeahead(
                {
                    hint: true,
                    highlight: true,
                    minLength: 1
                },
                {
                    name: "create",
                    source: substringMatcher(types)
                }
            );

            input.bind("typeahead:select", function (ev, selection) {
                APP.setCreateInFragment(selection);
            });
        }

        function buildCreateDropdown(form, types) {
            var dropdownDiv = $(
                '<div class="dropdown" style="display:inline-block;vertical-align:top"></div>'
            );
            form.append(dropdownDiv);

            var dropdownButton = $(
                '<button class="btn btn-secondary dropdown-toggle cordra-create-button" type="button" id="dropdownMenu1" data-toggle="dropdown">Create <span class="caret"></span></button>'
            );
            dropdownDiv.append(dropdownButton);

            var createList = $(
                '<ul class="dropdown-menu" role="menu" aria-labelledby="dropdownMenu1"></ul>'
            );
            dropdownDiv.append(createList);
            types.sort();
            for (var i = 0; i < types.length; i++) {
                var objectType = types[i];
                var typeTitle = objectType;

                var menuItem = $('<li role="presentation"></li>');
                var menuLink = $(
                    '<a role="menuitem" tabindex="-1" href="#">' + typeTitle + "</a>"
                );
                menuLink.attr("data-objectType", objectType);
                menuItem.append(menuLink);
                createList.append(menuItem);
                menuLink.on("click", onCreateClicked);
            }
        }

        function onCloseClick() {
            APP.clearFragment();
        }

        function hideResults() {
            resultsContainer.hide();
        }
        self.hideResults = hideResults;

        function showResults() {
            resultsContainer.show();
            //        if (lastQueryResponse != null) {
            //            resultsContainer.show(300);
            //        }
        }
        self.showResults = showResults;

        function onCreateClicked(e) {
            e.preventDefault();
            var clickedItem = $(e.target);
            var objectType = clickedItem.attr("data-objectType");
            APP.setCreateInFragment(objectType);
        }

        function onSearchButtonClick() {
            var query = searchInput.val();
            if ("" === query) {
                return;
            }
            if (isObjectId(query)) {
                query = "id:" + query;
            }
            var pageNum = 0;
            APP.setQueryInFragment(query);

            //APP.search(query, pageNum, pageSize, null, function (response) { onSuccess(query, null, response); }, onError);
        }

        function search(query, sortFields) {
            if ("" === query) {
                return;
            }
            searchInput.val(query);
            var pageNum = 0;
            APP.search(
                query,
                pageNum,
                pageSize,
                sortFields,
                function (response) {
                    onSuccess(query, sortFields, response);
                },
                onError
            );
        }
        self.search = search;

        function isObjectId(str) {
            var prefix = APP.getPrefix();
            if (startsWith(str, prefix + "/")) {
                var suffix = str.substring(prefix);
                if (!containsSpaces(suffix)) {
                    return true;
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }

        function containsSpaces(str) {
            return str.lastIndexOf(" ", 0) === 0;
        }

        function startsWith(str, prefix) {
            return str.lastIndexOf(prefix, 0) === 0;
        }

        function onSuccess(query, sortFields, response) {
            APP.notifications.clear();
            //APP.setQueryInFragment(query);
            lastQueryResponse = response;
            paginationDiv.empty();
            paginationBottomDiv.empty();
            resultsDiv.empty();
            var results = response.results;
            if (response.size > pageSize || response.size === -1) {
                var pagination = new SearchResultsPagination(
                    paginationDiv,
                    response.size,
                    response.pageNum,
                    response.pageSize,
                    function (ev) {
                        onPageClick(query, sortFields, ev);
                    }
                );
                var paginationBottom = new SearchResultsPagination(
                    paginationBottomDiv,
                    response.size,
                    response.pageNum,
                    response.pageSize,
                    function (ev) {
                        onPageClick(query, sortFields, ev);
                    }
                );
            } else if (response.size === 0) {
                //no-op
            }
            writeResultsToResultsDiv(results);
            retrieveJsonForm.attr(
                "action",
                getRestApiUriFor(query, response.pageNum, response.pageSize, sortFields)
            );
            resultsContainer.show();
        }

        function getRestApiUriFor(query, pageNum, pageSize, sortFields) {
            // was ../ under classic
            var uri =
                "objects/?query=" +
                encodeURIComponent(query) +
                "&pageNum=" +
                pageNum +
                "&pageSize=" +
                pageSize;
            if (sortFields) {
                uri = uri + "&sortFields=" + sortFields;
            }
            return uri;
        }

        function writeResultsToResultsDiv(results) {
            resultsDiv.empty();
            var list = $('<div class="search-results-list"></div>');
            resultsDiv.append(list);
            if (results.length === 0) {
                var noResultsLabel = $("<label>No Results</label>");
                resultsDiv.append(noResultsLabel);
            }
            for (var i = 0; i < results.length; i++) {
                var result = results[i];
                var searchResult = ObjectPreviewUtil.elementForSearchResult(
                    result,
                    onHandleClick,
                    APP.getUiConfig()
                );
                list.append(searchResult);
            }
        }

        function onPageClick(query, sortFields, ev) {
            ev.preventDefault();
            var pageNum = $(ev.target).data("pageNumber");
            APP.search(
                query,
                pageNum,
                pageSize,
                sortFields,
                function (response) {
                    onSuccess(query, sortFields, response);
                },
                onError
            );
        }

        function onHandleClick(e) {
            e.preventDefault();
            var link = $(this);
            var handle = link.attr("data-handle");
            //var handle = link.text();
            APP.resolveHandle(handle);
        }

        function onError(response) {
            resultsDiv.empty();
            APP.notifications.alertError(response.message);
        }

        constructor();
    }
    window.SearchWidget = SearchWidget;
})();
