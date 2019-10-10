package net.cnri.cordra.sync.local;

import java.io.Closeable;
import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.cnri.cordra.collections.PersistentMap;
import net.cnri.cordra.collections.PersistentMap.CloseableIterator;
import net.cnri.cordra.indexer.CordraTransaction;
import net.cnri.cordra.sync.TransactionManager;
import net.cnri.cordra.sync.TransactionReprocessingQueue;
import net.cnri.cordra.api.CordraException;

public class LocalTransactionReprocessingQueue implements TransactionReprocessingQueue {
    private static final Logger logger = LoggerFactory.getLogger(LocalTransactionReprocessingQueue.class);

    private final Map<Long, CordraTransaction> transactionsMap;
    public final long DELAY = 1000 * 60 * 2; // 2 mins
    private ThrowingConsumer<CordraTransaction> callback;
    private volatile boolean running;
    private volatile boolean closed;
    private ScheduledExecutorService execServ;

    public LocalTransactionReprocessingQueue(Path basePath) {
        if (basePath != null) {
            File txnsDir = basePath.resolve("cordraTxns").toFile();
            transactionsMap = new PersistentMap<>(txnsDir, "reprocessingQueueMap", Long.class, CordraTransaction.class);
        } else {
            transactionsMap = new ConcurrentHashMap<>();
        }
        execServ = Executors.newScheduledThreadPool(1);
    }

    @Override
    public void insert(CordraTransaction txn, String cordraServiceId) throws CordraException {
        transactionsMap.put(txn.txnId, txn);
    }

    @Override
    public void start(@SuppressWarnings("hiding") ThrowingConsumer<CordraTransaction> callback, TransactionManager transactionManager) throws CordraException {
        this.callback = callback;
        if (!running) {
            running = true;
            execServ.scheduleWithFixedDelay(this::poll, 1, 1, TimeUnit.MINUTES);
        }
    }

    public void poll() {
        try (CloseableIterator<CordraTransaction> iter = (CloseableIterator<CordraTransaction>)transactionsMap.values().iterator()) {
            long now = System.currentTimeMillis();
            long cutoffTimestamp = now - DELAY;
            while (iter.hasNext() && running) {
                CordraTransaction txn = iter.next();
                if (txn.timestamp > cutoffTimestamp) {
                    break;
                }
                try {
                    callback.accept(txn);
                    iter.remove();
                } catch (Exception e) {
                    logger.error("Exception processing transaction", e);
                    // we expect errors to apply to any transactions, so stop and try again later
                    break;
                }
            }
        }
    }

    @Override
    public void shutdown() {
        if (closed) return;
        running = false;
        execServ.shutdown();
        try {
            execServ.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (transactionsMap instanceof Closeable) {
            try { ((Closeable)transactionsMap).close(); } catch (Exception e) { logger.error("Shutdown error", e); }
        }
        closed = true;
    }
}
