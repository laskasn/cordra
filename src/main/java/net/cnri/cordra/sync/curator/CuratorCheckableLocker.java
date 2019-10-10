package net.cnri.cordra.sync.curator;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.cnri.cordra.sync.CheckableLocker;
import net.cnri.microservices.Alerter;
import net.cnri.cordra.api.InternalErrorCordraException;
import net.cnri.cordra.api.CordraException;

public class CuratorCheckableLocker implements CheckableLocker, ConnectionStateListener {
    private static final Logger logger = LoggerFactory.getLogger(CuratorCheckableLocker.class);

    private final CuratorFramework client;
    private final String lockPath;
    private final ExecutorService execServ;
    private final Alerter alerter;
    private final InterProcessLock lock;
    private final Lock inProcessLock = new ReentrantLock();
    private volatile boolean connectionError;

    public CuratorCheckableLocker(CuratorFramework client, String lockPath, ExecutorService execServ, Alerter alerter) {
        this.client = client;
        this.lockPath = lockPath;
        this.execServ = execServ;
        this.alerter = alerter;
        this.lock = new InterProcessMutex(client, lockPath);
    }

    @Override
    public void stateChanged(CuratorFramework clientArg, ConnectionState state) {
        if (state == ConnectionState.SUSPENDED || state == ConnectionState.LOST) {
            connectionError = true;
        }
    }

    @Override
    public void acquire() throws CordraException {
        inProcessLock.lock();
        connectionError = false;
        client.getConnectionStateListenable().addListener(this, execServ);
        try {
            lock.acquire();
        } catch (Exception e) {
            throw new InternalErrorCordraException(e);
        }
    }

    @Override
    public void release() {
        try {
            try {
                lock.release();
            } catch (Exception e) {
                // By Curator code, release should be guaranteed even with connection failures; but warn just in case
                logger.error("Exception releasing lock " + lockPath, e);
                alerter.alert("Exception releasing lock" + lockPath + ": " + e);
            }
            client.getConnectionStateListenable().removeListener(this);
        } finally {
            inProcessLock.unlock();
        }
    }

    @Override
    public boolean isLocked() {
        return !connectionError;
    }

}
