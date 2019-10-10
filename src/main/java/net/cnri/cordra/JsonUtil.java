package net.cnri.cordra;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BigIntegerNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.github.fge.jackson.JsonLoader;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.google.common.collect.Lists;
import com.google.gson.*;

import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.api.InternalErrorCordraException;

import java.io.IOException;
import java.io.Writer;
import java.math.BigInteger;
import java.util.*;

public class JsonUtil {

    public static List<String> findObjectsWithProperty(TreeNode jsonNode, String prop) {
        List<String> result = new ArrayList<>();
        findObjectsWithProperty(jsonNode, prop, "", result);
        return result;
    }

    private static void findObjectsWithProperty(TreeNode jsonNode, String prop, String pointer, List<String> result) {
        if (jsonNode.isArray()) {
            for (int i = 0; i < jsonNode.size(); i++) {
                TreeNode child = jsonNode.path(i);
                findObjectsWithProperty(child, prop, pointer + "/" + i, result);
            }
        } else if (jsonNode.isObject()) {
            Iterator<String> iter = jsonNode.fieldNames();
            while (iter.hasNext()) {
                String fieldName = iter.next();
                if (fieldName.equals(prop)) {
                    result.add(pointer);
                }
                TreeNode child = jsonNode.path(fieldName);
                findObjectsWithProperty(child, prop, pointer + "/" + encodeSegment(fieldName), result);
            }
        }
    }

    public static String encodeSegment(String s) {
        return s.replace("~", "~0").replace("/", "~1");
    }

    public static String decodeSegment(String s) {
        return s.replace("~1", "/").replace("~0", "~");
    }

    public static void deletePointer(JsonNode jsonNode, String pointer) {
        if ("".equals(pointer)) {
            if (jsonNode.isObject()) {
                Collection<String> allFieldNames = Lists.newArrayList(((ObjectNode)jsonNode).fieldNames());
                ((ObjectNode) jsonNode).remove(allFieldNames);
            } else if (jsonNode.isArray()) {
                ((ArrayNode) jsonNode).removeAll();
            }
            return;
        }
        String parentPointer = getParentJsonPointer(pointer);
        String lastSegment = getLastSegmentFromJsonPointer(pointer);
        String fieldName = decodeSegment(lastSegment);
        JsonNode parentNode = jsonNode.at(parentPointer);
        if (parentNode.isObject()) {
            ((ObjectNode) parentNode).remove(fieldName);
        } else if (parentNode.isArray()) {
            int indexToRemove = Integer.parseInt(fieldName);
            ((ArrayNode) parentNode).remove(indexToRemove);
        }
    }

    public static void replaceJsonAtPointer(JsonNode jsonNode, String pointer, JsonNode replacement) {
        if ("".equals(pointer)) {
            throw new IllegalArgumentException("Can't replace empty pointer");
        }
        String parentPointer = getParentJsonPointer(pointer);
        String lastSegment = getLastSegmentFromJsonPointer(pointer);
        String fieldName = decodeSegment(lastSegment);
        JsonNode parentNode = jsonNode.at(parentPointer);
        if (parentNode.isObject()) {
            ((ObjectNode) parentNode).set(fieldName, replacement);
        } else if (parentNode.isArray()) {
            try {
                int index = Integer.parseInt(fieldName);
                ((ArrayNode) parentNode).set(index, replacement);
            } catch (NumberFormatException e) {
                throw new IllegalStateException("Found array at " + parentPointer + " but next segment is " + lastSegment);
            }
        }
    }

    public static String prettyPrintJson(JsonNode jsonNode) throws CordraException {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode);
        } catch (JsonProcessingException e) {
            throw new InternalErrorCordraException(e);
        }
    }

    public static void prettyPrintJson(Writer writer, Object jsonNode) throws CordraException, IOException {
        ObjectMapper mapper = new ObjectMapper();
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(writer, jsonNode);
        } catch (JsonProcessingException e) {
            throw new InternalErrorCordraException(e);
        }
    }

    public static String printJson(Object jsonNode) throws InternalErrorCordraException {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsString(jsonNode);
        } catch (JsonProcessingException e) {
            throw new InternalErrorCordraException(e);
        }
    }

    public static void printJson(Writer writer, Object jsonNode) throws InternalErrorCordraException, IOException {
        ObjectMapper mapper = new ObjectMapper();
        try {
            mapper.writeValue(writer, jsonNode);
        } catch (JsonProcessingException e) {
            throw new InternalErrorCordraException(e);
        }
    }

    public static String getLastSegmentFromJsonPointer(String pointer) {
        if (!"".equals(pointer) && pointer.charAt(0) != '/') {
            throw new IllegalArgumentException("A Json Pointer that is not the empty string must start with a slash.");
        }
        int lastSlash = pointer.lastIndexOf("/");
        if (lastSlash == -1) {
            return pointer;
        }
        String lastSegment = pointer.substring(lastSlash+1);
        return lastSegment;
    }

    public static String getParentJsonPointer(String pointer) {
        if (!"".equals(pointer) && pointer.charAt(0) != '/') {
            throw new IllegalArgumentException("A Json Pointer that is not the empty string must start with a slash.");
        }
        int lastSlash = pointer.lastIndexOf("/");
        if (lastSlash == -1) {
            return pointer;
        }
        String parentPointer = pointer.substring(0, lastSlash);
        return parentPointer;
    }

    public static String convertJsonPointerToUseWildCardForArrayIndices(String jsonPointer, JsonNode jsonNode) {
        if ("/".equals(jsonPointer)) {
            return jsonPointer;
        }
        String[] segments = jsonPointer.split("/");
        String resultPointer = "";
        String parentPointer = "";
        String currentPointer = "";
        for (int i = 1; i < segments.length; i++) {
            String segment = segments[i];
            parentPointer = currentPointer;
//            if ("".equals(segment)) {
//                continue;
//            }

            currentPointer = currentPointer + "/" + segment;
            JsonNode parentNode = JsonUtil.getJsonAtPointer(parentPointer, jsonNode);
            if (parentNode.isArray()) {
                resultPointer = resultPointer + "/_";
            } else {
                resultPointer = resultPointer + "/" + segment;
            }
        }
        return resultPointer;
    }


    public static JsonSchema parseJsonSchema(JsonSchemaFactory factory, JsonNode schemaNode) throws ProcessingException {
        final JsonSchema schema = factory.getJsonSchema(schemaNode);
        return schema;
    }

    public static JsonNode parseJson(String jsonData) throws InvalidException {
        try {
            JsonNode dataNode = JsonLoader.fromString(jsonData);
            return dataNode;
        } catch (Exception e) {
            throw new InvalidException(e);
        }
    }

    public static JsonNode getJsonAtPointer(String jsonPointer, JsonNode dataNode) {
        return dataNode.at(jsonPointer);
    }

    public static boolean isValidJsonPointer(String jsonPointer) {
        if (jsonPointer.isEmpty()) return true;
        if (!jsonPointer.startsWith("/")) return false;
        for (int i = 0; i < jsonPointer.length(); i++) {
            if (jsonPointer.charAt(i) == '~') {
                if (i + 1 >= jsonPointer.length()) return false;
                char ch = jsonPointer.charAt(i+1);
                if (ch != '0' && ch != '1') return false;
            }
        }
        return true;
    }

    public static JsonNode getDeepProperty(JsonNode origin, String... keys) {
        JsonNode current = origin;
        for (String key : keys) {
            current = current.get(key);
            if (current == null) return null;
        }
        return current;
    }

    public static JsonNode gsonToJackson(JsonElement el) {
        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            ObjectNode res = new ObjectNode(JsonNodeFactory.instance);
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                res.set(entry.getKey(), gsonToJackson(entry.getValue()));
            }
            return res;
        } else if (el.isJsonArray()) {
            JsonArray arr = el.getAsJsonArray();
            ArrayNode res = new ArrayNode(JsonNodeFactory.instance, arr.size());
            for (JsonElement sub : arr) {
                res.add(gsonToJackson(sub));
            }
            return res;
        } else if (el.isJsonNull()) {
            return NullNode.getInstance();
        } else {
            JsonPrimitive prim = el.getAsJsonPrimitive();
            if (prim.isBoolean()) {
                if (prim.getAsBoolean()) {
                    return BooleanNode.TRUE;
                } else {
                    return BooleanNode.FALSE;
                }
            } else if (prim.isString()) {
                return new TextNode(prim.getAsString());
            } else {
                String numberString = prim.getAsString();
                // distinguishing integers is necessary for schema validation
                if (numberString.contains("e") || numberString.contains("E") || numberString.contains(".")) {
                    // If no dot and no e- or E-, then it is actually integral.
                    // Nonetheless we use DecimalNode to match the behavior of JsonUtil.parseJson.
                    // Similarly we avoid DoubleNode.
                    return new DecimalNode(prim.getAsBigDecimal());
//                } else if (numberString.contains(".")) {
//                    if (numberString.length() <= 15) {
//                        return new DoubleNode(prim.getAsDouble());
//                    } else {
//                        return new DecimalNode(prim.getAsBigDecimal());
//                    }
                } else {
                    if (numberString.length() <= 9) {
                        return new IntNode(prim.getAsInt());
                    } else if (numberString.length() <= 18) {
                        return integralNode(prim.getAsLong());
                    } else {
                        return integralNode(prim.getAsBigInteger());
                    }
                }
            }
        }
    }

    private static JsonNode integralNode(long n) {
        if (Integer.MIN_VALUE <= n && n <= Integer.MAX_VALUE) {
            return new IntNode((int) n);
        } else {
            return new LongNode(n);
        }
    }

    private static final BigInteger MIN_INTEGER = BigInteger.valueOf(Integer.MIN_VALUE);
    private static final BigInteger MAX_INTEGER = BigInteger.valueOf(Integer.MAX_VALUE);
    private static final BigInteger MIN_LONG = BigInteger.valueOf(Long.MIN_VALUE);
    private static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);

    private static JsonNode integralNode(BigInteger n) {
        if (MIN_INTEGER.compareTo(n) <= 0 && n.compareTo(MAX_INTEGER) <= 0) {
            return new IntNode(n.intValue());
        } else if (MIN_LONG.compareTo(n) <= 0 && n.compareTo(MAX_LONG) <= 0) {
            return new LongNode(n.longValue());
        } else {
            return new BigIntegerNode(n);
        }
    }

    public static JsonElement pruneToMatchPointers(JsonElement el, Collection<String> pointers) {
        return net.cnri.cordra.util.JsonUtil.pruneToMatchPointers(el, pointers);
    }
}
