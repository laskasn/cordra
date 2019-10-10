package net.cnri.cordra.sync;

import java.util.Map;
import java.util.Queue;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.cnri.cordra.indexer.CordraTransaction;
import net.cnri.cordra.api.CordraException;

public class DelegatingErrorCatchingTransactionReprocessingQueue implements TransactionReprocessingQueue {
    private static final Logger logger = LoggerFactory.getLogger(DelegatingErrorCatchingTransactionReprocessingQueue.class);

    private final TransactionReprocessingQueue delegate;
    private final Queue<Map.Entry<CordraTransaction, String>> memoryQueue = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService execServ;
    private ThrowingConsumer<CordraTransaction> callback;
    private TransactionManager transactionManager;
    private volatile boolean running;
    private volatile boolean closed;

    public DelegatingErrorCatchingTransactionReprocessingQueue(TransactionReprocessingQueue delegate) {
        this.delegate = delegate;
        execServ = Executors.newScheduledThreadPool(1);
    }

    @Override
    public void insert(CordraTransaction txn, String cordraServiceId) throws CordraException {
        try {
            delegate.insert(txn, cordraServiceId);
        } catch (Exception e) {
            memoryQueue.add(new SimpleImmutableEntry<>(txn, cordraServiceId));
            throw e;
        }
    }

    @Override
    @SuppressWarnings("hiding")
    public void start(ThrowingConsumer<CordraTransaction> callback, TransactionManager transactionManager) throws CordraException {
        this.callback = callback;
        this.transactionManager = transactionManager;
        delegate.start(callback, transactionManager);
        if (!running) {
            running = true;
            execServ.scheduleWithFixedDelay(this::poll, 1, 1, TimeUnit.MINUTES);
        }
    }

    public void poll() {
        Map.Entry<CordraTransaction, String> rememberedTransaction;
        while (running && (rememberedTransaction = memoryQueue.poll()) != null) {
            try {
                CordraTransaction txn = rememberedTransaction.getKey();
                String cordraServiceId = rememberedTransaction.getValue();
                callback.accept(txn);
                transactionManager.closeTransaction(txn.txnId, cordraServiceId);
            } catch (Exception e) {
                memoryQueue.add(rememberedTransaction);
                logger.error("Exception reprocessing remembered transactions", e);
                return;
            }
        }
    }

    @Override
    public void shutdown() {
        if (closed) return;
        running = false;
        delegate.shutdown();
        execServ.shutdown();
        try {
            execServ.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        closed = true;
    }

}
