package net.cnri.cordra.indexer.elasticsearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.cnri.cordra.Design;
import net.cnri.cordra.GsonUtility;
import net.cnri.cordra.api.*;
import net.cnri.cordra.indexer.*;
import net.cnri.cordra.storage.CordraStorage;
import net.cnri.cordra.sync.NameLocker;
import net.cnri.microservices.Alerter;
import net.cnri.util.StreamUtil;
import net.cnri.util.StringUtils;
import org.apache.http.HttpStatus;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshResponse;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.main.MainResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.*;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.document.DocumentField;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// These have include_type_name set to false, which works on Elasticsearch 7
// See https://github.com/elastic/elasticsearch/issues/40897
//import org.elasticsearch.client.indices.CreateIndexRequest;
//import org.elasticsearch.client.indices.CreateIndexResponse;
// These have include_type_name set to true, which works on Elasticsearch 6 and 7
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public class ElasticsearchIndexer implements CordraIndexer {
    private static Logger logger = LoggerFactory.getLogger(ElasticsearchIndexer.class);
    public static final String INDEX_NAME = "cordra";
    // Why _doc? See https://www.elastic.co/blog/moving-from-types-to-typeless-apis-in-elasticsearch-7-0
    private static final String TYPE_NAME = "_doc";

    private final CordraStorage storage;
    private final NameLocker objectLocker;
    private final DocumentBuilderElasticsearch documentBuilder;
    private final RestHighLevelClient client;
    private final ExecutorService exec;
    private final Gson gson;
    @SuppressWarnings("unused")
    private final Alerter alerter;
    private final int majorVersion;

    public ElasticsearchIndexer(RestClientBuilder restClientBuilder, CordraStorage storage, NameLocker objectLocker, Alerter alerter, Settings indexSettings) throws IOException, IndexerException {
        this.storage = storage;
        this.objectLocker = objectLocker;
        this.client = new RestHighLevelClient(restClientBuilder);
        this.documentBuilder = new DocumentBuilderElasticsearch(false, storage);
        this.exec = Executors.newSingleThreadExecutor();
        this.gson = GsonUtility.getGson();
        this.alerter = alerter;

        try {
            MainResponse response = client.info(RequestOptions.DEFAULT);
            majorVersion = response.getVersion().major;

            GetIndexRequest request = new GetIndexRequest(INDEX_NAME);
            boolean exists = client.indices().exists(request, RequestOptions.DEFAULT);
            if (!exists) {
                try (InputStream resource = ElasticsearchIndexer.class.getResourceAsStream("settings.json")) {
                    indexSettings = Settings.builder()
                        .put(indexSettings)
                        .loadFromStream("settings.json", resource, true)
                        .build();
                }
                String mappingJson;
                try (InputStream resource = ElasticsearchIndexer.class.getResourceAsStream("mappings.json");
                    InputStreamReader reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
                    mappingJson = StreamUtil.readFully(reader);
                } catch (Exception e) {
                    throw new AssertionError("Invalid Elasticsearch configuration");
                }
                initializeIndex(mappingJson, indexSettings);
            } else {
                if (majorVersion < 6 || majorVersion > 7) {
                    throw new IndexerException("Only Elasticsearch version 6 and 7 are supported.");
                }
            }
        } catch (IOException | IndexerException e) {
            logger.error("Error initializing Elasticsearch.");
            throw e;
        }
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
            boolean indexPayloadsNow = !hasPayloads;
            if (indexPayloads && hasPayloads) {
                if (co.metadata == null) co.metadata = new CordraObject.Metadata();
                if (co.metadata.internalMetadata == null) co.metadata.internalMetadata = new JsonObject();
                co.metadata.internalMetadata.addProperty(PAYLOAD_INDEX_STATE, INDEX_IN_PROCESS);
                co.metadata.internalMetadata.addProperty(PAYLOAD_INDEX_CORDRA_SERVICE_ID, cordraServiceId);
            }
            Map<String, List<Object>> doc = documentBuilder.build(co, indexPayloadsNow, pointerToSchemaMap, cleanupActions);
            if (indexPayloads && hasPayloads) {
                exec.submit(() -> indexObjectWithPayloadsLogExceptions(co, pointerToSchemaMap));
            }
            IndexResponse response = index(co.id, doc);
            if (!(response.status().getStatus() >= 200 && response.status().getStatus() < 400)) {
                throw new IndexerException("Could not index object: "+co.id+". Status Code: "+response.status().getStatus());
            }
        } catch (Exception e) {
            throw new IndexerException(e);
        } finally {
            for (Runnable runnable : cleanupActions) {
                runnable.run();
            }
        }
    }

    @Override
    public void indexObjects(String cordraServiceId, List<CordraObjectWithIndexDetails> batchWithDetails) throws IndexerException {
        // Adds a batch of digital objects to an index in a single request. If an objects type allows for indexing
        // payloads and the object contains payloads, those payloads will be indexed now.
        List<Runnable> cleanupActions = new ArrayList<>();
        try {
            List<ElasticInputDocumentWithHandle> documentBatch = new ArrayList<>();
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
                Map<String, List<Object>> doc = documentBuilder.build(co, performIndexPayloads, item.pointerToSchemaMap, cleanupActions);
                documentBatch.add(new ElasticInputDocumentWithHandle(doc, co.id));
            }
            BulkResponse response = indexBatch(documentBatch);
            if(response.hasFailures()) {
                for (BulkItemResponse bulkItemResponse : response) {
                    if (bulkItemResponse.isFailed()) {
                        BulkItemResponse.Failure failure = bulkItemResponse.getFailure();
                        logger.error("Error during indexObjects: "+failure.toString());
                    }
                }
                throw new IndexerException("Unexpected ElasticSearch response "  + response);
            }
            if (!(response.status().getStatus() >= 200 && response.status().getStatus() < 400)) {
                throw new IndexerException("Unexpected ElasticSearch response "  + response);
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
            // this is just to get it into the index; it is not stored
            co.metadata.internalMetadata.addProperty(PAYLOAD_INDEX_STATE, INDEX_COMPLETE);
            Map<String, List<Object>> doc = documentBuilder.build(co, indexPayloads, pointerToSchemaMap, cleanupActions);
            objectLocker.lock(co.id);
            locked = true;
            if (storage.get(co.id) == null) return;
            IndexResponse response = index(co.id, doc);
            if (!(response.status().getStatus() >= 200 && response.status().getStatus() < 400)) {
                throw new IndexerException("Could not index object: " + co.id + ". Status Code: " + response.status().getStatus());
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

    public static class ElasticInputDocumentWithHandle {
        public Map<String, List<Object>> doc;
        public String handle;

        public ElasticInputDocumentWithHandle(Map<String, List<Object>> doc, String handle) {
            this.doc = doc;
            this.handle = handle;
        }
    }

    private BulkResponse indexBatch(List<ElasticInputDocumentWithHandle> batch) throws IOException {
        BulkRequest bulkRequest = new BulkRequest();
        for (ElasticInputDocumentWithHandle item : batch) {
            //There is no need to percent encode the handle with bulk requests.
            String json = gson.toJson(item.doc);
            IndexRequest request = new IndexRequest(INDEX_NAME);
            request.id(item.handle);
            request.type(TYPE_NAME);
            request.source(json, XContentType.JSON);
            bulkRequest.add(request);
        }
        return client.bulk(bulkRequest, RequestOptions.DEFAULT);
    }

    private IndexResponse index(String handle, Map<String, List<Object>> doc) throws IOException {
        String encodedHandle = StringUtils.encodeURLComponent(handle);
        String json = gson.toJson(doc);
        IndexRequest request = new IndexRequest(INDEX_NAME);
        request.id(encodedHandle);
        request.type(TYPE_NAME);
        request.source(json, XContentType.JSON);
        return client.index(request, RequestOptions.DEFAULT);
    }

    @Override
    public void deleteObject(String handle) throws IndexerException {
        String encodedHandle = StringUtils.encodeURLComponent(handle);
        DeleteRequest request = new DeleteRequest(INDEX_NAME);
        request.id(encodedHandle);
        request.type(TYPE_NAME);
        try {
            client.delete(request, RequestOptions.DEFAULT);
        } catch (Exception e) {
            throw new IndexerException("Error deleting object: "+handle);
        }
    }

    @Override
    @SuppressWarnings("resource")
    public SearchResults<CordraObject> search(String query, QueryParams params) throws IndexerException {
        return search(query, params, CordraObject.class);
//        try {
//            SearchResponse results = getSearchResults(query, params);
//            ElasticScrollableSearchResults scrollableSearchResults = new ElasticScrollableSearchResults(results, highLevelClient);
//            return new AbstractCordraSearchResultsFromIndexerSearchResultsBatch<CordraObject, SearchHit>(scrollableSearchResults, storage, CordraObject.class) {
//                @Override
//                public String getIdFromDocument(SearchHit document) {
//                    return document.getId();
//                }
//            };
//        } catch (Exception e) {
//            throw new IndexerException(e);
//        }
    }

    @Override
    @SuppressWarnings("resource")
    public SearchResults<String> searchHandles(String query, QueryParams params) throws IndexerException {
        return search(query, params, String.class);
//        try {
//            SearchResponse results = getSearchResults(query, params);
//            ElasticScrollableSearchResults scrollableSearchResults = new ElasticScrollableSearchResults(results, highLevelClient);
//            return new AbstractCordraSearchResultsFromIndexerSearchResultsBatch<String, SearchHit>(scrollableSearchResults, storage, String.class) {
//                @Override
//                public String getIdFromDocument(SearchHit document) {
//                    return document.getId();
//                }
//            };
//        } catch (Exception e) {
//            throw new IndexerException(e);
//        }
    }

    @Override
    public SearchResults<IdType> searchIdType(String query, QueryParams params) throws IndexerException {
        return search(query, params, IdType.class);
    }

    @SuppressWarnings("resource")
    private <T> SearchResults<T> search(String query, QueryParams params, Class<T> klass) throws IndexerException {
        try {
            SearchResponse results = getSearchResults(query, params);
            ElasticScrollableSearchResults scrollableSearchResults = new ElasticScrollableSearchResults(results, client);
            return new AbstractCordraSearchResultsFromIndexerSearchResultsBatch<T, SearchHit>(scrollableSearchResults, storage, klass) {
                @Override
                public String getIdFromDocument(SearchHit document) {
                    return document.getId();
                }
                @Override
                public String getTypeFromDocument(SearchHit document) {
                    DocumentField field = document.field("type.raw");
                    if (field == null) field = document.field("type"); // possible backward compatibility
                    if (field == null) return null;
                    return (String) field.getValue();
                }
            };
        } catch (Exception e) {
            throw new IndexerException(e);
        }
    }

    public int calculateFromPosition(QueryParams params) {
        int pageNum = params.getPageNumber();
        int pageSize = params.getPageSize();
        if (pageNum == 0) {
            return 0;
        }
        if (pageSize <= 0) {
            return 0;
        }
        int from = pageNum * pageSize;
        return from;
    }

    private SearchResponse getSearchResults(String query, QueryParams params) throws IOException {
        if(params == null) {
            params = QueryParams.DEFAULT;
        }
        int from = calculateFromPosition(params);
        int size = params.getPageSize();
        List<SortField> sortFields = params.getSortFields();
        boolean getAllResults = false;

        QueryBuilder queryBuilder = QueryBuilders
                .queryStringQuery(CordraIndexer.fixSlashes(query));
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder
            .query(queryBuilder)
            .from(from)
            .trackTotalHits(true)
            .explain(true);
        if (size < 0) {
            size = 500;
            getAllResults = true;
        }
        searchSourceBuilder.size(size);
        if (sortFields != null) {
            for (SortField sortField : sortFields) {
                if (!"undefined".equals(sortField.getName())) {
                    SortOrder sortOrder = sortField.isReverse() ? SortOrder.DESC : SortOrder.ASC;
                    String sortFieldName = documentBuilder.getSortFieldName(sortField.getName());
                    if (sortFieldName != null) {
                        searchSourceBuilder.sort(sortFieldName, sortOrder);
                    }
                }
            }
        }
        searchSourceBuilder.storedFields(Arrays.asList("type.raw", "type"));
        SearchRequest request = new SearchRequest(INDEX_NAME);
        request.source(searchSourceBuilder);
        request.searchType(SearchType.DFS_QUERY_THEN_FETCH);
        if (getAllResults) request.scroll("60s");

        // for now, use low-level client so we can support Elasticsearch 6 and 7
        // https://github.com/elastic/elasticsearch/issues/43925
        // As of Elasticsearch 6.8.3, we could use the HLRC, but we'd lose support for server versions < 6.6.0...
        // https://github.com/elastic/elasticsearch/pull/46076
        boolean totalHitsAsInt = majorVersion >= 7;
        Request lowLevelRequest = SearchRequestResponseConverter.convertRequest(request, totalHitsAsInt);
        Response response = client.getLowLevelClient().performRequest(lowLevelRequest);
        return SearchRequestResponseConverter.convertResponse(response);
//        return client.search(request, RequestOptions.DEFAULT);
    }

    @Override
    public void ensureIndexUpToDate() throws IndexerException {
        RefreshResponse response;
        try {
            RefreshRequest request = new RefreshRequest(INDEX_NAME);
            response = client.indices().refresh(request, RequestOptions.DEFAULT);
        } catch (Exception e) {
            throw new IndexerException(e);
        }
        if (response.getStatus().getStatus() != HttpStatus.SC_OK) {
            throw new IndexerException("Refresh index response not OK. Code: "+response.getStatus().getStatus());
        }
    }

    @Override
    public void close() throws IOException {
        client.close();
        exec.shutdown();
    }

    private void initializeIndex(String mappingJson, Settings indexSettings) throws IOException, IndexerException {
        logger.info("Initializing Elasticsearch index.");
        CreateIndexRequest request = new CreateIndexRequest(INDEX_NAME);
        request.settings(indexSettings);
        request.mapping(TYPE_NAME, mappingJson, XContentType.JSON);
        @SuppressWarnings("deprecation") // for Elasticsearch 6 support
        CreateIndexResponse response = client.indices().create(request, RequestOptions.DEFAULT);
        if (!response.isAcknowledged()) {
            throw new IndexerException("Create index response not acknowledged.");
        }
    }
}
