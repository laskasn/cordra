package net.cnri.cordra.api;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;

public class HttpCordraObjectSearchResults implements SearchResults<CordraObject> {
    private final Gson gson = new Gson();
    private final CloseableHttpResponse response;
    private final HttpEntity entity;
    private final JsonReader jsonReader;
    private final int size;
    private boolean closed = false;

    public HttpCordraObjectSearchResults(CloseableHttpResponse response, HttpEntity entity) throws CordraException {
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
        } catch (IOException e) {
            throw new InternalErrorCordraException(e);
        }
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public Iterator<CordraObject> iterator() {
        return new JsonReaderIterator();
    }

    @Override
    public void close() {
        closed = true;
        if (jsonReader != null) try { jsonReader.close(); } catch (IOException e) { }
        if (entity != null) EntityUtils.consumeQuietly(entity);
        if (response != null) try { response.close(); } catch (IOException e) { }
    }

    private class JsonReaderIterator implements Iterator<CordraObject> {
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
        public CordraObject next() {
            if (hasNextResult == null) hasNext();
            if (!hasNextResult) throw new NoSuchElementException();
            hasNextResult = null;
            try {
                CordraObject d = gson.fromJson(jsonReader, CordraObject.class);
                return d;
            } catch (Exception e) {
                throw new UncheckedCordraException(new InternalErrorCordraException(e));
            }
        }
    }
}
