package net.cnri.cordra.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Map.Entry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class SearchJsonObject {

    public static String queryFromJsonElement(JsonElement json) {
        List<String> searchTerms = getSearchTermsFromJsonElement(json, "");
        if (searchTerms.isEmpty()) {
            return "*:*";
        }
        String query = "+" + String.join(" +", searchTerms);
        return query;
    }

    public static List<String> getSearchTermsFromJsonElement(JsonElement json, String parentJsonPointer) {
        List<String> terms = new ArrayList<>();
        if (json.isJsonObject()) {
            JsonObject jsonObject = json.getAsJsonObject();
            Set<Entry<String, JsonElement>> properties = jsonObject.entrySet();
            for (Entry<String, JsonElement> prop : properties) {
                String key = prop.getKey();
                JsonElement valueElement = prop.getValue();
                String encodedKey = key.replace("~", "~0").replace("/", "~1");
                String currentJsonPointer = parentJsonPointer + "/" + encodedKey;
                terms.addAll(getSearchTermsFromJsonElement(valueElement, currentJsonPointer));
            }
        } else if (json.isJsonArray()) {
            JsonArray array = json.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                JsonElement arrayElement = array.get(i);
                String currentJsonPointer = parentJsonPointer + "/_";
                terms.addAll(getSearchTermsFromJsonElement(arrayElement, currentJsonPointer));
            }
        } else if (json.isJsonPrimitive() || json.isJsonNull()) {
            String value = json.getAsString();
            if (!"".equals(value)) {
                String term = parentJsonPointer + ":\"" + value + "\"";
                terms.add(term);
            }
        }
        return terms;
    }

}
