package net.cnri.cordra.sync;

import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.indexer.CordraTransaction;
import net.cnri.util.LoggingUtil;

public class InstrumentedTransactionManager implements TransactionManager {

    private static Logger logger = LoggerFactory.getLogger(InstrumentedTransactionManager.class);
    private final TransactionManager delegate;
    
    public InstrumentedTransactionManager(TransactionManager delegate) {
        this.delegate = delegate;
    }

    @Override
    public void start(String cordraServiceId) {
        delegate.start(cordraServiceId);
    }

    @Override
    public long getAndIncrementNextTransactionId() throws CordraException {
        return delegate.getAndIncrementNextTransactionId();
    }

    @Override
    public void openTransaction(long txnId, String cordraServiceId, CordraTransaction txn) throws CordraException {
        LoggingUtil.run(logger, () -> {
            delegate.openTransaction(txnId, cordraServiceId, txn);
        }); 
    }

    @Override
    public void closeTransaction(long txnId, String cordraServiceId) throws CordraException {
        LoggingUtil.run(logger, () -> {
            delegate.closeTransaction(txnId, cordraServiceId);
        }); 
    }

    @Override
    public List<String> getCordraServiceIdsWithOpenTransactions() throws CordraException {
        return delegate.getCordraServiceIdsWithOpenTransactions();
    }

    @Override
    public Iterator<Entry<Long, CordraTransaction>> iterateTransactions(String cordraServiceId) throws CordraException {
        return delegate.iterateTransactions(cordraServiceId);
    }

    @Override
    public void cleanup(String cordraServiceId) throws CordraException {
        delegate.cleanup(cordraServiceId);
    }

    @Override
    public boolean isReindexInProcess() throws CordraException {
        return delegate.isReindexInProcess();
    }

    @Override
    public void setReindexInProcess(boolean isReindexInProcess) throws CordraException {
        delegate.setReindexInProcess(isReindexInProcess);
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }  
    
}
