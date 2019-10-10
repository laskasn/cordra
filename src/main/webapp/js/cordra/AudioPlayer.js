function AudioPlayer(containerDiv, audioUri) {
    var self = this;

    function constructor() {
        var width = 500;
        var audio = $('<audio style="width:'+width+'px" controls>');
        audio.attr("src", audioUri);
        containerDiv.append(audio);
    }

    constructor();
}
