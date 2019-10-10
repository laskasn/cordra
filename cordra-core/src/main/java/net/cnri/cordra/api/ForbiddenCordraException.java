package net.cnri.cordra.api;

public class ForbiddenCordraException extends CordraException {

    public ForbiddenCordraException(String message) {
        super(message);
    }

    public ForbiddenCordraException(String message, Throwable cause) {
        super(message, cause);
    }

    public ForbiddenCordraException(Throwable cause) {
        super(cause);
    }
}
