package net.cnri.cordra.storage.hds;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import com.amazonaws.util.CountingInputStream;

import net.cnri.cordra.AttributesUtil;
import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.api.CordraObject;
import net.cnri.cordra.api.InternalErrorCordraException;
import net.cnri.cordra.api.Payload;
import net.cnri.cordra.api.SearchResults;
import net.cnri.cordra.api.UncheckedCordraException;
import net.cnri.cordra.collections.AbstractSearchResults;
import net.cnri.cordra.api.NotFoundCordraException;
import net.cnri.cordra.storage.CordraStorage;

public class HdsStorage implements CordraStorage {

    private final HashDirectoryStorage hds;

    public HdsStorage(File baseDirectory) throws CordraException {
        hds = new HashDirectoryStorage();
        hds.initWithDirectory(baseDirectory, false);
    }

    @Override
    public CordraObject get(String id) throws CordraException {
        if (!hds.doesObjectExist(id)) return null;
        HeaderSet headers = hds.getAttributes(id, null, null);
        CordraObject co = new CordraObject();
        co.id = id;
        co.metadata = new CordraObject.Metadata();
        for (HeaderItem item : headers) {
            AttributesUtil.setAttribute(co, item.getName(), item.getValue());
        }
        List<String> payloadNames = hds.listDataElements(id);
        if (payloadNames == null || payloadNames.isEmpty()) return co;
        co.payloads = new ArrayList<>();
        for (String payloadName : payloadNames) {
            HeaderSet payloadHeaders = hds.getAttributes(id, payloadName, null);
            Payload payload = new Payload();
            payload.name = payloadName;
            for (HeaderItem item : payloadHeaders) {
                AttributesUtil.setAttribute(payload, item.getName(), item.getValue());
            }
            co.payloads.add(payload);
        }
        return co;
    }

    @Override
    public InputStream getPayload(String id, String payloadName) throws CordraException {
        if (!hds.doesDataElementExist(id, payloadName)) {
            return null;
        }
        return hds.getDataElement(id, payloadName);
    }

    @Override
    @SuppressWarnings("null")
    public InputStream getPartialPayload(String id, String payloadName, Long start, Long end) throws CordraException {
        if (!hds.doesDataElementExist(id, payloadName)) {
            return null;
        }
        if (start == null && end == null) {
            return hds.getDataElement(id, payloadName);
        }
        long size = getSize(id, payloadName);
        if (size < 0) {
            return hds.getDataElement(id, payloadName);
        }
        if (end == null) {
            return hds.getDataElement(id, payloadName, start, size - 1);
        }
        if (start == null) {
            start = size - end;
            if (start < 0) {
                return hds.getDataElement(id, payloadName);
            }
            return hds.getDataElement(id, payloadName, start, size);
        }
        if (start > end) {
            return new ByteArrayInputStream(new byte[0]);
        }
        if (end > size - 1) end = size - 1;
        return hds.getDataElement(id, payloadName, start, end - start + 1);
    }

    private long getSize(String id, String payloadName) throws CordraException {
        HeaderSet payloadHeaders = hds.getAttributes(id, payloadName, null);
        for (HeaderItem item : payloadHeaders) {
            if ("internal.size".equals(item.getName())) {
                return Long.parseLong(item.getValue());
            }
        }
        return -1;
    }

    @Override
    public CordraObject create(CordraObject d) throws CordraException {
        hds.createObject(d.id);
        if (d.metadata == null) d.metadata = new CordraObject.Metadata();
        Map<String, String> atts = AttributesUtil.getAttributes(d, true, true);
        HeaderSet headers = toHeaderSet(atts);
        hds.setAttributes(d.id, null, headers);
        if (d.payloads != null && !d.payloads.isEmpty()) {
            for (Payload payload : d.payloads) {
                if (payload.getInputStream() != null) {
                    try (CountingInputStream is = new CountingInputStream(payload.getInputStream())) {
                        hds.storeDataElement(d.id, payload.name, is, false);
                        payload.size = is.getByteCount();
                    } catch (IOException e) {
                        throw new InternalErrorCordraException(e);
                    }
                }
                Map<String, String> payloadAtts = AttributesUtil.getAttributes(payload);
                HeaderSet payloadHeaders = toHeaderSet(payloadAtts);
                hds.setAttributes(d.id, payload.name, payloadHeaders);
            }
        }
        return get(d.id);
    }


    @Override
    public CordraObject update(CordraObject d) throws CordraException {
        CordraObject existingCo = get(d.id);
        if (existingCo == null) throw new NotFoundCordraException("Not found: " + d.id);
        Map<String, String> existingAtts = AttributesUtil.getAttributes(existingCo, true, true);
        Map<String, String> atts = AttributesUtil.getAttributes(d, true, true);
        for (String existingKey : existingAtts.keySet()) {
            if (!atts.containsKey(existingKey)) {
                atts.put(existingKey, null); // makes HDS delete the key
            }
        }
        HeaderSet headers = toHeaderSet(atts);
        hds.setAttributes(d.id, null, headers);
        if (d.getPayloadsToDelete() != null) {
            for (String payloadToDelete : d.getPayloadsToDelete()) {
                hds.deleteDataElement(d.id, payloadToDelete);
            }
        }
        d.clearPayloadsToDelete();
        if (d.payloads != null && !d.payloads.isEmpty()) {
            for (Payload payload : d.payloads) {
                if (payload.getInputStream() != null) {
                    try (CountingInputStream is = new CountingInputStream(payload.getInputStream())) {
                        hds.storeDataElement(d.id, payload.name, is, false);
                        payload.size = is.getByteCount();
                    } catch (IOException e) {
                        throw new InternalErrorCordraException(e);
                    } finally {
                        payload.setInputStream(null);
                    }
                }
                Map<String, String> payloadAtts = AttributesUtil.getAttributes(payload);
                // delete if necessary
                if (payload.filename == null) payloadAtts.put("filename", null);
                if (payload.mediaType == null) payloadAtts.put("mimetype", null);
                HeaderSet payloadHeaders = toHeaderSet(payloadAtts);
                hds.setAttributes(d.id, payload.name, payloadHeaders);
            }
        }
        return get(d.id);
    }

    private HeaderSet toHeaderSet(Map<String, String> atts) {
        HeaderSet headers = new HeaderSet();
        for (Map.Entry<String, String> entry : atts.entrySet()) {
            headers.addHeader(entry.getKey(), entry.getValue());
        }
        return headers;
    }

    @Override
    public void delete(String id) throws CordraException {
        hds.deleteObject(id);
    }

    @Override
    public SearchResults<CordraObject> list() throws CordraException {
        return new AbstractSearchResults<CordraObject>() {
            Enumeration<String> enume = hds.listObjects();

            @Override
            public int size() {
                return -1;
            }

            @Override
            protected CordraObject computeNext() {
                if (!enume.hasMoreElements()) return null;
                try {
                    return get(enume.nextElement());
                } catch (CordraException e) {
                    throw new UncheckedCordraException(e);
                }
            }

            @Override
            public void closeOnlyOnce() {
                if (enume instanceof Closeable) {
                    try {
                        ((Closeable)enume).close();
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }
        };
    }

    @Override
    public SearchResults<String> listHandles() throws CordraException {
        return new AbstractSearchResults<String>() {
            Enumeration<String> enume = hds.listObjects();

            @Override
            public int size() {
                return -1;
            }

            @Override
            protected String computeNext() {
                if (!enume.hasMoreElements()) return null;
                return enume.nextElement();
            }

            @Override
            public void closeOnlyOnce() {
                if (enume instanceof Closeable) {
                    try {
                        ((Closeable)enume).close();
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }
        };
    }

    @Override
    public void close() {
        hds.close();
    }

}
