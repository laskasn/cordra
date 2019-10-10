package net.cnri.cordra.collections;

import java.util.Iterator;

import net.cnri.cordra.api.SearchResults;

public class SearchResultsFromIterator<T> implements SearchResults<T> {

    private final int size;
    private final Iterator<T> iter;
    
    public SearchResultsFromIterator(int size, Iterator<T> iter) {
        this.size = size;
        this.iter = iter;
    }
    
    @Override
    public int size() {
        return size;
    }

    @Override
    public Iterator<T> iterator() {
        return iter;
    }
    
    @Override
    public void close() {
        // no-op
    }
}
