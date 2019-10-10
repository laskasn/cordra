var cordra = require("cordra");

exports.beforeSchemaValidation = beforeSchemaValidation;

function beforeSchemaValidation(obj, context) {
    if (!obj.identifier) obj.identifier = "";
    if (!obj.password) obj.password = "";
    var password = obj.password;
    if (context.isNew || password) {
        if (password.length < 8) {
            throw "Password is too short. Min length 8 characters";
        }
    }
    return obj;
}



