package net.cnri.cordra.indexer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.api.CordraObject;
import net.cnri.cordra.api.SearchResults;
import net.cnri.cordra.api.UncheckedCordraException;
import net.cnri.cordra.collections.AbstractSearchResults;
import net.cnri.cordra.storage.CordraStorage;

public abstract class AbstractCordraSearchResultsFromIndexerSearchResultsBatch<T, Doc> extends AbstractSearchResults<T> {
    private final SearchResults<Doc> results;
    private final Iterator<Doc> searchResultsIter;
    private final CordraStorage storage;
    private final Class<T> klass;

    private final int batchSize = 1000;
    private SearchResults<CordraObject> batch = null;
    private Iterator<CordraObject> batchIter = null;

    public AbstractCordraSearchResultsFromIndexerSearchResultsBatch(SearchResults<Doc> results, CordraStorage storage, Class<T> klass) {
        this.results = results;
        this.searchResultsIter = results.iterator();
        this.storage = storage;
        this.klass = klass;
    }

    private void getNextBatch() {
        try {
            if (batch != null) {
                batch.close();
            }
            List<String> idBatch = readNextBatchOfIdsFromResultsIter();
            batch = storage.get(idBatch);
            batchIter = batch.iterator();
        } catch (CordraException e) {
            throw new UncheckedCordraException(e);
        }
    }

    private List<String> readNextBatchOfIdsFromResultsIter() {
        List<String> idBatch = new ArrayList<>();
        for (int i = 0; i < batchSize; i++) {
            if (!searchResultsIter.hasNext()) {
                 break;
            }
            String id = readNextId();
            idBatch.add(id);
        }
        return idBatch;
    }

    private String readNextId() {
       if (!searchResultsIter.hasNext()) return null;
       Doc from = searchResultsIter.next();
       String id = getIdFromDocument(from);
       return id;
    }

    abstract public String getIdFromDocument(Doc document);
    abstract public String getTypeFromDocument(Doc document);

    @Override
    public int size() {
        return results.size();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected T computeNext() {
        if (klass == String.class) {
            return (T) computeNextStringId();
        } else if (klass == IdType.class) {
            return (T) computeNextIdType();
        } else {
            return (T) computeNextCordraObjectViaBatch();
        }
    }

    private CordraObject computeNextCordraObjectViaBatch() {
        if (batch == null) {
            getNextBatch();
        }
        if (batchIter.hasNext()) {
            CordraObject next = batchIter.next();
            return next;
        } else if (searchResultsIter.hasNext()) {
            getNextBatch();
            return (CordraObject) computeNext();
        } else {
            return null;
        }
    }

    private IdType computeNextIdType() {
        if (!searchResultsIter.hasNext()) return null;
        Doc from = searchResultsIter.next();
        String id = getIdFromDocument(from);
        String type = getTypeFromDocument(from);
        return new IdType(id, type);
    }

    private String computeNextStringId() {
        if (!searchResultsIter.hasNext()) return null;
        Doc from = searchResultsIter.next();
        String id = getIdFromDocument(from);
        return id;
    }

    @Override
    protected void closeOnlyOnce() {
        results.close();
        if (batch != null) {
            batch.close();
        }
    }
}
