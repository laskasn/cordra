package net.cnri.util;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ThrottledExecutorService extends AbstractExecutorService {
    
    private final ScheduledExecutorService scheduledExecServ;
    private final long averageDelayMs;
    private final long fuzzMs;
    
    private volatile long lastStart;
    private final AtomicBoolean requested = new AtomicBoolean(false);
    
    public ThrottledExecutorService(long averageDelayMs) {
        this(averageDelayMs, 0);
    }
    
    public ThrottledExecutorService(long averageDelayMs, long fuzzMs) {
        this.scheduledExecServ = Executors.newScheduledThreadPool(1);
        ((ScheduledThreadPoolExecutor)this.scheduledExecServ).setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        this.averageDelayMs = averageDelayMs;
        this.fuzzMs = fuzzMs;
    }

    @Override
    public void execute(Runnable command) {
        if (requested.getAndSet(true) == true) return;
        long now = System.currentTimeMillis();
        if (now - lastStart > averageDelayMs + fuzzMs) {
            scheduledExecServ.execute(() -> {
                lastStart = System.currentTimeMillis();
                requested.set(false);
                command.run();
            });
        } else {
            long wait = averageDelayMs - (now - lastStart);
            wait += ThreadLocalRandom.current().nextLong(-fuzzMs, fuzzMs + 1);
            if (wait < averageDelayMs - fuzzMs) wait = averageDelayMs - fuzzMs;
            if (wait > averageDelayMs + fuzzMs) wait = averageDelayMs + fuzzMs;
            if (wait < 0) wait = 0;
            scheduledExecServ.schedule(() -> {
                lastStart = System.currentTimeMillis();
                requested.set(false);
                command.run();
            }, wait, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void shutdown() {
        scheduledExecServ.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return scheduledExecServ.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return scheduledExecServ.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return scheduledExecServ.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return scheduledExecServ.awaitTermination(timeout, unit);
    }

}
