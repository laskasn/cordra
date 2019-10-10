package net.cnri.cordra.indexer.lucene;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.gson.JsonObject;
import net.cnri.cordra.Design;
import net.cnri.cordra.api.*;
import net.cnri.cordra.collections.AbstractSearchResults;
import net.cnri.cordra.indexer.CordraIndexer;
import net.cnri.cordra.indexer.IdType;
import net.cnri.cordra.indexer.IndexerException;
import net.cnri.cordra.indexer.ObjectTransformer;
import net.cnri.cordra.storage.CordraStorage;
import net.cnri.cordra.sync.NameLocker;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.*;
import org.apache.lucene.store.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class LuceneIndexer implements CordraIndexer {
    private static Logger logger = LoggerFactory.getLogger(LuceneIndexer.class);
    private static int SEARCH_WINDOW_SIZE = 8192;

    private DocumentBuilderLucene documentBuilder;
    private final IndexWriter indexWriter;
    private final ExecutorService exec;
    private Analyzer analyzer;
    private final QueryCache queryCache;
    private final SearcherManager searcherManager;
    private AtomicBoolean isCommitScheduled = new AtomicBoolean();
    private ScheduledExecutorService commitExecServ = Executors.newScheduledThreadPool(1);
    {
        ((ScheduledThreadPoolExecutor)commitExecServ).setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    }
    private final CordraStorage storage;
    private final NameLocker objectLocker;
    private boolean isStoreFields = false;
    private volatile boolean shutdown = false;


    // for testing
    @SuppressWarnings("resource")
    public LuceneIndexer(CordraStorage storage, NameLocker objectLocker) throws IOException {
        this(new ByteBuffersDirectory(), storage, false, objectLocker);
    }

    public LuceneIndexer(File indexBase, CordraStorage storage, boolean isStoreFields, NameLocker objectLocker) throws IOException {
        this(newFSDirectory(indexBase), storage, isStoreFields, objectLocker);
    }

    private static Directory newFSDirectory(File indexBase) throws IOException {
        File indexDir = new File(indexBase, "cordraIndex");
        indexDir.mkdirs();
        return FSDirectory.open(indexDir.toPath());
    }

    private LuceneIndexer(Directory indexDirectory, CordraStorage storage, boolean isStoreFields, NameLocker objectLocker) throws IOException {
        this.storage = storage;
        this.objectLocker = objectLocker;
        this.isStoreFields = isStoreFields;
        this.documentBuilder = new DocumentBuilderLucene(this.isStoreFields, storage);
        exec = Executors.newSingleThreadExecutor();
        initAnalyzer();
        indexWriter = new IndexWriter(indexDirectory, new IndexWriterConfig(analyzer).setOpenMode(OpenMode.CREATE_OR_APPEND));
        if(!DirectoryReader.indexExists(indexDirectory)) {
            indexWriter.commit();
        }
        searcherManager = new SearcherManager(indexWriter, true, true, null);
        long maxRamBytesUsed = Math.min(1L << 30 /* 1GB */, Runtime.getRuntime().maxMemory() / 5);
        queryCache = new LRUQueryCache(1000, maxRamBytesUsed);
    }

    @SuppressWarnings("resource")
    private void initAnalyzer() {
        Map<String,Analyzer> analyzerMap = new HashMap<>();
        analyzerMap.put("id", new LowerCaseKeywordAnalyzer());
        analyzerMap.put("repoid",new LowerCaseKeywordAnalyzer());
        analyzerMap.put("indexVersion",new WhitespaceAnalyzer());
        analyzerMap.put("type", new LowerCaseKeywordAnalyzer());
        analyzerMap.put("aclRead", new LowerCaseKeywordAnalyzer());
        analyzerMap.put("aclWrite", new LowerCaseKeywordAnalyzer());
        analyzerMap.put("createdBy", new LowerCaseKeywordAnalyzer());
        analyzerMap.put("username", new LowerCaseKeywordAnalyzer());
        analyzerMap.put("schemaName", new LowerCaseKeywordAnalyzer());
        analyzerMap.put("javaScriptModuleName", new LowerCaseKeywordAnalyzer());
        analyzerMap.put("internal.pointsAt", new LowerCaseKeywordAnalyzer());
        analyzerMap.put("isVersion", new LowerCaseKeywordAnalyzer());
        analyzerMap.put("versionOf", new LowerCaseKeywordAnalyzer());
        analyzerMap.put("payloadIndexState", new LowerCaseKeywordAnalyzer());
        analyzerMap.put("payloadIndexCordraServiceId", new LowerCaseKeywordAnalyzer());
        analyzer = new PerFieldAnalyzerWrapper(new LowerCaseStandardTokenizerAnalyzer(),  analyzerMap);
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
            Document doc = documentBuilder.build(co, indexPayloadsNow, pointerToSchemaMap, cleanupActions);
            //Document doc = new DocumentBuilderLucene(isStoreFields, storage).build(co, indexPayloadsNow, pointerToSchemaMap, cleanupActions);
            if (indexPayloads && hasPayloads && !shutdown) {
                exec.submit(() -> indexObjectWithPayloadsAndLogException(co, pointerToSchemaMap));
            }
            indexWriter.updateDocument(new Term("id", co.id), doc);
            commitAfterDelay();
        } catch (Exception e) {
            throw new IndexerException(e);
        } finally {
            for (Runnable runnable : cleanupActions) {
                runnable.run();
            }
        }
    }

    private void indexObjectWithPayloadsAndLogException(CordraObject co, Map<String, JsonNode> pointerToSchemaMap) {
        if (shutdown) return;
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
            boolean indexPayloads = true;
            // this is just to get it into the index; it is not stored
            co.metadata.internalMetadata.addProperty(PAYLOAD_INDEX_STATE, INDEX_COMPLETE);
            Document doc = documentBuilder.build(co, indexPayloads, pointerToSchemaMap, cleanupActions);
            //Document doc = new DocumentBuilderLucene(isStoreFields, storage).build(co, indexPayloads, pointerToSchemaMap, cleanupActions);
            objectLocker.lock(co.id);
            locked = true;
            if (storage.get(co.id) == null) return;
            indexWriter.updateDocument(new Term("id", co.id), doc);
            commitAfterDelay();
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
            indexWriter.deleteDocuments(new Term("id", handle));
            commitAfterDelay();
        } catch (Exception e) {
            throw new IndexerException("Unexpected Lucene response ", e);
        }
    }

    @Override
    public SearchResults<CordraObject> search(String query, QueryParams params) throws IndexerException {
        return search(query, params, CordraObject.class);
    }

    @Override
    public SearchResults<String> searchHandles(String query, QueryParams params) throws IndexerException {
        return search(query, params, String.class);
    }

    @Override
    public SearchResults<IdType> searchIdType(String query, QueryParams params) throws IndexerException {
        return search(query, params, IdType.class);
    }

    public <T> SearchResults<T> search(String query, QueryParams params, Class<T> klass) throws IndexerException {
        String queryStrFixed = CordraIndexer.fixSlashes(query);
        //QueryParser queryParser = new QueryParser("internal.all", analyzer);
        QueryParser queryParser = new CordraQueryParser(analyzer);
        IndexSearcher searcher = null;
        try {
            searcher = searcherManager.acquire();
            searcher.setQueryCache(queryCache);
            Query q = queryParser.parse(queryStrFixed);
            Sort sort = buildSort(params);
            TopDocs topDocs;
            int totalHits;
            if (params != null && params.getPageSize() == 0) {
                TotalHitCountCollector collector = new TotalHitCountCollector();
                searcher.search(q, collector);
                topDocs = null;
                totalHits = collector.getTotalHits();
            } else if (sort == null) {
                topDocs = searcher.search(q, SEARCH_WINDOW_SIZE);
                totalHits = (int) topDocs.totalHits;
            } else {
                topDocs = searcher.search(q, SEARCH_WINDOW_SIZE, sort);
                totalHits = (int) topDocs.totalHits;
            }
            return new LuceneSearchResults<>(searcher, q, sort, params, topDocs, totalHits, klass);
            //return new QueryResults<>(topDocs.totalHits, new CloseableIteratorFromSearch<>(searcher, q, sort, params, topDocs, klass));
        } catch (Exception e) {
            if (searcher != null) try {
                // Note: in non-exceptional cases, the searcherManager.release method is called when the QueryResults is closed.
                searcherManager.release(searcher);
            } catch (IOException ex) {
                e.addSuppressed(ex);
            }
            throw new IndexerException(e);
        }
    }

    public static class CordraQueryParser extends QueryParser {
        public CordraQueryParser(Analyzer analyzer) {
            super("internal.all", analyzer);
        }

        @Override
        protected org.apache.lucene.search.Query newTermQuery(org.apache.lucene.index.Term term) {
            if ("txnId".equals(term.field())) {
                return LongPoint.newExactQuery("txnId", Long.parseLong(term.text()));
            } else {
                return super.newTermQuery(term);
            }
        }

        @Override
        protected org.apache.lucene.search.Query newRangeQuery(String fieldParam, String start, String end, boolean startInclusive, boolean endInclusive) {
            if ("txnId".equals(fieldParam)) {
                long endLong = 0;
                long startLong = 0;
                if ("*".equals(end) || end == null) {
                    endLong = Long.MAX_VALUE;
                } else {
                    endLong = Long.parseLong(end);
                }
                if ("*".equals(start) || start == null) {
                    startLong = Long.MIN_VALUE;
                } else {
                    startLong = Long.parseLong(start);
                }
                if (!startInclusive) {
                    startLong = Math.addExact(startLong, 1);
                }
                if (!endInclusive) {
                    endLong = Math.addExact(endLong, -1);
                }
                return LongPoint.newRangeQuery("txnId", startLong, endLong);
            } else {
                return super.newRangeQuery(fieldParam, start, end, startInclusive, endInclusive);
            }
        }
    }

    private Sort buildSort(QueryParams params) {
        if (params == null) return null;
        if (params.getSortFields() == null || params.getSortFields().isEmpty()) return null;
        SortField[] sortFields = params.getSortFields().stream()
                        .map(this::makeLuceneSortField)
                        .filter(Objects::nonNull)
                        .toArray(size -> new SortField[size]);
        if (sortFields.length == 0) return null;
        return new Sort(sortFields);
    }

    private SortField makeLuceneSortField(net.cnri.cordra.api.SortField cordraSortField) {
        String fieldName = documentBuilder.getSortFieldName(cordraSortField.getName());
        if (fieldName == null) return null;
        if ("sort_txnId".equals(fieldName)) {
            return new SortField(fieldName, SortField.Type.LONG, cordraSortField.isReverse());
        }
        if ("score".equals(fieldName)) {
            return new SortField(null, SortField.Type.SCORE, !cordraSortField.isReverse());
        }
        return new SortField(fieldName, SortField.Type.STRING, cordraSortField.isReverse());
    }

    private static final Set<String> ID_SET = Collections.singleton("id");
    private static final Set<String> ID_TYPE_SET = new HashSet<>(Arrays.asList("id", "type"));

    private class LuceneSearchResults<T> extends AbstractSearchResults<T> {
        final IndexSearcher searcher;
        final Query q;
        final Sort sort;
        int pageSize = -1;
        int toSkip = 0;
        TopDocs topDocs;
        int totalHits;
        int next = 0;
        boolean skipped = false;
        int returned = 0;
        final Class<T> klass;

        public LuceneSearchResults(IndexSearcher searcher, Query q, Sort sort, QueryParams params, TopDocs topDocs, int totalHits, Class<T> klass) {
            this.searcher = searcher;
            this.q = q;
            this.sort = sort;
            this.topDocs = topDocs;
            this.totalHits = totalHits;
            this.klass = klass;
            if (params != null) {
                pageSize = params.getPageSize();
                toSkip = pageSize * params.getPageNumber();
                if (toSkip <= 0) skipped = true;
            }
        }

        @Override
        protected T computeNext() {
            if (pageSize >= 0 && returned >= pageSize) return null;
            try {
                ScoreDoc scoreDoc;
                if (!skipped) {
                    scoreDoc = skipScoreDocs();
                } else {
                    scoreDoc = nextScoreDoc();
                }
                if (scoreDoc == null) return null;
                returned++;
                T result = getResult(scoreDoc);
                if (result == null) return computeNext();
                return result;
            } catch (IOException e) {
                throw new UncheckedCordraException(new InternalErrorCordraException(e));
            } catch (CordraException e) {
                throw new UncheckedCordraException(new InternalErrorCordraException(e));
            }

        }

        private ScoreDoc skipScoreDocs() throws IOException {
            int numSkipped = 0;
            while (numSkipped < toSkip) {
                if (topDocs.scoreDocs.length == 0) {
                    return null;
                } else if ((toSkip - numSkipped) < topDocs.scoreDocs.length) {
                    next = toSkip - numSkipped;
                    numSkipped = toSkip;
                } else {
                    numSkipped += topDocs.scoreDocs.length;
                    refillTopDocs();
                }
            }
            skipped = true;
            return nextScoreDoc();
        }

        private ScoreDoc nextScoreDoc() throws IOException {
            if (topDocs.scoreDocs.length > next) {
                ScoreDoc scoreDoc = topDocs.scoreDocs[next];
                next++;
                return scoreDoc;
            } else {
                if (next == 0) return null;
                refillTopDocs();
                next = 0;
                return nextScoreDoc();
            }
        }

        private void refillTopDocs() throws IOException {
            if (sort == null) {
                topDocs = searcher.searchAfter(topDocs.scoreDocs[topDocs.scoreDocs.length - 1], q, SEARCH_WINDOW_SIZE);
            } else {
                topDocs = searcher.searchAfter(topDocs.scoreDocs[topDocs.scoreDocs.length - 1], q, SEARCH_WINDOW_SIZE, sort);
            }
        }

        // This is the magic allowing the sharing of code across search and searchHandles
        @SuppressWarnings("unchecked")
        private T getResult(ScoreDoc scoreDoc) throws IOException, CordraException {
            if (klass == String.class) {
                Document doc = searcher.doc(scoreDoc.doc, ID_SET);
                return (T) doc.get("id");
            } else if (klass == IdType.class) {
                Document doc = searcher.doc(scoreDoc.doc, ID_TYPE_SET);
                IdType idType = new IdType(doc.get("id"), doc.get("type"));
                return (T) idType;
            } else {
                Document doc = searcher.doc(scoreDoc.doc);
// XXX isStoreFields
//                if (isStoreFields) {
//                    return (T) new LuceneSearchResultDigitalObject(doc);
//                } else {
                    String id = doc.get("id");
                    return (T) storage.get(id);
//                }
            }
        }

        @Override
        protected void closeOnlyOnce() {
            try { searcherManager.release(searcher); } catch (IOException e) { logger.warn("Error releasing searcher", e); }
        }

        @Override
        public int size() {
            return totalHits;
        }
    }

    @Override
    public void ensureIndexUpToDate() throws IndexerException {
//        try {
//            indexWriter.commit();
//        } catch (IOException e) {
//            throw new IndexerException(e);
//        }
        try {
            searcherManager.maybeRefreshBlocking();
        } catch (Exception e) {
            throw new IndexerException(e);
        }
    }

    private void commitAfterDelay() {
        if (isCommitScheduled.getAndSet(true)) return;
        commitExecServ.schedule(this::commitLoggingException, 5, TimeUnit.SECONDS);
    }

    private void commitLoggingException() {
        if (shutdown) return;
        isCommitScheduled.set(false);
        try {
            indexWriter.commit();
        } catch (IOException e) {
            logger.warn("Error committing", e);
        }
    }

    @Override
    public void close() throws IOException {
        if (shutdown) return;
        shutdown = true;
        documentBuilder.shutdown();
        exec.shutdown();
        try {
            if (!exec.awaitTermination(10, TimeUnit.MINUTES)) {
                logger.warn("Shutdown issue: payload indexing execServ not finished");
            }
        } catch (InterruptedException e) {
            logger.warn("Shutdown issue: interrupt");
            Thread.currentThread().interrupt();
        }
        commitExecServ.shutdown();
        try {
            if (!commitExecServ.awaitTermination(10, TimeUnit.MINUTES)) {
                logger.warn("Shutdown issue: commit execServ not finished");
            }
        } catch (InterruptedException e) {
            logger.warn("Shutdown issue: interrupt");
            Thread.currentThread().interrupt();
        }
        if (isCommitScheduled.get()) {
            try {
                indexWriter.commit();
            } catch (IOException e) {
                logger.warn("Error committing", e);
            }
        }
        searcherManager.close();
        indexWriter.close();
    }

}
