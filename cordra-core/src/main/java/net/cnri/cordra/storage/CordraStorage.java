package net.cnri.cordra.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.api.CordraObject;
import net.cnri.cordra.api.NotFoundCordraException;
import net.cnri.cordra.api.SearchResults;
import net.cnri.cordra.api.UncheckedCordraException;
import net.cnri.cordra.collections.SearchResultsFromStream;

public interface CordraStorage {

    CordraObject get(String id) throws CordraException;

    @SuppressWarnings("unused")
    default SearchResults<CordraObject> get(Collection<String> ids) throws CordraException {
        Stream<CordraObject> objStream = ids.stream().map(id -> {
            try {
                return get(id);
            } catch (NotFoundCordraException e) {
                return null;
            } catch (CordraException e) {
                throw new UncheckedCordraException(e);
            }
        }).filter(Objects::nonNull);
        SearchResults<CordraObject> result = new SearchResultsFromStream<>(-1, objStream);
        return result;
    }

    InputStream getPayload(String id, String payloadName) throws CordraException;

    InputStream getPartialPayload(String id, String payloadName, Long start, Long end) throws CordraException;

    CordraObject create(CordraObject d) throws CordraException;

    CordraObject update(CordraObject d) throws CordraException;

    void delete(String id) throws CordraException;

    SearchResults<CordraObject> list() throws CordraException;

    SearchResults<String> listHandles() throws CordraException;

    @SuppressWarnings("resource")
    default SearchResults<CordraObject> listByType(List<String> types) throws CordraException {
        SearchResults<CordraObject> result = new SearchResultsFromStream<>(-1, list().stream().filter(co -> types.contains(co.type)));
        return result;
    }

    @SuppressWarnings("resource")
    default SearchResults<String> listHandlesByType(List<String> types) throws CordraException {
        SearchResults<String> result = new SearchResultsFromStream<>(-1, list().stream().filter(co -> types.contains(co.type)).map(co -> co.id));
        return result;
    }

    void close() throws IOException, CordraException;
}