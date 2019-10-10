package net.cnri.cordra;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonschema.core.report.LogLevel;
import com.github.fge.jsonschema.core.report.ProcessingMessage;
import com.github.fge.jsonschema.core.report.ProcessingReport;

import java.util.HashMap;
import java.util.Map;

public class SchemaExtractor {

    public static Map<String, JsonNode> extract(ProcessingReport report, JsonNode schema) {
        Map<String, JsonNode> schemas = new HashMap<>();
        for (ProcessingMessage msg : report) {
            if (msg.getLogLevel() == LogLevel.INFO && "net.cnri.message".equals(msg.getMessage())) {
                JsonNode node = msg.asJson();
                String keyword = node.get("keyword").asText(null);
                if (keyword == null) continue;
                if ("format".equals(keyword)) {
                    keyword = node.get("attribute").asText(null);
                    if (keyword == null) continue;
                }
                String pointer = node.get("instance").get("pointer").asText(null);
                if (pointer == null) continue;
                String schemaPointer = node.get("schema").get("pointer").asText(null);
                if (schemaPointer == null) continue;

                JsonNode fieldNameNode = node.get("fieldName");
                if (fieldNameNode != null) {
                    pointer += fieldNameNode.asText("");
                }
                JsonNode fieldPointerNode = node.get("fieldPointer");
                if (fieldPointerNode != null) {
                    schemaPointer = fieldPointerNode.asText("");
                }

                JsonNode subSchema = JsonUtil.getJsonAtPointer(schemaPointer, schema);
                schemas.put(pointer, subSchema);
            }
        }
        return schemas;
    }

}
