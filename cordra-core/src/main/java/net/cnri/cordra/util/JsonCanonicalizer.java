package net.cnri.cordra.util;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * A tool for creating canonical strings from JSON elements, and testing two JSON elements for canonical equality.
 * Objects are canonicalized by putting keys in lexicographic order.
 * Arrays are canonicalized by putting elements in lexicographic order
 * of their canonicalizations; note that arrays are thus always considered unordered.
 *
 * For canonicalizing the primitives, we emulate JSON.stringify.  These are the rules:
 *
 * null, true, and false are canonical.
 *
 * The canonical form of a string uses 7 of the 8 slash escapes: \" \\ \b \f \n \r \t.
 * The eighth, \/, is not used.  Other control characters less than U+001F are escaped as
 * slash-u00xx using lowercase hex digits. No other characters are escaped.
 *
 * For numbers, scientific notation is only used if the absolute value of the
 * number is &lt; 0.000001 or &gt;= 1e21.  The exponent is a lowercase e and always
 * includes a + or - sign.  No trailing zeros are used after a decimal point.
 */
public class JsonCanonicalizer {

    static String jsonStringify(JsonPrimitive prim) {
        if (prim.isJsonNull()) return "null";
        if (prim.isBoolean()) return String.valueOf(prim.getAsBoolean());
        if (prim.isNumber()) {
            return jsonStringify(prim.getAsBigDecimal());
        }
        if (prim.isString()) {
            return jsonStringify(prim.getAsString());
        }
        throw new AssertionError("JsonPrimitive not null or boolean or number or string");
    }

    // http://www.ecma-international.org/ecma-262/5.1/#sec-15.12.3
    static String jsonStringify(String s) {
       StringBuilder sb = new StringBuilder(s.length() + 2);
       sb.append("\"");
       sb.append(s);
       sb.append("\"");
       for (int i = 1; i < sb.length() - 1; i++) {
           char ch = sb.charAt(i);
           if (ch == '"') {
               sb.replace(i, i+1, "\\\"");
               i++;
           } else if (ch == '\\') {
               sb.replace(i, i+1, "\\\\");
               i++;
           } else if (ch >= 0x20) {
               continue;
           } else if (ch == '\b') {
               sb.replace(i, i+1, "\\b");
               i++;
           } else if (ch == '\f') {
               sb.replace(i, i+1, "\\f");
               i++;
           } else if (ch == '\n') {
               sb.replace(i, i+1, "\\n");
               i++;
           } else if (ch == '\r') {
               sb.replace(i, i+1, "\\r");
               i++;
           } else if (ch == '\t') {
               sb.replace(i, i+1, "\\t");
               i++;
           } else {
               sb.replace(i, i+1, String.format("\\u%04x", (int)ch));
               i += 5;
           }
       }
       return sb.toString();
    }

    // http://www.ecma-international.org/ecma-262/5.1/#sec-9.8.1
    static String jsonStringify(BigDecimal m) {
        int signum = m.signum();
        if (signum == 0) return "0";
        if (signum < 0) return "-" + jsonStringify(m.negate());
        int nMinusK = -m.scale();
        String s = m.unscaledValue().toString();
        for (int i = s.length() - 1; i >= 0; i--) {
            if (s.charAt(i) != '0') {
                nMinusK += s.length() - (i+1);
                s = s.substring(0, i + 1);
                break;
            }
        }
        int k = s.length();
        int n = nMinusK + k;
        StringBuilder sb = new StringBuilder();
        if (k <= n && n <= 21) {
            sb.append(s);
            for (int i = 0; i < n - k; i++) {
                sb.append("0");
            }
        } else if (0 < n && n <= 21) {
            sb.append(s);
            sb.insert(n, ".");
        } else if (-6 < n && n <= 0) {
            sb.append("0.");
            for (int i = 0; i < -n; i++) {
                sb.append("0");
            }
            sb.append(s);
        } else {
            sb.append(s);
            if (k > 1) {
                sb.insert(1, ".");
            }
            sb.append("e");
            if (n - 1 > 0) {
                sb.append("+");
            } else {
                sb.append("-");
            }
            sb.append(Math.abs(n - 1));
        }
        return sb.toString();
    }

    /**
     * Returns the canonical string for the JSON element.
     */
    public static String canonicalize(JsonElement el) {
        StringBuilder sb = new StringBuilder();
        canonicalize(el, sb);
        return sb.toString();
    }

    /**
     * Appends the canonical string for the JSON element to a StringBuilder.
     */
    public static void canonicalize(JsonElement el, StringBuilder sb) {
        if (el.isJsonNull()) {
            sb.append("null");
        } else if (el.isJsonPrimitive()) {
            sb.append(jsonStringify(el.getAsJsonPrimitive()));
        } else if (el.isJsonObject()) {
            sb.append("{");
            Set<Map.Entry<String, JsonElement>> entrySet = el.getAsJsonObject().entrySet();
            if (!entrySet.isEmpty()) {
                entrySet.stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    sb.append(jsonStringify(entry.getKey()))
                    .append(":");
                    canonicalize(entry.getValue(), sb);
                    sb.append(",");
                });
                // remove extra comma
                sb.deleteCharAt(sb.length() - 1);
            }
            sb.append("}");
        } else if (el.isJsonArray()) {
            JsonArray arr = el.getAsJsonArray();
            sb.append("[");
            if (arr.size() == 0) {
                // nothing
            } else if (arr.size() == 1) {
                canonicalize(arr.get(0), sb);
            } else {
                StreamSupport.stream(arr.spliterator(), false)
                .map(JsonCanonicalizer::canonicalize)
                .sorted()
                .forEach(s -> sb.append(s).append(","));
                // remove extra comma
                sb.deleteCharAt(sb.length() - 1);
            }
            sb.append("]");
        } else {
            throw new AssertionError("JsonElement not null or primitive or object or array");
        }
    }

    /**
     * Checks two JSON elements for canonical equality.
     */
    public static boolean equals(JsonElement el1, JsonElement el2) {
        if (el1 == null) return el2 == null;
        if (el2 == null) return false;
        if (el1.isJsonNull()) {
            return el2.isJsonNull();
        } else if (el1.isJsonPrimitive()) {
            if (!el2.isJsonPrimitive()) return false;
            JsonPrimitive prim1 = el1.getAsJsonPrimitive();
            JsonPrimitive prim2 = el2.getAsJsonPrimitive();
            if (prim1.isJsonNull()) return prim2.isJsonNull();
            if (prim1.isBoolean()) {
                return prim2.isBoolean() && prim1.getAsBoolean() == prim2.getAsBoolean();
            }
            if (prim1.isNumber()) {
                return prim2.isNumber() && prim1.getAsBigDecimal().compareTo(prim2.getAsBigDecimal()) == 0;
            }
            if (prim1.isString()) {
                return prim2.isString() && prim1.getAsString().equals(prim2.getAsString());
            }
            throw new AssertionError("JsonPrimitive not null or boolean or number or string");
        }
        if (el1.isJsonObject()) {
            if (!el2.isJsonObject()) return false;
            Set<Map.Entry<String,JsonElement>> set1 = el1.getAsJsonObject().entrySet();
            Set<Map.Entry<String,JsonElement>> set2 = el2.getAsJsonObject().entrySet();
            if (set1.size() != set2.size()) return false;
            return set1.stream()
                .allMatch(entry -> equals(entry.getValue(), el2.getAsJsonObject().get(entry.getKey())));
        }
        if (el1.isJsonArray()) {
            if (!el2.isJsonArray()) return false;
            JsonArray arr1 = el1.getAsJsonArray();
            JsonArray arr2 = el2.getAsJsonArray();
            if (arr1.size() != arr2.size()) return false;
            if (arr1.size() == 0) return true;
            if (arr1.size() == 1) return equals(arr1.get(0), arr2.get(0));
            List<String> list1 = jsonArrayToSortedListOfStrings(arr1);
            List<String> list2 = jsonArrayToSortedListOfStrings(arr2);
            return list1.equals(list2);
        }
        throw new AssertionError("JsonElement not null or primitive or object or array");
    }

    private static List<String> jsonArrayToSortedListOfStrings(JsonArray arr1) {
        return StreamSupport.stream(arr1.spliterator(), false)
            .map(JsonCanonicalizer::canonicalize)
            .sorted()
            .collect(Collectors.toList());
    }

}
