package net.cnri.cordra;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.api.CordraObject;
import net.cnri.cordra.api.SearchResults;
import net.cnri.cordra.api.UncheckedCordraException;

import java.util.Map;

public class UserProcessor {

    private final CordraService cordra;

    public UserProcessor(CordraService cordra) {
        this.cordra = cordra;
    }
    
    private boolean findUsernameThrowIfNotUnique(String handle, CordraObject co, JsonNode json, Map<String, JsonNode> pointerToSchemaMap) throws CordraException, InvalidException {
        for (Map.Entry<String, JsonNode> entry : pointerToSchemaMap.entrySet()) {
            String jsonPointer = entry.getKey();
            JsonNode subSchema = entry.getValue();
            JsonNode authNode = SchemaUtil.getDeepCordraSchemaProperty(subSchema, "auth");
            if (authNode != null && authNode.isObject()) {
                authNode = authNode.get("type");
            }
            if (authNode == null || !"username".equals(authNode.asText())) continue;
 
            JsonNode usernameNode = JsonUtil.getJsonAtPointer(jsonPointer, json);
            String username = usernameNode.asText();

            if (isUsernameUnique(username, handle)) {
                if (co != null) {
                    if (co.metadata == null) co.metadata = new CordraObject.Metadata();
                    if (co.metadata.internalMetadata == null) co.metadata.internalMetadata = new JsonObject();
                    co.metadata.internalMetadata.addProperty("username", username);
                }
                return true;
            } else {
                throw new InvalidException("Username "+username+" is not unique.");
            }
        }
        return false;
    }
    
    public void preprocess(String handle, JsonNode json, Map<String, JsonNode> pointerToSchemaMap) throws CordraException, InvalidException {
        findUsernameThrowIfNotUnique(handle, null, json, pointerToSchemaMap);
    }

    public boolean process(CordraObject co, JsonNode json, Map<String, JsonNode> pointerToSchemaMap) throws CordraException, InvalidException {
        boolean hasUsername = findUsernameThrowIfNotUnique(co.id, co, json, pointerToSchemaMap);
        if (hasUsername) {
            return true;
        } else {
            if (co.metadata == null || co.metadata.internalMetadata == null) return false;
            if (co.metadata.internalMetadata.get("username") != null) {
                co.metadata.internalMetadata.remove("username");
                return true;
            } else {
                return false;
            }
        }
    }

    public boolean isUsernameUnique(String username, String handle) throws CordraException {
        if ("admin".equalsIgnoreCase(username)) {
            return false; //admin is a reserved username. Users cannot change their name to admin
        }
        cordra.ensureIndexUpToDate();
        String q = "username:\"" + username + "\"";
        try (SearchResults<CordraObject> results = cordra.searchRepo(q)) {
            for (CordraObject co : results) {
                JsonElement foundUsername = co.metadata.internalMetadata.get("username");
                if (foundUsername != null && username.equalsIgnoreCase(foundUsername.getAsString())) {
                    if (!co.id.equals(handle)) {
                        return false;
                    }
                }
            }
        } catch (UncheckedCordraException e) {
            e.throwCause();
        }
        return true;
    }

    public boolean isUserAccountActive(JsonNode jsonNode, Map<String, JsonNode> pointerToSchemaMap) {
        for (Map.Entry<String, JsonNode> entry : pointerToSchemaMap.entrySet()) {
            String jsonPointer = entry.getKey();
            JsonNode subSchema = entry.getValue();
            JsonNode authNode = SchemaUtil.getDeepCordraSchemaProperty(subSchema, "auth");
            if (authNode != null && authNode.isObject()) {
                authNode = authNode.get("type");
            }
            if (authNode == null || !"accountActive".equals(authNode.asText())) continue;
            JsonNode accountActiveNode = JsonUtil.getJsonAtPointer(jsonPointer, jsonNode);
            if (accountActiveNode == null) {
                break;
            } else {
                // node exists, so default to false (assuming we don't want to accidentally allow folks through)
                return accountActiveNode.asBoolean(false);
            }
        }
        // node does not exist, so we're not using this feature. default to true.
        return true;
    }

    public static boolean isUser(Map<String, JsonNode> pointerToSchemaMap) {
        for (JsonNode subSchema : pointerToSchemaMap.values()) {
            JsonNode authNode = SchemaUtil.getDeepCordraSchemaProperty(subSchema, "auth");
            if (authNode != null && authNode.isObject()) {
                authNode = authNode.get("type");
            }
            if (authNode == null || !"username".equals(authNode.asText())) continue;
            return true;
        }
        return false;    
    }
}
