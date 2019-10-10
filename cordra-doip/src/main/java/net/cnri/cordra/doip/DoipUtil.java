package net.cnri.cordra.doip;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.cnri.cordra.api.CordraObject;
import net.cnri.cordra.api.Payload;
import net.dona.doip.client.DigitalObject;
import net.dona.doip.client.Element;
import net.dona.doip.util.GsonUtility;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Map;

public class DoipUtil {

    public static net.dona.doip.client.DigitalObject ofCordraObject(CordraObject co) {
        return ofCordraObject(co, false);
    }

    public static net.dona.doip.client.DigitalObject ofCordraObject(CordraObject co, boolean includeInputStreams) {
        net.dona.doip.client.DigitalObject digitalObject = new DigitalObject();
        digitalObject.id = co.id;
        digitalObject.type = co.type;
        digitalObject.attributes = new JsonObject();
        digitalObject.attributes.add("content", co.content);
        if (co.acl != null) digitalObject.attributes.add("acl", GsonUtility.getGson().toJsonTree(co.acl));
        if (co.metadata != null) digitalObject.attributes.add("metadata", GsonUtility.getGson().toJsonTree(co.metadata));
        if (co.userMetadata != null) {
            for (Map.Entry<String, JsonElement> attribute :  co.userMetadata.entrySet()) {
                digitalObject.attributes.add(doipAttributeOfUserMetadataKey(attribute.getKey()), attribute.getValue());
            }
        }
        digitalObject.elements = new ArrayList<>();
        if (co.payloads != null) {
            for (Payload payload : co.payloads) {
                Element el = new Element();
                el.id = payload.name;
                if (payload.size >= 0) el.length = payload.size;
                el.type = payload.mediaType;
                el.attributes = new JsonObject();
                if (payload.filename != null) el.attributes.addProperty("filename", payload.filename);
                if (includeInputStreams) {
                    @SuppressWarnings("resource")
                    InputStream in = payload.getInputStream();
                    if (in != null) {
                        el.in = in;
                    }
                }
                digitalObject.elements.add(el);
            }
        }
        return digitalObject;
    }

    // Prefix userMetadata. whenever needed to avoid clobbering
    private static String doipAttributeOfUserMetadataKey(String key) {
        if ("content".equals(key)) return "userMetadata.content";
        else if ("acl".equals(key)) return "userMetadata.acl";
        else if ("metadata".equals(key)) return "userMetadata.metadata";
        else if (key.startsWith("userMetadata.")) return "userMetadata." + key;
        else return key;
    }

    // Remove userMetadata. prefix
    private static String userMetadataKeyOfDoipAttribute(String key) {
        if (key.startsWith("userMetadata.")) return key.substring(13);
        else return key;
    }

    public static CordraObject toCordraObject(DigitalObject digitalObject) {
        if (digitalObject == null) return null;
        return toCordraObject(digitalObject, false);
    }

    public static CordraObject toCordraObject(DigitalObject digitalObject, boolean includeInputStreams) {
        if (digitalObject == null) return null;
        CordraObject co = new CordraObject();
        co.id = digitalObject.id;
        co.type = digitalObject.type;
        if (digitalObject.attributes != null) {
            JsonObject userMetadata = new JsonObject();
            for (Map.Entry<String, JsonElement> attribute :  digitalObject.attributes.entrySet()) {
                if ("content".equals(attribute.getKey())) {
                    co.content = digitalObject.attributes.get("content");
                } else if ("acl".equals(attribute.getKey())) {
                    co.acl = GsonUtility.getGson().fromJson(digitalObject.attributes.get("acl"), CordraObject.AccessControlList.class);
                } else if ("metadata".equals(attribute.getKey())) {
                    co.metadata = GsonUtility.getGson().fromJson(digitalObject.attributes.get("metadata"), CordraObject.Metadata.class);
                } else {
                    userMetadata.add(userMetadataKeyOfDoipAttribute(attribute.getKey()), attribute.getValue());
                }
            }
            if (!userMetadata.keySet().isEmpty()) {
                co.userMetadata = userMetadata;
            }
        }
        if (digitalObject.elements != null && digitalObject.elements.size() > 0) {
            co.payloads = new ArrayList<>();
            for (Element el : digitalObject.elements) {
                Payload p = new Payload();
                p.name = el.id;
                p.mediaType = el.type;
                if (el.length != null) {
                    p.size = el.length;
                }
                if (el.attributes != null) {
                    if (el.attributes.has("filename")) {
                        p.filename = el.attributes.get("filename").getAsString();
                    }
                }
                if (includeInputStreams) {
                    if (el.in != null) {
                        p.setInputStream(el.in);
                    }
                }
                co.payloads.add(p);
            }
        }
        return co;
    }
}
