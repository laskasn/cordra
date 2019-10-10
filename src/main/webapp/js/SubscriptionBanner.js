function SubscriptionBanner(containerDiv, prefixParam) {
    var self = this;
    var prefix = null;
    var ONE_DAY = 1000 * 60 * 60 * 24;

    function constructor() {
        if (!prefixParam) {
            return;
        }
        if (!startsWith(prefixParam, "0.NA/") && !startsWith(prefixParam, "0.na/")) {
            prefix = "0.NA/" + prefixParam;
        } else {
            prefix = prefixParam;
        }

        var uri = "https://hdl.handle.net/api/handles/" + prefix;

        fetch(uri).then(getResponseJson)
            .then(function (handleRecord) {
                var subscriptionInfoHandleValue = getValueByType(handleRecord, "PREFIX_SUBSCRIPTION_INFO");
                if (subscriptionInfoHandleValue) {
                    var subscriptionInfoJson = subscriptionInfoHandleValue.data.value;
                    var subscriptionInfo = JSON.parse(subscriptionInfoJson);
                    displaySubscriptionBanner(subscriptionInfo);
                } else {
                    //no subscription info, do not display banner
                }
            })
            .catch(function (reason) {
                // no-op
            });
    }

    function getResponseJson(response) {
        return response.json();
    }

    function displaySubscriptionBanner(subscriptionInfo) {
        var closeButton = $('<button class="btn btn-sm btn-default pull-right"><span class="glyphicon glyphicon-remove"></span></button>');
        containerDiv.append(closeButton);
        closeButton.on('click', onCloseClick);

        var expirationDateISO8601 = subscriptionInfo.expirationDate;
        var expirationDate = new Date(expirationDateISO8601);
        var duration = getTimeUntilExpiration(expirationDate);
        var daysLeft = msToDays(duration);
        var message = null;
        if (daysLeft > 1) {
            message = $('<p>Handle Prefix '+prefix+' will expire in '+ daysLeft +' days. Click <a href="https://www.handle.net">here</a> to convert the prefix to operational status.</p>');
        } else if (daysLeft === 1) {
            message = $('<p>Handle Prefix '+prefix+' will expire in '+ daysLeft +' day. Click <a href="https://www.handle.net/">here</a> to convert the prefix to operational status.</p>');
        } else if (daysLeft < 1) {
            message = $('<p>Handle Prefix '+prefix+' has expired. Click <a href="https://www.handle.net">here</a> to convert the prefix to operational status.</p>');
        }
        containerDiv.append(message);
        containerDiv.show();
    }

    function onCloseClick() {
        containerDiv.hide();
    }

    function msToDays(duration) {
        return Math.round(duration / ONE_DAY);
    }

    function getTimeUntilExpiration(expirationDate) {
        var expiresTimestamp = expirationDate.getTime();
        var now = new Date().getTime();
        var delta = expiresTimestamp - now;
        return delta;
    }

    function getValueByType(handleRecord, type) {
        for (var i = 0; i < handleRecord.values.length; i++) {
            var value = handleRecord.values[i];
            if (type === value.type) return value;
        }
        return null;
    }

    function startsWith(s, start) {
        return s.indexOf(start) === 0;
    }

    constructor();
}
