package net.cnri.cordra;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonschema.main.JsonSchema;

public class CordraDesignSchemaFactory {
    static final JsonNode cordraDesignSchemaNode;
    static final JsonSchema cordraDesignSchema;

    static {
        try {
            String cordraDesignSchemaString = DefaultSchemasFactory.getCordraDesignSchema();
            cordraDesignSchemaNode = JsonUtil.parseJson(cordraDesignSchemaString);
            cordraDesignSchema = JsonUtil.parseJsonSchema(JsonSchemaFactoryFactory.newJsonSchemaFactory(), cordraDesignSchemaNode);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public static JsonNode getNode() {
        return cordraDesignSchemaNode;
    }

    public static JsonSchema getSchema() {
        return cordraDesignSchema;
    }
}
