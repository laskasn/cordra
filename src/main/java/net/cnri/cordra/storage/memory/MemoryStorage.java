package net.cnri.cordra.storage.memory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.google.gson.Gson;

import net.cnri.cordra.GsonUtility;
import net.cnri.cordra.api.ConflictCordraException;
import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.api.CordraObject;
import net.cnri.cordra.api.NotFoundCordraException;
import net.cnri.cordra.api.Payload;
import net.cnri.cordra.api.SearchResults;
import net.cnri.cordra.collections.AbstractSearchResults;
import net.cnri.cordra.storage.CordraStorage;

public class MemoryStorage implements CordraStorage {

    private ConcurrentMap<String, String> objects;
    private ConcurrentMap<String, Map<String, byte[]>> payloads;
    private Gson gson;

    public MemoryStorage() {
        objects = new ConcurrentHashMap<>();
        payloads = new ConcurrentHashMap<>();
        gson = GsonUtility.getGson();
    }

    @Override
    public CordraObject get(String id) throws CordraException {
        String json = objects.get(id);
        if (json != null) {
            return gson.fromJson(json, CordraObject.class);
        } else {
            return null;
        }
    }

    @Override
    public InputStream getPayload(String id, String payloadName) throws CordraException {
        Map<String, byte[]> payloadsMap = payloads.get(id);
        if (payloadsMap == null) {
            return null;
        } else {
            byte[] payload = payloadsMap.get(payloadName);
            if (payload == null) {
                return null;
            } else {
                return new ByteArrayInputStream(payload);
            }
        }
    }

    @Override
    @SuppressWarnings("null")
    public InputStream getPartialPayload(String id, String payloadName, Long start, Long end) throws CordraException {
        Map<String, byte[]> payloadsMap = payloads.get(id);
        if (payloadsMap == null) {
            return null;
        } else {
            byte[] payload = payloadsMap.get(payloadName);
            if (payload == null) {
                return null;
            } else {
                if (start == null && end == null) {
                    return new ByteArrayInputStream(payload);
                }
                if (end == null) {
                    if (start < 0) start = 0L;
                    int length = (int)(payload.length - start);
                    return new ByteArrayInputStream(payload, start.intValue(), length);
                }
                if (start == null) {
                    start = payload.length - end;
                    if (start < 0) start = 0L;
                    int length = (int)(payload.length - start);
                    return new ByteArrayInputStream(payload, start.intValue(), length);
                }
                int length = end.intValue() - start.intValue() + 1;
                if (length < 0) length = 0;
                return new ByteArrayInputStream(payload, start.intValue(), length);
            }
        }
    }

    @Override
    public CordraObject create(CordraObject d) throws CordraException {
        if (objects.get(d.id) != null) {
            throw new ConflictCordraException("Object already exists: " + d.id);
        }
        if (d.payloads != null) {
            for (Payload p : d.payloads) {
                long length;
                try (InputStream in = p.getInputStream();) {
                    length = writeInputStreamToPayloadStorage(in, d.id, p.name);
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
        String json = gson.toJson(d);
        objects.put(d.id, json);
        return gson.fromJson(json, CordraObject.class);
    }

    private long writeInputStreamToPayloadStorage(InputStream in, String id, String name) throws IOException {
        long length = 0;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int r;
        while((r = in.read(buf)) > 0) {
            out.write(buf, 0, r);
            length += r;
        }
        byte[] data = out.toByteArray();
        Map<String, byte[]> payloadsMap = payloads.get(id);
        if (payloadsMap == null) {
            payloadsMap = new ConcurrentHashMap<>();
            payloads.put(id, payloadsMap);
        }
        payloadsMap.put(name, data);
        return length;
    }

    @Override
    public CordraObject update(CordraObject d) throws CordraException {
        if (objects.get(d.id) == null) {
            throw new NotFoundCordraException("Object does not exist: " + d.id);
        }
        List<String> payloadsToDelete = d.getPayloadsToDelete();
        if (payloadsToDelete != null) {
            Map<String, byte[]> payloadsMap = payloads.get(d.id);
            for (String payloadName : payloadsToDelete) {
                if (payloadsMap != null) {
                    payloadsMap.remove(payloadName);
                }
            }
        }
        d.clearPayloadsToDelete();
        if (d.payloads != null) {
            for (Payload p : d.payloads) {
                if (p.getInputStream() != null) {
                    try (InputStream in = p.getInputStream()) {
                        p.size = writeInputStreamToPayloadStorage(in, d.id, p.name);
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
        String json = gson.toJson(d);
        objects.put(d.id, json);
        return gson.fromJson(json, CordraObject.class);
    }

    @Override
    public void delete(String id) throws CordraException {
        String result = objects.remove(id);
        if (result == null) {
            throw new NotFoundCordraException("Object does not exist: " + id);
        }
        payloads.remove(id);
    }

    @Override
    public SearchResults<CordraObject> list() throws CordraException {
        return new AbstractSearchResults<CordraObject>() {

            private Iterator<String> jsonIter = objects.values().iterator();
            private int size = objects.size();

            @Override
            public int size() {
                return size;
            }

            @Override
            protected CordraObject computeNext() {
                if (jsonIter.hasNext()) {
                    String json = jsonIter.next();
                    return gson.fromJson(json, CordraObject.class);
                } else {
                    return null;
                }
            }
        };
    }

    @Override
    public SearchResults<String> listHandles() throws CordraException {
        return new AbstractSearchResults<String>() {

            private Iterator<String> jsonIter = objects.keySet().iterator();
            private int size = objects.size();

            @Override
            public int size() {
                return size;
            }

            @Override
            protected String computeNext() {
                if (jsonIter.hasNext()) {
                    return jsonIter.next();
                } else {
                    return null;
                }
            }
        };
    }

    @Override
    public void close() {
        //no-op
    }

}
