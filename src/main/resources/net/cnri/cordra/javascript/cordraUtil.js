module.exports.hashJson = function (jsElement) {
    return _cordraUtil.hashJson(JSON.stringify(jsElement));
};

module.exports.escape = function (s) {
    return _cordraUtil.escape(s);
};

module.exports.verifyHashes = function (coNashorn) {
    return JSON.parse(_cordraUtil.verifyHashes(JSON.stringify(coNashorn)));
};

module.exports.verifySecret = function (objectJsonOrString, jsonPointer, secret) {
    var objectString;
    if (typeof objectJsonOrString === 'string') {
        objectString = objectJsonOrString;
    } else {
        objectString = JSON.stringify(objectJsonOrString);
    }
    return JSON.parse(_cordraUtil.verifySecret(objectString, jsonPointer, secret));
};