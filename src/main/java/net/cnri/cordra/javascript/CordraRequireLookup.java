package net.cnri.cordra.javascript;

import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;

import net.cnri.cordra.CordraService;
import net.cnri.cordra.InvalidException;
import net.cnri.cordra.JsonUtil;
import net.cnri.cordra.SchemaUtil;
import net.cnri.cordra.api.InternalErrorCordraException;
import net.cnri.cordra.api.Payload;
import net.cnri.cordra.api.SearchResults;
import net.cnri.cordra.api.UncheckedCordraException;
import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.api.CordraObject;
import net.cnri.util.javascript.RequireLookup;

public class CordraRequireLookup implements RequireLookup, ModuleCordraObjectCache {
    public static final String HANDLE_MINTING_CONFIG_MODULE_ID = "/cordra/handle-minting-config";
    public static final String DESIGN_MODULE_ID = "/cordra/design";

    private final CordraService cordraService;
    private final ConcurrentMap<String, List<String>> objectIdsForModule = new ConcurrentHashMap<>();
    private volatile ConcurrentMap<String, String> schemas = new ConcurrentHashMap<>();
    private volatile ConcurrentMap<String, String> schemaJavaScripts = new ConcurrentHashMap<>();
    private volatile String handleJavaScript;
    private volatile String designJavaScript;

    public CordraRequireLookup(CordraService cordraService) {
        this.cordraService = cordraService;
    }

    public void putSchemaJavaScript(String type, String js) {
        if (js == null) schemaJavaScripts.remove(type);
        else schemaJavaScripts.put(type, js);
    }

    public void putSchema(String type, String schema) {
        if (schema == null) schemas.remove(type);
        else schemas.put(type, schema);
    }

    public String getSchemaJavaScript(String type) {
        return schemaJavaScripts.get(type);
    }

    public String getSchema(String type) {
        return schemas.get(type);
    }

    public ConcurrentMap<String, String> getAllSchemaJavaScripts() {
        return schemaJavaScripts;
    }

    public ConcurrentMap<String, String> getAllSchemas() {
        return schemas;
    }

    public void replaceAllSchemaJavaScript(ConcurrentMap<String, String> newSchemaJavaScripts) {
        this.schemaJavaScripts = newSchemaJavaScripts;
    }

    public void replaceAllSchemas(ConcurrentMap<String, String> newSchemas) {
        this.schemas = newSchemas;
    }

    public void removeSchemaJavaScript(String type) {
        schemaJavaScripts.remove(type);
    }

    public void removeSchema(String type) {
        schemas.remove(type);
    }

    public void setHandleJavaScript(String js) {
        handleJavaScript = js;
    }

    public String getHandleJavaScript() {
        return handleJavaScript;
    }

    public void setDesignJavaScript(String js) {
        designJavaScript = js;
    }

    public String getDesignJavaScript() {
        return designJavaScript;
    }

    public static String moduleIdForSchemaType(String type) {
        return "/cordra/schemas/" + type;
    }

    @Override
    public boolean exists(String filename) {
        if (filename.equals("cordra")) {
            return true;
        } else if (filename.equals("cordraUtil")) {
            return true;
        } else if (filename.startsWith("/cordra/schemas/")) {
            String type = filename.substring("/cordra/schemas/".length());
            if (type.endsWith(".schema.json")) {
                type = type.substring(0, type.length() - ".schema.json".length());
                return schemas.get(type) != null;
            } else {
                String schemaJavaScript = schemaJavaScripts.get(type);
                return schemaJavaScript != null;
            }
        } else if (filename.equals(HANDLE_MINTING_CONFIG_MODULE_ID)) {
            return handleJavaScript != null;
        } else if (filename.equals(DESIGN_MODULE_ID)) {
            return designJavaScript != null;
        } else {
            Collection<String> cachedObjectIds = getObjectIdsForModule(filename);
            if (cachedObjectIds != null) {
                return !cachedObjectIds.isEmpty();
            }
            try {
                cordraService.ensureIndexUpToDate();
                try (SearchResults<CordraObject> results = cordraService.searchRepo("javaScriptModuleName:\"" + quote(filename) + "\"")) {
                    List<String> objectIds = results.stream().map(co -> co.id).collect(Collectors.toList());
                    setObjectIdsForModule(filename, objectIds);
                    return !objectIds.isEmpty();
                }
            } catch (CordraException e) {
                throw new UncheckedCordraException(e);
            }
        }
    }

    @Override
    public Reader getContent(String filename) {
        if (filename.equals("cordra")) {
            return new InputStreamReader(getClass().getResourceAsStream("cordra.js"), StandardCharsets.UTF_8);
        } else if (filename.equals("cordraUtil")) {
                return new InputStreamReader(getClass().getResourceAsStream("cordraUtil.js"), StandardCharsets.UTF_8);
        } else if (filename.startsWith("/cordra/schemas/")) {
            String type = filename.substring("/cordra/schemas/".length());
            if (type.endsWith(".schema.json")) {
                type = type.substring(0, type.length() - ".schema.json".length());
                String schema = schemas.get(type);
                if (schema == null) return null;
                return new StringReader(schema);
             } else {
                String schemaJavaScript = schemaJavaScripts.get(type);
                if (schemaJavaScript == null) return null;
                return new StringReader(schemaJavaScript);
            }
        } else if (filename.equals(HANDLE_MINTING_CONFIG_MODULE_ID)) {
            if (handleJavaScript == null) return null;
            return new StringReader(handleJavaScript);
        } else if (filename.equals(DESIGN_MODULE_ID)) {
            if (designJavaScript == null) return null;
            return new StringReader(designJavaScript);
        } else {
            try {
                Collection<String> cachedObjectIds = getObjectIdsForModule(filename);
                if (cachedObjectIds != null) {
                    for (String objectId : cachedObjectIds) {
                        CordraObject co = cordraService.getCordraObject(objectId);
                        Reader res = findModuleInCordraObject(filename, co);
                        if (res != null) return res;
                    }
                }
                cordraService.ensureIndexUpToDate();
                Reader res = null;
                List<String> objectIds = new ArrayList<>();
                try (SearchResults<CordraObject> results = cordraService.searchRepo("javaScriptModuleName:\"" + quote(filename) + "\"")) {
                    for (CordraObject co : results) {
                        objectIds.add(co.id);
                        if (res == null) res = findModuleInCordraObject(filename, co);
                    }
                }
                setObjectIdsForModule(filename, objectIds);
                return res;
            } catch (CordraException e) {
                throw new UncheckedCordraException(e);
            }
        }
    }

    private Reader findModuleInCordraObject(String filename, CordraObject co) throws CordraException {
        List<String> directoryNames = getDirectoryNames(co);
        if (co.payloads != null) {
            for (Payload payload : co.payloads) {
                for (String directoryName : directoryNames) {
                    String moduleName = Paths.get("/" + directoryName).resolve(payload.name).normalize().toString();
                    if (filename.equals(moduleName)) {
                        // can't read from the search result object, so go back to the source
                        return new InputStreamReader(cordraService.readPayload(co.id, payload.name), StandardCharsets.UTF_8);
                    }
                }
            }
        }
        return null;
    }

    private List<String> getDirectoryNames(CordraObject co) throws CordraException {
        String type = co.type;
        if (co.content == null) throw new InternalErrorCordraException("Missing JSON attribute on " + co.id);
        try {
            JsonNode jsonNode = JsonUtil.gsonToJackson(co.content);
            Map<String, JsonNode> pointerToSchemaMap = cordraService.getPointerToSchemaMap(type, jsonNode);
            List<String> directoryNames = new ArrayList<>();
            for (Map.Entry<String, JsonNode> entry : pointerToSchemaMap.entrySet()) {
                String jsonPointer = entry.getKey();
                JsonNode subSchema = entry.getValue();
                if (!SchemaUtil.isPathForScriptsInPayloads(subSchema)) continue;
                JsonNode directoryNode = jsonNode.at(jsonPointer);
                if (directoryNode == null || !directoryNode.isTextual()) {
                    continue;
                } else {
                    directoryNames.add(directoryNode.asText());
                }
            }
            return directoryNames;
        } catch (InvalidException e) {
            throw new InternalErrorCordraException("Unexpected invalid json on " + co.id);
        }
    }

    @Override
    public Collection<String> getObjectIdsForModule(String module) {
        return objectIdsForModule.get(module);
    }

    @Override
    public void setObjectIdsForModule(String module, Collection<String> objectIds) {
        objectIdsForModule.put(module, new ArrayList<>(objectIds));
    }

    @Override
    public void clearObjectIdsForModule(String module) {
        objectIdsForModule.remove(module);
    }

    @Override
    public void clearAllObjectIdsForModuleValues() {
        objectIdsForModule.clear();
    }

    public static String quote(String s) {
        s = s.trim();
        if (s.startsWith("\"") && s.endsWith("\"")) return s;
        boolean quoted = true;
        for (int i = 0; i < s.length(); i++) {
            if (quoted) {
                quoted = false;
            } else {
                if (' ' == s.charAt(i)) return "\"" + s.replace("\\","\\\\").replace("\"", "\\\"") + "\"";
                else if ('\\' == s.charAt(i)) quoted = true;
            }
        }
        return s;
    }
}
