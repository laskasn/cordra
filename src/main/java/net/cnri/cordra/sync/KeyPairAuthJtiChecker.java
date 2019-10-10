package net.cnri.cordra.sync;

public interface KeyPairAuthJtiChecker {
    public boolean check(String iss, String jti, long exp, long now);
}
