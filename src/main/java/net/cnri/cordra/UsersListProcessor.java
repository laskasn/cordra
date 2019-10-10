package net.cnri.cordra;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.gson.JsonObject;
import net.cnri.cordra.api.CordraObject;

import java.util.Iterator;
import java.util.Map;

public class UsersListProcessor {

    public UsersListProcessor() {
    }

    public boolean process(CordraObject co, JsonNode json, Map<String, JsonNode> pointerToSchemaMap, String handleMintingConfigPrefix) {
        boolean wasSet = false;
        for (Map.Entry<String, JsonNode> entry : pointerToSchemaMap.entrySet()) {
            String jsonPointer = entry.getKey();
            JsonNode subSchema = entry.getValue();
            JsonNode authNode = SchemaUtil.getDeepCordraSchemaProperty(subSchema, "auth");
            if (authNode == null) continue;
            String prepend = null;
            if (authNode.isObject()) {
                JsonNode typeNode = authNode.get("type");
                if (typeNode == null || !"usersList".equals(typeNode.asText())) continue;
                JsonNode prependNode = authNode.get("prepend");
                if (prependNode != null && prependNode.isTextual()) {
                    prepend = prependNode.asText();
                }
                if (prepend == null) {
                    JsonNode prependHandleMintingConfigPrefixNode = authNode.get("prependHandleMintingConfigPrefix");
                    if (prependHandleMintingConfigPrefixNode != null && prependHandleMintingConfigPrefixNode.asBoolean()) {
                        if (handleMintingConfigPrefix != null) {
                            prepend = ensureSlash(handleMintingConfigPrefix);
                        }
                    }
                }
            } else {
                if (!"usersList".equals(authNode.asText())) continue;
            }
            if (prepend == null) prepend = "";
            JsonNode usersListNode = JsonUtil.getJsonAtPointer(jsonPointer, json);
            Iterator<JsonNode> elements = usersListNode.elements();
            StringBuilder attValue = new StringBuilder();
            while (elements.hasNext()) {
                JsonNode element = elements.next();
                String user = element.asText();
                attValue.append(prepend + user).append("\n");
            }
            if (co.metadata == null) co.metadata = new CordraObject.Metadata();
            if (co.metadata.internalMetadata == null) co.metadata.internalMetadata = new JsonObject();
            co.metadata.internalMetadata.addProperty("users", attValue.toString());
            wasSet = true;
        }
        if (wasSet)  {
            return true;
        } else {
            if (co.metadata.internalMetadata.get("users") != null) {
                co.metadata.internalMetadata.remove("users");
                return true;
            } else {
                return false;
            }
        }
    }

    private static String ensureSlash(String s) {
        if (s == null) return null;
        if (s.endsWith("/")) return s;
        else return s + "/";
    }
}
