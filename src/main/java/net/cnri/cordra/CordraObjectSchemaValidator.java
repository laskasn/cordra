package net.cnri.cordra;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchema;
import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.api.CordraObject;
import net.cnri.cordra.api.Payload;
import net.cnri.cordra.api.SearchResults;

import java.util.*;
import java.util.stream.Collectors;

public class CordraObjectSchemaValidator {

    private CordraService cordra;

    public CordraObjectSchemaValidator(CordraService cordra) {
        this.cordra = cordra;
    }

    public Map<String, JsonNode> schemaValidateAndReturnKeywordsMap(JsonNode dataNode, JsonNode schemaJson, JsonSchema schema) throws InvalidException {
        if (schema == null) throw new InvalidException("Null schema");
        ProcessingReport report = validateJson(dataNode, schema);
        if (!report.isSuccess()) {
            throw new InvalidException(report);
        }
        return SchemaExtractor.extract(report, schemaJson);
    }

    public void postSchemaValidate(JsonNode dataNode, Map<String, JsonNode> pointerToSchemaMap) throws InvalidException, CordraException {
        validateHandleReferencesBatch(dataNode, pointerToSchemaMap);
    }

    public boolean hasJavaScriptModules(Map<String, JsonNode> pointerToSchemaMap) {
        for (JsonNode subSchema : pointerToSchemaMap.values()) {
            if (SchemaUtil.isPathForScriptsInPayloads(subSchema)) return true;
        }
        return false;
    }

    void validateHandleReferencesBatch(JsonNode dataNode, Map<String, JsonNode> pointerToSchemaMap) throws InvalidException, CordraException {
        List<HandleReferenceValidationData> validationDataList = new ArrayList<>();
        for (Map.Entry<String, JsonNode> entry : pointerToSchemaMap.entrySet()) {
            String jsonPointer = entry.getKey();
            JsonNode subSchema = entry.getValue();
            JsonNode handleReferenceNode = SchemaUtil.getDeepCordraSchemaProperty(subSchema, "type", "handleReference");
            if (handleReferenceNode == null) continue;
            JsonNode handleReferenceTypesNode = handleReferenceNode.get("types");
            JsonNode handleReferenceExcludeTypesNode = handleReferenceNode.get("excludeTypes");

            List<String> referenceTypes = getHandleReferenceTypes(handleReferenceTypesNode);
            List<String> referenceExcludeTypes = getHandleReferenceTypes(handleReferenceExcludeTypesNode);

            JsonNode referenceNode = dataNode.at(jsonPointer);
            if (referenceNode == null) throw new InvalidException("Unexpected missing handle reference node " + jsonPointer);
            String handle = SchemaUtil.getHandleForReference(referenceNode, handleReferenceNode, cordra.getDesign().handleMintingConfig.prefix);
            if (handle == null) {
                throw new InvalidException("Unexpected missing handle reference node " + jsonPointer);
            }
            HandleReferenceValidationData validationData = new HandleReferenceValidationData(handle, referenceTypes, referenceExcludeTypes, jsonPointer);
            validationDataList.add(validationData);
        }
        getExistsAndType(validationDataList);
        for (HandleReferenceValidationData validationData : validationDataList) {
            validateHandleReference(validationData);
        }
    }

    private void getExistsAndType(List<HandleReferenceValidationData> validationDataList) throws CordraException {
        List<String> handles = validationDataList.stream().map(d -> d.handle).collect(Collectors.toList());

        Map<String, CordraObject> resultsMap = new HashMap<>();
        try (SearchResults<CordraObject> results = cordra.getObjects(handles)) {
            for (CordraObject co : results) {
                resultsMap.put(co.id, co);
            }
            for (HandleReferenceValidationData validationData : validationDataList) {
                CordraObject co = resultsMap.get(validationData.handle);
                if (co != null) {
                    validationData.exists = true;
                    validationData.type = co.type;
                }
            }
        }
    }

    public List<String> getHandleReferenceTypes(JsonNode handleReferenceTypeNode) {
        List<String> result = new ArrayList<>();
        if (handleReferenceTypeNode == null) {
            return result;
        }
        if (handleReferenceTypeNode.isTextual()) {
            String handleReferenceType = handleReferenceTypeNode.asText(null);
            result.add(handleReferenceType);
        } else if (handleReferenceTypeNode.isArray()) {
            Iterator<JsonNode> iter = handleReferenceTypeNode.elements();
            while (iter.hasNext()) {
                JsonNode current = iter.next();
                if (current.isTextual()) {
                    String handleReferenceType = current.asText(null);
                    result.add(handleReferenceType);
                }
            }
        }
        return result;
    }

    public static class HandleReferenceValidationData {
        public String handle;
        public String type;
        public boolean exists = false;
        public List<String> handleReferenceTypes;
        public List<String> handleReferenceExcludeTypes;
        public String jsonPointer;

        public HandleReferenceValidationData(String handle, List<String> handleReferenceTypes, List<String> handleReferenceExcludeTypes, String jsonPointer) {
            this.handle = handle;
            this.handleReferenceTypes = handleReferenceTypes;
            this.handleReferenceExcludeTypes = handleReferenceExcludeTypes;
            this.jsonPointer = jsonPointer;
        }
    }

    void validateHandleReference(HandleReferenceValidationData validationData) throws InvalidException {
        if (!validationData.exists) throw new InvalidException("Unexpected missing cordra object " + validationData.handle + " at " + validationData.jsonPointer);
        if (validationData.type == null) {
            throw new InvalidException("Cordra object " + validationData.handle + " referenced at " + validationData.jsonPointer + " is missing type");
        }
        if (validationData.handleReferenceExcludeTypes != null) {
            if (validationData.handleReferenceExcludeTypes.contains(validationData.type)) {
                throw new InvalidException("Cordra object " + validationData.handle + " referenced at " + validationData.jsonPointer + " has type " + validationData.type + " which is not permitted.");
            }
        }
        if (validationData.handleReferenceTypes != null && !validationData.handleReferenceTypes.isEmpty()) {
            if (!validationData.handleReferenceTypes.contains(validationData.type)) {
                throw new InvalidException("Cordra object " + validationData.handle + " referenced at " + validationData.jsonPointer + " has type " + validationData.type + ", expected " + validationData.handleReferenceTypes);
            }
        }
    }

    ProcessingReport validateJson(JsonNode dataNode, JsonSchema schema) throws InvalidException {
        try {
            return schema.validate(dataNode);
        } catch (ProcessingException e) {
            throw new InvalidException(e);
        }
    }

    public void validatePayloads(List<Payload> payloads) throws InvalidException {
        if (payloads == null) return;
        Set<String> seenNames = new HashSet<>();
        for (Payload payload : payloads) {
            if (payload.name == null || payload.name.isEmpty()) {
                String filename = payload.filename;
                if (filename != null && !filename.isEmpty()) {
                    throw new InvalidException("Payload for filename " + filename + " missing name");
                } else {
                    throw new InvalidException("Payload missing name");
                }
            }
            if (seenNames.contains(payload.name)) {
                throw new InvalidException("Duplicate payload " + payload.name);
            } else {
                seenNames.add(payload.name);
            }
        }
    }

//    public static Set<String> getPayloadPointers(Map<String, JsonNode> keywordsMap) {
//        if (keywordsMap == null) return Collections.emptySet();
//        Set<String> res = new HashSet<String>();
//        for (Map.Entry<String, JsonNode> entry : keywordsMap.entrySet()) {
//            JsonNode subSchema = entry.getValue();
//            if (SchemaUtil.getDeepCordraSchemaProperty(subSchema, "type", "payload") != null) {
//                String jsonPointer = entry.getKey();
//                res.add(jsonPointer);
//                continue;
//            }
////
////            JsonNode formatNode = subSchema.get("format");
////            if (formatNode == null) continue;
////            String format = formatNode.asText();
////            if ("file".equals(format)) {
////                String jsonPointer = entry.getKey();
////                res.add(jsonPointer);
////            }
//        }
//        return res;
//    }

}
