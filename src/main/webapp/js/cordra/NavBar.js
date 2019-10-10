(function () {
    "use strict";

    var window = window || self;

    function NavBar(navBarElement, navBarConfig, schemasParam) {
        var self = this;
        var adminDropdownLi = null;

        function constructor() {
            buildConfigurableItems(schemasParam);
            addAdminDropDown();
        }

        function buildConfigurableItems(schemas) {
            for (var i = 0; i < navBarConfig.length; i++) {
                var navLink = navBarConfig[i];
                if (navLink.type === "url") {
                    addUrlLink(navLink);
                } else if (navLink.type === "query") {
                    addQueryLink(navLink);
                } else if (navLink.type === "typeDropdown") {
                    addQueryTypeDropDown(navLink, schemas);
                }
            }
        }

        function addUrlLink(navLink) {
            var li = $("<li></li>");
            var link = $('<a target="_self"></a>');
            link.text(navLink.title);
            link.attr("href", "#urls/" + navLink.url);
            navBarElement.append(li);
            li.append(link);
        }

        function hideAdminMenu() {
            adminDropdownLi.hide();
        }
        self.hideAdminMenu = hideAdminMenu;

        function showAdminMenu() {
            adminDropdownLi.show();
        }
        self.showAdminMenu = showAdminMenu;

        function addAdminDropDown() {
            adminDropdownLi = $('<li class="dropdown" style="display:none"></li>');
            navBarElement.append(adminDropdownLi);
            var dropdownToggle = $(
                '<a href="#" class="dropdown-toggle" data-toggle="dropdown" role="button" aria-expanded="false"> </a>'
            );
            adminDropdownLi.append(dropdownToggle);

            var title = "Admin";
            dropdownToggle.text(title + " ");

            var caret = $('<span class="caret"></span>');
            dropdownToggle.append(caret);

            var dropdownList = $('<ul class="dropdown-menu" role="menu"></ul>');
            adminDropdownLi.append(dropdownList);

            dropdownList.append(createMenuItem("Types", "#schemas"));
            dropdownList.append(createMenuItem("Design Object", "#objects/design"));
            dropdownList.append(createMenuItem("Design JavaScript", "#designJavaScript"));
            dropdownList.append(createMenuItem("UI", "#ui"));
            dropdownList.append(createMenuItem("Authorization", "#authConfig"));
            dropdownList.append(createMenuItem("Handle Records", "#handleRecords"));
            dropdownList.append(createMenuItem("Security", "#networkAndSecurity"));
        }

        function createMenuItem(name, href) {
            var menuItem = $("<li></li>");
            var itemLink = $('<a target="_self"></a>');
            itemLink.text(name);
            itemLink.attr("href", href);
            menuItem.append(itemLink);
            return menuItem;
        }

        function addQueryTypeDropDown(navLink, schemas) {
            var dropdownLi = $('<li class="dropdown types"></li>');
            navBarElement.append(dropdownLi);

            var dropdownToggle = $(
                '<a href="#" class="dropdown-toggle" data-toggle="dropdown" role="button" aria-expanded="false"> </a>'
            );
            dropdownLi.append(dropdownToggle);

            var title = "Show Only";
            if (navLink.title) {
                title = navLink.title;
            }
            dropdownToggle.text(title + " ");

            var caret = $('<span class="caret"></span>');
            dropdownToggle.append(caret);

            var dropdownList = $('<ul class="dropdown-menu" role="menu"></ul>');
            dropdownLi.append(dropdownList);

            var types = Object.keys(schemas);
            types.sort();
            for (var i = 0; i < types.length; i++) {
                var schemaName = types[i];
                if (schemaName === "CordraDesign") continue;
                var schema = schemas[schemaName];
                var linkName = schemaName;
                //            if (schema.title) {
                //                linkName = schema.title;
                //            }
                var menuItem = $("<li></li>");
                dropdownList.append(menuItem);
                var itemLink = $('<a target="_self"></a>');
                itemLink.text(linkName);
                itemLink.attr(
                    "href",
                    "#objects/?query=type:" + encodeURIComponent('"' + schemaName + '"')
                );
                menuItem.append(itemLink);
            }
        }

        function addQueryLink(navLink) {
            var li = $("<li></li>");
            var link = $("<a href=# ></a>");
            link.text(navLink.title);
            link.attr("data-query", navLink.query);
            if (navLink.sortFields) {
                link.attr("data-sort-fields", navLink.sortFields);
            }
            link.on("click", onQueryLinkClick);
            navBarElement.append(li);
            li.append(link);
        }

        function onQueryLinkClick(e) {
            e.preventDefault();
            var link = $(this);
            var query = link.attr("data-query");
            var sortFields = link.attr("data-sort-fields");
            APP.performSearchWidgetSearch(query, sortFields);
        }

        function refresh(newSchemas) {
            navBarElement.empty();
            buildConfigurableItems(newSchemas);
            navBarElement.append(adminDropdownLi);
        }
        self.refresh = refresh;

        constructor();
    }

    window.NavBar = NavBar;
})();
