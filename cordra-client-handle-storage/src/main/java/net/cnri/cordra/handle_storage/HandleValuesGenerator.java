package net.cnri.cordra.handle_storage;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import jdk.nashorn.api.scripting.JSObject;
import net.cnri.cordra.api.CordraObject;
import net.cnri.cordra.api.Payload;
import net.cnri.util.StringUtils;
import net.cnri.util.javascript.JavaScriptEnvironment;
import net.cnri.util.javascript.JavaScriptRunner;
import net.cnri.util.javascript.RequireLookup;
import net.handle.hdllib.GsonUtility;
import net.handle.hdllib.HandleException;
import net.handle.hdllib.HandleValue;
import net.handle.hdllib.Util;

import javax.script.ScriptException;
import java.io.Reader;
import java.io.StringReader;
import java.util.List;

public class HandleValuesGenerator implements RequireLookup {
    private static final String CREATE_HANDLE_VALUES = "createHandleValues";
    public static final String HANDLE_MINTING_CONFIG_MODULE_ID = "/cordra/handle-minting-config";

    private final Gson gson = GsonUtility.getGson();
    private final JavaScriptEnvironment javaScriptEnvironment = new JavaScriptEnvironment(this);
    private volatile HandleMintingConfig config;

    public HandleValuesGenerator(HandleMintingConfig config) {
        if (config == null) {
            this.config = new HandleMintingConfig();
        } else {
            this.config = config;
        }
    }

    public void shutdown() {
        javaScriptEnvironment.shutdown();
    }

    public void setConfig(HandleMintingConfig newConfig) {
        HandleMintingConfig oldConfig = config;
        config = newConfig;
        if ((config.javascript == null && oldConfig.javascript != null) || (config.javascript != null && !config.javascript.equals(oldConfig.javascript))) {
            javaScriptEnvironment.clearCache();
        }
    }

    public HandleMintingConfig getConfig() {
        return config;
    }

    @Override
    public boolean exists(String filename) {
        if (config.javascript != null && filename.equals(HANDLE_MINTING_CONFIG_MODULE_ID)) {
            return true;
        }
        return false;
    }

    @Override
    public Reader getContent(String filename) {
        if (config.javascript != null && filename.equals(HANDLE_MINTING_CONFIG_MODULE_ID)) {
            return new StringReader(config.javascript);
        }
        return null;
    }

    public HandleValue[] generate(CordraObject cordraObject) throws HandleException {
        if (config.javascript != null) {
            try {
                return generateHandleValuesFromJavaScript(cordraObject);
            } catch (Exception e) {
                throw new HandleException(HandleException.INTERNAL_ERROR, e);
            }
        } else if (config.baseUri != null) {
            String locXml = createLocFor(cordraObject);
            HandleValue locationValue = new HandleValue(1, Util.encodeString("10320/loc"), Util.encodeString(locXml));
            return new HandleValue[]{locationValue};
        } else {
            HandleValue value = new HandleValue(1, "DESC", "Server not configured for handle resolution. Please contact the server administrator.");
            return new HandleValue[]{value};
        }
    }

    private HandleValue[] generateHandleValuesFromJavaScript(CordraObject cordraObject) throws ScriptException, InterruptedException {
        JavaScriptRunner runner = javaScriptEnvironment.getRunner(null, null);
        try {
            Object moduleExports = runner.requireById(HANDLE_MINTING_CONFIG_MODULE_ID);
            if (!(moduleExports instanceof JSObject)) return new HandleValue[0];
            Object method = ((JSObject) moduleExports).getMember(CREATE_HANDLE_VALUES);
            if (!(method instanceof JSObject)) return new HandleValue[0];
            JSObject obj = runner.jsonParse(gson.toJson(cordraObject));
            JSObject res = (JSObject) runner.submitAndGet(() -> ((JSObject) method).call(null, obj));
            String outputString = runner.jsonStringify(res);
            return gson.fromJson(outputString, HandleValue[].class);
        } finally {
            javaScriptEnvironment.recycle(runner);
        }
    }

    private String createLocFor(CordraObject cordraObject) {
        String baseUri = ensureSlash(config.baseUri);
        String id = cordraObject.id;
        String type = cordraObject.type;
        StringBuilder sb = new StringBuilder();
        sb.append("<locations>\n");
        List<LinkConfig> links = getConfigForObjectType(config, type);
        for (LinkConfig link : links) {
            String href = "";
            String weight = "0";
            if (link.primary) {
                weight = "1";
            }
            String view = "";
            if ("json".equals(link.type)) {
                href = baseUri + "objects/" + StringUtils.encodeURLPath(id);
                view = "json";
                String line = "<location href=\"" + href + "\" weight=\"" + weight + "\" view=\"" + view + "\" />\n";
                sb.append(line);
            } else if ("ui".equals(link.type)) {
                href = baseUri + "#objects/" + StringUtils.encodeURLPath(id);
                view = "ui";
                String line = "<location href=\"" + href + "\" weight=\"" + weight + "\" view=\"" + view + "\" />\n";
                sb.append(line);
            } else if ("payload".equals(link.type)) {
                if (link.all != null && link.all == true) {
                    List<Payload> elements = cordraObject.payloads;
                    for (Payload element : elements) {
                        String line = getLocationForPayload(element, baseUri, weight, id);
                        sb.append(line);
                    }
                } else if (link.specific != null) {
                    Payload element = cordraObject.payloads.stream()
                            .filter(p -> p.name.equals(link.specific))
                            .findFirst().orElse(null);
                    if (element == null) continue;
                    String line = getLocationForPayload(element, baseUri, weight, id);
                    sb.append(line);
                }
            } else if ("url".equals(link.type)) {
                if (link.specific != null) {
                    String url = getJsonAtPointer(link.specific, cordraObject.content);
                    if (url == null) continue;
                    view = link.specific;
                    String line = "<location href=\"" + url + "\" weight=\"" + weight + "\" view=\"" + view + "\" />\n";
                    sb.append(line);
                }
            } else {
                continue;
            }
        }
        sb.append("</locations>");
        return sb.toString();
    }

    private static String ensureSlash(String s) {
        if (s == null) return null;
        if (s.endsWith("/")) return s;
        return s + "/";
    }

    public static List<LinkConfig> getConfigForObjectType(HandleMintingConfig config, String type) {
        if (config.schemaSpecificLinks == null) {
            return config.defaultLinks;
        } else {
            List<LinkConfig> result = config.schemaSpecificLinks.get(type);
            if (result == null) {
                return config.defaultLinks;
            } else {
                return result;
            }
        }
    }

    public static String getLocationForPayload(Payload element, String baseUri, String weight, String id) {
        String payloadName = element.name;
        String href = baseUri + "objects/" + StringUtils.encodeURLPath(id) + "?payload=" + StringUtils.encodeURLPath(payloadName);
        String view = payloadName;
        String line = "<location href=\"" + href + "\" weight=\"" + weight + "\" view=\"" + view + "\" />\n";
        return line;
    }

    private String getJsonAtPointer(String jsonPointer, JsonElement content) {
        if (jsonPointer.isEmpty()) return content.getAsString();
        if (!jsonPointer.startsWith("/")) return null;
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
        return content.getAsString();
    }


}
