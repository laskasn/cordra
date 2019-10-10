(function(){
"use strict";

var window = window || self;

var SchemaUtil = {};
window.SchemaUtil = SchemaUtil;

function getDeepCordraSchemaProperty(obj) {
    var args = Array.prototype.slice.call(arguments);
    args.shift();
    args.unshift(Constants.CORDRA_SCHEMA_KEYWORD);
    args.unshift(obj);
    var res = JsonUtil.getDeepProperty.apply(null, args);
    if (res !== null && res !== undefined) return res;
    args[1] = Constants.OLD_REPOSITORY_SCHEMA_KEYWORD;
    res = JsonUtil.getDeepProperty.apply(null, args);
    return res;
}
SchemaUtil.getDeepCordraSchemaProperty = getDeepCordraSchemaProperty;

})();
