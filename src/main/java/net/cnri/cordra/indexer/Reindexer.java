package net.cnri.cordra.indexer;

import com.fasterxml.jackson.databind.JsonNode;
import net.cnri.cordra.CordraService;
import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.api.CordraObject;
import net.cnri.cordra.api.SearchResults;
import net.cnri.cordra.model.CordraConfig;
import net.cnri.cordra.storage.CordraStorage;
import net.cnri.cordra.sync.NameLocker;
import net.cnri.cordra.sync.TransactionManager;
import net.cnri.microservices.Alerter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import javax.script.ScriptException;

public class Reindexer {
    private static final Logger logger = LoggerFactory.getLogger(Reindexer.class);

    // used to avoid logging missing types during a reindex
    private volatile boolean isReindexInProcess = false;
    private final CordraStorage storage;
    private final CordraIndexer indexer;
    private final TransactionManager transactionManager;
    private final CordraConfig cordraConfig;
    private final String cordraServiceId;
    private final NameLocker objectLocker;
    private final Alerter alerter;

    private final CordraService cordraService; //Consider refactor to avoid circular reference

    private AtomicLong progressCount;
    private AtomicLong lastProgressOutput;
    private long start;
    private volatile boolean shutdown = false;

    public Reindexer(CordraStorage storage,
                     CordraIndexer indexer,
                     TransactionManager transactionManager,
                     CordraConfig cordraConfig,
                     CordraService cordraService,
                     String cordraServiceId,
                     NameLocker objectLocker,
                     Alerter alerter
    ) {
        this.storage = storage;
        this.indexer = indexer;
        this.transactionManager = transactionManager;
        this.cordraConfig = cordraConfig;
        this.cordraService = cordraService;
        this.cordraServiceId = cordraServiceId;
        this.objectLocker = objectLocker;
        this.alerter = alerter;
    }

    public void reindexEverything(boolean isBrandNewDesignObject) throws CordraException, IndexerException {
        transactionManager.setReindexInProcess(true);
        isReindexInProcess = true;
        reindexPriority(isBrandNewDesignObject);
        if (cordraConfig.reindexing.async != null && cordraConfig.reindexing.async) {
            ExecutorService backgroundReindexExecServ = Executors.newSingleThreadExecutor();
            try {
                backgroundReindexExecServ.submit(() -> {
                    if (!isBrandNewDesignObject) {
                        if (cordraConfig.reindexing.logProgressToConsole) {
                            System.out.println("Reindexing all objects in background thread...");
                        }
                        logger.info("Reindexing all objects in background thread...");
                    }
                    try (SearchResults<String> allObjectsListHandles = storage.listHandles()) {
                        reindexList(allObjectsListHandles, "all objects", cordraConfig.reindexing.lockDuringBackgroundReindex, isBrandNewDesignObject);
                        if (!shutdown) {
                            transactionManager.setReindexInProcess(false);
                            isReindexInProcess = false;
                        }
                    } catch (Exception e) {
                        logger.error("reindexing error in background thread", e);
                        alerter.alert("reindexing error in background thread: " + e);
                    }
                });
            } finally {
                backgroundReindexExecServ.shutdown();
            }
        } else {
            try (SearchResults<CordraObject> allObjectsList = storage.list()) {
                reindexList(allObjectsList, "all objects", false, isBrandNewDesignObject);
            }
            if (!shutdown) {
                transactionManager.setReindexInProcess(false);
                isReindexInProcess = false;
            }
        }
    }

    private void reindexPriority(boolean isBrandNewDesignObject) throws CordraException, IndexerException {
        List<String> priorityTypes = null;
        // if requested by configuration
        if (cordraConfig.reindexing.priorityTypes != null) {
            priorityTypes = new ArrayList<>(cordraConfig.reindexing.priorityTypes);
        }
        // if main reindex is async, always reindex Schema and CordraDesign priority
        if (cordraConfig.reindexing.async != null && cordraConfig.reindexing.async) {
            if (priorityTypes == null) priorityTypes = new ArrayList<>();
            if (!priorityTypes.contains("Schema")) priorityTypes.add("Schema");
            if (!priorityTypes.contains("CordraDesign")) priorityTypes.add("CordraDesign");
        }
        if (priorityTypes != null) {
            try (SearchResults<CordraObject> priorityObjectsList = storage.listByType(priorityTypes)) {
                reindexList(priorityObjectsList, "priority objects", false, isBrandNewDesignObject);
            }
        }
    }

    private <T> void reindexList(SearchResults<T> list, String printedMessage, boolean lockObjectIds, boolean isBrandNewDesignObject) throws CordraException, IndexerException {
        progressCount = new AtomicLong(0L);
        lastProgressOutput = new AtomicLong(0L);
        start = System.currentTimeMillis();
        ExecutorService reindexExecServ = null;
        if (!isBrandNewDesignObject) {
            logger.info("Reindexing " + printedMessage);
            if (cordraConfig.reindexing.logProgressToConsole) {
                System.out.println("Reindexing " + printedMessage);
            }
        }
        AtomicBoolean hasFailed = new AtomicBoolean(false);
        try {
            int numThreads = cordraConfig.reindexing.numThreads;
            int queueSize = numThreads *3;
            BlockingQueue<Runnable> queue = new LinkedBlockingDeque<>(queueSize);
            reindexExecServ = new ThreadPoolExecutor(numThreads, numThreads, 0, TimeUnit.MILLISECONDS, queue, new ThreadPoolExecutor.CallerRunsPolicy());
            List<T> batch = new ArrayList<>();
            for (T coOrHandle : list) {
                if (shutdown) break;
                if (hasFailed.get()) throw new IndexerException("Exception during reindexing");
                batch.add(coOrHandle);
                if (batch.size() == cordraConfig.reindexing.batchSize) {
                    reindexBatchWithExecutor(reindexExecServ, batch, lockObjectIds, hasFailed);
                    batch = new ArrayList<>();
                }
            }
            if (hasFailed.get()) throw new IndexerException("Exception during reindexing");
            if (!batch.isEmpty()) {
                reindexBatchWithExecutor(reindexExecServ, batch, lockObjectIds, hasFailed);
            }
        } finally {
            shutdownExecServAndWait(reindexExecServ);
        }
        if (hasFailed.get()) throw new IndexerException("Exception during reindexing");
        cordraService.ensureIndexUpToDate();
        if (!shutdown) {
            if (!isBrandNewDesignObject) {
                logger.info("Reindexing " + printedMessage + " complete");
                if (cordraConfig.reindexing.logProgressToConsole) {
                    System.out.println("Reindexing " + printedMessage + " complete");
                }
                long end = System.currentTimeMillis();
                long progress = progressCount.get();
                Rate rate = new Rate(start, end, progress);
                String finalSummary = "Reindexing took " + rate.timeInSeconds + " seconds. (" + rate.rate + " objects/second)";
                logger.info(finalSummary);
                if (cordraConfig.reindexing.logProgressToConsole) {
                    System.out.println(finalSummary);
                }
            }
        } else {
            logger.info("Reindexing " + printedMessage + " interrupted");
            if (cordraConfig.reindexing.logProgressToConsole) {
                System.out.println("Reindexing " + printedMessage + " interrupted");
            }
        }
    }

    public static class Rate {
        public long rate;
        public long timeInMs;
        public double timeInSeconds;
        public long count;
        public long start;
        public long end;

        public Rate(long start, long end, long count) {
            this.start = start;
            this.end = end;
            this.count = count;
            timeInMs = end - start;
            timeInSeconds = timeInMs / 1000d;
            rate = (long) (count / timeInSeconds);
        }
    }

    private <T> void reindexBatchWithExecutor(ExecutorService reindexExecServ, List<T> batch, boolean lockObjectIds, AtomicBoolean hasFailed) {
        reindexExecServ.submit(() -> {
            try {
                if (hasFailed.get()) return;
                indexBatch(batch, lockObjectIds);
                outputProgressIfNecessary(batch.size());
            } catch (Exception e) {
                if (!hasFailed.getAndSet(true)) {
                    logger.error("reindexing error in background thread", e);
                    alerter.alert("reindexing error in background thread: " + e);
                }
            }
        });
    }

    private synchronized void outputProgressIfNecessary(int batchSize) {
        long progress = progressCount.addAndGet(batchSize);
        long lastProgress = lastProgressOutput.get();
        long countSinceLastOutput = progress - lastProgress;
        if (countSinceLastOutput >= cordraConfig.reindexing.logChunkSize) {
            long now = System.currentTimeMillis();
            Rate rate = new Rate(start, now, progress);
            logger.info("Reindexing progress: " + progress + " (" + rate.rate + " objects/second)");
            if (cordraConfig.reindexing.logProgressToConsole) {
                System.out.println("Reindexing progress: " + progress + " (" + rate.rate + " objects/second)") ;
            }
            lastProgressOutput.set(progress);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> void indexBatch(List<T> batch, boolean lockObjectIds) throws CordraException {
        if (batch.isEmpty()) return;
        boolean isStrings = batch.get(0) instanceof String;
        List<String> ids = null;
        try {
            if (lockObjectIds) {
                if (isStrings) {
                    ids = (List<String>) batch;
                } else {
                    ids = ((List<CordraObject>)batch).stream().map(co -> co.id).collect(Collectors.toList());
                }
                objectLocker.lockInOrder(ids);
            }
            List<CordraObject> batchObjects;
            if (isStrings) {
                batchObjects = storage.get((List<String>) batch).stream().collect(Collectors.toList());
            } else {
                batchObjects = (List<CordraObject>) batch;
            }
            List<CordraObjectWithIndexDetails> batchWithDetails = new ArrayList<>();
            for (CordraObject co : batchObjects) {
                Map<String, JsonNode> pointerToSchemaMap = cordraService.getPointerToSchemaMapForIndexing(co, this.isReindexInProcess);
                boolean indexPayloads = cordraService.shouldIndexPayloads(co.type);
                batchWithDetails.add(new CordraObjectWithIndexDetails(co, pointerToSchemaMap, indexPayloads));
            }
            try {
                indexer.indexObjects(cordraServiceId, batchWithDetails);
            } catch (IndexerException e) {
                if (e.getCause() instanceof ScriptException) {
                    logger.error("Script exception during reindexing. Consider using Cordra config.json reindexing.priorityTypes to index types like JavaScriptDirectory first.");
                }
                throw e;
            }
        } finally {
            if (lockObjectIds) {
                objectLocker.releaseAll(ids);
            }
        }
    }

    public boolean getIsReindexInProcess() {
        return isReindexInProcess;
    }

    private void shutdownExecServAndWait(ExecutorService execServ) {
        if (execServ != null) {
            execServ.shutdown();
            try {
                execServ.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                execServ.shutdownNow();
            }
        }
    }

    public void shutdown() {
        shutdown = true;
    }
}
