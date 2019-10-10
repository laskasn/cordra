package net.cnri.cordra.sync.local;

import net.cnri.cordra.collections.PersistentMap;
import net.cnri.cordra.indexer.CordraTransaction;
import net.cnri.cordra.sync.TransactionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class FileBasedTransactionManager implements TransactionManager {
    private static Logger logger = LoggerFactory.getLogger(FileBasedTransactionManager.class);

    public static final String REINDEX_IN_PROCESS = "reindexInProcess";

    private final Map<Long, CordraTransaction> transactionsMap;
    private final Map<String, Integer> txnStatus;
    private final AtomicInteger nextTxnIdSuffix;

    public FileBasedTransactionManager(Path basePath) {
        nextTxnIdSuffix = new AtomicInteger(0);
        if (basePath != null) {
            File txnsDir = basePath.resolve("cordraTxns").toFile();
            transactionsMap = new PersistentMap<>(txnsDir, "transactionsMap", Long.class, CordraTransaction.class);
            txnStatus = new PersistentMap<>(txnsDir, "txnStatus", String.class, Integer.class);
        } else {
            transactionsMap = new ConcurrentHashMap<>();
            txnStatus = new ConcurrentHashMap<>();
        }
    }

    @Override
    public void start(String cordraServiceId) {
        // no-op
    }

    @Override
    public long getAndIncrementNextTransactionId() {
        int suffix = nextTxnIdSuffix.getAndIncrement() % 1000;
        while (suffix < 0) suffix += 1000;
        return System.currentTimeMillis() * 1_000L + suffix;
    }

    @Override
    public void openTransaction(long txnId, String cordraServiceId, CordraTransaction txn) {
        transactionsMap.put(txnId, txn);
    }

    @Override
    public void closeTransaction(long txnId, String cordraServiceId) {
        transactionsMap.remove(txnId);
    }

    @Override
    public java.util.List<String> getCordraServiceIdsWithOpenTransactions() {
        return Collections.singletonList("0");
    }

    @Override
    public Iterator<Entry<Long, CordraTransaction>> iterateTransactions(String cordraServiceId) {
        return transactionsMap.entrySet().iterator();
    }

    @Override
    public void cleanup(String cordraServiceId) {
        // no-op
    }

    @Override
    public boolean isReindexInProcess() {
        return txnStatus.containsKey(REINDEX_IN_PROCESS);
    }

    @Override
    public void setReindexInProcess(boolean isReindexInProcess) {
        if (isReindexInProcess) {
            txnStatus.put(REINDEX_IN_PROCESS, 1);
        } else {
            txnStatus.remove(REINDEX_IN_PROCESS);
        }
    }

    @Override
    public void shutdown() {
        if (transactionsMap instanceof Closeable) {
            try { ((Closeable)transactionsMap).close(); } catch (Exception e) { logger.error("Shutdown error", e); }
        }
        if (txnStatus instanceof Closeable) {
            try { ((Closeable)txnStatus).close(); } catch (Exception e) { logger.error("Shutdown error", e); }
        }
    }

}
