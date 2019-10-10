package net.cnri.cordra.api;

import com.google.gson.JsonParser;

public class CordraException extends Exception {
    public CordraException(String message) {
        super(message);
    }

    public CordraException(Throwable cause) {
        super(cause);
    }

    public CordraException(String message, Throwable cause) {
        super(message, cause);
    }

    public static CordraException fromStatusCode(int statusCode) {
        return fromStatusCode(statusCode, null);
    }

    public static CordraException fromStatusCode(int statusCode, String responseString) {
        String responseMessage = "" + statusCode;
        if (responseString != null) {
            try {
                String message = new JsonParser().parse(responseString).getAsJsonObject().get("message").getAsString();
                if (message != null) responseMessage += "; " + message;
            } catch (Exception e) {
                // ignore;
            }
        }
        if (statusCode < 400) {
            //throw new IllegalArgumentException("Not an exception: " + statusCode);
            return new InternalErrorCordraException("Unexpected status: " + responseMessage);
        } else if (400 == statusCode) {
            return new BadRequestCordraException("Bad request: " + responseMessage);
        } else if (401 == statusCode) {
            return new UnauthorizedCordraException("Unauthorized: " + responseMessage);
        } else if (403 == statusCode) {
            return new ForbiddenCordraException("Forbidden: " + responseMessage);
        } else if (404 == statusCode) {
            return new NotFoundCordraException("Not found: " + responseMessage);
        } else if (409 == statusCode) {
            return new ConflictCordraException("Conflict: " + responseMessage);
        } else if (statusCode < 500) {
            return new BadRequestCordraException("Bad request: " + responseMessage);
        } else {
            return new InternalErrorCordraException("Unexpected status: " + responseMessage);
        }
    }
}
