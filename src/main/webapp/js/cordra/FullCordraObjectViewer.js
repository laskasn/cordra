function FullCordraObjectViewer(containerDiv, cordraObject) {
    var self = this;

    function constructor() {
        var cordraObjectDiv = $('<div class="col-md-12 nopadding"></div>');
        containerDiv.append(cordraObjectDiv);

        var attributesDiv = $(
            '<div style="height: 500px; display:block; width:100%;"></div>'
        );
        cordraObjectDiv.append(attributesDiv);

        var options = {
            ace: ace,
            theme: "ace/theme/textmate",
            mode: "code",
            modes: ["code", "tree"], // allowed modes
            onError: function (err) {
                alert(err.toString());
            }
        };
        editor = new JsonEditorOnline(attributesDiv[0], options, cordraObject);
        APP.disableJsonEditorOnline(editor);
    }

    constructor();
}
