package net.cnri.cordra.util;

import java.io.IOException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.jackson.JsonLoader;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.report.LogLevel;
import com.github.fge.jsonschema.core.report.ProcessingMessage;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchema;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * A tool for taking a JSON object and removing all properties not mentioned in a given schema;
 * also allows removing specified top-level properties.
 */
public class JsonPruner {

    private  static JsonNode pruneToMatchSchemaHelper(JsonElement obj, JsonSchema schema) throws IOException, ProcessingException {
        JsonNode node = JsonLoader.fromString(obj.toString());
        ProcessingReport report = schema.validate(node, true);
        for (ProcessingMessage msg : report) {
            if (msg.getLogLevel() == LogLevel.INFO && "net.cnri.additionalProperties".equals(msg.getMessage())) {
                JsonNode msgNode = msg.asJson();
                if (!msgNode.has("instance")) continue;
                JsonNode instanceNode = msgNode.get("instance");
                if (!instanceNode.has("pointer")) continue;
                String pointer = instanceNode.get("pointer").asText(null);
                if (pointer == null) continue;
                if (!msgNode.has("additionalProperties")) continue;
                JsonNode parentNode = node.at(pointer);
                if (parentNode == null || parentNode.isMissingNode()) continue;
                msgNode.get("additionalProperties").elements()
                .forEachRemaining(propNameNode -> {
                    ((ObjectNode)parentNode).remove(propNameNode.asText());
                });
            }
        }
        return node;
    }

    /**
     * Constructs a new JsonElement which is obtained by removing all properties from the given object
     * which are not specified in the given schema.
     */
    public static JsonElement pruneToMatchSchema(JsonElement obj, JsonSchema schema) throws IOException, ProcessingException {
        JsonNode node = pruneToMatchSchemaHelper(obj, schema);
        return new JsonParser().parse(node.toString());
    }

    /**
     * Constructs a new JsonElement which is obtained by removing all properties from the given object
     * which are not specified in the given schema, along with additional specified top-level properties.
     */
    public static JsonElement pruneToMatchSchemaWithoutProperties(JsonElement obj, JsonSchema schema, String... propertiesToRemove) throws IOException, ProcessingException {
        JsonNode node = pruneToMatchSchemaHelper(obj, schema);
        if (node instanceof ObjectNode) {
            for (String property : propertiesToRemove) {
                ((ObjectNode)node).remove(property);
            }
        }
        return new JsonParser().parse(node.toString());
    }

    /**
     * Constructs a new JsonObject which is obtained by removing specified top-level properties from the given object.
     */
    public static JsonObject withoutProperties(JsonObject obj, String... propertiesToRemove) {
        JsonObject clone = obj.deepCopy();
        for (String property : propertiesToRemove) {
            clone.remove(property);
        }
        return clone;
    }
}
