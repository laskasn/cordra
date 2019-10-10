package net.cnri.cordra.auth;

import net.cnri.cordra.CordraService;
import net.cnri.cordra.api.CordraException;

public class StoredInRepoAdminPasswordChecker implements AdminPasswordCheckerInterface {

    private final CordraService cordra;

    public StoredInRepoAdminPasswordChecker(CordraService cordra) {
        this.cordra = cordra;
    }

    @Override
    public boolean check(String password) throws CordraException {
        return cordra.checkAdminPassword(password);
    }

    @Override
    public void setPassword(String password) throws Exception {
        cordra.setAdminPassword(password);
    }
}
