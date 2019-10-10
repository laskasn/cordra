package net.cnri.cordra.storage.mongodb;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.TimeUnit;

import com.mongodb.MongoClientURI;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mongodb.MongoClient;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.GridFSDownloadStream;
import com.mongodb.client.gridfs.GridFSFindIterable;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.mongodb.client.gridfs.model.GridFSUploadOptions;
import com.mongodb.client.model.FindOneAndDeleteOptions;
import com.mongodb.client.model.FindOneAndReplaceOptions;
import com.mongodb.client.model.IndexOptions;

import net.cnri.cordra.GsonUtility;
import net.cnri.cordra.api.ConflictCordraException;
import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.api.CordraObject;
import net.cnri.cordra.api.NotFoundCordraException;
import net.cnri.cordra.api.Payload;
import net.cnri.cordra.api.SearchResults;
import net.cnri.cordra.collections.AbstractSearchResults;
import net.cnri.cordra.collections.SearchResultsFromStream;
import net.cnri.cordra.storage.CordraStorage;
import net.cnri.cordra.storage.LimitedInputStream;

public class MongoDbStorage implements CordraStorage {
    private static Logger logger = LoggerFactory.getLogger(MongoDbStorage.class);
    public static long DEFAULT_MAX_TIME_MS = 30_000;
    public static long DEFAULT_MAX_TIME_MS_LONG_RUNNING = 1000 * 60 * 60 * 24 * 10 ; //10 days

    private final static Gson gson = GsonUtility.getGson();
    private MongoClient client;
    private MongoDatabase db;
    private MongoCollection<Document> collection;
    private GridFSBucket gridFSBucket;
    private MongoCollection<Document> fsCollection;
    private long maxTimeMs;
    private long maxTimeMsLongRunning;

    public MongoDbStorage(MongoClient client) {
        this(client, null, null, null, DEFAULT_MAX_TIME_MS, DEFAULT_MAX_TIME_MS_LONG_RUNNING);
    }

    public MongoDbStorage(MongoClient client, String databaseName, String collectionName, String gridFsBucketName, long maxTimeMs, long maxTimeMsLongRunning) {
        initialize(client, databaseName, collectionName, gridFsBucketName, maxTimeMs, maxTimeMsLongRunning);
    }

    @SuppressWarnings("resource")
    public MongoDbStorage(JsonObject options) {
        String connectionUri = getAsStringOrNull(options, "connectionUri");
        MongoClient mongoClient;
        if (connectionUri != null) {
            mongoClient = new MongoClient(new MongoClientURI(connectionUri));
        } else {
            mongoClient = new MongoClient();
        }
        String maxTimeMsString = getAsStringOrNull(options, "maxTimeMs");
        long maxTimeMsOption = -1;
        if (maxTimeMsString != null) {
            maxTimeMsOption = Long.parseLong(maxTimeMsString);
        }
        String maxTimeMsLongRunningString = getAsStringOrNull(options, "maxTimeMsLongRunning");
        long maxTimeMsLongRunningOption = -1;
        if (maxTimeMsLongRunningString != null) {
            maxTimeMsLongRunningOption = Long.parseLong(maxTimeMsLongRunningString);
        }
        String databaseName = getAsStringOrNull(options, "databaseName");
        String collectionName = getAsStringOrNull(options, "collectionName");
        String gridFsBucketName = getAsStringOrNull(options, "gridFsBucketName");
        initialize(mongoClient, databaseName, collectionName, gridFsBucketName, maxTimeMsOption, maxTimeMsLongRunningOption);
    }

    @SuppressWarnings("hiding")
    private void initialize(MongoClient client, String databaseName, String collectionName, String gridFsBucketName, long maxTimeMs, long maxTimeMsLongRunning) {
        this.maxTimeMs = maxTimeMs < 0 ? DEFAULT_MAX_TIME_MS : maxTimeMs;
        this.maxTimeMsLongRunning = maxTimeMsLongRunning < 0 ? DEFAULT_MAX_TIME_MS_LONG_RUNNING : maxTimeMsLongRunning;
        this.client = client;
        this.db = this.client.getDatabase(databaseName == null ? "cordra" : databaseName);
        this.collection = db.getCollection(collectionName == null ? "cordra" : collectionName);

        IndexOptions indexOptions = new IndexOptions();
        indexOptions.unique(true);
        this.collection.createIndex(new Document("id", 1), indexOptions);

        IndexOptions typeIndexOptions = new IndexOptions();
        typeIndexOptions.unique(false);
        typeIndexOptions.background(true);
        this.collection.createIndex(new Document("type", 1), typeIndexOptions);

        if (gridFsBucketName == null) gridFsBucketName = "fs";
        gridFSBucket = GridFSBuckets.create(db, gridFsBucketName);
        fsCollection = db.getCollection(gridFsBucketName + ".files");

        IndexOptions fsIdIndexOptions = new IndexOptions();
        fsIdIndexOptions.unique(false);
        fsCollection.createIndex(new Document("metadata.id", 1), fsIdIndexOptions);

        Document fsIdNameIndex = new Document().append("metadata.id", 1).append("metadata.name", 1);
        fsCollection.createIndex(fsIdNameIndex, indexOptions);
    }

    private static String getAsStringOrNull(JsonObject options, String propertyName) {
        if (options.has(propertyName)) {
            String result = options.get(propertyName).getAsString();
            return result;
        } else {
            return null;
        }
    }

    @Override
    public CordraObject get(String id) throws CordraException {
        Document query = new Document("id", id);
        Document doc = collection.find(query).maxTime(maxTimeMs, TimeUnit.MILLISECONDS).first();
        if (doc == null) {
            return null;
        }
        CordraObject d = documentToCordraObject(doc);
        return d;
    }

    @Override
    public SearchResults<CordraObject> get(Collection<String> ids) throws CordraException {
        Map<String, CordraObject> resultsMap = new HashMap<>();
        Document query = new Document("id", new Document("$in", ids));
        FindIterable<Document> findIter = collection.find(query).maxTime(maxTimeMsLongRunning, TimeUnit.MILLISECONDS);
        try (MongoCursor<Document> cursor = findIter.iterator()) {
            while (cursor.hasNext()) {
                CordraObject co = documentToCordraObject(cursor.next());
                resultsMap.put(co.id, co);
            }
        }
        return new SearchResultsFromStream<>(resultsMap.size(), ids.stream().map(resultsMap::get).filter(Objects::nonNull));
    }

    static Document cordraObjectToDocument(CordraObject d) throws CordraException {
        JsonObject obj = gson.toJsonTree(d).getAsJsonObject();
        Document doc = MongoDbUtil.jsonObjectToDocument(obj);
        MongoDbUtil.percentEncodeFieldNames(doc);
        return doc;
    }

    static CordraObject documentToCordraObject(Document doc) {
        JsonElement el = gson.toJsonTree(doc);
        MongoDbUtil.percentDecodeFieldNames(el);
        CordraObject d = gson.fromJson(el, CordraObject.class);
        return d;
    }

    @Override
    public InputStream getPayload(String id, String payloadName) throws CordraException {
        Document metadata = new Document();
        metadata.append("id", id).append("name", payloadName);
        Document query = new Document("metadata", metadata);

        com.mongodb.client.gridfs.model.GridFSFile f = gridFSBucket.find(query).maxTime(maxTimeMs, TimeUnit.MILLISECONDS).first();
        if (f == null) {
            return null;
        }
        ObjectId objectId = f.getObjectId();
        GridFSDownloadStream downloadStream = gridFSBucket.openDownloadStream(objectId);
        return downloadStream;
    }

    @Override
    public InputStream getPartialPayload(String id, String payloadName, Long start, Long end) throws CordraException {
        Document metadata = new Document();
        metadata.append("id", id).append("name", payloadName);
        Document query = new Document("metadata", metadata);

        com.mongodb.client.gridfs.model.GridFSFile f = gridFSBucket.find(query).maxTime(maxTimeMs, TimeUnit.MILLISECONDS).first();
        if (f == null) {
            return null;
        }
        ObjectId objectId = f.getObjectId();
        GridFSDownloadStream downloadStream = gridFSBucket.openDownloadStream(objectId);
        if (start == null && end == null) {
            return downloadStream;
        }
        if (start == null) {
            long size = f.getLength();
            start = size - end;
            if (start <= 0) return downloadStream;
            return new LimitedInputStream(downloadStream, start, Long.MAX_VALUE);
        }
        if (end == null) {
            return new LimitedInputStream(downloadStream, start, Long.MAX_VALUE);
        }
        long length = end - start + 1;
        if (length < 0) length = 0;
        return new LimitedInputStream(downloadStream, start, length);
    }

    @Override
    public CordraObject create(CordraObject d) throws CordraException {
        Document foundDocument = collection.find(new Document("id", d.id)).maxTime(maxTimeMs, TimeUnit.MILLISECONDS).first();
        if (foundDocument != null) {
            throw new ConflictCordraException("Object already exists: " + d.id);
        }
        if (d.payloads != null) {
            for (Payload p : d.payloads) {
                try (InputStream in = p.getInputStream();) {
                    long length = writeInputStreamToGridFS(in, d.id, p.name);
                    p.size = length;
                } catch (IOException e) {
                    throw new CordraException(e);
                } finally {
                    p.setInputStream(null);
                }
            }
            if (d.payloads.isEmpty()) {
                d.payloads = null;
            }
        }
        Document doc = cordraObjectToDocument(d);
        collection.insertOne(doc);
        return d;
    }

    private long writeInputStreamToGridFS(InputStream in, String id, String payloadName) {
        GridFSUploadOptions options = new GridFSUploadOptions()
                .metadata(new Document().append("id", id).append("name", payloadName));
        ObjectId fileId = gridFSBucket.uploadFromStream(payloadName, in, options);
        long length = getFileSizeWithFind(fileId);
        return length;
    }

    private long getFileSizeWithFind(ObjectId fileId) {
        Document query= new Document("_id", fileId);
        GridFSFile f = gridFSBucket.find(query).maxTime(maxTimeMs, TimeUnit.MILLISECONDS).first();
        return f.getLength();
    }

    @Override
    public CordraObject update(CordraObject d) throws CordraException {
        Document foundDocument = collection.find(new Document("id", d.id)).maxTime(maxTimeMs, TimeUnit.MILLISECONDS).first();
        if (foundDocument == null) {
            throw new NotFoundCordraException("Object does not exist: " + d.id);
        }
        List<String> payloadsToDelete = d.getPayloadsToDelete();
        String id = d.id;
        for (String payloadName : payloadsToDelete) {
            Document metadata = new Document();
            metadata.append("id", id).append("name", payloadName);
            Document query = new Document("metadata", metadata);
            com.mongodb.client.gridfs.model.GridFSFile f = gridFSBucket.find(query).maxTime(maxTimeMs, TimeUnit.MILLISECONDS).first();
            if (f == null) {
                // shouldn't happen, but we can ignore since we were deleting anyway
                logger.warn("Unexpected missing GridFSFile " + id + " " + payloadName);
            } else {
                ObjectId objectId = f.getObjectId();
                logger.info("deleting file " + objectId.toString());
                gridFSBucket.delete(objectId);
            }
        }
        d.clearPayloadsToDelete();
        if (d.payloads != null) {
            for (Payload p : d.payloads) {
                if (p.getInputStream() != null) {
                    Document metadata = new Document();
                    metadata.append("id", id).append("name", p.name);
                    Document query = new Document("metadata", metadata);
                    com.mongodb.client.gridfs.model.GridFSFile f = gridFSBucket.find(query).maxTime(maxTimeMs, TimeUnit.MILLISECONDS).first();
                    if (f != null) {
                        ObjectId objectId = f.getObjectId();
                        gridFSBucket.delete(objectId);
                    }
                    try (InputStream in = p.getInputStream();) {
                        long length = writeInputStreamToGridFS(in, d.id, p.name);
                        p.size = length;
                    } catch (IOException e) {
                        throw new CordraException(e);
                    } finally {
                        p.setInputStream(null);
                    }
                }
            }
            if (d.payloads.isEmpty()) {
                d.payloads = null;
            }
        }
        Document doc = cordraObjectToDocument(d);
        Document query = new Document("id", d.id);
        collection.findOneAndReplace(query, doc, new FindOneAndReplaceOptions().maxTime(maxTimeMs, TimeUnit.MILLISECONDS));
        return d;
    }

    @Override
    public void delete(String id) throws CordraException {
        Document foundDocument = collection.find(new Document("id", id)).maxTime(maxTimeMs, TimeUnit.MILLISECONDS).first();
        if (foundDocument == null) {
            throw new NotFoundCordraException("Object does not exist: " + id);
        }

        collection.findOneAndDelete(new Document("id", id), new FindOneAndDeleteOptions().maxTime(maxTimeMs, TimeUnit.MILLISECONDS));

        Document query = new Document("metadata.id", id);

        GridFSFindIterable iter = gridFSBucket.find(query).maxTime(maxTimeMs, TimeUnit.MILLISECONDS);
        MongoCursor<com.mongodb.client.gridfs.model.GridFSFile> cursor = iter.iterator();
        try {
            while (cursor.hasNext()) {
                com.mongodb.client.gridfs.model.GridFSFile f = cursor.next();
                ObjectId objectId = f.getObjectId();
                gridFSBucket.delete(objectId);
            }
        } finally {
            cursor.close();
        }
    }

    @Override
    public SearchResults<CordraObject> list() throws CordraException {
        return new MongoDbListSearchResults();
    }

    @Override
    public SearchResults<String> listHandles() throws CordraException {
        return new MongoDbListHandlesSearchResults();
    }

    @Override
    public SearchResults<CordraObject> listByType(List<String> types) throws CordraException {
        Document query = new Document("type", new Document("$in", types));
        return new MongoDbListSearchResults(query);
    }

    @Override
    public SearchResults<String> listHandlesByType(List<String> types) throws CordraException {
        Document query = new Document("type", new Document("$in", types));
        return new MongoDbListHandlesSearchResults(query);
    }

    public SearchResults<CordraObject> directSearch(Document query) {
        return new MongoDbListSearchResults(query);
    }

    public SearchResults<String> directSearchHandles(Document query) {
        return new MongoDbListHandlesSearchResults(query);
    }

    @Override
    public void close() {
        client.close();
    }

    private class MongoDbListSearchResults extends AbstractSearchResults<CordraObject> {
        private final MongoCursor<Document> cursor;

        public MongoDbListSearchResults(Bson query) {
            FindIterable<Document> findIter = collection.find(query).maxTime(maxTimeMsLongRunning, TimeUnit.MILLISECONDS);
            cursor = findIter.iterator();
        }

        public MongoDbListSearchResults() {
            FindIterable<Document> findIter = collection.find(new Document()).maxTime(maxTimeMsLongRunning, TimeUnit.MILLISECONDS);
            cursor = findIter.iterator();
        }

        @Override
        public int size() {
            return -1;
        }

        @Override
        protected CordraObject computeNext() {
            if (!cursor.hasNext()) return null;
            return documentToCordraObject(cursor.next());
        }

        @Override
        protected void closeOnlyOnce() {
            cursor.close();
        }
    }

    private static final Document ID_PROJECTION = new Document().append("id", 1).append("_id", 0);

    private class MongoDbListHandlesSearchResults extends AbstractSearchResults<String> {
        private final MongoCursor<Document> cursor;

        public MongoDbListHandlesSearchResults(Bson query) {
            FindIterable<Document> findIter = collection.find(query).projection(ID_PROJECTION).maxTime(maxTimeMsLongRunning, TimeUnit.MILLISECONDS);
            cursor = findIter.iterator();
        }

        public MongoDbListHandlesSearchResults() {
            FindIterable<Document> findIter = collection.find(new Document()).projection(ID_PROJECTION).maxTime(maxTimeMsLongRunning, TimeUnit.MILLISECONDS);
            cursor = findIter.iterator();
        }

        @Override
        public int size() {
            return -1;
        }

        @Override
        protected String computeNext() {
            if (!cursor.hasNext()) return null;
            return cursor.next().getString("id");
        }

        @Override
        protected void closeOnlyOnce() {
            cursor.close();
        }
    }
}
