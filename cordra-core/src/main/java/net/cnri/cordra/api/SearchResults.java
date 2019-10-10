package net.cnri.cordra.api;

import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public interface SearchResults<T> extends Iterable<T>, AutoCloseable {
    int size();
    @Override
    Iterator<T> iterator();
    @Override
    void close();

    @Override
    default Spliterator<T> spliterator() {
        int characteristics = Spliterator.IMMUTABLE | Spliterator.NONNULL | Spliterator.ORDERED;
        if (size() >= 0) {
            return Spliterators.spliterator(iterator(), size(), characteristics);
        } else {
            return Spliterators.spliteratorUnknownSize(iterator(), characteristics);
        }
    }
    default Stream<T> stream() {
        Stream<T> stream = StreamSupport.stream(spliterator(), false);
        return stream.onClose(this::close);
    }
    default Stream<T> parallelStream() {
        Stream<T> stream = StreamSupport.stream(spliterator(), true);
        return stream.onClose(this::close);
    }
}
