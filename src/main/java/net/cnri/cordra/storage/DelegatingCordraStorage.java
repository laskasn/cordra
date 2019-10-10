package net.cnri.cordra.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;

import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.api.CordraObject;
import net.cnri.cordra.api.SearchResults;

public class DelegatingCordraStorage implements CordraStorage {
    private final CordraStorage delegate;

    public DelegatingCordraStorage(CordraStorage delegate) {
        this.delegate = delegate;
    }

    @Override
    public void close() throws IOException, CordraException {
        delegate.close();
    }

    @Override
    public CordraObject create(CordraObject d) throws CordraException {
        return delegate.create(d);
    }

    @Override
    public void delete(String id) throws CordraException {
        delegate.delete(id);
    }

    @Override
    public CordraObject get(String id) throws CordraException {
        return delegate.get(id);
    }

    @Override
    public InputStream getPartialPayload(String id, String payloadName, Long start, Long end) throws CordraException {
        return delegate.getPartialPayload(id, payloadName, start, end);
    }

    @Override
    public InputStream getPayload(String arg0, String arg1) throws CordraException {
        return delegate.getPayload(arg0, arg1);
    }

    @Override
    public SearchResults<CordraObject> list() throws CordraException {
        return delegate.list();
    }

    @Override
    public SearchResults<String> listHandles() throws CordraException {
        return delegate.listHandles();
    }

    @Override
    public CordraObject update(CordraObject d) throws CordraException {
        return delegate.update(d);
    }

    @Override
    public SearchResults<CordraObject> get(Collection<String> ids) throws CordraException {
        return delegate.get(ids);
    }

    @Override
    public SearchResults<CordraObject> listByType(List<String> types) throws CordraException {
        return delegate.listByType(types);
    }

    @Override
    public SearchResults<String> listHandlesByType(List<String> types) throws CordraException {
        return delegate.listHandlesByType(types);
    }
}
