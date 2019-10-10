package net.cnri.cordra.sync.local;

import net.cnri.cordra.sync.CheckableLocker;

public class NoopCheckableLocker implements CheckableLocker {

    @Override
    public void acquire() {
        // no-op
    }

    @Override
    public void release() {
        // no-op
    }

    @Override
    public boolean isLocked() {
        return true;
    }

}
