package net.cnri.cordra;

import com.google.gson.JsonObject;
import net.cnri.cordra.api.*;
import net.cnri.cordra.auth.AuthConfig;
import net.cnri.cordra.auth.QueryRestrictor;
import net.cnri.cordra.indexer.CordraIndexer;
import net.cnri.cordra.storage.CordraStorage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VersionManager {

    private final CordraStorage storage;
    private final CordraIndexer indexer;
    private final HandleMinter handleMinter;

    public static final String PUBLISHED_BY = "publishedBy";
    public static final String PUBLISHED_ON = "publishedOn";
    public static final String VERSION_OF = "versionOf";
    public static final String IS_VERSION = "isVersion";

    public VersionManager(CordraStorage storage, CordraIndexer indexer, HandleMinter handleMinter) {
        this.storage = storage;
        this.indexer = indexer;
        this.handleMinter = handleMinter;
    }

    public CordraObject publishVersion(String objectId, String versionId, boolean clonePayloads, String userId, long txnId) throws CordraException, VersionException, ConflictCordraException {
        CordraObject co = storage.get(objectId);
        if (co == null) {
            throw new VersionException("Cannot publish a version for an object that does not exist: " + objectId);
        }
        if (!isTipObject(co)) {
            throw new VersionException("Cannot publish a version for an object that is not the tip.");
        }
        if (versionId != null && !versionId.isEmpty() && !CordraService.isValidHandle(versionId)) {
            throw new BadRequestCordraException("Invalid handle: " + versionId);
        }
        CordraObject version = co;
        if (clonePayloads) {
            setInputStreamsForPayloadsOnVersion(objectId, version); //set input streams on co
        } else {
            version.payloads = Collections.emptyList();
        }
        long now = System.currentTimeMillis();
        if (userId == null) {
            userId = "anonymous";
        }
        if (version.metadata == null) version.metadata = new CordraObject.Metadata();
        if (version.metadata.internalMetadata == null) version.metadata.internalMetadata = new JsonObject();
        version.metadata.publishedBy = userId;
        version.metadata.versionOf = objectId;
        version.metadata.publishedOn = now;
        version.metadata.isVersion = true;
        version.metadata.txnId = txnId;

        if (versionId == null || versionId.isEmpty()) {
            while (true) {
                String handle = handleMinter.mintByTimestamp();
                version.id = handle;
                try {
                    return storage.create(version);
                } catch (ConflictCordraException e) {
                    continue;
                }
            }
        } else {
            version.id = versionId;
            return storage.create(version);
        }
    }

    private void setInputStreamsForPayloadsOnVersion(String originalId, CordraObject co) throws CordraException {
        List<Payload> srcElements = co.payloads;
        if (srcElements == null) return;
        for (Payload srcElement : srcElements) {
            srcElement.setInputStream(storage.getPayload(originalId, srcElement.name));
        }
    }

    private boolean isTipObject(CordraObject co) {
        if (co.metadata == null) return true;
        String tipId = co.metadata.versionOf;
        if (tipId == null) {
            return true;
        } else {
            return false;
        }
    }

    public List<CordraObject> getVersionsFor(String objectId, String userId, boolean hasUserObject, List<String> groupIds, AuthConfig authConfig) throws CordraException {
        List<CordraObject> result = new ArrayList<>();
        CordraObject co = storage.get(objectId);
        if (co == null) {
            return Collections.emptyList();
        }
        if (co.metadata == null) co.metadata = new CordraObject.Metadata();
        if (co.metadata.internalMetadata == null) co.metadata.internalMetadata = new JsonObject();
        String tipId = co.metadata.versionOf;
        if (tipId != null) {
            co = storage.get(tipId);
            if (co == null) {
                return Collections.emptyList();
            }
        } else {
            tipId = objectId;
        }
        String query = VERSION_OF+":\""+tipId + "\" objatt_" + VERSION_OF +":\""+tipId + "\"";
        boolean excludeVersions = false;
        String restrictedQuery = QueryRestrictor.restrict(query, userId, hasUserObject, groupIds, authConfig, excludeVersions);
        try (SearchResults<CordraObject> results = indexer.search(restrictedQuery);) {
            for (CordraObject current : results) {
                result.add(current);
            }
        }
        result.add(co); //Should we include the tip in the response?
        return result;
    }
}
