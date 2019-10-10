package net.cnri.cordra.sync.curator;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.framework.recipes.locks.InterProcessReadWriteLock;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.cnri.cordra.sync.CheckableLocker;
import net.cnri.cordra.sync.SingleThreadReadWriteCheckableLocker;
import net.cnri.microservices.Alerter;
import net.cnri.cordra.api.InternalErrorCordraException;
import net.cnri.cordra.api.CordraException;

public class CuratorSingleThreadReadWriteCheckableLocker implements SingleThreadReadWriteCheckableLocker {
    private static final Logger logger = LoggerFactory.getLogger(CuratorSingleThreadReadWriteCheckableLocker.class);

    private final CuratorFramework client;
    private final String lockPath;
    private final ExecutorService execServ;
    private final Alerter alerter;
    private final CheckableLocker readLock;
    private final CheckableLocker writeLock;
    private final Lock singleThreadedLock;

    public CuratorSingleThreadReadWriteCheckableLocker(CuratorFramework client, String lockPath, ExecutorService execServ, Alerter alerter) {
        this.client = client;
        this.lockPath = lockPath;
        this.execServ = execServ;
        this.alerter = alerter;
        InterProcessReadWriteLock readWriteLock = new InterProcessReadWriteLock(client, lockPath);
        this.readLock = new CheckableLockerOfInterProcessMutex(readWriteLock.readLock());
        this.writeLock = new CheckableLockerOfInterProcessMutex(readWriteLock.writeLock());
        this.singleThreadedLock = new ReentrantLock();
    }

    @Override
    public CheckableLocker readLock() {
        return readLock;
    }

    @Override
    public CheckableLocker writeLock() {
        return writeLock;
    }

    class CheckableLockerOfInterProcessMutex implements CheckableLocker, ConnectionStateListener {
        private final InterProcessMutex mutex;
        private volatile boolean connectionError;

        public CheckableLockerOfInterProcessMutex(InterProcessMutex mutex) {
            this.mutex = mutex;
        }

        @Override
        public void stateChanged(CuratorFramework clientArg, ConnectionState state) {
            if (state == ConnectionState.SUSPENDED || state == ConnectionState.LOST) {
                connectionError = true;
            }
        }

        @Override
        public void acquire() throws CordraException {
            singleThreadedLock.lock();
            connectionError = false;
            client.getConnectionStateListenable().addListener(this, execServ);
            try {
                mutex.acquire();
            } catch (Exception e) {
                throw new InternalErrorCordraException(e);
            }
        }

        @Override
        public void release() {
            try {
                mutex.release();
            } catch (Exception e) {
                // By Curator code, release should be guaranteed even with connection failures; but warn just in case
                logger.error("Exception releasing lock " + lockPath, e);
                alerter.alert("Exception releasing lock" + lockPath + ": " + e);
            }
            client.getConnectionStateListenable().removeListener(this);
            singleThreadedLock.unlock();
        }

        @Override
        public boolean isLocked() {
            return !connectionError;
        }

    }
}
