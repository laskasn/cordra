function VideoPlayer(containerDiv, videoUri) {
    var self = this;

    function constructor() {
        //var width = containerDiv.width();
        var width = 500;
        var video = $('<video style="width:'+width+'px" controls>');
        video.attr("src", videoUri);
        containerDiv.append(video);
    }

    constructor();
}
