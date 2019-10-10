package net.cnri.cordra.sync;

import net.cnri.cordra.AllHandlesUpdater;

public interface AllHandlesUpdaterSync {

    boolean getAndSetInProgress();
    AllHandlesUpdater.UpdateStatus getStatus();
    void initUpdate();
    void setTotalCount(long count);
    void clearInProgress();
    void incrementProgressCount();
    void incrementExceptionCount();
}
