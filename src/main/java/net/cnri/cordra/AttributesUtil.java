package net.cnri.cordra;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.cnri.cordra.api.CordraObject;
import net.cnri.cordra.api.Payload;

import java.util.*;

public class AttributesUtil {

    public static final String ACL_READ = "aclRead";
    public static final String ACL_WRITE = "aclWrite";
    private static final Gson gson = GsonUtility.getGson();

    public static Map<String, String> getAttributes(CordraObject cordraObject, boolean includeJson, boolean includeAllInternalMetadata) {
        Map<String, String> res = new HashMap<>();
        if (cordraObject.metadata.internalMetadata != null) {
            Map<String, String> internalMetadataAsMap = getInternalMetadataAsMap(cordraObject.metadata.internalMetadata, includeAllInternalMetadata);
            res.putAll(internalMetadataAsMap);
        }
        // omit this as unused to reduce document size; reconsider for "store values in index" approach
        if (includeJson) {
            if (cordraObject.content != null) res.put("json", cordraObject.getContentAsString());
            if (cordraObject.userMetadata != null) res.put("userMetadata", gson.toJson(cordraObject.userMetadata));
        }
        if (cordraObject.type != null) res.put("type", cordraObject.type);
        if (cordraObject.acl != null) {
            if (cordraObject.acl.readers != null) {
                res.put(ACL_READ, toNewLineSeparatedString(cordraObject.acl.readers));
            }
            if (cordraObject.acl.writers != null) {
                res.put(ACL_WRITE, toNewLineSeparatedString(cordraObject.acl.writers));
            }
        }
        if (cordraObject.metadata.hashes != null) res.put("hashes", gson.toJson(cordraObject.metadata.hashes));
        if (cordraObject.metadata.createdBy != null) res.put("createdBy", cordraObject.metadata.createdBy);
        if (cordraObject.metadata.modifiedBy != null) res.put("modifiedBy", cordraObject.metadata.modifiedBy);
        if (Boolean.TRUE.equals(cordraObject.metadata.isVersion)) res.put("isVersion", String.valueOf(cordraObject.metadata.isVersion));
        if (cordraObject.metadata.versionOf != null) res.put("versionOf", cordraObject.metadata.versionOf);
        if (cordraObject.metadata.publishedBy != null) res.put("publishedBy", cordraObject.metadata.publishedBy);
        if (cordraObject.metadata.publishedOn != null) res.put("publishedOn", String.valueOf(cordraObject.metadata.publishedOn));
        if (cordraObject.metadata.txnId != null) res.put("txnId", String.valueOf(cordraObject.metadata.txnId));
        res.put("internal.created", String.valueOf(cordraObject.metadata.createdOn));
        res.put("internal.modified", String.valueOf(cordraObject.metadata.modifiedOn));
        return res;
    }

    private static Map<String, String> getInternalMetadataAsMap(JsonObject internalMetadata, boolean includeAll) {
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : internalMetadata.entrySet()) {
            String key = entry.getKey();
            String value;
            if (entry.getValue().isJsonPrimitive()) {
                value = entry.getValue().getAsString();
            } else {
                value = gson.toJson(entry.getValue());
            }
            if (includeAll) {
                result.put(key, value);
            } else {
                boolean isUserPasswordProperty = "hash".equals(key) || "salt".equals(key) || "iterations".equals(key) || "algorithm".equals(key);
                boolean isSecureProperty = "secureProperties".equals(key);
                boolean isAdminPasswordProperty = CordraService.HASH_ATTRIBUTE.equals(key) || CordraService.SALT_ATTRIBUTE.equals(key)
                    || CordraService.HASH_ALGORITHM_ATTRIBUTE.equals(key) || CordraService.ITERATIONS_ATTRIBUTE.equals(key);
                boolean shouldIndex = !isAdminPasswordProperty && !isUserPasswordProperty && !isSecureProperty;
                if (shouldIndex) result.put(key, value);
            }
        }
        return result;
    }

    public static Map<String, String> getAttributes(Payload payload) {
        Map<String, String> res = new HashMap<>();
        if (payload.filename != null) res.put("filename", payload.filename);
        if (payload.mediaType != null) res.put("mimetype", payload.mediaType);
        res.put("internal.size", String.valueOf(payload.size));
        return res;
    }

    private static String toNewLineSeparatedString(Collection<String> ids) {
        StringBuilder sb = new StringBuilder();
        for (String userId : ids) {
            sb.append(userId).append("\n");
        }
        return sb.toString();
    }

    public static void setAttribute(CordraObject cordraObject, String name, String value) {
        if ("json".equals(name)) {
            cordraObject.setContent(value);
        } else if ("userMetadata".equals(name)) {
            if (value == null) cordraObject.userMetadata = null;
            else cordraObject.userMetadata = new JsonParser().parse(value).getAsJsonObject();
        } else if ("type".equals(name)) {
            cordraObject.type = value;
        } else if (ACL_READ.equals(name)) {
            if (value == null && cordraObject.acl != null) {
                cordraObject.acl.readers = null;
            } else if (value != null) {
                if (cordraObject.acl == null) cordraObject.acl = new CordraObject.AccessControlList();
                List<String> ids = Arrays.asList(value.split("\n"));
                cordraObject.acl.readers = ids;
            }
        } else if (ACL_WRITE.equals(name)) {
            if (value == null && cordraObject.acl != null) {
                cordraObject.acl.writers = null;
            } else if (value != null) {
                if (cordraObject.acl == null) cordraObject.acl = new CordraObject.AccessControlList();
                List<String> ids = Arrays.asList(value.split("\n"));
                cordraObject.acl.writers = ids;
            }
        } else if ("createdBy".equals(name)) {
            cordraObject.metadata.createdBy = value;
        } else if ("modifiedBy".equals(name)) {
            cordraObject.metadata.modifiedBy = value;
        } else if ("isVersion".equals(name)) {
            if (Boolean.parseBoolean(value)) {
                cordraObject.metadata.isVersion = true;
            }
        } else if ("versionOf".equals(name)) {
            cordraObject.metadata.versionOf = value;
        } else if ("publishedBy".equals(name)) {
            cordraObject.metadata.publishedBy = value;
        } else if ("publishedOn".equals(name)) {
            if (value == null) cordraObject.metadata.publishedOn = null;
            else cordraObject.metadata.publishedOn = Long.valueOf(value);
        } else if ("remoteRepository".equals(name)) {
            cordraObject.metadata.remoteRepository = value;
        } else if ("txnId".equals(name)) {
            if (value == null) cordraObject.metadata.txnId = null;
            else cordraObject.metadata.txnId = Long.parseLong(value);
        } else if ("hashes".equals(name)) {
            if (value == null) cordraObject.metadata.hashes = null;
            else cordraObject.metadata.hashes = new JsonParser().parse(value).getAsJsonObject();
        } else if ("internal.created".equals(name)) {
            if (value != null) cordraObject.metadata.createdOn = Long.parseLong(value);
        } else if ("internal.modified".equals(name)) {
            if (value != null) cordraObject.metadata.modifiedOn = Long.parseLong(value);
        } else {
            if (cordraObject.metadata.internalMetadata == null) cordraObject.metadata.internalMetadata = new JsonObject();
            if (value == null) cordraObject.metadata.internalMetadata.remove(name);
            else cordraObject.metadata.internalMetadata.addProperty(name, value);
        }
    }

    public static void setAttribute(Payload payload, String name, String value) {
        if ("filename".equals(name)) {
            payload.filename = value;
        } else if ("mimetype".equals(name)) {
            payload.mediaType = value;
        } else if ("internal.size".equals(name)) {
            payload.size = Long.parseLong(value);
        } else {
            // ignore
        }
    }
}
