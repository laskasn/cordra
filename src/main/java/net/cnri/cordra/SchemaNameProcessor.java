package net.cnri.cordra;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.gson.JsonObject;
import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.api.CordraObject;

public class SchemaNameProcessor {
    private final CordraService cordra;
    
    public SchemaNameProcessor(CordraService cordra) {
        this.cordra = cordra;
    }

    public void preprocess(String type, String handle, JsonNode json) throws CordraException, InvalidException {
        if (!"Schema".equals(type)) return;
        cordra.ensureIndexUpToDate();
        String name = JsonUtil.getJsonAtPointer("/name", json).asText();
        if (!isSchemaNameUnique(handle, name)) {
            throw new InvalidException("Schema name "+name+" is not unique.");
        }
    }
    
    public void process(String type, CordraObject co, JsonNode json) throws CordraException, InvalidException {
        if (!"Schema".equals(type)) return;
        cordra.ensureIndexUpToDate();
        String name = JsonUtil.getJsonAtPointer("/name", json).asText();
        if (!isSchemaNameUnique(co.id, name)) {
            throw new InvalidException("Schema name "+name+" is not unique.");
        }
        if (co.metadata == null) co.metadata = new CordraObject.Metadata();
        if (co.metadata.internalMetadata == null) co.metadata.internalMetadata = new JsonObject();
        co.metadata.internalMetadata.addProperty("schemaName", name);
    }

    private boolean isSchemaNameUnique(String id, String name) throws CordraException {
        if ("Schema".equals(name)) {
            return false;
        }
        String foundId = cordra.idFromType(name);
        return foundId == null || foundId.equals(id);
    }
}
