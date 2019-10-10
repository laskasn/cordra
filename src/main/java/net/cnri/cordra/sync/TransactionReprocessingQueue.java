package net.cnri.cordra.sync;

import net.cnri.cordra.indexer.CordraTransaction;
import net.cnri.cordra.api.CordraException;

public interface TransactionReprocessingQueue {
    void insert(CordraTransaction txn, String cordraServiceId) throws CordraException;
    void start(ThrowingConsumer<CordraTransaction> callback, TransactionManager transactionManager) throws CordraException;
    void shutdown();
    
    interface ThrowingConsumer<T> {
        void accept(T t) throws Exception;
    }
}
