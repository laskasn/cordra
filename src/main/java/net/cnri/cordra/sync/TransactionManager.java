package net.cnri.cordra.sync;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.cnri.cordra.indexer.CordraTransaction;
import net.cnri.cordra.api.CordraException;

public interface TransactionManager {
    void start(String cordraServiceId);
    
    long getAndIncrementNextTransactionId() throws CordraException;
    
    void openTransaction(long txnId, String cordraServiceId, CordraTransaction txn) throws CordraException;
    void closeTransaction(long txnId, String cordraServiceId) throws CordraException;
    
    List<String> getCordraServiceIdsWithOpenTransactions() throws CordraException;
    Iterator<Map.Entry<Long, CordraTransaction>> iterateTransactions(String cordraServiceId) throws CordraException;
    void cleanup(String cordraServiceId) throws CordraException;
    
    boolean isReindexInProcess() throws CordraException;
    void setReindexInProcess(boolean isReindexInProcess) throws CordraException;
    
    void shutdown();
}
