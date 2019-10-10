package net.cnri.cordra;

public class AdminPasswordCordraException extends Exception {

    public AdminPasswordCordraException() {
        super("Cordra admin password has not been configured.");
    }
    
}
