package net.cnri.cordra.api;

public class BadRequestCordraException extends CordraException {

    public BadRequestCordraException(String message) {
        super(message);
    }

    public BadRequestCordraException(String message, Throwable cause) {
        super(message, cause);
    }

    public BadRequestCordraException(Throwable cause) {
        super(cause);
    }
}
