package net.cnri.cordra.storage.multi;

import net.cnri.cordra.RequestContext;
import net.cnri.cordra.api.CordraObject;

@SuppressWarnings("unused")
public interface StorageChooser {
    String ALL_STORAGES = "all";

    String getStorageForCordraObject(CordraObject cordraObject, RequestContext requestContext);

    default String getStorageForObjectId(String objectId, RequestContext requestContext) {
        return ALL_STORAGES;
    }

    default String getStorageFromContext(RequestContext requestContext) {
        return ALL_STORAGES;
    }
}
