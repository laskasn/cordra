package net.cnri.cordra.indexer.solr;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.gson.JsonObject;
import net.cnri.cordra.Design;
import net.cnri.cordra.api.*;
import net.cnri.cordra.collections.SearchResultsFromIterator;
import net.cnri.cordra.indexer.*;
import net.cnri.cordra.storage.CordraStorage;
import net.cnri.cordra.sync.NameLocker;
import net.cnri.microservices.Alerter;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrQuery.ORDER;
import org.apache.solr.client.solrj.SolrQuery.SortClause;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class SolrIndexer implements CordraIndexer {
    private static Logger logger = LoggerFactory.getLogger(SolrIndexer.class);

    @SuppressWarnings("deprecation")
    private static final String MIN_REPFACT_PARAM_FOR_OLDER_SOLR = UpdateRequest.MIN_REPFACT;

    private final CordraStorage storage;
    private final NameLocker objectLocker;
    private final DocumentBuilderSolr documentBuilder;
    private final SolrClient solr;
    private final ExecutorService exec;
    // XXX isStoreFields
    @SuppressWarnings("unused")
    private final boolean isStoreFields;
    private final int minRf;
    private final Alerter alerter;

    public SolrIndexer(SolrClient solr, CordraStorage storage, boolean isStoreFields, int minRf, NameLocker objectLocker, Alerter alerter) {
        this.storage = storage;
        this.objectLocker = objectLocker;
        this.isStoreFields = isStoreFields;
        this.solr = solr;
        this.documentBuilder = new DocumentBuilderSolr(false, storage);
        this.exec = Executors.newSingleThreadExecutor();
        this.minRf = minRf;
        this.alerter = alerter;
    }

    @Override
    public void setDesignSupplier(Supplier<Design> designSupplier) {
        documentBuilder.setDesignSupplier(designSupplier);
    }

    @Override
    public void setObjectTransformer(ObjectTransformer objectTransformer) {
        documentBuilder.setObjectTransformer(objectTransformer);
    }

    @Override
    public void indexObject(String cordraServiceId, CordraObject co, boolean indexPayloads, Map<String, JsonNode> pointerToSchemaMap) throws IndexerException {
        List<Runnable> cleanupActions = new ArrayList<>();
        try {
            boolean hasPayloads = (co.payloads != null && co.payloads.size() != 0);
            if (indexPayloads && hasPayloads) {
                if (co.metadata == null) co.metadata = new CordraObject.Metadata();
                if (co.metadata.internalMetadata == null) co.metadata.internalMetadata = new JsonObject();
                co.metadata.internalMetadata.addProperty(PAYLOAD_INDEX_STATE, INDEX_IN_PROCESS);
                co.metadata.internalMetadata.addProperty(PAYLOAD_INDEX_CORDRA_SERVICE_ID, cordraServiceId);
            }
            SolrInputDocument doc = documentBuilder.build(co, false, pointerToSchemaMap, cleanupActions);
            //SolrInputDocument doc = new DocumentBuilderSolr(false, storage).build(co, indexPayloadsNow, pointerToSchemaMap, cleanupActions);
            if (indexPayloads && hasPayloads) {
                exec.submit(() -> indexObjectWithPayloadsLogExceptions(co, pointerToSchemaMap));
            }
            UpdateResponse response = add(co.id, doc);
            if (response.getStatus() != 0) {
                throw new IndexerException("Unexpected Solr response "  + response);
            }
        } catch (Exception e) {
            throw new IndexerException(e);
        } finally {
            for (Runnable runnable : cleanupActions) {
                runnable.run();
            }
        }
    }

    public static class SolrInputDocumentWithHandle {
        public SolrInputDocument doc;
        public String handle;

        public SolrInputDocumentWithHandle(SolrInputDocument doc, String handle) {
            this.doc = doc;
            this.handle = handle;
        }
    }

    @Override
    public void indexObjects(String cordraServiceId, List<CordraObjectWithIndexDetails> batchWithDetails) throws IndexerException {
        // Adds a batch of digital objects to an index in a single request. If an objects type allows for indexing
        // payloads and the object contains payloads, those payloads will be indexed now.
        List<Runnable> cleanupActions = new ArrayList<>();
        try {
            List<SolrInputDocumentWithHandle> documentBatch = new ArrayList<>();
            for (CordraObjectWithIndexDetails item : batchWithDetails) {
                CordraObject co = item.co;
                boolean hasPayloads = (co.payloads != null && co.payloads.size() != 0);
                boolean indexPayloads = item.indexPayloads;
                boolean performIndexPayloads = indexPayloads && hasPayloads;
                if (performIndexPayloads) {
                    if (co.metadata == null) co.metadata = new CordraObject.Metadata();
                    if (co.metadata.internalMetadata == null) co.metadata.internalMetadata = new JsonObject();
                    co.metadata.internalMetadata.addProperty(PAYLOAD_INDEX_STATE, INDEX_COMPLETE);
                    co.metadata.internalMetadata.addProperty(PAYLOAD_INDEX_CORDRA_SERVICE_ID, cordraServiceId);
                }
                SolrInputDocument doc = documentBuilder.build(co, performIndexPayloads, item.pointerToSchemaMap, cleanupActions);
                documentBatch.add(new SolrInputDocumentWithHandle(doc, co.id));
            }
            UpdateResponse response = addBatch(documentBatch);
            if (response.getStatus() != 0) {
                throw new IndexerException("Unexpected Solr response "  + response);
            }
        } catch (Exception e) {
            throw new IndexerException(e);
        } finally {
            for (Runnable runnable : cleanupActions) {
                runnable.run();
            }
        }
    }

    private void indexObjectWithPayloadsLogExceptions(CordraObject co, Map<String, JsonNode> pointerToSchemaMap) {
        try {
            indexObjectWithPayloads(co, pointerToSchemaMap);
        } catch (CordraException e) {
            logger.error("Error indexing object " + co.id, e);
        }
    }

    private void indexObjectWithPayloads(CordraObject co, Map<String, JsonNode> pointerToSchemaMap) throws CordraException {
        boolean locked = false;
        List<Runnable> cleanupActions = new ArrayList<>();
        try {
            if (storage.get(co.id) == null) return;
            boolean indexPayloads = true;
            co.metadata.internalMetadata.addProperty(PAYLOAD_INDEX_STATE, INDEX_COMPLETE);
            SolrInputDocument doc = documentBuilder.build(co, indexPayloads, pointerToSchemaMap, cleanupActions);
            //SolrInputDocument doc = new DocumentBuilderSolr(false, storage).build(co, indexPayloads, pointerToSchemaMap, cleanupActions);
            objectLocker.lock(co.id);
            locked = true;
            if (storage.get(co.id) == null) return;
            UpdateResponse response = add(co.id, doc);
            if (response.getStatus() != 0) {
                throw new IndexerException("Unexpected Solr response "  + response);
            }
        } catch (Exception e) {
            throw new IndexerException(e);
        } finally {
            for (Runnable runnable : cleanupActions) {
                runnable.run();
            }
            if (locked) objectLocker.release(co.id);
        }
    }

    @Override
    public void deleteObject(String handle) throws IndexerException {
        try {
            UpdateResponse response = deleteById(handle);
            if (response.getStatus() != 0) {
                throw new IndexerException("Unexpected Solr response "  + response);
            }
        } catch (Exception e) {
            throw new IndexerException(e);
        }
    }

    private UpdateResponse addBatch(List<SolrInputDocumentWithHandle> batch) throws IOException, SolrServerException {
        UpdateRequest req = new UpdateRequest();
        for (SolrInputDocumentWithHandle item : batch) {
            req.add(item.doc);
        }
        if (minRf > 1) {
            req.setParam(MIN_REPFACT_PARAM_FOR_OLDER_SOLR, String.valueOf(minRf));
        }
        UpdateResponse resp = req.process(solr);
        if (minRf > 1) {
            int actualRf = ((CloudSolrClient)solr).getMinAchievedReplicationFactor("cordra", resp.getResponse());
            if (actualRf < minRf) {
                String batchString = batch.stream().map(item -> item.handle).collect(Collectors.joining(","));
                alerter.alert("Low replication factor adding batch [" + batchString + "] to index; expected " + minRf + " got " + actualRf);
            }
        }
        return resp;
    }

    private UpdateResponse add(String handle, SolrInputDocument doc) throws IOException, SolrServerException {
        UpdateRequest req = new UpdateRequest();
        req.add(doc);
        if (minRf > 1) {
            req.setParam(MIN_REPFACT_PARAM_FOR_OLDER_SOLR, String.valueOf(minRf));
        }
        UpdateResponse resp = req.process(solr);
        if (minRf > 1) {
            int actualRf = ((CloudSolrClient)solr).getMinAchievedReplicationFactor("cordra", resp.getResponse());
            if (actualRf < minRf) {
                alerter.alert("Low replication factor adding " + handle + " to index; expected " + minRf + " got " + actualRf);
            }
        }
        return resp;
    }

    private UpdateResponse deleteById(String handle) throws IOException, SolrServerException {
        UpdateRequest req = new UpdateRequest();
        req.deleteById(handle);
        if (minRf > 1) {
            req.setParam(MIN_REPFACT_PARAM_FOR_OLDER_SOLR, String.valueOf(minRf));
        }
        UpdateResponse resp = req.process(solr);
        if (minRf > 1) {
            int actualRf = ((CloudSolrClient)solr).getMinAchievedReplicationFactor("cordra", resp.getResponse());
            if (actualRf < minRf) {
                alerter.alert("Low replication factor deleting " + handle + " from index; expected " + minRf + " got " + actualRf);
            }
        }
        return resp;
    }

    @Override
    public SearchResults<CordraObject> search(String queryString, QueryParams params) throws IndexerException {
        return search(queryString, params, CordraObject.class);
    }

    @Override
    public SearchResults<String> searchHandles(String queryString, QueryParams params) throws IndexerException {
        return search(queryString, params, String.class);
    }

    @Override
    public SearchResults<IdType> searchIdType(String queryString, QueryParams params) throws IndexerException {
        return search(queryString, params, IdType.class);
    }

    @SuppressWarnings("resource")
    private <T> SearchResults<T> search(String queryString, QueryParams params, Class<T> klass) throws IndexerException {
        if (queryString.contains("{!")) {
            throw new IndexerException("Parse failure: {!");
        }
        SolrQuery query = new SolrQuery(CordraIndexer.fixSlashes(queryString));
        query.setFields("id", "type");
        setParamsOnQuery(params, query);
        try {
            QueryResponse response;
            if (isPaginated(query)) {
                response = solr.query(query);
            } else {
                response = unpaginatedQuery(query);
            }
            if (response.getStatus() != 0) {
                throw new IndexerException("Unexpected Solr response "  + response);
            }
            SolrDocumentList results = response.getResults();
            SearchResults<SolrDocument> indexerSearchResults = new SearchResultsFromIterator<>((int)results.getNumFound(), results.iterator());
            return new AbstractCordraSearchResultsFromIndexerSearchResultsBatch<T, SolrDocument>(indexerSearchResults, storage, klass) {
                @Override
                public String getIdFromDocument(SolrDocument document) {
                    return (String) document.getFirstValue("id");
                }
                @Override
                public String getTypeFromDocument(SolrDocument document) {
                    return (String) document.getFirstValue("type");
                }
            };
        } catch (Exception e) {
            throw new IndexerException(e);
        }
    }

    private void setParamsOnQuery(QueryParams params, SolrQuery query) {
        if (params == null) {
            query.setRows(Integer.MAX_VALUE);
            return;
        }
        int pageSize = params.getPageSize();
        if (pageSize >= 0) {
            query.setRows(pageSize);
            if (pageSize > 0) {
                query.setStart(pageSize * params.getPageNumber());
            }
        } else {
            query.setRows(Integer.MAX_VALUE);
        }
        List<SortField> sortFields = params.getSortFields();
        if (sortFields != null && !sortFields.isEmpty()) {
            List<SortClause> sortClauses = sortFields.stream().map(this::sortFieldToSortClause).filter(Objects::nonNull).collect(Collectors.toList());
            query.setSorts(sortClauses);
        }
    }

    private SortClause sortFieldToSortClause(SortField sortField) {
        String name = documentBuilder.getSortFieldName(sortField.getName());
        if (name == null) return null;
        ORDER order;
        if (sortField.isReverse()) {
            order = ORDER.desc;
        } else {
            order = ORDER.asc;
        }
        return new SortClause(name, order);
    }

    private boolean isPaginated(SolrQuery query) {
        if (query.getStart() != null && query.getStart().intValue() > 0) return true;
        if (query.getRows() == null) return true;
        return query.getRows().intValue() != Integer.MAX_VALUE;
    }

    // Query repeatedly at increasing page sizes until everything is found (or the page size is Integer.MAX_VALUE).
    // Generally this will stop after one query (if < 100 results) or two queries.
    // The only exception would be if the number of results is growing bizarrely fast (which is unlikely in practice).
    private QueryResponse unpaginatedQuery(SolrQuery query) throws Exception {
        int rows = 10;
        while (true) {
            query.setRows(rows);
            QueryResponse response = solr.query(query);
            if (rows == Integer.MAX_VALUE) {
                return response;
            }
            long numFound = response.getResults().getNumFound();
            if (numFound <= rows) {
                return response;
            }
            long nextRows = Math.max(((long)rows) * 10, numFound * 11 / 10);
            if (nextRows > Integer.MAX_VALUE || (int)nextRows <= numFound || (int)nextRows <= rows) {
                rows = Integer.MAX_VALUE;
            } else {
                rows = (int) nextRows;
            }
        }
    }

    @Override
    public void ensureIndexUpToDate() throws IndexerException {
        UpdateResponse response;
        try {
            response = solr.commit(false, true, true);
        } catch (Exception e) {
            throw new IndexerException(e);
        }
        if (response.getStatus() != 0) {
            throw new IndexerException("Unexpected Solr response "  + response);
        }
    }

    @Override
    public void close() throws IOException {
        solr.close();
        exec.shutdown();
    }
}
