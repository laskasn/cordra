package net.cnri.cordra.relationships;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.cnri.cordra.CordraService;
import net.cnri.cordra.InvalidException;
import net.cnri.cordra.JsonUtil;
import net.cnri.cordra.SchemaUtil;
import net.cnri.cordra.auth.QueryRestrictor;
import net.cnri.cordra.model.SearchResult;
import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.api.CordraObject;
import net.cnri.cordra.api.NotFoundCordraException;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.gson.JsonElement;

public class RelationshipsService {
    private static Logger logger = LoggerFactory.getLogger(new Object() { }.getClass().getEnclosingClass());

    private final CordraService cordra;

    public RelationshipsService(CordraService cordra) {
        this.cordra = cordra;
    }

    public Relationships getRelationshipsFor(String objectId, boolean outboundOnly, String userId, boolean hasUserObject) throws CordraException, InvalidException {
        cordra.ensureIndexUpToDate();
        String queryString = "internal.pointsAt:"+objectId;
        List<String> groupIds = cordra.getAclEnforcer().getGroupsForUser(userId);
        boolean excludeVersions = true;
        String  whoPointsAtObject = QueryRestrictor.restrict(queryString, userId, hasUserObject, groupIds, cordra.getDesign().authConfig, excludeVersions);

        List<Edge> edges = new ArrayList<>();
        List<Node> nodes = new ArrayList<>();
        Map<String, SearchResult> results = new HashMap<>();

        Node selfNode = new Node(objectId, objectId);
        nodes.add(selfNode);

        CordraObject selfObject = cordra.getCordraObject(objectId);
        results.put(objectId, searchResultFor(selfObject));

        if (!outboundOnly) {
            Set<CordraObject> pointersToObjectSet = cordra.searchRepo(whoPointsAtObject)
                .stream()
                .collect(Collectors.toSet());
            for (CordraObject co : pointersToObjectSet) {
                String id = co.id;
                String type = co.type;
                Node node = new Node();
                node.setId(id);
                nodes.add(node);

                for (String jsonPointer : getJsonPointersTo(objectId, type, co.content)) {
                    Edge edge = new Edge();
                    edge.setFrom(id);
                    edge.setTo(objectId);
                    edge.setJsonPointer(jsonPointer);
                    edges.add(edge);
                }
                results.put(id, searchResultFor(co));
            }
        }

        List<ObjectPointer> pointersFromObjectList = pointedAtIds(objectId);
        for (ObjectPointer objectPointer : pointersFromObjectList) {
            String id = objectPointer.objectId;
            if (!results.containsKey(id)) {
                try {
                    CordraObject co = cordra.getCordraObject(id);
                    if (!cordra.getAclEnforcer().canRead(userId, hasUserObject, co)) {
                        continue;
                    }
                    results.put(id, searchResultFor(co));
                } catch (NotFoundCordraException e) {
                    //Someone deleted an object that the focus object was pointing at. Don't include it in the results.
                    continue;
                }
                Node node = new Node();
                node.setId(id);
                nodes.add(node);
            }
            Edge edge = new Edge();
            edge.setTo(id);
            edge.setFrom(objectId);
            edge.setJsonPointer(objectPointer.jsonPointer);
            edges.add(edge);
        }
        return new Relationships(nodes, edges, results);
    }

    private static SearchResult searchResultFor(CordraObject co) {
        return new SearchResult(co.id, co.type, co.content, co.metadata.createdOn, co.metadata.createdBy);
    }

    public List<ObjectPointer> pointedAtIds(String objectId, String objectType, JsonNode jsonNode, String handleMintingConfigPrefix) throws InvalidException {
        Map<String, JsonNode> pointerToSchemaMap = cordra.getPointerToSchemaMap(objectType, jsonNode);
        return pointedAtIds(objectId, jsonNode, pointerToSchemaMap, handleMintingConfigPrefix);
    }

    public static List<ObjectPointer> pointedAtIds(String objectId, JsonNode jsonNode, Map<String, JsonNode> pointerToSchemaMap, String handleMintingConfigPrefix) {
        List<ObjectPointer> pointedAtIds = new ArrayList<>();
        for (Map.Entry<String, JsonNode> entry : pointerToSchemaMap.entrySet()) {
            String jsonPointer = entry.getKey();
            JsonNode subSchema = entry.getValue();
            JsonNode handleReferenceNode = SchemaUtil.getDeepCordraSchemaProperty(subSchema, "type", "handleReference");
            if (handleReferenceNode == null) continue;
            JsonNode handleReferenceTypeNode = handleReferenceNode.get("types");
            if (handleReferenceTypeNode == null) continue;
            if (!handleReferenceTypeNode.isTextual() && !handleReferenceTypeNode.isArray()) continue;

            JsonNode referenceNode = jsonNode.at(jsonPointer);
            if (referenceNode == null) {
                logger.warn("Unexpected missing handle reference node " + jsonPointer + " in " + objectId);
            } else {
                String handle = SchemaUtil.getHandleForReference(referenceNode, handleReferenceNode, handleMintingConfigPrefix);
                pointedAtIds.add(new ObjectPointer(handle, jsonPointer));
            }
        }
        return pointedAtIds;
    }

    private List<ObjectPointer> pointedAtIds(String objectId) throws CordraException, InvalidException {
        CordraObject co = cordra.getCordraObject(objectId);
        String objectType = co.type;
        if (objectType == null) {
            throw new NotFoundCordraException(objectId);
        }
        if (co.content == null) {
            throw new NotFoundCordraException(objectId);
        }
        JsonNode jsonNode = JsonUtil.gsonToJackson(co.content);
        return pointedAtIds(objectId, objectType, jsonNode, cordra.getDesign().handleMintingConfig.prefix);
    }

    public static class ObjectPointer {
        public String objectId;
        public String jsonPointer;

        public ObjectPointer(String objectId, String jsonPointer) {
            this.objectId = objectId;
            this.jsonPointer = jsonPointer;
        }
    }

    // returns all the json pointers in co that are handleReferenceName pointing to objectId
    List<String> getJsonPointersTo(String objectId, String type, JsonElement json) throws InvalidException {
        if (type == null || json == null) return Collections.emptyList();
        JsonNode jsonNode = JsonUtil.gsonToJackson(json);
        List<String> res = new ArrayList<>();
        Map<String, JsonNode> pointerToSchemaMap = cordra.getPointerToSchemaMap(type, jsonNode);
        for (Map.Entry<String, JsonNode> entry : pointerToSchemaMap.entrySet()) {
            String jsonPointer = entry.getKey();
            JsonNode subSchema = entry.getValue();
            JsonNode handleReferenceNode = SchemaUtil.getDeepCordraSchemaProperty(subSchema, "type", "handleReference");
            if (handleReferenceNode == null) continue;
            JsonNode referenceNode = jsonNode.at(jsonPointer);
            if (referenceNode == null) {
                logger.warn("Unexpected missing handleReferenceType node " + jsonPointer);
            } else {
                String handle = SchemaUtil.getHandleForReference(referenceNode, handleReferenceNode, cordra.getDesign().handleMintingConfig.prefix);
                if (objectId.equals(handle)) {
                     res.add(jsonPointer);
                 }
            }
        }
        return res;
    }
}
