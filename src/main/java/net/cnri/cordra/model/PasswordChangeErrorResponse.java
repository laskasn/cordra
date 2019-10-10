package net.cnri.cordra.model;

public class PasswordChangeErrorResponse extends CordraErrorResponse {

    public boolean passwordChangeRequired = true;

    public PasswordChangeErrorResponse(String message) {
        super(message);
    }
}
