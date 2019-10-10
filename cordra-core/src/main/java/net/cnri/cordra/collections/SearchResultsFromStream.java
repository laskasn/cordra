package net.cnri.cordra.collections;

import java.util.Iterator;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;

import net.cnri.cordra.api.SearchResults;

public class SearchResultsFromStream<T> implements SearchResults<T> {

    private final int size;
    private final Stream<T> stream;
    
    public SearchResultsFromStream(int size, Stream<T> stream) {
        this.size = size;
        this.stream = stream;
    }
    
    @Override
    public int size() {
        return size;
    }

    @Override
    public Iterator<T> iterator() {
        return stream.iterator();
    }

    @Override
    public void close() {
        stream.close();
    }

    @Override
    public void forEach(Consumer<? super T> action) {
        stream.forEach(action);
    }

    @Override
    public Spliterator<T> spliterator() {
        return stream.spliterator();
    }

    @Override
    public Stream<T> stream() {
        return stream;
    }

    @Override
    public Stream<T> parallelStream() {
        return stream.parallel();
    }
}
