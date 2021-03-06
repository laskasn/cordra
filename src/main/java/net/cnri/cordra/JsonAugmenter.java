package net.cnri.cordra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.gson.JsonObject;
import net.cnri.cordra.api.CordraObject;
import net.cnri.util.FastDateFormat;

import java.util.Map;

public class JsonAugmenter {
    public boolean augment(CordraObject co, JsonNode json, Map<String, JsonNode> pointerToSchemaMap, String userId, String handleMintingConfigPrefix) {
        boolean found = false;
        for (Map.Entry<String, JsonNode> entry : pointerToSchemaMap.entrySet()) {
            String jsonPointer = entry.getKey();
            JsonNode subSchema = entry.getValue();
            JsonNode autoGeneratedFieldNode = SchemaUtil.getDeepCordraSchemaProperty(subSchema, "type", "autoGeneratedField");
            if (autoGeneratedFieldNode == null) continue;
            String autoGeneratedFieldName;
            String prepend = null;
            if (autoGeneratedFieldNode.isTextual()) {
                autoGeneratedFieldName = autoGeneratedFieldNode.asText(null);
            } else if (autoGeneratedFieldNode.isObject()) {
                JsonNode nameNode = autoGeneratedFieldNode.get("type");
                if (nameNode == null) continue;
                autoGeneratedFieldName = nameNode.asText(null);
                JsonNode prependNode = autoGeneratedFieldNode.get("prepend");
                if (prependNode != null && prependNode.isTextual()) {
                    prepend = prependNode.asText(null);
                }
                if (prepend == null) {
                    JsonNode prependHandleMintingConfigPrefixNode = autoGeneratedFieldNode.get("prependHandleMintingConfigPrefix");
                    if (prependHandleMintingConfigPrefixNode != null && prependHandleMintingConfigPrefixNode.asBoolean()) {
                        if (handleMintingConfigPrefix != null) {
                            prepend = ensureSlash(handleMintingConfigPrefix);
                        }
                    }
                }
            } else continue;
            if (autoGeneratedFieldName == null) continue;
            augmentAtPointer(co, json, jsonPointer, autoGeneratedFieldName, prepend, userId);
            found = true;
        }
        return found;
    }

    private static String ensureSlash(String s) {
        if (s == null) return null;
        if (s.endsWith("/")) return s;
        else return s + "/";
    }

    private static void augmentAtPointer(CordraObject co, JsonNode json, String jsonPointer, String autoGeneratedFieldName, String prepend, String userId) {
        String replacement = getAutoGeneratedFieldWithPrepend(co, autoGeneratedFieldName, prepend, userId);
        JsonUtil.replaceJsonAtPointer(json, jsonPointer, new TextNode(replacement));
    }

    private static String getAutoGeneratedFieldWithPrepend(CordraObject co, String autoGeneratedFieldName, String prepend, String userId) {
        String full = getAutoGeneratedFieldWithoutPrepend(co, autoGeneratedFieldName, userId);
        if (full == null) return null;
        if (prepend == null) return full;
        if (full.startsWith(prepend)) return full.substring(prepend.length());
        return full;
    }

    private static String getAutoGeneratedFieldWithoutPrepend(CordraObject co, String autoGeneratedFieldName, String userId) {
        if (co.metadata == null) co.metadata = new CordraObject.Metadata();
        if (co.metadata.internalMetadata == null) co.metadata.internalMetadata = new JsonObject();
        if ("handle".equals(autoGeneratedFieldName)) {
            return co.id;
        } else if ("creationDate".equals(autoGeneratedFieldName) || "createdOn".equals(autoGeneratedFieldName)) {
            return formatDate(co.metadata.createdOn);
        } else if ("modificationDate".equals(autoGeneratedFieldName) || "modifiedOn".equals(autoGeneratedFieldName)) {
            return formatDate(System.currentTimeMillis());
        } else if ("createdBy".equals(autoGeneratedFieldName)) {
            return co.metadata.createdBy;
        } else if ("type".equals(autoGeneratedFieldName)) {
            return co.type;
        } else if ("modifiedBy".equals(autoGeneratedFieldName)) {
            if (userId == null) {
                return "anonymous";
            } else {
                return userId;
            }
        } else {
            throw new IllegalArgumentException(autoGeneratedFieldName);
        }
    }

    private static String formatDate(long date) {
        return FastDateFormat.formatUtc(date);
    }
}
