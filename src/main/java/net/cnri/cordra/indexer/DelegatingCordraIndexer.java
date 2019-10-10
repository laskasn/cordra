package net.cnri.cordra.indexer;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.JsonNode;

import net.cnri.cordra.Design;
import net.cnri.cordra.api.CordraObject;
import net.cnri.cordra.api.QueryParams;
import net.cnri.cordra.api.SearchResults;

public class DelegatingCordraIndexer implements CordraIndexer {

    private final CordraIndexer delegate;

    public DelegatingCordraIndexer(CordraIndexer delegate) {
        this.delegate = delegate;
    }

    @Override
    public void setDesignSupplier(Supplier<Design> designSupplier) {
        delegate.setDesignSupplier(designSupplier);
    }

    @Override
    public void setObjectTransformer(ObjectTransformer objectTransformer) {
        delegate.setObjectTransformer(objectTransformer);
    }

    @Override
    public void indexObject(String cordraServiceId, CordraObject co, boolean indexPayloads, Map<String, JsonNode> pointerToSchemaMap) throws IndexerException {
        delegate.indexObject(cordraServiceId, co, indexPayloads, pointerToSchemaMap);
    }

    @Override
    public void indexObjects(String cordraServiceId, List<CordraObjectWithIndexDetails> batchWithDetails) throws IndexerException {
        delegate.indexObjects(cordraServiceId, batchWithDetails);
    }

    @Override
    public void deleteObject(CordraObject co) throws IndexerException {
        delegate.deleteObject(co);
    }

    @Override
    public void deleteObject(String handle) throws IndexerException {
        delegate.deleteObject(handle);
    }

    @Override
    public SearchResults<CordraObject> search(String query) throws IndexerException {
        return delegate.search(query);
    }

    @Override
    public SearchResults<String> searchHandles(String query) throws IndexerException {
        return delegate.searchHandles(query);
    }

    @Override
    public SearchResults<IdType> searchIdType(String query) throws IndexerException {
        return delegate.searchIdType(query);
    }

    @Override
    public SearchResults<CordraObject> search(String query, QueryParams params) throws IndexerException {
        return delegate.search(query, params);
    }

    @Override
    public SearchResults<String> searchHandles(String query, QueryParams params) throws IndexerException {
        return delegate.searchHandles(query, params);
    }

    @Override
    public SearchResults<IdType> searchIdType(String query, QueryParams params) throws IndexerException {
        return delegate.searchIdType(query, params);
    }

    @Override
    public void ensureIndexUpToDate() throws IndexerException {
        delegate.ensureIndexUpToDate();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }
}
