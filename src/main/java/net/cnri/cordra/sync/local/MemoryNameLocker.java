package net.cnri.cordra.sync.local;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.sync.NameLocker;

public class MemoryNameLocker implements NameLocker {
    
    private final ConcurrentHashMap<String, ReferenceCountedLock> locks = new ConcurrentHashMap<>();

    @Override
    public void lock(String objectId) {
        ReferenceCountedLock existingLock = locks.get(objectId);
        if (existingLock != null && getAndIncrementIfPositive(existingLock.count) > 0) {
            existingLock.lock.lock();
            return;
        }
        ReferenceCountedLock newLock = new ReferenceCountedLock();
        while (true) {
            existingLock = locks.putIfAbsent(objectId, newLock);
            if (existingLock == null) {
                newLock.lock.lock();
                return;
            }
            if (getAndIncrementIfPositive(existingLock.count) > 0) {
                existingLock.lock.lock();
                return;
            }
        }
    }

    @Override
    public void release(String objectId) {
        ReferenceCountedLock existingLock = locks.get(objectId);
        if (existingLock.count.decrementAndGet() == 0) {
            locks.remove(objectId);
        }
        existingLock.lock.unlock();
    }
    
    @Override
    public boolean isLocked(String name) {
        return true;
    }

    @Override
    public void lockInOrder(List<String> names) throws CordraException {
        List<String> sortedNames = names.stream().sorted().collect(Collectors.toList());
        for (String name : sortedNames) {
            lock(name);
        }
    }

    private static int getAndIncrementIfPositive(AtomicInteger ai) {
        while(true) {
            int current = ai.get();
            if(current<=0) return 0;
            if(current==Integer.MAX_VALUE) throw new IllegalStateException("Integer overflow");
            if(ai.compareAndSet(current, current+1)) return current;
        }
    }
    
    private static class ReferenceCountedLock {
        Lock lock;
        AtomicInteger count;

        public ReferenceCountedLock() {
            lock = new ReentrantLock();
            count = new AtomicInteger(1);
        }
    }
}
