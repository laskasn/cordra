function UploadProgressBar(containerDiv) {
    var self = this;
    var progressBar = null;
    var statusLabel = null;

    function constructor() {
        build();
    }

    function build() {
        var progressDiv = $('<div class="progress"></div>');
        progressDiv.css("margin-bottom", "0");
        progressDiv.css("margin-top", "10px");
        containerDiv.append(progressDiv);
        progressBar = $('<div class="progress-bar" role="progressbar" aria-valuenow="60" aria-valuemin="0" aria-valuemax="100" style="width: 0%"></div>');
        progressDiv.append(progressBar);
        statusLabel = $('<div></div>');
        containerDiv.append(statusLabel);
    }

    function setStatus(percent, bytesComplete, bytesTotal) {
        var percentString = percent + "%";
        progressBar.width(percentString);
        var statusText = percentString + " ("+ bytesComplete + "/" + bytesTotal + " bytes)";
        if (percent === 100) {
            statusText = "Upload complete, processing data...";
        }
        statusLabel.text(statusText);
    }
    self.setStatus = setStatus;

    function clear() {
        containerDiv.empty();
        build();
    }
    self.clear = clear;

    constructor();
}
