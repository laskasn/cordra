package net.cnri.cordra.util;

import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonLoader;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;

/**
 * Utilities involving JSON schemas.
 */
public class JsonSchemaUtil {
    /**
     * Returns a parsed JSON schema given the string content of the schema.
     */
    public static JsonSchema parseJsonSchema(JsonSchemaFactory factory, String schemaString) throws IOException, ProcessingException {
        JsonNode schemaNode = JsonLoader.fromString(schemaString);
        JsonSchema schema = factory.getJsonSchema(schemaNode);
        return schema;
    }
}
