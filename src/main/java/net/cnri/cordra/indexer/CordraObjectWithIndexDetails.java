package net.cnri.cordra.indexer;

import com.fasterxml.jackson.databind.JsonNode;
import net.cnri.cordra.api.CordraObject;

import java.util.Map;

public class CordraObjectWithIndexDetails {

    public CordraObject co;
    public Map<String, JsonNode> pointerToSchemaMap;
    public boolean indexPayloads = true;

    public CordraObjectWithIndexDetails(CordraObject co, Map<String, JsonNode> pointerToSchemaMap, boolean indexPayloads) {
        this.co = co;
        this.pointerToSchemaMap = pointerToSchemaMap;
        this.indexPayloads = indexPayloads;
    }
}
