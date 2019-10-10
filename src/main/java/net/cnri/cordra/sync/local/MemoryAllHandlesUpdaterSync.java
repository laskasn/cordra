package net.cnri.cordra.sync.local;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import net.cnri.cordra.AllHandlesUpdater.UpdateStatus;
import net.cnri.cordra.sync.AllHandlesUpdaterSync;

public class MemoryAllHandlesUpdaterSync implements AllHandlesUpdaterSync {

    private AtomicBoolean inProgress = new AtomicBoolean(false); 
    private AtomicLong progressCount = new AtomicLong(0);
    private long totalCount;
    private AtomicLong exceptionCount = new AtomicLong(0);
    private long startTime = 0L;

    @Override
    public UpdateStatus getStatus() {
        UpdateStatus status = new UpdateStatus();
        status.inProgress = this.inProgress.get();
        status.total = this.totalCount;
        status.startTime = this.startTime;
        status.progress = this.progressCount.get();
        status.exceptionCount = this.exceptionCount.get();
        return status;    
    }

    @Override
    public boolean getAndSetInProgress() {
        return inProgress.getAndSet(true);
    }
    
    @Override
    public void initUpdate() {
        startTime = System.currentTimeMillis();
        exceptionCount.set(0);
        progressCount.set(0);
        totalCount = 0;
        inProgress.set(true);
    }

    @Override
    public void setTotalCount(long count) {
        totalCount = count;
    }
    
    @Override
    public void clearInProgress() {
        inProgress.set(false);
    }
    
    @Override
    public void incrementProgressCount() {
        progressCount.getAndIncrement();
    }

    @Override
    public void incrementExceptionCount() {
        exceptionCount.getAndIncrement();
    }

}
