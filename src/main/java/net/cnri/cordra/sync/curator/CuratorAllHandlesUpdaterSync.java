package net.cnri.cordra.sync.curator;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.atomic.AtomicValue;
import org.apache.curator.framework.recipes.atomic.DistributedAtomicLong;
import org.apache.curator.framework.recipes.atomic.DistributedAtomicValue;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.cnri.cordra.AllHandlesUpdater.UpdateStatus;
import net.cnri.cordra.sync.AllHandlesUpdaterSync;

public class CuratorAllHandlesUpdaterSync implements AllHandlesUpdaterSync {
    private static final Logger logger = LoggerFactory.getLogger(CuratorAllHandlesUpdaterSync.class);

    private static final String IN_PROGRESS_PATH = "/updateAllHandles/inProgress";
    private static final String PROGRESS_COUNT_PATH = "/updateAllHandles/progressCount";
    private static final String EXCEPTION_COUNT_PATH = "/updateAllHandles/exceptionCount";
    private static final String TOTAL_COUNT_PATH = "/updateAllHandles/totalCount";
    private static final String START_TIME_PATH = "/updateAllHandles/startTime";

    private final DistributedAtomicValue inProgress;
    private final DistributedAtomicLong progressCount;
    private final DistributedAtomicLong exceptionCount;
    private final DistributedAtomicLong totalCount;
    private final DistributedAtomicLong startTime;

    public CuratorAllHandlesUpdaterSync(CuratorFramework client) throws Exception {
        ExponentialBackoffRetry retryPolicy = new ExponentialBackoffRetry(1000, 3);
        CuratorUtil.ensurePath(client, IN_PROGRESS_PATH);
        CuratorUtil.ensurePath(client, PROGRESS_COUNT_PATH);
        CuratorUtil.ensurePath(client, EXCEPTION_COUNT_PATH);
        CuratorUtil.ensurePath(client, TOTAL_COUNT_PATH);
        CuratorUtil.ensurePath(client, START_TIME_PATH);
        this.inProgress = new DistributedAtomicValue(client, IN_PROGRESS_PATH, retryPolicy);
        this.progressCount = new DistributedAtomicLong(client, PROGRESS_COUNT_PATH, retryPolicy);
        this.exceptionCount = new DistributedAtomicLong(client, EXCEPTION_COUNT_PATH, retryPolicy);
        this.totalCount = new DistributedAtomicLong(client, TOTAL_COUNT_PATH, retryPolicy);
        this.startTime = new DistributedAtomicLong(client, START_TIME_PATH, retryPolicy);
    }

    @Override
    public boolean getAndSetInProgress() {
        try {
            while (true) {
                byte[] currentValue = inProgress.get().postValue();
                if (currentValue.length > 0 && currentValue[0] == 1) return true;
                byte[] newValueBytes = new byte[] { 1 };
                AtomicValue<byte[]> result = inProgress.compareAndSet(currentValue, newValueBytes);
                if (result.succeeded()) {
                    return currentValue.length > 0 && currentValue[0] == 1;
                }
            }
        } catch (Exception e) {
            logger.error("Exception starting all handles update", e);
            return true;
        }
    }

    @Override
    public UpdateStatus getStatus() {
        // okay for these values to be stale
        try {
            UpdateStatus status = new UpdateStatus();
            byte[] currentValue = inProgress.get().postValue();
            status.inProgress = currentValue.length > 0 && currentValue[0] == 1;
            status.total = totalCount.get().postValue();
            status.startTime = startTime.get().postValue();
            status.progress = progressCount.get().postValue();
            status.exceptionCount = exceptionCount.get().postValue();
            return status;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void initUpdate() {
        try {
            startTime.forceSet(System.currentTimeMillis());
            exceptionCount.forceSet(0L);
            progressCount.forceSet(0L);
            totalCount.forceSet(0L);
            inProgress.forceSet(new byte[] { 1 });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setTotalCount(long count) {
        try {
            totalCount.forceSet(count);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void clearInProgress() {
        try {
            inProgress.forceSet(new byte[] { 0 });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void incrementProgressCount() {
        try {
            while (true) {
                if (progressCount.increment().succeeded()) return;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void incrementExceptionCount() {
        try {
            while (true) {
                if (exceptionCount.increment().succeeded()) return;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
