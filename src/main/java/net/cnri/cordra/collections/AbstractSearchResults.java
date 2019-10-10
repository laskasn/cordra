package net.cnri.cordra.collections;

import java.util.Iterator;
import java.util.NoSuchElementException;

import net.cnri.cordra.api.SearchResults;

public abstract class AbstractSearchResults<T> implements SearchResults<T> {
    private T next;
    private boolean closed;
    
    @Override
    abstract public int size();
    
    /**
     * Implement this to return the next element.  Return {@code null} to indicate no next element.
     * 
     * @return the next element, or {@code null} to indicate no next element. 
     */
    abstract protected T computeNext();

    /**
     * Implement this to release resources held by the iterator.  Will be called only once.
     */
    protected void closeOnlyOnce() {}
    
    @Override
    public Iterator<T> iterator() {
        return new Iterator<T>() {
            /**
             * Returns {@code true} if the iteration has more elements.  Will call {@link #close()} if there are no more elements.
             */
            @Override
            public boolean hasNext() {
                if(closed) return false;
                if(next!=null) return true;
                next = computeNext();
                if(next==null) {
                    close();
                    closed = true;
                }
                return next!=null;
            }
            
            /**
             * Returns the next element in the iteration.  Throws {@code NoSuchElementException} if the iteration has no more elements.
             * Will call {@link #close()} if there are no more elements.
             */
            @Override
            public T next() {
                if(!hasNext()) throw new NoSuchElementException();
                T res = next;
                next = null;
                return res;
            }
            
            /**
             * Throws {@code UnsupportedOperationException}.
             */
            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }
    
    /**
     * Calls {@link #closeOnlyOnce()} only if the iterator has not already been closed.
     */
    @Override
    public void close() {
        if(!closed) {
            closeOnlyOnce();
            closed = true;
        }
    }
}
