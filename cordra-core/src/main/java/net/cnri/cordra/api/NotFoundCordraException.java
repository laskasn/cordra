package net.cnri.cordra.api;

public class NotFoundCordraException extends CordraException {

    public NotFoundCordraException(String message) {
        super(message);
    }

    public NotFoundCordraException(String message, Throwable cause) {
        super(message, cause);
    }

    public NotFoundCordraException(Throwable cause) {
        super(cause);
    }
}
