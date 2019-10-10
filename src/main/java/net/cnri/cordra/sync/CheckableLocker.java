package net.cnri.cordra.sync;

import net.cnri.cordra.api.CordraException;

public interface CheckableLocker {
    void acquire() throws CordraException;
    void release();
    boolean isLocked();
}
