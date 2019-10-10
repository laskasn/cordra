package net.cnri.cordra.sync;

/**
 * A read/write lock that still guarantees only one thread per process is reading.
 */
public interface SingleThreadReadWriteCheckableLocker {
    CheckableLocker readLock();
    CheckableLocker writeLock();
}
