package net.cnri.cordra.util;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

public class JsonUtil {
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

    public static JsonElement getJsonAtPointer(JsonElement content, String jsonPointer) {
        if (jsonPointer.isEmpty()) return content;
        if (!isValidJsonPointer(jsonPointer)) return null;
        String[] parts = jsonPointer.split("/", -1);
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            String segment = part.replace("~1", "/").replace("~0", "~");
            if (content.isJsonObject()) content = content.getAsJsonObject().get(segment);
            else if (content.isJsonArray()) {
                try {
                    content = content.getAsJsonArray().get(Integer.parseInt(segment));
                } catch (NumberFormatException e) {
                    return null;
                }
            } else {
                return null;
            }
            if (content == null) return null;
        }
        return content;
    }

    public static void setJsonAtPointer(JsonElement content, String jsonPointer, JsonElement toAdd, boolean createNeededParents) {
        if (jsonPointer.isEmpty()) throw new IllegalArgumentException("setJsonAtPointer cannot take empty JSON pointer");
        if (!isValidJsonPointer(jsonPointer)) throw new IllegalArgumentException("JSON pointer is not valid");
        String[] parts = jsonPointer.split("/", -1);
        for (int i = 1; i < parts.length - 1; i++) {
            String part = parts[i];
            String segment = part.replace("~1", "/").replace("~0", "~");
            if (content.isJsonObject()) {
                JsonObject contentParent = content.getAsJsonObject();
                content = contentParent.get(segment);
                if (content == null && createNeededParents) {
                    content = new JsonObject();
                    contentParent.add(segment, content);
                }
            }
            else if (content.isJsonArray()) {
                try {
                    int index = Integer.parseInt(segment);
                    JsonArray contentParent = content.getAsJsonArray();
                    if (createNeededParents && contentParent.size() <= index) {
                        while (contentParent.size() < index) {
                            content.getAsJsonArray().add(JsonNull.INSTANCE);
                        }
                        content = new JsonObject();
                        contentParent.add(content);
                    } else {
                        content = content.getAsJsonArray().get(index);
                    }
                } catch (NumberFormatException | IndexOutOfBoundsException e) {
                    throw new IllegalArgumentException("setJsonAtPointer unable to find parent of " + jsonPointer);
                }
            } else {
                throw new IllegalArgumentException("setJsonAtPointer unable to find parent of " + jsonPointer);
            }
            if (content == null) {
                throw new IllegalArgumentException("setJsonAtPointer unable to find parent of " + jsonPointer);
            }
        }
        String lastSegment = parts[parts.length - 1].replace("~1", "/").replace("~0", "~");
        if (content.isJsonArray()) {
            try {
                int num = Integer.parseInt(lastSegment);
                while (content.getAsJsonArray().size() <= num) {
                    content.getAsJsonArray().add(JsonNull.INSTANCE);
                }
                content.getAsJsonArray().set(num, toAdd);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("setJsonAtPointer parent of " + jsonPointer + " is array");
            }
        } else if (content.isJsonObject()) {
            content.getAsJsonObject().add(lastSegment, toAdd);
        } else {
            throw new IllegalArgumentException("setJsonAtPointer parent of " + jsonPointer + " is primitive");
        }
    }

    public static void setJsonAtPointer(JsonElement content, String jsonPointer, JsonElement toAdd) {
        setJsonAtPointer(content, jsonPointer, toAdd, false);
    }

    public static JsonElement pruneToMatchPointers(JsonElement el, Collection<String> pointers) {
        JsonElement clone = el.deepCopy();
        if (pointers.contains("")) return clone;
        Map<String, Boolean> expandedPointers = expandPointers(pointers);
        String location = "";
        pruneToMatchPointers(clone, expandedPointers, location);
        return clone;
    }

    private static void pruneToMatchPointers(JsonElement el, Map<String, Boolean> pointers, String parentPointer) {
        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            Set<String> properties = new HashSet<>();
            properties.addAll(obj.keySet());
            for (String property : properties) {
                String currentPointer = parentPointer + "/" + property;
                Boolean currentPointerRequired = pointers.get(currentPointer);
                if (currentPointerRequired == null) {
                    obj.remove(property);
                } else if (currentPointerRequired == true) {
                    continue;
                } else {
                    JsonElement subEL = obj.get(property);
                    if (subEL.isJsonObject() || subEL.isJsonArray()) {
                        pruneToMatchPointers(subEL, pointers, currentPointer);
                    }
                }
            }
        } else if (el.isJsonArray()) {
            JsonArray array = el.getAsJsonArray();
            String currentPointerArray = parentPointer + "/_";
            Boolean currentPointerRequired = pointers.get(currentPointerArray);
            if (currentPointerRequired != null) {
                if (currentPointerRequired == true) {
                    return;
                } else {
                    for (int i = 0; i < array.size(); i++) {
                        JsonElement arrayElement = array.get(i);
                        pruneToMatchPointers(arrayElement, pointers, currentPointerArray);
                    }
                }
            } else {
                int i = 0;
                Iterator<JsonElement> iter = array.iterator();
                while (iter.hasNext()) {
                    JsonElement arrayElement = iter.next();
                    String currentPointerSpecificArrayItem = parentPointer + "/" +i;
                    i++;
                    Boolean currentPointerSpecificArrayItemRequired = pointers.get(currentPointerSpecificArrayItem);
                    if (currentPointerSpecificArrayItemRequired != null) {
                        if (currentPointerSpecificArrayItemRequired == true) {
                            continue;
                        } else {
                            pruneToMatchPointers(arrayElement, pointers, currentPointerSpecificArrayItem);
                        }
                    } else {
                        iter.remove();
                    }
                }
            }
        }
    }

    private static Map<String, Boolean> expandPointers(Collection<String> pointers) {
        Map<String, Boolean> result = new HashMap<>();
        for (String pointer : pointers) {
            result.putAll(expandPointer(pointer));
        }
        return result;
    }

    private static Map<String, Boolean> expandPointer(String pointer) {
        Map<String, Boolean> result = new HashMap<>();
        result.put(pointer, true);
        String[] segmentsArray = pointer.split("/");
        for (int t = 1; t < segmentsArray.length; t++) {
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < t; i++) {
                sb.append("/").append(segmentsArray[i]);
            }
            String subPointer = sb.toString();
            if (!subPointer.isEmpty()) {
                result.put(subPointer, false);
            }
        }
        return result;
    }
}
