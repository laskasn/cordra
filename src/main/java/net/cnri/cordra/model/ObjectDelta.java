package net.cnri.cordra.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import net.cnri.cordra.GsonUtility;
import net.cnri.cordra.InvalidException;
import net.cnri.cordra.api.CordraObject;
import net.cnri.cordra.api.CordraObject.AccessControlList;
import net.cnri.cordra.api.InternalErrorCordraException;
import net.cnri.cordra.api.Payload;

public class ObjectDelta {
    public String id; // okay to leave null (not okay to change for update)
    public String type; // null to leave unchanged
    public String jsonData; // null to leave unchanged
    public CordraObject.AccessControlList acl; // null to leave unchanged
    public JsonObject userMetadata; // null to leave unchanged
    // note no metadata
    public List<Payload> payloads; // payloads to add, may be empty or null
    public Collection<String> payloadsToDelete; // may be empty to null

    public ObjectDelta(String id, String type, String jsonData, AccessControlList acl, JsonObject userMetadata, List<Payload> payloads, Collection<String> payloadsToDelete) {
        this.id = id;
        this.type = type;
        this.jsonData = jsonData;
        this.acl = acl;
        this.userMetadata = userMetadata;
        this.payloads = payloads;
        this.payloadsToDelete = payloadsToDelete;
    }

    public CordraObject asCordraObjectForCreate() throws InvalidException {
        CordraObject res = new CordraObject();
        res.id = id;
        res.type = type;
        res.content = getContentAsJsonElement();
        res.acl = acl;
        res.userMetadata = userMetadata;
        res.payloads = payloads;
        return res;
    }

    private JsonElement getContentAsJsonElement() throws InvalidException {
        try {
            return new JsonParser().parse(jsonData);
        } catch (JsonParseException e) {
            throw new InvalidException("Unable to parse content", e);
        }
    }

    public CordraObject asCordraObjectForUpdate(CordraObject original) throws InvalidException {
        CordraObject res = new CordraObject();
        if (id != null) {
            res.id = id;
        } else {
            res.id = original.id;
        }
        if (type != null) {
            res.type = type;
        } else {
            res.type = original.type;
        }
        if (jsonData != null) {
            res.content = getContentAsJsonElement();
        } else {
            res.content = new JsonParser().parse(original.content.toString());
        }
        if (acl != null) {
            res.acl = acl;
        } else if (original.acl == null) {
            res.acl = null;
        } else {
            res.acl = new CordraObject.AccessControlList();
            if (original.acl.readers != null) res.acl.readers = new ArrayList<>(original.acl.readers);
            if (original.acl.writers != null) res.acl.writers = new ArrayList<>(original.acl.writers);
        }
        if (userMetadata != null) {
            res.userMetadata = userMetadata;
        } else {
            res.userMetadata = original.userMetadata;
        } 
        if (payloads == null && original.payloads == null) {
            res.payloads = null;
        } else if (original.payloads == null) {
            res.payloads = payloads;
        } else if (payloads == null) {
            res.payloads = clonePayloads(original.payloads);
        } else {
            // original has payloads and some are added/changed
            res.payloads = new ArrayList<>();
            for (Payload payload : original.payloads) {
                Payload newPayload = getPayloadByName(payloads, payload.name);
                if (newPayload != null) res.payloads.add(newPayload);
                else res.payloads.add(clonePayload(payload));
            }
            for (Payload newPayload : payloads) {
                Payload alreadySeen = getPayloadByName(res.payloads, newPayload.name);
                if (alreadySeen == null) res.payloads.add(newPayload);
            }
        }
        if (payloadsToDelete != null) {
            for (String payloadToDelete : payloadsToDelete) {
                res.deletePayload(payloadToDelete);
            }
        }
        return res;
    }

    private static List<Payload> clonePayloads(List<Payload> payloads) {
        return payloads.stream()
            .map(ObjectDelta::clonePayload)
            .collect(Collectors.toList());
    }

    private static Payload clonePayload(Payload payload) {
        Payload res = new Payload();
        res.name = payload.name;
        res.filename = payload.filename;
        res.mediaType = payload.mediaType;
        res.size = payload.size;
        return res;
    }

    private static Payload getPayloadByName(List<Payload> payloads, String name) {
        if (payloads == null) return null;
        for (Payload payload : payloads) {
            if (payload.name.equals(name)) return payload;
        }
        return null;
    }

    public static CordraObject applyPayloadsToCordraObject(CordraObject co, List<Payload> inputPayloads) throws InternalErrorCordraException {
        List<Payload> payloads = new ArrayList<>();
        if (co.payloads != null) {
            for (Payload payload : co.payloads) {
                Payload inputPayload = getPayloadByName(inputPayloads, payload.name);
                if (inputPayload == null) {
                    throw new InternalErrorCordraException("Cannot introduce new payload name " + payload.name);
                }
                inputPayload.filename = payload.filename;
                inputPayload.mediaType = payload.mediaType;
                payloads.add(inputPayload);
            }
        }
        co.payloads = payloads;
        return co;
    }

    public static ObjectDelta fromCordraObjectForCreate(CordraObject co, List<Payload> inputPayloads) throws InternalErrorCordraException {
        co = applyPayloadsToCordraObject(co, inputPayloads);
        if (co.content == null) throw new InternalErrorCordraException("Cannot have null content for creation");
        return new ObjectDelta(co.id, co.type, co.getContentAsString(), co.acl, co.userMetadata, co.payloads, null);
    }

    public static ObjectDelta fromStringifiedCordraObjectForCreate(String json, List<Payload> inputPayloads) throws InternalErrorCordraException {
        CordraObject co;
        try {
            co = GsonUtility.getGson().fromJson(json, CordraObject.class);
        } catch (JsonParseException e) {
            throw new InternalErrorCordraException("Couldn't parse json into CordraObject", e);
        }
        return fromCordraObjectForCreate(co, inputPayloads);
    }

    public static ObjectDelta fromCordraObjectForUpdate(CordraObject original, CordraObject update, List<Payload> inputPayloads) throws InternalErrorCordraException {
        if (update.id != null && !original.id.equals(update.id)) {
            throw new InternalErrorCordraException("Cannot change id of object " + original.id);
        }
        List<String> payloadsToDelete = new ArrayList<>();
        List<Payload> payloads = new ArrayList<>();
        if (update.payloads != null) {
            for (Payload payload : update.payloads) {
                Payload inputPayload = getPayloadByName(inputPayloads, payload.name);
                if (inputPayload != null) {
                    inputPayload.filename = payload.filename;
                    inputPayload.mediaType = payload.mediaType;
                    payloads.add(inputPayload);
                } else {
                    Payload originalPayload = getPayloadByName(original.payloads, payload.name);
                    if (originalPayload == null) {
                        throw new InternalErrorCordraException("Cannot introduce new payload name " + payload.name);
                    } else if (!Objects.equals(originalPayload.filename, payload.filename) ||
                        !Objects.equals(originalPayload.mediaType, payload.mediaType)) {
                        throw new InternalErrorCordraException("Cannot change payload metadata without uploading data for " + payload.name);
                    }
                }
            }
        }
        if (original.payloads != null) {
            for (Payload originalPayload : original.payloads) {
                Payload updatePayload = getPayloadByName(update.payloads, originalPayload.name);
                if (updatePayload == null) {
                    payloadsToDelete.add(originalPayload.name);
                }
            }
        }
        String jsonData;
        if (update.content == null) {
            jsonData = null;
        } else {
            jsonData = update.getContentAsString();
        }
        return new ObjectDelta(update.id, update.type, jsonData, update.acl, update.userMetadata, payloads, payloadsToDelete);
    }

    public static ObjectDelta fromStringifiedCordraObjectForUpdate(CordraObject original, String updateJson, List<Payload> inputPayloads) throws InternalErrorCordraException {
        CordraObject update;
        try {
            update = GsonUtility.getGson().fromJson(updateJson, CordraObject.class);
        } catch (JsonParseException e) {
            throw new InternalErrorCordraException("Couldn't parse json into CordraObject", e);
        }
        return fromCordraObjectForUpdate(original, update, inputPayloads);
    }

}
