/*************************************************************************\
    Copyright (c) 2019 Corporation for National Research Initiatives;
                        All rights reserved.
\*************************************************************************/

package net.cnri.cordra.storage.multi;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.cnri.cordra.CordraServiceFactory;
import net.cnri.cordra.GsonUtility;
import net.cnri.cordra.RequestContext;
import net.cnri.cordra.RequestContextHolder;
import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.api.CordraObject;
import net.cnri.cordra.api.InternalErrorCordraException;
import net.cnri.cordra.api.NotFoundCordraException;
import net.cnri.cordra.api.SearchResults;
import net.cnri.cordra.api.UncheckedCordraException;
import net.cnri.cordra.collections.AbstractSearchResults;
import net.cnri.cordra.storage.CordraStorage;
import net.cnri.cordra.storage.StorageConfig;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

/*
Example json options
  "storage": {
    "module": "multi",
    "options": {
      "storageChooser": "net.cnri.cordra.storage.multi.ChooseOnTypeStorageChooser",
      "storageChooserOptions": {
        "default": "A",
        "typesMap": {
          "B": [
            "User",
            "Group",
            "Schema",
            "CordraDesign"
          ]
        }
      },
      "storageConfigs": {
        "A": {
          "module": "memory"
        },
        "B": {
          "module": "memory"
        }
      }
    }
  }
 */
public class MultiCordraStorage implements CordraStorage {
    private Map<String, CordraStorage> storageMap;
    private StorageChooser storageChooser;

    public MultiCordraStorage(JsonObject options) throws IOException, CordraException {
        storageMap = buildStorageMapFromOptions(options);
        String storageChooserClassName = options.get("storageChooser").getAsString();
        JsonObject chooserOptions = null;
        if (options.has("storageChooserOptions")) {
            chooserOptions = options.get("storageChooserOptions").getAsJsonObject();
        }
        storageChooser = loadStorageChooser(storageChooserClassName, chooserOptions);
    }

    public MultiCordraStorage(Map<String, CordraStorage> storageMap, StorageChooser storageChooser) {
        this.storageMap = storageMap;
        this.storageChooser = storageChooser;
    }

    // for testing
    public Map<String, CordraStorage> getStorageMap() {
        return storageMap;
    }

    // for testing
    public StorageChooser getStorageChooser() {
        return storageChooser;
    }

    private StorageChooser loadStorageChooser(String storageChooserClassName, JsonObject storageChooserOptions) throws CordraException {
        StorageChooser result = null;
        Class<?> storageChooserClass;
        try {
            storageChooserClass = Class.forName(storageChooserClassName);
            Constructor<?>[] constructors = storageChooserClass.getConstructors();
            Constructor<?> defaultConstructor = null;
            for (Constructor<?> c : constructors) {
                Class<?>[] parameterTypes = c.getParameterTypes();
                if (parameterTypes.length == 0) {
                    defaultConstructor = c;
                }
                if ((parameterTypes.length == 1) && (parameterTypes[0] == JsonObject.class) && storageChooserOptions != null) {
                    result = (StorageChooser) c.newInstance(storageChooserOptions);
                    break;
                }
            }
            if (defaultConstructor != null && storageChooserOptions == null) {
                result = (StorageChooser) defaultConstructor.newInstance();
            }
            if (result == null) {
                throw new CordraException("Could not find valid constructor in custom choosing function " + storageChooserClassName);
            }
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new CordraException(e);
        }
        return result;
    }

    private Map<String, CordraStorage> buildStorageMapFromOptions(JsonObject options) throws IOException, CordraException {
        Map<String, CordraStorage> result = new HashMap<>();
        Gson gson = GsonUtility.getGson();
        JsonObject storageConfigs = options.get("storageConfigs").getAsJsonObject();
        for (String storageName : storageConfigs.keySet()) {
            JsonObject storageConfigJson = storageConfigs.get(storageName).getAsJsonObject();
            StorageConfig storageConfig = gson.fromJson(storageConfigJson, StorageConfig.class);
            CordraStorage storage = CordraServiceFactory.createStorageForConfig(storageConfig, null, true);
            result.put(storageName, storage);
        }
        return result;
    }

    private RequestContext getRequestContext() {
        RequestContext context = RequestContextHolder.get();
        if (context == null) return new RequestContext();
        else return context;
    }

    private CordraStorage getStorageForObject(CordraObject co) throws InternalErrorCordraException {
        String storageName = storageChooser.getStorageForCordraObject(co, getRequestContext());
        CordraStorage res = storageMap.get(storageName);
        if (res == null) {
            throw new InternalErrorCordraException("No corresponding storage");
        }
        return res;
    }

    private Collection<CordraStorage> getStoragesForId(String id) throws InternalErrorCordraException {
        String storageName = storageChooser.getStorageForObjectId(id, getRequestContext());
        if (StorageChooser.ALL_STORAGES.equals(storageName) && !storageMap.containsKey(StorageChooser.ALL_STORAGES)) {
            return storageMap.values();
        }
        CordraStorage res = storageMap.get(storageName);
        if (res == null) {
            throw new InternalErrorCordraException("No corresponding storage");
        }
        return Collections.singletonList(res);
    }

    private Collection<CordraStorage> getStoragesFromContext() throws InternalErrorCordraException {
        String storageName = storageChooser.getStorageFromContext(getRequestContext());
        if (StorageChooser.ALL_STORAGES.equals(storageName) && !storageMap.containsKey(StorageChooser.ALL_STORAGES)) {
            return storageMap.values();
        }
        CordraStorage res = storageMap.get(storageName);
        if (res == null) {
            throw new InternalErrorCordraException("No corresponding storage");
        }
        return Collections.singletonList(res);
    }

    @Override
    public CordraObject get(String id) throws CordraException {
        Collection<CordraStorage> storages = getStoragesForId(id);
        for (CordraStorage storage : storages) {
            CordraObject co = storage.get(id);
            if (co != null) {
                return co;
            }
        }
        return null;
    }

    @Override
    public SearchResults<CordraObject> get(Collection<String> ids) throws CordraException {
        RequestContext requestContext = getRequestContext();
        Map<String, List<String>> storageToIdMap = new HashMap<>();
        List<String> idsWithMultipleStorages = new ArrayList<>();
        for (String id : ids) {
            String storageName = storageChooser.getStorageForObjectId(id, requestContext);
            if (StorageChooser.ALL_STORAGES.equals(storageName) && !storageMap.containsKey(StorageChooser.ALL_STORAGES)) {
                idsWithMultipleStorages.add(id);
            } else {
                List<String> list = storageToIdMap.get(storageName);
                if (list == null) {
                    CordraStorage res = storageMap.get(storageName);
                    if (res == null) {
                        throw new InternalErrorCordraException("No corresponding storage");
                    }
                    list = new ArrayList<>();
                    storageToIdMap.put(storageName, list);
                }
                list.add(id);
            }
        }
        return new MultiGetSearchResults(storageMap, storageToIdMap, idsWithMultipleStorages);
    }

    @Override
    public InputStream getPayload(String id, String payloadName) throws CordraException {
        Collection<CordraStorage> storages = getStoragesForId(id);
        for (CordraStorage storage : storages) {
            InputStream in = storage.getPayload(id, payloadName);
            if (in != null) {
                return in;
            }
        }
        return null;
    }

    @Override
    public InputStream getPartialPayload(String id, String payloadName, Long start, Long end) throws CordraException {
        Collection<CordraStorage> storages = getStoragesForId(id);
        for (CordraStorage storage : storages) {
            InputStream in = storage.getPartialPayload(id, payloadName, start, end);
            if (in != null) {
                return in;
            }
        }
        return null;
    }

    @Override
    public CordraObject create(CordraObject co) throws CordraException {
        CordraStorage storage = getStorageForObject(co);
        return storage.create(co);
    }

    @Override
    public CordraObject update(CordraObject co) throws CordraException {
        CordraStorage storage = getStorageForObject(co);
        return storage.update(co);
    }

    @Override
    public void delete(String id) throws CordraException {
        Collection<CordraStorage> storages = getStoragesForId(id);
        for (CordraStorage storage : storages) {
            try {
                storage.delete(id);
                return;
            } catch (NotFoundCordraException e) {
                // not found, try next storage
            }
        }
        throw new NotFoundCordraException("Object does not exist: " + id);
    }

    @Override
    public SearchResults<CordraObject> list() throws CordraException {
        return new ConcatenatedSearchResults<>(getStoragesFromContext(), CordraStorage::list);
    }

    @Override
    public SearchResults<String> listHandles() throws CordraException {
        return new ConcatenatedSearchResults<>(getStoragesFromContext(), CordraStorage::listHandles);
    }

    @Override
    public SearchResults<CordraObject> listByType(List<String> types) throws CordraException {
        return new ConcatenatedSearchResults<>(getStoragesFromContext(), storage -> storage.listByType(types));
    }

    @Override
    public SearchResults<String> listHandlesByType(List<String> types) throws CordraException {
        return new ConcatenatedSearchResults<>(getStoragesFromContext(), storage -> storage.listHandlesByType(types));
    }

    @Override
    public void close() throws IOException, CordraException {
        for (CordraStorage storage : storageMap.values()) {
            storage.close();
        }
    }

    @FunctionalInterface
    private interface ThrowingStorageMethod<T> {
        SearchResults<T> call(CordraStorage storage) throws CordraException;
    }

    private static class ConcatenatedSearchResults<T> extends AbstractSearchResults<T> {

        private final Iterator<CordraStorage> storageIterator;
        private final ThrowingStorageMethod<T> method;
        private SearchResults<T> currentResults;
        private Iterator<T> currentIterator;

        public ConcatenatedSearchResults(Collection<CordraStorage> storages, ThrowingStorageMethod<T> method) {
            this.storageIterator = storages.iterator();
            this.method = method;
        }

        @Override
        public int size() {
            return -1;
        }

        @Override
        protected T computeNext() {
            while (true) {
                if (currentIterator == null) {
                    if (!storageIterator.hasNext()) {
                        return null;
                    }
                    CordraStorage nextStorage = storageIterator.next();
                    try {
                        currentResults = method.call(nextStorage);
                    } catch (CordraException e) {
                        throw new UncheckedCordraException(e);
                    }
                    currentIterator = currentResults.iterator();
                }
                if (currentIterator.hasNext()) {
                    return currentIterator.next();
                } else {
                    currentResults.close();
                    currentResults = null;
                    currentIterator = null;
                }
            }
        }

        @Override
        protected void closeOnlyOnce() {
            if (currentResults != null) currentResults.close();
        }
    }

    private static class MultiGetSearchResults extends AbstractSearchResults<CordraObject> {

        private final Map<String, CordraStorage> storageMap;
        private final Map<String, List<String>> storageToIdMap;
        private final Iterator<String> storageNameIterator;
        private final Iterator<String> idsWithMultipleStoragesIterator;
        private SearchResults<CordraObject> currentResults;
        private Iterator<CordraObject> currentIterator;

        public MultiGetSearchResults(Map<String, CordraStorage> storageMap, Map<String, List<String>> storageToIdMap, List<String> idsWithMultipleStorages) {
            this.storageMap = storageMap;
            this.storageToIdMap = storageToIdMap;
            this.storageNameIterator = storageToIdMap.keySet().iterator();
            this.idsWithMultipleStoragesIterator = idsWithMultipleStorages.iterator();
        }

        @Override
        public int size() {
            return -1;
        }

        @Override
        protected CordraObject computeNext() {
            while (true) {
                if (currentIterator == null) {
                    if (!storageNameIterator.hasNext()) {
                        return computeNextForIdsWithMultipleStorages();
                    }
                    String nextStorageName = storageNameIterator.next();
                    List<String> ids = storageToIdMap.get(nextStorageName);
                    try {
                        currentResults = storageMap.get(nextStorageName).get(ids);
                    } catch (CordraException e) {
                        throw new UncheckedCordraException(e);
                    }
                    currentIterator = currentResults.iterator();
                }
                if (currentIterator.hasNext()) {
                    return currentIterator.next();
                } else {
                    currentResults.close();
                    currentResults = null;
                    currentIterator = null;
                }
            }
        }

        private CordraObject computeNextForIdsWithMultipleStorages() {
            while (true) {
                if (!idsWithMultipleStoragesIterator.hasNext()) {
                    return null;
                }
                String id = idsWithMultipleStoragesIterator.next();
                for (CordraStorage storage : storageMap.values()) {
                    try {
                        CordraObject co = storage.get(id);
                        if (co != null) {
                            return co;
                        }
                    } catch (CordraException e) {
                        throw new UncheckedCordraException(e);
                    }
                }
            }
        }

        @Override
        protected void closeOnlyOnce() {
            if (currentResults != null) currentResults.close();
        }
    }

}
