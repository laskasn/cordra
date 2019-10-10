package net.cnri.cordra;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

public class DesignPlusSchemas extends Design {

    public Map<String, String> schemaIds;
    public Map<String, JsonNode> schemas; // map from name to schema json

    public DesignPlusSchemas(Map<String, JsonNode> schemas, Design design, Map<String, String> schemaIds) {
        super(design);
        this.schemaIds = schemaIds;
        this.schemas = schemas;
    }
}
