package net.cnri.cordra;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonschema.main.JsonSchema;

public class SchemaSchemaFactory {
    static final JsonNode schemaSchemaNode;
    static final JsonSchema schemaSchema;

    static {
        try {
            String schemaSchemaString = DefaultSchemasFactory.getSchemaSchema();
            schemaSchemaNode = JsonUtil.parseJson(schemaSchemaString);
            schemaSchema = JsonUtil.parseJsonSchema(JsonSchemaFactoryFactory.newJsonSchemaFactory(), schemaSchemaNode);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public static JsonNode getNode() {
        return schemaSchemaNode;
    }

    public static JsonSchema getSchema() {
        return schemaSchema;
    }
}
