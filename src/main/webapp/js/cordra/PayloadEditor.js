(function () {
    "use strict";

    var window = window || self;

    function PayloadEditor(
        parentPayloadsEditor,
        payloadTr,
        payload,
        disabled,
        isNewParam
    ) {
        var self = this;
        var nameInput = null;
        var nameLabel = null;
        var fileInput = null;

        var tdName = null;
        var tdFilenameAndSize = null;
        var tdFileInput = null;
        var tdDelete = null;

        var mediaTr = null;
        var textEditorTr = null;
        var mediaContainer = null;
        var textEditor = null;
        var isTextChanged = false;
        var revertButton = null;
        var editButton = null;

        function constructor() {
            tdName = $("<td></td>");
            tdFilenameAndSize = $("<td></td>");
            tdFileInput = $("<td></td>");
            tdDelete = $('<td colspan="2"></td>');

            payloadTr.append(tdName);
            payloadTr.append(tdFilenameAndSize);
            payloadTr.append(tdFileInput);
            payloadTr.append(tdDelete);

            self.objectId = APP.getObjectId();

            fileInput = $("<input/>");
            fileInput.attr("type", "file");

            buildControls(payload);

            if (!disabled) {
                tdFileInput.append(fileInput);
                prettifyThisFileInput(fileInput);
            }
        }

        function isNew() {
            return isNewParam;
        }
        self.isNew = isNew;

        function getName() {
            if (payload) {
                return payload.name;
            } else {
                return nameInput.val();
            }
        }
        self.getName = getName;

        function getNameFromInput() {
            return nameInput.val();
        }
        self.getNameFromInput = getNameFromInput;

        function getNameFromInputOrLabel() {
            if (nameInput != null) {
                return nameInput.val();
            } else {
                return nameLabel.text();
            }
        }
        self.getNameFromInputOrLabel = getNameFromInputOrLabel;

        function getBlob() {
            var blob = fileInput[0].files[0];
            if (blob == null && isTextChanged) {
                blob = new Blob([getCurrentText()], { type: payload.mediaType });
                return new File([blob], payload.filename);
            }
            return blob;
        }
        self.getBlob = getBlob;

        function buildControls(payload) {
            //containerDiv.empty();
            if (!disabled) {
                var closeButton = $(
                    '<button class="btn btn-sm btn-danger pull-right"><i class="fa fa-trash"></i></button>'
                );
                tdDelete.append(closeButton);
                closeButton.on("click", onCloseClick);
            }
            if (isNew()) {
                nameInput = $('<input type="text" style="width:100%" />');
                var payloadName = parentPayloadsEditor.getNextDefaultPayloadName();
                nameInput.val(payloadName);
                tdName.append(nameInput);
                nameInput.trigger("focus");
            } else {
                nameLabel = $("<span></span>");
                tdName.append(nameLabel);
                nameLabel.text(payload.name);

                if (isVideo() || isAudio()) {
                    var playButton = $(
                        '<button class="btn btn-sm btn-default pull-right"><span class="glyphicon glyphicon-play"></span></button>'
                    );
                    tdDelete.append(playButton);
                    playButton.on("click", showMediaPlayer);
                }
                if (!disabled && isText()) {
                    editButton = $(
                        '<button class="btn btn-sm btn-primary pull-right"><i class="fa fa-edit"></i></button>'
                    );
                    tdDelete.append(editButton);
                    editButton.on("click", showTextEditor);

                    revertButton = $(
                        '<button class="btn btn-sm btn-warning pull-right" style="display:none"><i class="fa fa-undo"></i></button>'
                    );
                    tdDelete.append(revertButton);
                    revertButton.on("click", revertTextEditor);

                    var revertButtonSpan = $("<span></span>");
                    revertButton.append(revertButtonSpan);
                    revertButtonSpan.text("Revert");
                }

                var iconName = FileIconUtil.getFontAwesomeIconNameFor(
                    payload.mediaType,
                    payload.filename
                );
                var icon = $('<i class="fa fa-' + iconName + ' fa-1x"></i>');
                tdFilenameAndSize.append(icon);
                tdFilenameAndSize.append(" ");

                var downloadButton = buildDownloadButton(payload.filename);
                tdFilenameAndSize.append(downloadButton);

                var sizeLabel = $('<p class="helpText"></p>');
                sizeLabel.text("[" + payload.size + " bytes" + "]");
                tdFilenameAndSize.append(sizeLabel);
            }
        }

        function isVideo() {
            return FileIconUtil.isVideo(payload.mediaType, payload.filename);
        }

        function isAudio() {
            return FileIconUtil.isAudio(payload.mediaType, payload.filename);
        }

        function isText() {
            return FileIconUtil.isText(payload.mediaType, payload.filename);
        }

        function isJson() {
            return FileIconUtil.isJson(payload.mediaType, payload.filename);
        }

        function isJavaScript() {
            return FileIconUtil.isJavaScript(payload.mediaType, payload.filename);
        }

        function showMediaPlayer() {
            if (mediaTr != null) {
                mediaTr.remove();
                mediaTr = null;
            } else {
                var mediaUri = getUri();
                mediaTr = $("<tr></tr>");
                var td = $('<td colspan="5"></td>');
                var mediaContainer = $('<div style="width:100%"></div>');
                payloadTr.after(mediaTr);
                mediaTr.append(td);
                td.append(mediaContainer);
                APP.getAccessToken().then(function (accessToken) {
                    if (accessToken) {
                        mediaUri += "&access_token=" + accessToken;
                    }
                    var player;
                    if (isVideo()) {
                        player = new VideoPlayer(mediaContainer, mediaUri);
                    } else if (isAudio()) {
                        player = new AudioPlayer(mediaContainer, mediaUri);
                    }
                });
            }
        }

        function isNeedSaving() {
            return isTextChanged;
        }
        self.isNeedSaving = isNeedSaving;

        function getCurrentText() {
            if (textEditor == null) {
                return null;
            }
            return textEditor.getValue();
        }
        self.getCurrentText = getCurrentText;

        var isShowingTextEditor = false;

        function revertTextEditor() {
            if (textEditorTr != null) {
                textEditorTr.remove();
                textEditorTr = null;
                isShowingTextEditor = false;
                isTextChanged = false;
                textEditor = null;
                revertButton.hide();
            }
        }

        function showTextEditor() {
            if (isShowingTextEditor) {
                textEditorTr.hide();
                isShowingTextEditor = false;
            } else {
                if (textEditorTr != null) {
                    textEditorTr.show();
                } else {
                    textEditorTr = $("<tr></tr>");
                    var td = $('<td colspan="5"></td>');
                    mediaContainer = $('<div style="width:100%"></div>');
                    payloadTr.after(textEditorTr);
                    textEditorTr.append(td);
                    td.append(mediaContainer);
                    APP.getPayloadContent(
                        self.objectId,
                        payload.name,
                        onGotPayloadContentSuccess,
                        onGotPayloadError
                    );
                }
                isShowingTextEditor = true;
            }
        }

        function onGotPayloadContentSuccess(res) {
            cnri.BlobUtil.readBlob(res, "text").then(function (blobText) {
                var textEditorDiv = $('<div class="ace_editor"></div>');
                mediaContainer.append(textEditorDiv);
                textEditor = ace.edit(textEditorDiv[0]);
                textEditor.setTheme("ace/theme/textmate");
                if (isJavaScript()) {
                    textEditor.getSession().setMode("ace/mode/javascript");
                } else if (isJson()) {
                    textEditor.getSession().setMode("ace/mode/json");
                }
                textEditor.setOptions({
                    maxLines: Infinity,
                    minLines: 10
                });
                textEditor.$blockScrolling = Infinity;
                textEditor.setValue(blobText, -1);
                textEditor.getSession().on("change", function () {
                    isTextChanged = true;
                    revertButton.show();
                });
            });
        }

        function onGotPayloadError(res) {
            res.json().then(function (json) {
                console.log(json);
            });
        }

        function onCloseClick() {
            parentPayloadsEditor.deletePayload(self);
            payloadTr.remove();
        }

        function buildDownloadButton(text) {
            var form = $('<form style="display:none" method="POST"/>');
            form.attr("action", getDownloadUri());
            var accessTokenInput = $('<input type="hidden" name="access_token"/>');
            form.append(accessTokenInput);
            var downloadButton = $('<a href="#"></a>');
            if (text) {
                downloadButton.text(text);
            } else {
                downloadButton.text("Download");
            }
            downloadButton.on("click", function (event) {
                event.preventDefault();
                APP.getAccessToken().then(function (accessToken) {
                    accessTokenInput.val(accessToken);
                    form.trigger("submit");
                });
            });
            downloadButton.append(form);
            return downloadButton;
        }

        function getUri() {
            // was ../ under classic
            return (
                "objects/" +
                self.objectId +
                "?payload=" +
                encodeURIComponent(payload.name).replace(/%2F/g, "/")
            );
        }

        function getDownloadUri() {
            // was ../ under classic
            return (
                "objects/" +
                self.objectId +
                "?payload=" +
                encodeURIComponent(payload.name).replace(/%2F/g, "/") +
                "&disposition=attachment"
            );
        }

        function prettifyThisFileInput(fileInput) {
            //          if (navigator.userAgent.search("Safari") >= 0 && navigator.userAgent.search("Chrome") < 0) return;
            fileInput = $(fileInput);
            //          if(fileInput.css('left')==='-1999px') return;
            //          fileInput.css('left','-1999px');
            if (fileInput.css("opacity") === "0") return;
            fileInput.css("opacity", "0");
            fileInput.css("z-index", "-100");
            fileInput.css("position", "fixed");
            fileInput.css("left", "-10px");
            fileInput.css("height", "1px");
            fileInput.css("width", "1px");
            fileInput.css("margin", "0");
            fileInput.css("padding", "0");
            var textForButton = "Choose file";
            var button = $(
                '<button class="btn btn-sm btn-primary" type="button"><i class="fa fa-file"></i><span>' +
                textForButton +
                "</span></button>"
            );
            var span = $('<p class="helpText">No files chosen</p>');
            var div = $('<div class="hide-with-buttons"/>');
            div.append(button, span);
            fileInput.before(div);
            button.off("click").on("click", function (event) {
                event.stopImmediatePropagation();
                fileInput.trigger("click");
            });
            fileInput.on("change", function () {
                if (fileInput[0].files.length === 0) {
                    span.text("No files chosen");
                } else if (fileInput[0].files.length === 1) {
                    span.text(fileInput[0].files[0].name);
                } else {
                    span.text(fileInput[0].files.length + " files");
                }
                revertTextEditor();
                if (editButton) editButton.hide();
            });
        }

        constructor();
    }

    window.PayloadEditor = PayloadEditor;
})();
