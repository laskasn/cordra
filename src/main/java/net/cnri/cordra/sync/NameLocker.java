package net.cnri.cordra.sync;

import net.cnri.cordra.api.CordraException;

import java.util.List;

public interface NameLocker {
    /**
     * Locks based on a given name.  This is a blocking call.
     */
    public void lock(String name) throws CordraException;

    /**
     * Releases the lock based on a given name.
     */
    public void release(String name);

    /**
     * Returns whether the lock based on the given name is still valid.
     */
    public boolean isLocked(String name);

    public void lockInOrder(List<String> names) throws CordraException;

    default public void releaseAll(List<String> names) {
        for (String name : names) {
            release(name);
        }
    }
}
