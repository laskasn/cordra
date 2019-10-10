package net.cnri.cordra.model;

import com.google.gson.JsonElement;

public class SearchResult {
    String id;
    String type;
    JsonElement json;
    long createdOn;
    String createdBy;

    public SearchResult(String id, String type, JsonElement json, long createdOn, String createdBy) {
        this.id = id;
        this.type = type;
        this.json = json;
        this.createdOn = createdOn;
        this.createdBy = createdBy;
    }

    public SearchResult(String id, String type, JsonElement json) {
        this.id = id;
        this.type = type;
        this.json = json;
    }

    public SearchResult(String id) {
        this.id = id;
    }
}
