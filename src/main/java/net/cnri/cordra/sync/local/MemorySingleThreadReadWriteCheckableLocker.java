package net.cnri.cordra.sync.local;

import net.cnri.cordra.sync.CheckableLocker;
import net.cnri.cordra.sync.SingleThreadReadWriteCheckableLocker;

public class MemorySingleThreadReadWriteCheckableLocker implements SingleThreadReadWriteCheckableLocker {

    private final CheckableLocker lock = new MemoryCheckableLocker();
    
    @Override
    public CheckableLocker readLock() {
        return lock;
    }

    @Override
    public CheckableLocker writeLock() {
        return lock;
    }

}
