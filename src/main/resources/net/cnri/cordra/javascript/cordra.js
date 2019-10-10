module.exports.get = function (id, full) {
    return JSON.parse(_cordraReturningStrings.get(id, full));
};

module.exports.search = function (query, pageNum, pageSize, sortFieldsString) {
    if (pageNum === undefined) pageNum = 0;
    if (pageSize === undefined) pageSize = -1;
    return JSON.parse(_cordraReturningStrings.search(query, pageNum, pageSize, sortFieldsString));
};

