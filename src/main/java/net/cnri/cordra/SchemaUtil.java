package net.cnri.cordra;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class SchemaUtil {

    public static JsonNode getDeepCordraSchemaProperty(JsonNode origin, String... keys) {
        if (keys == null) keys = new String[0];
        String[] keysPlusOne = new String[keys.length + 1];
        System.arraycopy(keys, 0, keysPlusOne, 1, keys.length);
        keysPlusOne[0] = Constants.CORDRA_SCHEMA_KEYWORD;
        JsonNode res = JsonUtil.getDeepProperty(origin, keysPlusOne);
        if (res != null) return res;
        keysPlusOne[0] = Constants.OLD_REPOSITORY_SCHEMA_KEYWORD;
        return JsonUtil.getDeepProperty(origin, keysPlusOne);
    }

    public static boolean isPathForScriptsInPayloads(JsonNode subSchema) {
        JsonNode directorySchemaNode = SchemaUtil.getDeepCordraSchemaProperty(subSchema, "referrable", "payloads");
        if (directorySchemaNode == null || !directorySchemaNode.isTextual()) return false;
        if (!"scripts".equals(directorySchemaNode.asText()) && !"script".equals(directorySchemaNode.asText())) return false;
        directorySchemaNode = SchemaUtil.getDeepCordraSchemaProperty(subSchema, "referrable", "id");
        if (directorySchemaNode == null || !directorySchemaNode.isBoolean() || !directorySchemaNode.asBoolean()) return false;
        return true;
    }

    public static boolean changePropertyOrder(JsonNode jsonNode, JsonNode schemaNode) {
        return changePropertyOrder(jsonNode, schemaNode, schemaNode);
    }

    static boolean changePropertyOrder(JsonNode jsonNode, JsonNode rootSchemaNode, JsonNode currentSchemaNode) {
        if (currentSchemaNode == null || !currentSchemaNode.isObject()) return false;
        if (!jsonNode.isObject() && !jsonNode.isArray()) return false;
        if (currentSchemaNode.has("$ref")) {
            return changePropertyOrderWithRef(jsonNode, rootSchemaNode, currentSchemaNode);
        }
        if (jsonNode.isObject()) {
            return changePropertyOrderForObject(jsonNode, rootSchemaNode, currentSchemaNode);
        }
        if (jsonNode.isArray()) {
            return changePropertyOrderForArray(jsonNode, rootSchemaNode, currentSchemaNode);
        }
        return false;
    }

    private static boolean changePropertyOrderForObject(JsonNode jsonNode, JsonNode rootSchemaNode, JsonNode currentSchemaNode) {
        if (!currentSchemaNode.has("properties")) return false;
        JsonNode propertiesNode = currentSchemaNode.get("properties");
        if (!propertiesNode.isObject()) return false;
        List<String> schemaPropertyNames = fieldNamesAsList(propertiesNode);
        List<String> nodePropertyNames = fieldNamesAsList(jsonNode);
        boolean isRightOrder = propertyNamesInRightOrder(nodePropertyNames, schemaPropertyNames);
        boolean changed = false;
        if (!isRightOrder) {
            rearrangeProperties((ObjectNode)jsonNode, schemaPropertyNames);
            changed = true;
        }
        for (String nodePropertyName : nodePropertyNames) {
            if (schemaPropertyNames.contains(nodePropertyName)) {
                boolean subobjectChanged = changePropertyOrder(jsonNode.get(nodePropertyName), rootSchemaNode, propertiesNode.get(nodePropertyName));
                changed = changed || subobjectChanged;
            }
        }
        return changed;
    }

    private static List<String> fieldNamesAsList(JsonNode node) {
        List<String> res = new ArrayList<>();
        node.fieldNames().forEachRemaining(res::add);
        return res;
    }

    private static boolean changePropertyOrderForArray(JsonNode jsonNode, JsonNode rootSchemaNode, JsonNode currentSchemaNode) {
        JsonNode itemsNode = currentSchemaNode.get("items");
        if (itemsNode == null || !itemsNode.isObject()) return false;
        boolean changed = false;
        for (JsonNode element : jsonNode) {
            boolean subobjectChanged = changePropertyOrder(element, rootSchemaNode, itemsNode);
            changed = changed || subobjectChanged;
        }
        return changed;
    }

    private static boolean changePropertyOrderWithRef(JsonNode jsonNode, JsonNode rootSchemaNode, JsonNode currentSchemaNode) {
        String ref = currentSchemaNode.get("$ref").asText();
        if (ref != null && ref.startsWith("#")) {
            String jsonPointer = ref.substring(1);
            JsonNode refNode = JsonUtil.getJsonAtPointer(jsonPointer, rootSchemaNode);
            return changePropertyOrder(jsonNode, rootSchemaNode, refNode);
        } else {
            return false;
        }
    }

    static void rearrangeProperties(ObjectNode objectNode, List<String> schemaPropertyNames) {
        Map<String, JsonNode> fields = new LinkedHashMap<>();
        objectNode.fields().forEachRemaining(entry -> {
            fields.put(entry.getKey(), entry.getValue());
        });
        objectNode.removeAll();
        for (String schemaPropertyName : schemaPropertyNames) {
            if (fields.containsKey(schemaPropertyName)) {
                objectNode.set(schemaPropertyName, fields.get(schemaPropertyName));
                fields.remove(schemaPropertyName);
            }
        }
        for (Map.Entry<String, JsonNode> entry : fields.entrySet()) {
            objectNode.set(entry.getKey(), entry.getValue());
        }
    }

    static boolean propertyNamesInRightOrder(List<String> nodePropertyNames, List<String> schemaPropertyNames) {
        int lastIndex = -1;
        for (String nodePropertyName : nodePropertyNames) {
            int thisIndex = schemaPropertyNames.indexOf(nodePropertyName);
            if (thisIndex < 0) {
                lastIndex = Integer.MAX_VALUE;
            } else {
                if (thisIndex > lastIndex) {
                    lastIndex = thisIndex;
                } else {
                    return false;
                }
            }
        }
        return true;
    }

    public static String getHandleForReference(JsonNode referenceNode, JsonNode handleReferenceSchemaNode, String handleMintingConfigPrefix) {
        JsonNode prependNode = handleReferenceSchemaNode.get("prepend");
        if (prependNode != null && prependNode.isTextual()) {
            return prependNode.asText() + referenceNode.asText();
        } else {
            JsonNode prependHandleMintingConfigPrefixNode = handleReferenceSchemaNode.get("prependHandleMintingConfigPrefix");
            if (prependHandleMintingConfigPrefixNode != null && prependHandleMintingConfigPrefixNode.asBoolean()) {
                if (handleMintingConfigPrefix == null) {
                    return referenceNode.asText();
                } else {
                    return ensureSlash(handleMintingConfigPrefix) + referenceNode.asText();
                }
            } else {
                return referenceNode.asText();
            }
        }
    }

    private static String ensureSlash(String s) {
        if (s == null) return null;
        if (s.endsWith("/")) return s;
        else return s + "/";
    }
}
