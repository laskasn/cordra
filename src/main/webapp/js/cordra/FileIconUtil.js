(function(){
"use strict";

var window = window || self;

var FileIconUtil = {};
window.FileIconUtil = FileIconUtil;

function getFontAwesomeIconNameFor(mediaType, filename) {
    if (!mediaType && !filename) {
        return "file-o";
    }
    if (isMsWord(mediaType, filename))          return "file-word-o";
    if (isMsExcel(mediaType, filename))         return "file-excel-o";
    if (isMsPowerpoint(mediaType, filename))    return "file-powerpoint-o";
    if (isZip(mediaType, filename))             return "file-zip-o";
    if (isImage(mediaType, filename))           return "file-image-o";
    if (isPdf(mediaType, filename))             return "file-pdf-o";
    if (isVideo(mediaType, filename))           return "file-video-o";
    if (isAudio(mediaType, filename))           return "file-audio-o";
    if (isText(mediaType, filename))            return "file-text-o";
    return "file-o";
}
FileIconUtil.getFontAwesomeIconNameFor = getFontAwesomeIconNameFor;

function isMsWord(mediaType, filename) {
    if (mediaType === "application/msword") return true;
    else if (endsWith(filename, ".doc")) return true;
    else if (endsWith(filename, ".docx")) return true;
    return false;
}

function isMsExcel(mediaType, filename) {
    if (mediaType === "application/vnd.ms-excel") return true;
    else if (endsWith(filename, ".xls")) return true;
    else if (endsWith(filename, ".xlsx")) return true;
    return false;
}

function isMsPowerpoint(mediaType, filename) {
    if (mediaType === "application/vnd.ms-powerpoint") return true;
    else if (endsWith(filename, ".ppt")) return true;
    else if (endsWith(filename, ".pptx")) return true;
    return false;
}

function isZip(mediaType, filename) {
    if (mediaType === "application/zip") return true;
    else if (endsWith(filename, ".zip")) return true;
    else if (endsWith(filename, ".gz")) return true;
    else if (endsWith(filename, ".tar")) return true;
    return false;
}

function isText(mediaType, filename) {
    if (endsWith(filename, ".txt")) return true;
    if (endsWith(filename, ".js")) return true;
    if (endsWith(filename, ".json")) return true;
    return false;
}
FileIconUtil.isText = isText;

function isJson(mediaType, filename) {
    if (endsWith(filename, ".json")) return true;
    return false;
}
FileIconUtil.isJson = isJson;

function isJavaScript(mediaType, filename) {
    if (endsWith(filename, ".js")) return true;
    return false;
}
FileIconUtil.isJavaScript = isJavaScript;

function isImage(mediaType, filename) {
    if (endsWith(filename, ".gif")) return true;
    else if (endsWith(filename, ".jpg")) return true;
    else if (endsWith(filename, ".jpeg")) return true;
    else if (endsWith(filename, ".tiff")) return true;
    else if (endsWith(filename, ".png")) return true;
    else if (endsWith(filename, ".bmp")) return true;
    return false;
}

function isVideo(mediaType, filename) {
    if (mediaType && startsWith(mediaType, "video/")) {
        return true;
    }
    if (!filename) {
        return false;
    }
    var videoExensions = [".3gp", ".amv", ".asf", ".avi", ".divx", ".mov", ".mp4", ".mpg", ".mpeg", ".qt", ".rm", ".wmv"];
    for (var i = 0; i < videoExensions.length; i++) {
        var extension = videoExensions[i];
        if (endsWith(filename, extension)) return true;
    }
    return false;
}
FileIconUtil.isVideo = isVideo;


function isAudio(mediaType, filename) {
    if (mediaType && startsWith(mediaType, "audio/")) {
        return true;
    }
    if (!filename) {
        return false;
    }
    var audioExensions = [".mp3", ".wav", ".ogg", ".wma"];
    for (var i = 0; i < audioExensions.length; i++) {
        var extension = audioExensions[i];
        if (endsWith(filename, extension)) return true;
    }
    return false;
}
FileIconUtil.isAudio = isAudio;

function isPdf(mediaType, filename) {
    if (endsWith(filename, ".pdf")) return true;
    return false;
}

function endsWith(str, suffix) {
    return str.indexOf(suffix, str.length - suffix.length) !== -1;
}

function startsWith(str, prefix) {
    return str.lastIndexOf(prefix, 0) === 0
}

})();
