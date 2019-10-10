package net.cnri.cordra.storage;

import java.io.InputStream;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.api.CordraObject;
import net.cnri.cordra.api.SearchResults;
import net.cnri.util.LoggingUtil;

public class InstrumentedCordraStorage extends DelegatingCordraStorage {

    private static Logger logger = LoggerFactory.getLogger(InstrumentedCordraStorage.class);

    public InstrumentedCordraStorage(CordraStorage delegate) {
        super(delegate);
    }

    @Override
    public CordraObject get(String id) throws CordraException {
        return LoggingUtil.run(logger, () -> {
            return super.get(id);
        });
    }

    @Override
    public InputStream getPayload(String id, String payloadName) throws CordraException {
        return LoggingUtil.run(logger, () -> {
            return super.getPayload(id, payloadName);
        });
    }

    @Override
    public InputStream getPartialPayload(String id, String payloadName, Long start, Long end) throws CordraException {
        return LoggingUtil.run(logger, () -> {
            return super.getPartialPayload(id, payloadName, start, end);
        });
    }

    @Override
    public CordraObject create(CordraObject d) throws CordraException {
        return LoggingUtil.run(logger, () -> {
            return super.create(d);
        });
    }

    @Override
    public CordraObject update(CordraObject d) throws CordraException {
        return LoggingUtil.run(logger, () -> {
            return super.update(d);
        });
    }

    @Override
    public void delete(String id) throws CordraException {
        LoggingUtil.run(logger, () -> {
            super.delete(id);
        });
    }

    @Override
    public SearchResults<CordraObject> get(Collection<String> ids) throws CordraException {
        return LoggingUtil.run(logger, () -> {
            return super.get(ids);
        });
    }

    @Override
    public SearchResults<CordraObject> listByType(List<String> types) throws CordraException {
        return LoggingUtil.run(logger, () -> {
            return super.listByType(types);
        });
    }

    @Override
    public SearchResults<String> listHandlesByType(List<String> types) throws CordraException {
        return LoggingUtil.run(logger, () -> {
            return super.listHandlesByType(types);
        });
    }
}
