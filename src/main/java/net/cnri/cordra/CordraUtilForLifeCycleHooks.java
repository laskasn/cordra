package net.cnri.cordra;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.api.CordraObject;
import net.cnri.cordra.storage.CordraStorage;

public class CordraUtilForLifeCycleHooks {

    private CordraService cordraService;

    public void init(@SuppressWarnings("hiding") CordraService cordraService) {
        this.cordraService = cordraService;
    }

    public String hashJson(String jsonString) {
        JsonParser parser = new JsonParser();
        JsonElement el = parser.parse(jsonString);
        return CordraObjectHasher.hashJson(el);
    }

    /**
     * Returns a String where those characters that QueryParser
     * expects to be escaped are escaped by a preceding <code>\</code>.
     */
    public String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            // These characters are part of the query syntax and must be escaped
            if (c == '\\' || c == '+' || c == '-' || c == '!' || c == '(' || c == ')' || c == ':'
                    || c == '^' || c == '[' || c == ']' || c == '\"' || c == '{' || c == '}' || c == '~'
                    || c == '*' || c == '?' || c == '|' || c == '&' || c == '/') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    public String verifySecret(String objectJson, String jsonPointer, String secret) {
        CordraObject cordraObject = GsonUtility.getGson().fromJson(objectJson, CordraObject.class);
        return String.valueOf(cordraService.verifySecureProperty(cordraObject, jsonPointer, secret));
    }

    public String verifyHashes(String coJson) throws CordraException {
        Gson gson = GsonUtility.getGson();
        CordraObject co = gson.fromJson(coJson, CordraObject.class);
        CordraStorage storage = cordraService.storage;
        CordraObjectHasher hasher = new CordraObjectHasher();
        CordraObjectHasher.VerificationReport report = hasher.verify(co, storage);
        String result = gson.toJson(report);
        return result;
    }

}
