package net.cnri.cordra.replication.kafka;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.IOUtils;

import net.cnri.cordra.api.CordraObject;
import net.cnri.cordra.api.Payload;
import net.cnri.cordra.storage.CordraStorage;
import net.cnri.cordra.api.CordraException;

public class CordraObjectWithPayloadsAsStrings {

    public CordraObject cordraObject;
    public Map<String, String> payloads;
    
    public static CordraObjectWithPayloadsAsStrings fromCordraObject(CordraObject co, CordraStorage storage) throws CordraException, IOException {
        return fromCordraObject(co, storage, true);
    }

    public static CordraObjectWithPayloadsAsStrings fromCordraObject(CordraObject co, CordraStorage storage, boolean includePayloads) throws CordraException, IOException {
        CordraObjectWithPayloadsAsStrings result = new CordraObjectWithPayloadsAsStrings();
        result.cordraObject = co;
        if (co.payloads == null) {
            return result;
        }
        if (includePayloads) {
            result.payloads = new HashMap<>();
            for (Payload payload : co.payloads) {
                String base64Payload = inputStreamToBase64String(storage.getPayload(co.id, payload.name));
                result.payloads.put(payload.name, base64Payload);
            }
        }
        return result;
    }
    
    public static String inputStreamToBase64String(InputStream in) throws IOException {
        byte[] bytes = IOUtils.toByteArray(in);
        String result = Base64.getEncoder().encodeToString(bytes);
        return result;
    }
    
    public boolean exists(String id, CordraStorage storage) throws CordraException {
        return storage.get(id) != null;
    }
    
    public void writeIntoStorage(CordraStorage storage) throws CordraException {
        if (cordraObject.payloads != null) {
            for (Payload p : cordraObject.payloads) {
                String name = p.name;
                String base64OfPayload = payloads.get(name);
                InputStream in = new ByteArrayInputStream(Base64.getDecoder().decode(base64OfPayload));
                p.setInputStream(in);
            }
        }
        if (exists(cordraObject.id, storage)) {
            storage.update(cordraObject);
        } else {
            storage.create(cordraObject);
        }
    }
}
