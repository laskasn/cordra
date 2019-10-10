package net.cnri.cordra.sync.curator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.cnri.cordra.sync.NameLocker;
import net.cnri.microservices.Alerter;
import net.cnri.cordra.api.InternalErrorCordraException;
import net.cnri.cordra.api.CordraException;

public class CuratorNameLocker implements NameLocker, ConnectionStateListener {
    private static final Logger logger = LoggerFactory.getLogger(CuratorNameLocker.class);
    private static final int SIZE = 2048;

    private final CuratorFramework client;
    private final ExecutorService execServ;
    private final Alerter alerter;
    private final InterProcessMutex[] locks = new InterProcessMutex[SIZE];
    private final AtomicBoolean connectionError = new AtomicBoolean();
    private final ReadWriteLock connectionErrorLock = new ReentrantReadWriteLock();

    public CuratorNameLocker(CuratorFramework client, String lockPath, ExecutorService execServ, Alerter alerter) {
        this.client = client;
        this.execServ = execServ;
        this.alerter = alerter;
        for (int i = 0; i < SIZE; i++) {
            locks[i] = new InterProcessMutex(client, lockPath + "/" + i);
        }
        this.client.getConnectionStateListenable().addListener(this, this.execServ);
    }

    @Override
    public void stateChanged(CuratorFramework clientArg, ConnectionState state) {
        if (state == ConnectionState.SUSPENDED || state == ConnectionState.LOST) {
            connectionError.set(true);
        }
    }

    /*
     * This method was written by Doug Lea with assistance from members of JCP JSR-166 Expert Group
     * and released to the public domain, as explained at
     * http://creativecommons.org/licenses/publicdomain
     *
     * As of 2010/06/11, this method is identical to the (package private) hash method in OpenJDK 7's
     * java.util.HashMap class.
     */
    private static int smear(int hashCode) {
        hashCode ^= (hashCode >>> 20) ^ (hashCode >>> 12);
        return hashCode ^ (hashCode >>> 7) ^ (hashCode >>> 4);
    }

    @Override
    public void lock(String name) throws CordraException {
        connectionErrorLock.readLock().lock();
        if (connectionError.get()) {
            // clear connection errors before locking; wait for all existing locks to be released
            connectionErrorLock.readLock().unlock();
            connectionErrorLock.writeLock().lock();
            connectionErrorLock.readLock().lock();
            try {
                connectionError.set(false);
            } finally {
                connectionErrorLock.writeLock().unlock();
            }
        }
        int index = smear(name.hashCode()) % SIZE;
        if (index < 0) index += SIZE;
        try {
            locks[index].acquire();
        } catch (Exception e) {
            connectionErrorLock.readLock().unlock();
            throw new InternalErrorCordraException("Exception acquiring lock for " + name + " index " + index, e);
        }
    }

    @Override
    public void release(String name) {
        try {
            int index = smear(name.hashCode()) % SIZE;
            if (index < 0) index += SIZE;
            try {
                locks[index].release();
            } catch (Exception e) {
                // By Curator code, release should be guaranteed even with connection failures; but warn just in case
                logger.error("Exception releasing lock for " + name + " index " + index, e);
                alerter.alert("Exception releasing lock for " + name + " index " + index + ": " + e);
            }
        } finally {
            connectionErrorLock.readLock().unlock();
        }
    }

    @Override
    public boolean isLocked(String name) {
        return !connectionError.get();
    }

    @Override
    public void lockInOrder(List<String> names) throws CordraException {
        connectionErrorLock.readLock().lock();
        if (connectionError.get()) {
            // clear connection errors before locking; wait for all existing locks to be released
            connectionErrorLock.readLock().unlock();
            connectionErrorLock.writeLock().lock();
            connectionErrorLock.readLock().lock();
            try {
                connectionError.set(false);
            } finally {
                connectionErrorLock.writeLock().unlock();
            }
        }
        List<Integer> stripeIndexes = new ArrayList<>();
        for (String name : names) {
            int index = smear(name.hashCode()) % SIZE;
            if (index < 0) index += SIZE;
            stripeIndexes.add(index);
        }
        Collections.sort(stripeIndexes);
        try {
            for (int index : stripeIndexes) {
                locks[index].acquire();
            }
        } catch (Exception e) {
            connectionErrorLock.readLock().unlock();
            throw new InternalErrorCordraException("Exception acquiring locks for list");
        }
    }

    @Override
    public void releaseAll(List<String> names) {
        Exception exception = null;
        try {
            for (String name : names) {
                try {
                    releaseWithoutTouchingConnectionErrorLock(name);
                } catch (Exception e) {
                    if (exception == null) exception = e;
                    else exception.addSuppressed(e);
                }
            }
        } finally {
            connectionErrorLock.readLock().unlock();
        }
        if (exception == null) return;
        if (exception instanceof RuntimeException) throw (RuntimeException) exception;
        throw new RuntimeException(exception);
    }

    private void releaseWithoutTouchingConnectionErrorLock(String name) {
        int index = smear(name.hashCode()) % SIZE;
        if (index < 0) index += SIZE;
        try {
            locks[index].release();
        } catch (Exception e) {
            // By Curator code, release should be guaranteed even with connection failures; but warn just in case
            logger.error("Exception releasing lock for " + name + " index " + index, e);
            alerter.alert("Exception releasing lock for " + name + " index " + index + ": " + e);
        }
    }
}
