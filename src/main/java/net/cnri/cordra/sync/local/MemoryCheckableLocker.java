package net.cnri.cordra.sync.local;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import net.cnri.cordra.sync.CheckableLocker;

public class MemoryCheckableLocker implements CheckableLocker {

    final Lock lock = new ReentrantLock();
    
    @Override
    public void acquire() {
        lock.lock();
    }

    @Override
    public void release() {
        lock.unlock();
    }

    @Override
    public boolean isLocked() {
        return true;
    }

}
