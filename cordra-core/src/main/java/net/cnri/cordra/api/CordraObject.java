/*************************************************************************\
    Copyright (c) 2019 Corporation for National Research Initiatives;
                        All rights reserved.
\*************************************************************************/

package net.cnri.cordra.api;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class CordraObject {

    // Changes to CordraObject need to be reflected in AttributesUtil to continue to work with legacy storage

    public String id;
    public String type;
    public JsonElement content;
    public AccessControlList acl;
    public JsonObject userMetadata;
    public Metadata metadata;
    public JsonObject responseContext;
    public List<Payload> payloads;

    private transient List<String> payloadsToDelete = new ArrayList<>();

    public CordraObject() { }

    public CordraObject(String type, String json) {
        this.type = type;
        setContent(json);
    }

    public CordraObject(String type, JsonElement content) {
        this.type = type;
        this.content = content;
    }

    public CordraObject(String type, Object object) {
        this.type = type;
        Gson gson = new Gson();
        this.content = gson.toJsonTree(object);
    }

    public void setContent(String json) {
        content = new JsonParser().parse(json);
    }

    public void setContent(Object object) {
        Gson gson = new Gson();
        this.content = gson.toJsonTree(object);
    }

    public String getContentAsString() {
        Gson gson = new Gson();
        return gson.toJson(content);
    }

    public <T> T getContent(Class<T> klass) {
        Gson gson = new Gson();
        return gson.fromJson(content, klass);
    }

    public void addPayload(String name, String filename, String mediaType, InputStream in) {
        if (payloads == null) payloads = new ArrayList<>();
        Payload p = new Payload();
        p.name = name;
        p.filename = filename;
        p.mediaType = mediaType;
        p.setInputStream(in);
        payloads.add(p);
    }

    public void deletePayload(String name) {
        payloadsToDelete.add(name);
        removePayloadFromList(name);
    }

    public List<String> getPayloadsToDelete() {
        return payloadsToDelete;
    }

    public void clearPayloadsToDelete() {
        payloadsToDelete = new ArrayList<>();
    }

    private void removePayloadFromList(String name) {
        if (payloads == null) return;
        payloads.removeIf(p -> p.name.equals(name));
        if (payloads.isEmpty()) payloads = null;
    }

    public static class AccessControlList {
        public List<String> readers;
        public List<String> writers;
    }

    public static class Metadata {
        public JsonObject hashes;
        public long createdOn;
        public String createdBy;
        public long modifiedOn;
        public String modifiedBy;
        public Boolean isVersion;
        public String versionOf;
        public String publishedBy;
        public Long publishedOn;
        public String remoteRepository;

        public Long txnId;

        public JsonObject internalMetadata;
    }
}
