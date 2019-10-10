package net.cnri.cordra.api;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.cnri.cordra.storage.CordraStorage;

@SuppressWarnings("unused")
public interface CordraClient extends AutoCloseable, CordraStorage {
    default Gson getGson() {
        return new Gson();
    }
    default void setGson(Gson gson) {
        throw new UnsupportedOperationException();
    }

    @Override
    default CordraObject get(String id) throws CordraException {
        return get(id, new Options().setUseDefaultCredentials(true));
    }
    default CordraObject get(String id, String username, String password) throws CordraException {
        return get(id, new Options().setUsername(username).setPassword(password));
    }
    CordraObject get(String id, Options options) throws CordraException;

    @Override
    default InputStream getPayload(String id, String payloadName) throws CordraException {
        return getPayload(id, payloadName, new Options().setUseDefaultCredentials(true));
    }
    default InputStream getPayload(String id, String payloadName, String username, String password) throws CordraException {
        return getPayload(id, payloadName, new Options().setUsername(username).setPassword(password));
    }
    InputStream getPayload(String id, String payloadName, Options options) throws CordraException;

    @Override
    default InputStream getPartialPayload(String id, String payloadName, Long start, Long end) throws CordraException {
        return getPartialPayload(id, payloadName, start, end, new Options().setUseDefaultCredentials(true));
    }
    default InputStream getPartialPayload(String id, String payloadName, Long start, Long end, String username, String password) throws CordraException {
        return getPartialPayload(id, payloadName, start, end, new Options().setUsername(username).setPassword(password));
    }
    InputStream getPartialPayload(String id, String payloadName, Long start, Long end, Options options) throws CordraException;

    @Override
    default CordraObject create(CordraObject d) throws CordraException {
        return create(d, new Options().setUseDefaultCredentials(true));
    }
    default CordraObject create(CordraObject d, String username, String password) throws CordraException {
        return create(d, new Options().setUsername(username).setPassword(password));
    }
    default CordraObject create(CordraObject d, boolean isDryRun, String username, String password) throws CordraException {
        return create(d, new Options().setUsername(username).setPassword(password).setDryRun(isDryRun));
    }
    default CordraObject create(CordraObject d, boolean isDryRun) throws CordraException {
        return create(d, new Options().setUseDefaultCredentials(true).setDryRun(isDryRun));
    }
    CordraObject create(CordraObject d, Options options) throws CordraException;

    @Override
    default CordraObject update(CordraObject d) throws CordraException {
        return update(d, new Options().setUseDefaultCredentials(true));
    }
    default CordraObject update(CordraObject d, String username, String password) throws CordraException {
        return update(d, new Options().setUsername(username).setPassword(password));
    }
    default CordraObject update(CordraObject d, boolean isDryRun, String username, String password) throws CordraException {
        return update(d, new Options().setUsername(username).setPassword(password).setDryRun(isDryRun));
    }
    default CordraObject update(CordraObject d, boolean isDryRun) throws CordraException {
        return update(d, new Options().setUseDefaultCredentials(true).setDryRun(isDryRun));
    }
    CordraObject update(CordraObject d, Options options) throws CordraException;

    default List<String> listMethods(String objectId) throws CordraException {
        return listMethods(objectId, new Options().setUseDefaultCredentials(true));
    }
    default List<String> listMethods(String objectId, String username, String password) throws CordraException {
        return listMethods(objectId, new Options().setUsername(username).setPassword(password));
    }
    default List<String> listMethods(String objectId, Options options) throws CordraException { throw new UnsupportedOperationException(); }

    default List<String> listMethodsForType(String type, boolean isStatic) throws CordraException {
        return listMethodsForType(type, isStatic, new Options().setUseDefaultCredentials(true));
    }
    default List<String> listMethodsForType(String type, boolean isStatic, String username, String password) throws CordraException {
        return listMethodsForType(type, isStatic, new Options().setUsername(username).setPassword(password));
    }
    default List<String> listMethodsForType(String type, boolean isStatic, Options options) throws CordraException { throw new UnsupportedOperationException(); }

    default JsonElement call(String objectId, String methodName, JsonElement params) throws CordraException {
        return call(objectId, methodName, params, new Options().setUseDefaultCredentials(true));
    }
    default JsonElement call(String objectId, String methodName, JsonElement params, String username, String password) throws CordraException {
        return call(objectId, methodName, params, new Options().setUsername(username).setPassword(password));
    }
    default JsonElement call(String objectId, String methodName, JsonElement params, Options options) throws CordraException { throw new UnsupportedOperationException(); }

    default JsonElement callForType(String type, String methodName, JsonElement params) throws CordraException {
        return callForType(type, methodName, params, new Options().setUseDefaultCredentials(true));
    }
    default JsonElement callForType(String type, String methodName, JsonElement params, String username, String password) throws CordraException {
        return callForType(type, methodName, params, new Options().setUsername(username).setPassword(password));
    }
    default JsonElement callForType(String type, String methodName, JsonElement params, Options options) throws CordraException { throw new UnsupportedOperationException(); }

    default VersionInfo publishVersion(String objectId, String versionId, boolean clonePayloads) throws CordraException {
        return publishVersion(objectId, versionId, clonePayloads, new Options().setUseDefaultCredentials(true));
    }
    default VersionInfo publishVersion(String objectId, String versionId, boolean clonePayloads, String username, String password) throws CordraException {
        return publishVersion(objectId, versionId, clonePayloads, new Options().setUsername(username).setPassword(password));
    }
    default VersionInfo publishVersion(String objectId, String versionId, boolean clonePayloads, Options options) throws CordraException { throw new UnsupportedOperationException(); }

    default List<VersionInfo> getVersionsFor(String objectId) throws CordraException {
        return getVersionsFor(objectId, new Options().setUseDefaultCredentials(true));
    }
    default List<VersionInfo> getVersionsFor(String objectId, String username, String password) throws CordraException {
        return getVersionsFor(objectId, new Options().setUsername(username).setPassword(password));
    }
    default List<VersionInfo> getVersionsFor(String id, Options options) throws CordraException { throw new UnsupportedOperationException(); }

    @Override
    default void delete(String id) throws CordraException {
        delete(id, new Options().setUseDefaultCredentials(true));
    }
    default void delete(String id, String username, String password) throws CordraException {
        delete(id, new Options().setUsername(username).setPassword(password));
    }
    void delete(String id, Options options) throws CordraException;

    @Override
    default SearchResults<CordraObject> list() throws CordraException {
        return list(new Options().setUseDefaultCredentials(true));
    }
    @Override
    default SearchResults<String> listHandles() throws CordraException {
        return listHandles(new Options().setUseDefaultCredentials(true));
    }

    default SearchResults<CordraObject> list(Options options) throws CordraException {
        return search("*:*", options);
    }

    default SearchResults<String> listHandles(Options options) throws CordraException {
        return searchHandles("*:*", options);
    }


    default SearchResults<CordraObject> search(String query) throws CordraException {
        return search(query, QueryParams.DEFAULT, new Options().setUseDefaultCredentials(true));
    }
    default SearchResults<CordraObject> search(String query, String username, String password) throws CordraException {
        return search(query, new Options().setUsername(username).setPassword(password));
    }
    default SearchResults<CordraObject> search(String query, Options options) throws CordraException {
        return search(query, QueryParams.DEFAULT, options);
    }
    default SearchResults<CordraObject> search(String query, QueryParams params) throws CordraException {
        return search(query, params, new Options().setUseDefaultCredentials(true));
    }
    default SearchResults<CordraObject> search(String query, QueryParams params, String username, String password) throws CordraException {
        return search(query, params, new Options().setUsername(username).setPassword(password));
    }
    SearchResults<CordraObject> search(String query, QueryParams params, Options options) throws CordraException;

    default SearchResults<String> searchHandles(String query) throws CordraException {
        return searchHandles(query, QueryParams.DEFAULT, new Options().setUseDefaultCredentials(true));
    }
    default SearchResults<String> searchHandles(String query, String username, String password) throws CordraException {
        return searchHandles(query, QueryParams.DEFAULT, new Options().setUsername(username).setPassword(password));
    }
    default SearchResults<String> searchHandles(String query, Options options) throws CordraException {
        return searchHandles(query, QueryParams.DEFAULT, options);
    }
    default SearchResults<String> searchHandles(String query, QueryParams params) throws CordraException {
        return searchHandles(query, params, new Options().setUseDefaultCredentials(true));
    }
    default SearchResults<String> searchHandles(String query, QueryParams params, String username, String password) throws CordraException {
        return searchHandles(query, params, new Options().setUsername(username).setPassword(password));
    }
    SearchResults<String> searchHandles(String query, QueryParams params, Options options) throws CordraException;

    default boolean authenticate() throws CordraException {
        return authenticateAndGetResponse(new Options().setUseDefaultCredentials(true)).active;
    }
    default boolean authenticate(String username, String password) throws CordraException {
        return authenticateAndGetResponse(new Options().setUsername(username).setPassword(password)).active;
    }
    default AuthResponse authenticateAndGetResponse() throws CordraException {
        return authenticateAndGetResponse(new Options().setUseDefaultCredentials(true));
    }
    default AuthResponse authenticateAndGetResponse(String username, String password) throws CordraException {
        return authenticateAndGetResponse(new Options().setUsername(username).setPassword(password));
    }
    AuthResponse authenticateAndGetResponse(Options options) throws CordraException;

    void changePassword(String newPassword) throws CordraException;
    void changePassword(String username, String password, String newPassword) throws CordraException;

    default String getContentAsJson(String id) throws CordraException {
        CordraObject d = get(id);
        return getGson().toJson(d.content);
    }

    default <T> T getContent(String id, Class<T> klass) throws CordraException {
        CordraObject d = get(id);
        return getGson().fromJson(d.content, klass);
    }

    default CordraObject create(String type, String contentJson) throws CordraException {
        CordraObject d = new CordraObject();
        d.type = type;
        d.content = new JsonParser().parse(contentJson);
        return create(d);
    }

    default CordraObject create(String type, String contentJson, String username, String password) throws CordraException {
        CordraObject d = new CordraObject();
        d.type = type;
        d.content = new JsonParser().parse(contentJson);
        return create(d, username, password);
    }

    default CordraObject update(String id, String contentJson) throws CordraException {
        CordraObject d = new CordraObject();
        d.id = id;
        d.content = new JsonParser().parse(contentJson);
        return update(d);
    }

    default CordraObject update(String id, String contentJson, String username, String password) throws CordraException {
        CordraObject d = new CordraObject();
        d.id = id;
        d.content = new JsonParser().parse(contentJson);
        return update(d, username, password);
    }

    default CordraObject create(String type, Object content) throws CordraException {
        CordraObject d = new CordraObject();
        d.type = type;
        d.content = getGson().toJsonTree(content);
        return create(d);
    }

    default CordraObject create(String type, Object content, String username, String password) throws CordraException {
        CordraObject d = new CordraObject();
        d.type = type;
        d.content = getGson().toJsonTree(content);
        return create(d, username, password);
    }

    default CordraObject update(String id, Object content) throws CordraException {
        CordraObject d = new CordraObject();
        d.id = id;
        d.content = getGson().toJsonTree(content);
        return update(d);
    }

    default CordraObject create(String type, String contentJson, boolean isDryRun) throws CordraException {
        CordraObject d = new CordraObject();
        d.type = type;
        d.content = new JsonParser().parse(contentJson);
        return create(d, isDryRun);
    }

    default CordraObject create(String type, String contentJson, boolean isDryRun, String username, String password) throws CordraException {
        CordraObject d = new CordraObject();
        d.type = type;
        d.content = new JsonParser().parse(contentJson);
        return create(d, isDryRun, username, password);
    }

    default CordraObject update(String id, String contentJson, boolean isDryRun) throws CordraException {
        CordraObject d = new CordraObject();
        d.id = id;
        d.content = new JsonParser().parse(contentJson);
        return update(d, isDryRun);
    }

    default CordraObject update(String id, String contentJson, boolean isDryRun, String username, String password) throws CordraException {
        CordraObject d = new CordraObject();
        d.id = id;
        d.content = new JsonParser().parse(contentJson);
        return update(d, isDryRun, username, password);
    }

    default CordraObject create(String type, Object content, boolean isDryRun) throws CordraException {
        CordraObject d = new CordraObject();
        d.type = type;
        d.content = getGson().toJsonTree(content);
        return create(d, isDryRun);
    }

    default CordraObject create(String type, Object content, boolean isDryRun, String username, String password) throws CordraException {
        CordraObject d = new CordraObject();
        d.type = type;
        d.content = getGson().toJsonTree(content);
        return create(d, isDryRun, username, password);
    }

    default CordraObject update(String id, Object content, boolean isDryRun) throws CordraException {
        CordraObject d = new CordraObject();
        d.id = id;
        d.content = getGson().toJsonTree(content);
        return update(d, isDryRun);
    }

    default void reindexBatch(List<String> batchIds, Options options) throws CordraException {
        throw new UnsupportedOperationException();
    }

    @Override
    default void close() throws IOException, CordraException {
        //no-op
    }
}
