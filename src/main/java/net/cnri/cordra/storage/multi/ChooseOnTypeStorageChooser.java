package net.cnri.cordra.storage.multi;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import net.cnri.cordra.GsonUtility;
import net.cnri.cordra.RequestContext;
import net.cnri.cordra.api.CordraObject;
import java.util.Map;
import java.util.Set;

public class ChooseOnTypeStorageChooser implements StorageChooser {

    private Map<String, Set<String>> storageNameToTypesMap;
    private String defaultStorageName;

    public ChooseOnTypeStorageChooser(JsonObject options) {
        Gson gson = GsonUtility.getGson();
        defaultStorageName = options.get("default").getAsString();
        storageNameToTypesMap = gson.fromJson(options.get("typesMap"), new TypeToken<Map<String, Set<String>>>(){}.getType());
    }

    @Override
    public String getStorageForCordraObject(CordraObject cordraObject, RequestContext requestContext) {
        return getStorageForType(cordraObject.type);
    }

    @Override
    public String getStorageForObjectId(String objectId, RequestContext requestContext) {
        JsonObject userContext = getUserContext(requestContext);
        if (userContext == null) return ALL_STORAGES;
        JsonElement type = userContext.get("objectType");
        if (type == null) return ALL_STORAGES;
        return getStorageForType(type.getAsString());
    }

    private JsonObject getUserContext(RequestContext context) {
        if (context == null) return null;
        return context.getRequestContext();
    }

    private String getStorageForType(String type) {
        for (Map.Entry<String, Set<String>> entry : storageNameToTypesMap.entrySet()) {
            String storageName = entry.getKey();
            Set<String> types = entry.getValue();
            if (types.contains(type)) {
                return storageName;
            }
        }
        return defaultStorageName;
    }
}
