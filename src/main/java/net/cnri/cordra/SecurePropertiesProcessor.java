package net.cnri.cordra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.cnri.cordra.api.CordraObject;
import net.cnri.cordra.auth.HashAndSalt;

import java.util.*;
import java.util.stream.Collectors;

public class SecurePropertiesProcessor {

    public boolean process(CordraObject co, JsonNode json, Map<String, JsonNode> pointerToSchemaMap) throws InvalidException {
        if (co.metadata == null) co.metadata = new CordraObject.Metadata();
        if (co.metadata.internalMetadata == null) co.metadata.internalMetadata = new JsonObject();
        JsonObject secureProperties = co.metadata.internalMetadata.getAsJsonObject("secureProperties");
        boolean changedJson = false;
        List<String> secureKeysSeen = new ArrayList<>();
        for (Map.Entry<String, JsonNode> entry : pointerToSchemaMap.entrySet()) {
            String jsonPointer = entry.getKey();
            JsonNode subSchema = entry.getValue();
            JsonNode settingNode = SchemaUtil.getDeepCordraSchemaProperty(subSchema, "secureProperty");
            boolean storeSecurely = settingNode != null && settingNode.asBoolean(false);
            if (!storeSecurely) continue;

            JsonNode secureNode = JsonUtil.getJsonAtPointer(jsonPointer, json);
            if (!secureNode.isTextual()) {
                throw new InvalidException("The \"secureProperty\" flag can only be used on properties of type \"string\".");
            }
            String secureText = secureNode.asText();
            JsonObject propertyObject = getJsonObjectForSecureProperty(co, jsonPointer);
            if (propertyObject == null) {
                propertyObject = new JsonObject();
                if (secureProperties == null) {
                    secureProperties = new JsonObject();
                    co.metadata.internalMetadata.add("secureProperties", secureProperties);
                }
                secureProperties.add(jsonPointer, propertyObject);
            }

            secureKeysSeen.add(jsonPointer);
            JsonElement existingHash = propertyObject.get("hash");
            if (existingHash != null) {
                if ("".equals(secureText)) {
                    // Do not update if it is the empty string. This allows the object to be modified without having to set the property every time.
                    continue;
                }
            }

            JsonUtil.replaceJsonAtPointer(json, jsonPointer, new TextNode(""));
            changedJson = true;

            HashAndSalt hashAndSalt = new HashAndSalt(secureText, HashAndSalt.NIST_2017_HASH_ITERATION_COUNT_10K, HashAndSalt.DEFAULT_ALGORITHM);
            String hash = hashAndSalt.getHashString();
            String salt = hashAndSalt.getSaltString();
            String iterations = hashAndSalt.getIterations().toString();
            String alg = hashAndSalt.getAlgorithm();
            propertyObject.addProperty("hash", hash);
            propertyObject.addProperty("salt", salt);
            propertyObject.addProperty("iterations", iterations);
            propertyObject.addProperty("algorithm", alg);
        }
        if (secureProperties != null) {
            List<String> removedKeys = secureProperties.keySet().stream()
                .filter(s -> !secureKeysSeen.contains(s))
                .collect(Collectors.toList());
            removedKeys.forEach(secureProperties::remove);
            if (secureProperties.keySet().isEmpty()) {
                co.metadata.internalMetadata.remove("secureProperties");
            }
        }
        return changedJson;
    }

    public boolean verifySecureProperty(CordraObject cordraObject, String jsonPointer, String secretToVerify) {
        JsonObject propertyObject = getJsonObjectForSecureProperty(cordraObject, jsonPointer);
        if (propertyObject == null) return false;

        JsonElement hashElement = propertyObject.get("hash");
        JsonElement saltElement = propertyObject.get("salt");
        if (hashElement == null || saltElement == null) {
            return false;
        }
        String hash = hashElement.getAsString();
        String salt = saltElement.getAsString();

        JsonElement iterationsElement = propertyObject.get("iterations");
        Integer iterations;
        if (iterationsElement != null) {
            iterations = iterationsElement.getAsInt();
        } else {
            iterations = HashAndSalt.LEGACY_HASH_ITERATION_COUNT_2048;
        }
        JsonElement algorithm = propertyObject.get("algorithm");
        String algString = null;
        if (algorithm != null) algString = algorithm.getAsString();
        HashAndSalt hashAndSalt = new HashAndSalt(hash, salt, iterations, algString);
        return hashAndSalt.verifySecret(secretToVerify);
    }

    private JsonObject getJsonObjectForSecureProperty(CordraObject co, String jsonPointer) {
        if (co.metadata == null) return null;
        if (co.metadata.internalMetadata == null) return null;
        JsonElement secureProperties = co.metadata.internalMetadata.get("secureProperties");
        if (secureProperties == null || !secureProperties.getAsJsonObject().has(jsonPointer)) {
            return null;
        }
        return secureProperties.getAsJsonObject().get(jsonPointer).getAsJsonObject();
    }
}
