package net.cnri.cordra.indexer;

import java.util.Iterator;

import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.api.CordraObject;
import net.cnri.cordra.api.SearchResults;
import net.cnri.cordra.api.UncheckedCordraException;
import net.cnri.cordra.collections.AbstractSearchResults;
import net.cnri.cordra.storage.CordraStorage;

public abstract class AbstractCordraSearchResultsFromIndexerSearchResults<T, Doc> extends AbstractSearchResults<T> {
    private final SearchResults<Doc> results;
    private final Iterator<Doc> iter; 
    private final CordraStorage storage;
    private final Class<T> klass;
    
    public AbstractCordraSearchResultsFromIndexerSearchResults(SearchResults<Doc> results, CordraStorage storage, Class<T> klass) {
        this.results = results;
        this.iter = results.iterator();
        this.storage = storage;
        this.klass = klass;
    }
    
    abstract public String getIdFromDocument(Doc document);
    
    @Override
    public int size() {
        return results.size();
    }
    
    @Override
    @SuppressWarnings("unchecked")
    protected T computeNext() {
        while (true) {
            if (!iter.hasNext()) {
                return null;
            }
            Doc from = iter.next();
            String id = getIdFromDocument(from);
            if (klass == String.class) {
                return (T)id;
            } else {
                CordraObject next = null;
//                if (isStoreFields) {
//                    // XXX either implement this, or turn off isStoreFields
//                    // next = new SolrSearchResultDigitalObject(from);
//                } else {
                try {
                    next = storage.get(id);
                } catch (CordraException e) {
                    throw new UncheckedCordraException(e);
                }
//                }
                if (next != null) return (T)next;
            }
        }
    }
    
    @Override
    protected void closeOnlyOnce() {
        results.close();
    }   
}
