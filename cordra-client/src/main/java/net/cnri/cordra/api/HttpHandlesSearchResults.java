package net.cnri.cordra.api;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class HttpHandlesSearchResults implements SearchResults<String> {
    private final Gson gson = new Gson();
    private final CloseableHttpResponse response;
    private final HttpEntity entity;
    private final JsonReader jsonReader;
    private final int size;
    private boolean closed = false;
    private boolean isCordraObjectsInResults = false;

    public HttpHandlesSearchResults(CloseableHttpResponse response, HttpEntity entity) throws CordraException {
        try {
            this.response = response;
            this.entity = entity;
            this.jsonReader = new JsonReader(new InputStreamReader(this.entity.getContent(), "UTF-8"));
            jsonReader.beginObject();
            @SuppressWarnings("hiding") int size = -1;
            while (jsonReader.hasNext()) {
                String name = jsonReader.nextName();
                if ("size".equals(name)) {
                    size = jsonReader.nextInt();
                } else if ("results".equals(name)) {
                    jsonReader.beginArray();
                    break;
                } else {
                    jsonReader.nextString();
                }
            }
            this.size = size;
            JsonToken typeInResults = jsonReader.peek();
            if (typeInResults == JsonToken.BEGIN_OBJECT) {
                isCordraObjectsInResults = true;
            }
        } catch (IOException e) {
            throw new InternalErrorCordraException(e);
        }
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public Iterator<String> iterator() {
        return new JsonReaderIterator();
    }

    @Override
    public void close() {
        closed = true;
        if (jsonReader != null) try { jsonReader.close(); } catch (IOException e) { }
        if (entity != null) EntityUtils.consumeQuietly(entity);
        if (response != null) try { response.close(); } catch (IOException e) { }
    }

    private class JsonReaderIterator implements Iterator<String> {
        private Boolean hasNextResult;

        @Override
        public boolean hasNext() {
            if (hasNextResult != null) return hasNextResult.booleanValue();
            if (closed) throw new IllegalStateException("Already closed");
            try {
                boolean res = jsonReader.hasNext();
                hasNextResult = res;
                if (res == false) {
                    close();
                }
                return res;
            } catch (IOException e) {
                throw new UncheckedCordraException(new InternalErrorCordraException(e));
            }
        }

        @Override
        public String next() {
            if (hasNextResult == null) hasNext();
            if (!hasNextResult) throw new NoSuchElementException();
            hasNextResult = null;
            try {
                String id;
                if (isCordraObjectsInResults) {
                    CordraObject co = gson.fromJson(jsonReader, CordraObject.class);
                    id = co.id;
                } else {
                    id = jsonReader.nextString();
                }
                return id;
            } catch (Exception e) {
                throw new UncheckedCordraException(new InternalErrorCordraException(e));
            }
        }
    }
}
