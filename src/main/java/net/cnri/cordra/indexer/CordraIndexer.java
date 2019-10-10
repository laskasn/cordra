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

public interface CordraIndexer {

    public static final String PAYLOAD_INDEX_STATE = "payloadIndexState";
    public static final String PAYLOAD_INDEX_CORDRA_SERVICE_ID = "payloadIndexCordraServiceId";
    public static final String INDEX_IN_PROCESS = "indexInProcess";
    public static final String INDEX_ERROR = "indexError";
    public static final String INDEX_COMPLETE = "indexComplete";

    public static String fixSlashes(String s) {
        if(s==null) return null;
        return s.replaceAll("(?<!\\\\)/","\\\\/");
    }

    public void setDesignSupplier(Supplier<Design> designSupplier);

    public void setObjectTransformer(ObjectTransformer objectTransformer);

    public void indexObject(String cordraServiceId, CordraObject co, boolean indexPayloads, Map<String, JsonNode> pointerToSchemaMap) throws IndexerException;

    public default void indexObjects(String cordraServiceId, List<CordraObjectWithIndexDetails> batch) throws IndexerException {
        for (CordraObjectWithIndexDetails item : batch) {
            indexObject(cordraServiceId, item.co, item.indexPayloads, item.pointerToSchemaMap);
        }
    }

    public default void deleteObject(CordraObject co) throws IndexerException {
        deleteObject(co.id);
    }

    public void deleteObject(String handle) throws IndexerException;

    public default SearchResults<CordraObject> search(String query) throws IndexerException {
        return search(query, null);
    }

    public default SearchResults<String> searchHandles(String query) throws IndexerException {
        return searchHandles(query, null);
    }

    public default SearchResults<IdType> searchIdType(String query) throws IndexerException {
        return searchIdType(query, null);
    }

    public SearchResults<CordraObject> search(String query, QueryParams params) throws IndexerException;

    public SearchResults<String> searchHandles(String query, QueryParams params) throws IndexerException;

    public SearchResults<IdType> searchIdType(String query, QueryParams params) throws IndexerException;

    public void ensureIndexUpToDate() throws IndexerException;

    public void close() throws IOException;
}
