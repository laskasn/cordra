package net.cnri.cordra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.cnri.cordra.api.CordraObject;
import net.cnri.cordra.auth.HashAndSalt;

import java.util.Map;

//Replaces password fields with null in the json and stores a hash and salt of the password as internal metadata on the CordraObject
public class PasswordProcessor {

    private int MIN_PASSWORD_LENGTH = 1;

    public void preprocess(JsonNode json, Map<String, JsonNode> pointerToSchemaMap) throws InvalidException {
        for (Map.Entry<String, JsonNode> entry : pointerToSchemaMap.entrySet()) {
            String jsonPointer = entry.getKey();
            JsonNode subSchema = entry.getValue();
            JsonNode authNode = SchemaUtil.getDeepCordraSchemaProperty(subSchema, "auth");
            if (authNode != null && authNode.isObject()) {
                authNode = authNode.get("type");
            }
            if (authNode == null || !"password".equals(authNode.asText())) continue;

            JsonNode passwordNode = JsonUtil.getJsonAtPointer(jsonPointer, json);
            String password = passwordNode.asText();
            if (!isValidPassword(password)) {
                throw new InvalidException("Password does not meet minimum length of " + MIN_PASSWORD_LENGTH);
            }
        }
    }

    public void setPasswordIntoJson(String password, JsonNode json, Map<String, JsonNode> pointerToSchemaMap) {
        for (Map.Entry<String, JsonNode> entry : pointerToSchemaMap.entrySet()) {
            String jsonPointer = entry.getKey();
            JsonNode subSchema = entry.getValue();
            JsonNode authNode = SchemaUtil.getDeepCordraSchemaProperty(subSchema, "auth");
            if (authNode != null && authNode.isObject()) {
                authNode = authNode.get("type");
            }
            if (authNode == null || !"password".equals(authNode.asText())) continue;
            JsonUtil.replaceJsonAtPointer(json, jsonPointer, new TextNode(password));
        }
    }

    public void setRequirePasswordChangeFlag(boolean value, JsonNode json, Map<String, JsonNode> pointerToSchemaMap) {
        for (Map.Entry<String, JsonNode> entry : pointerToSchemaMap.entrySet()) {
            String jsonPointer = entry.getKey();
            JsonNode subSchema = entry.getValue();
            JsonNode authNode = SchemaUtil.getDeepCordraSchemaProperty(subSchema, "auth");
            if (authNode != null && authNode.isObject()) {
                authNode = authNode.get("type");
            }
            if (authNode == null || !"requirePasswordChange".equals(authNode.asText())) continue;
            JsonUtil.replaceJsonAtPointer(json, jsonPointer, BooleanNode.valueOf(value));
        }
    }

    public boolean getRequirePasswordChangeFlag(JsonNode jsonNode, Map<String, JsonNode> pointerToSchemaMap) {
        for (Map.Entry<String, JsonNode> entry : pointerToSchemaMap.entrySet()) {
            String jsonPointer = entry.getKey();
            JsonNode subSchema = entry.getValue();
            JsonNode authNode = SchemaUtil.getDeepCordraSchemaProperty(subSchema, "auth");
            if (authNode != null && authNode.isObject()) {
                authNode = authNode.get("type");
            }
            if (authNode == null || !"requirePasswordChange".equals(authNode.asText())) continue;
            JsonNode requirePasswordChangeNode = JsonUtil.getJsonAtPointer(jsonPointer, jsonNode);
            if (requirePasswordChangeNode == null) {
                break;
            } else {
                return requirePasswordChangeNode.asBoolean(false);
            }
        }
        return false;
    }

    public boolean process(CordraObject co, JsonNode json, Map<String, JsonNode> pointerToSchemaMap) throws InvalidException {
        if (co.metadata == null) co.metadata = new CordraObject.Metadata();
        if (co.metadata.internalMetadata == null) co.metadata.internalMetadata = new JsonObject();
        boolean changedJson = false;
        for (Map.Entry<String, JsonNode> entry : pointerToSchemaMap.entrySet()) {
            String jsonPointer = entry.getKey();
            JsonNode subSchema = entry.getValue();
            JsonNode authNode = SchemaUtil.getDeepCordraSchemaProperty(subSchema, "auth");
            if (authNode != null && authNode.isObject()) {
                authNode = authNode.get("type");
            }
            if (authNode == null || !"password".equals(authNode.asText())) continue;

            JsonNode passwordNode = JsonUtil.getJsonAtPointer(jsonPointer, json);
            String password = passwordNode.asText();

            JsonElement existingHash = co.metadata.internalMetadata.get("hash");
            if (existingHash != null) {
                if ("".equals(password)) {
                    // Do not update the password if it is the empty string.
                    // This allows the digital object to be modified with out having to set the password every time.
                    return false;
                }
            }

            if (!isValidPassword(password)) {
                throw new InvalidException("Password does not meet minimum length of " + MIN_PASSWORD_LENGTH);
            }

            JsonUtil.replaceJsonAtPointer(json, jsonPointer, new TextNode(""));
            changedJson = true;

            HashAndSalt hashAndSalt = new HashAndSalt(password, HashAndSalt.NIST_2017_HASH_ITERATION_COUNT_10K, HashAndSalt.DEFAULT_ALGORITHM);
            String hash = hashAndSalt.getHashString();
            String salt = hashAndSalt.getSaltString();
            String iterations = hashAndSalt.getIterations().toString();
            String alg = hashAndSalt.getAlgorithm();
            co.metadata.internalMetadata.addProperty("hash", hash);
            co.metadata.internalMetadata.addProperty("salt", salt);
            co.metadata.internalMetadata.addProperty("iterations", iterations);
            co.metadata.internalMetadata.addProperty("algorithm", alg);
        }
        return changedJson;
    }

    private boolean isValidPassword(String password) {
        if (password == null) {
            return false;
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            return false;
        }
        return true;
    }
}
