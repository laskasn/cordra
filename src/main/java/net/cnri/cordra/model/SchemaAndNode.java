package net.cnri.cordra.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonschema.main.JsonSchema;

public class SchemaAndNode {
    public JsonSchema schema;
    public JsonNode schemaNode;

    public SchemaAndNode(JsonSchema schema, JsonNode schemaNode) {
        this.schema = schema;
        this.schemaNode = schemaNode;
    }
}
