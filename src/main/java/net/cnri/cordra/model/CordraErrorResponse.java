package net.cnri.cordra.model;

public class CordraErrorResponse {
    String message;

    public CordraErrorResponse() {}

    public CordraErrorResponse(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
