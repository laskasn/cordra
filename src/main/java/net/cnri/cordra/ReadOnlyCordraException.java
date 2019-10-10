package net.cnri.cordra;

public class ReadOnlyCordraException extends Exception {

    public ReadOnlyCordraException() {
        this("Cordra is read-only");
    }

    public ReadOnlyCordraException(String message) {
        super(message);
    }

    public ReadOnlyCordraException(Throwable cause) {
        super("Cordra is read-only", cause);
    }

    public ReadOnlyCordraException(String message, Throwable cause) {
        super(message, cause);
    }

}
