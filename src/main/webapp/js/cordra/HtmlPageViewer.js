(function () {
    "use strict";

    var window = window || self;

    function HtmlPageViewer(containerDiv, options) {
        var self = this;

        function constructor() {
            containerDiv.empty();
            // var closeButton = $(
            //   '<button class="btn btn-sm btn-default pull-right"><span class="glyphicon glyphicon-remove"></span></button>'
            // );
            // containerDiv.append(closeButton);
            // closeButton.on("click", onCloseClick);
            var contentUrl = options.url;

            //        var base = $('<base target="_blank" />');
            //        containerDiv.append(base);

            var iframe = $(
                '<iframe style="width:100%; min-height:217px; max-height:1200px" frameborder="0" scrolling="no" marginheight="0" marginwidth="0"></iframe>'
            );
            iframe.attr('src', contentUrl);
            iframe.on("load", function () {
                if (iframe[0].contentDocument) {
                    iframe.height(iframe[0].contentDocument.body.scrollHeight);
                }
                if (217 === iframe.height()) {
                    iframe.css("min-height", "900px");
                }
            });
            containerDiv.append(iframe);
        }

        function onCloseClick() {
            APP.clearFragment();
            //APP.hideHtmlContent();
        }

        constructor();
    }

    window.HtmlPageViewer = HtmlPageViewer;
})();
