package net.cnri.cordra.auth;

import net.cnri.cordra.api.CordraException;

public interface AdminPasswordCheckerInterface {

    public boolean check(String password) throws CordraException;

    public void setPassword(String password) throws Exception;
}
