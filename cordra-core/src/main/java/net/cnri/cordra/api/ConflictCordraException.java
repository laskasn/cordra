package net.cnri.cordra.api;

public class ConflictCordraException extends BadRequestCordraException {

    public ConflictCordraException(String message) {
        super(message);
    }

    public ConflictCordraException(String message, Throwable cause) {
        super(message, cause);
    }

    public ConflictCordraException(Throwable cause) {
        super(cause);
    }
}
