package net.cnri.cordra.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.cnri.cordra.api.CordraException;
import net.cnri.util.LoggingUtil;

import java.util.List;

public class InstrumentedNameLocker implements NameLocker {

    private static Logger logger = LoggerFactory.getLogger(InstrumentedNameLocker.class);
    
    private final NameLocker delegate;
    
    public InstrumentedNameLocker(NameLocker delegate) {
        this.delegate = delegate;
    }

    @Override
    public void lock(String name) throws CordraException {
        LoggingUtil.run(logger, () -> {
            delegate.lock(name);
        }); 
    }

    @Override
    public void release(String name) {
        LoggingUtil.runWithoutThrow(logger, () -> {
            delegate.release(name);
        }); 
    }

    @Override
    public boolean isLocked(String name) {
        return LoggingUtil.runWithoutThrow(logger, () -> {
            return delegate.isLocked(name);
        }); 
    }

    @Override
    public void lockInOrder(List<String> names) throws CordraException {
        LoggingUtil.run(logger, () -> {
            delegate.lockInOrder(names);
        });
    }

    @Override
    public void releaseAll(List<String> names) {
        LoggingUtil.runWithoutThrow(logger, () -> {
            delegate.releaseAll(names);
        });
    }
}
