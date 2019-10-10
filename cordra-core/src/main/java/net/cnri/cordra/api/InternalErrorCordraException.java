package net.cnri.cordra.api;

public class InternalErrorCordraException extends CordraException {

    public InternalErrorCordraException(String message, Throwable cause) {
        super(message, cause);
    }

    public InternalErrorCordraException(String message) {
        super(message);
    }

    public InternalErrorCordraException(Throwable cause) {
        super(cause);
    }
}
