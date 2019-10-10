package net.cnri.cordra.api;

public class UnauthorizedCordraException extends CordraException {

    public UnauthorizedCordraException(String message, Throwable cause) {
        super(message, cause);
    }

    public UnauthorizedCordraException(String message) {
        super(message);
    }

    public UnauthorizedCordraException(Throwable cause) {
        super(cause);
    }
}
