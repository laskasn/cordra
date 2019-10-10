package net.cnri.cordra;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

import net.cnri.cordra.sync.AllHandlesUpdaterSync;
import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.api.CordraObject;
import net.cnri.cordra.api.SearchResults;

public class AllHandlesUpdater {
    private Logger logger = LoggerFactory.getLogger(new Object() { }.getClass().getEnclosingClass());

    static boolean testing = false;
    private Thread finishingThread;
    private ExecutorService execServ;
    private volatile boolean shutdown = false;
    private static final int NUM_THREADS = 20;
    private final AllHandlesUpdaterSync sync;

    public AllHandlesUpdater(AllHandlesUpdaterSync sync) {
        this.sync = sync;
    }

    public void shutdown() {
        shutdown = true;
        if (execServ != null) {
            execServ.shutdown();
        }
        if (finishingThread != null) {
            try {
                finishingThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void updateAllHandles(final HandleClient handleClient, final CordraService cordra) {
        if (!testing && handleClient == null) {
            return;
        }
        if (sync.getAndSetInProgress()) {
            return;
        }
        sync.initUpdate();
        BlockingQueue<Runnable> queue = new LinkedBlockingDeque<>(100);
        execServ = new ThreadPoolExecutor(NUM_THREADS, NUM_THREADS, 0, TimeUnit.MILLISECONDS, queue, new ThreadPoolExecutor.CallerRunsPolicy());
        finishingThread = new Thread() {
            @Override
            public void run() {
                finishUpdateAllHandles(handleClient, cordra);
            }
        };
        finishingThread.start();
    }

    private void finishUpdateAllHandles(HandleClient handleClient, CordraService cordra) {
        try {
            cordra.ensureIndexUpToDate();
            try (SearchResults<CordraObject> results = cordra.searchRepo("*:*");) {
                sync.setTotalCount(results.size()-1); //minus one to subtract the design object
                for (CordraObject co : results) {
                    if (shutdown) break;
                    if (!CordraService.DESIGN_OBJECT_ID.equals(co.id)) {
                        execServ.submit(() -> updateHandle(handleClient, co));
                    }
                }
            }
        } catch (CordraException e) {
            logger.error("Error listing objects", e);
            sync.incrementExceptionCount();
        } finally {
            execServ.shutdown();
            try {
                execServ.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                execServ.shutdownNow();
                Thread.currentThread().interrupt();
            }
            sync.clearInProgress();
        }
    }

    public UpdateStatus getStatus() {
        return sync.getStatus();
    }

    public void updateHandle(HandleClient handleClient, CordraObject co) {
        if (shutdown) return;
        try {
            String type = co.type;
            JsonNode jsonNode = JsonUtil.gsonToJackson(co.content);
            if (!testing) {
                handleClient.updateHandleThrowingExceptions(co.id, co, type, jsonNode);
            }
            sync.incrementProgressCount();
        } catch (Exception e) {
            logger.error("Exception updating handle " + co.id, e);
            sync.incrementExceptionCount();
        }
    }

    public static class UpdateStatus {
        public boolean inProgress = false;
        public long total;
        public long progress;
        public long startTime;
        public long exceptionCount;
    }
}
