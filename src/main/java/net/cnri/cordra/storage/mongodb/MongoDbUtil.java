package net.cnri.cordra.storage.mongodb;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.cnri.cordra.api.CordraException;
import org.bson.Document;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import net.cnri.util.StringUtils;

public class MongoDbUtil {

    public static Document jsonObjectToDocument(JsonObject obj) throws CordraException {
        Document doc = new Document();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            if (value.isJsonObject()) {
                doc.put(key, jsonObjectToDocument(value.getAsJsonObject()));
            } else if (value.isJsonArray()) {
                doc.put(key, jsonArrayToList(value.getAsJsonArray()));
            } else if (value.isJsonNull()) {
                doc.put(key, null);
            } else {
                doc.put(key, convertPrimitiveForMongo(value.getAsJsonPrimitive()));
            }
        }
        return doc;
    }

    private static List<Object> jsonArrayToList(JsonArray arr) throws CordraException {
        List<Object> res = new ArrayList<>(arr.size());
        for (JsonElement value : arr) {
            if (value.isJsonObject()) {
                res.add(jsonObjectToDocument(value.getAsJsonObject()));
            } else if (value.isJsonArray()) {
                res.add(jsonArrayToList(value.getAsJsonArray()));
            } else if (value.isJsonNull()) {
                res.add(null);
            } else {
                res.add(convertPrimitiveForMongo(value.getAsJsonPrimitive()));
            }
        }
        return res;
    }

    private static Object convertPrimitiveForMongo(JsonPrimitive prim) throws CordraException {
        if (prim.isBoolean()) {
            return prim.getAsBoolean();
        } else if (prim.isString()) {
            return prim.getAsString();
        } else {
            String numberString = prim.getAsString();
            double doubleValue = prim.getAsDouble();
            if (doubleValue == Double.POSITIVE_INFINITY || doubleValue == Double.NEGATIVE_INFINITY) {
                throw new CordraException("MongoDbStorage does not support storing very large or very small numbers. Invalid value: " + numberString);
            }
            if (numberString.contains(".") || numberString.contains("e-") || numberString.contains("E-")) {
                return doubleValue;
            } else {
                if (doubleValue >= Integer.MIN_VALUE && doubleValue <= Integer.MAX_VALUE) {
                    return prim.getAsInt();
                } else if (doubleValue >= Long.MIN_VALUE && doubleValue <= Long.MAX_VALUE) {
                    return prim.getAsLong();
                } else {
                    return doubleValue;
                }
            }
        }
    }

    public static void percentDecodeFieldNames(JsonElement el) {
        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            boolean changed = false;
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                if (!changed) {
                    String currentKey = entry.getKey();
                    String newKey = decodeKey(currentKey);
                    if (!newKey.equals(currentKey)) {
                        changed = true;
                    }
                }
                percentDecodeFieldNames(entry.getValue());
            }
            if (changed) {
                List<String> keys = new ArrayList<>(obj.keySet());
                for (String currentKey : keys) {
                    String newKey = decodeKey(currentKey);
                    obj.add(newKey, obj.remove(currentKey));
                }
            }
        } else if (el.isJsonArray()) {
            for (JsonElement sub : el.getAsJsonArray()) {
                percentDecodeFieldNames(sub);
            }
        }
    }

    public static void percentEncodeFieldNames(Document doc) {
        boolean changed = false;
        for (Map.Entry<String, Object> entry : doc.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Document) {
                percentEncodeFieldNames((Document)value);
            } else if (value instanceof List) {
                @SuppressWarnings("rawtypes")
                List list = (List) value;
                percentEncodeFieldNames(list);
            }
            String encodedKey = encodeKey(key);
            if (!key.equals(encodedKey)) {
                changed = true;
            }
        }
        if (changed) {
            List<String> keys = new ArrayList<>(doc.keySet());
            for (String key : keys) {
                String encodedKey = encodeKey(key);
                doc.put(encodedKey, doc.remove(key));
            }
        }
    }

    @SuppressWarnings({"rawtypes"})
    private static void percentEncodeFieldNames(List list) {
        for (int i = 0; i < list.size(); i++) {
            Object el = list.get(i);
            if (el instanceof List) {
                List sublist = (List) el;
                percentEncodeFieldNames(sublist);
            } else if (el instanceof Document) {
                percentEncodeFieldNames((Document)el);
            }
        }
    }

    public static boolean isKeyNeedsEncoding(String key) {
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (i == 0 && '$' == c) return true;
            if ('%' == c) return true;
            if ('.' == c) return true;
            if ('\u0000' == c) return true;
        }
        return false;
    }

    public static String encodeKey(String key) {
        if (key.equals("_id")) {
            return "%5Fid";
        }
        if (!isKeyNeedsEncoding(key)) {
            return key;
        }
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (i == 0) {
                if ('$' == c) {
                    buffer.append("%24");
                    continue;
                }
            }
            if ('%' == c) {
                buffer.append("%25");
            } else if ('.' == c) {
                buffer.append("%2E");
            } else if ('\u0000' == c) {
                buffer.append("%00");
            } else {
                buffer.append(c);
            }
        }
        return buffer.toString();
    }

    public static String encodeKeyMarginallyFaster(String key) {
        if (key.equals("_id")) {
            return "%5Fid";
        }
        if (!isKeyNeedsEncoding(key)) {
            return key;
        }
        int firstNormalCharIndex = -1;
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            String replacement = null;
            if (i == 0 && '$' == c) {
                replacement = "%24";
            } else if ('%' == c) {
                replacement = "%25";
            } else if ('.' == c) {
                replacement = "%2E";
            } else if ('\u0000' == c) {
                replacement = "%00";
            }
            if (replacement != null) {
                if (firstNormalCharIndex >= 0) {
                    buffer.append(key, firstNormalCharIndex, i);
                    firstNormalCharIndex = -1;
                }
                buffer.append(replacement);
            } else if (firstNormalCharIndex < 0) {
                firstNormalCharIndex = i;
            }
        }
        if (firstNormalCharIndex >= 0) {
            buffer.append(key, firstNormalCharIndex, key.length());
        }
        return buffer.toString();
    }


    public static String decodeKey(String key) {
        return StringUtils.decodeURLIgnorePlus(key);
    }
}
