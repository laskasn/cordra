package net.cnri.cordra.api;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

public class InstrumentedCordraClient implements CordraClient {

    private static Logger logger = LoggerFactory.getLogger(InstrumentedCordraClient.class);
    private final CordraClient delegate;

    public InstrumentedCordraClient(CordraClient delegate) {
        this.delegate = delegate;
    }

    @Override
    public Gson getGson() {
        return delegate.getGson();
    }

    @Override
    public void setGson(Gson gson) {
        delegate.setGson(gson);
    }

    @Override
    public CordraObject get(String id) throws CordraException {
        return run(() -> {
            return delegate.get(id);
        });
    }

    @Override
    public SearchResults<CordraObject> get(Collection<String> ids) throws CordraException {
        return run(() -> {
            return delegate.get(ids);
        });
    }

    @Override
    public CordraObject get(String id, String username, String password) throws CordraException {
        return run(() -> {
            return delegate.get(id, username, password);
        });
    }

    @Override
    public InputStream getPayload(String id, String payloadName) throws CordraException {
        return run(() -> {
            return delegate.getPayload(id, payloadName);
        });
    }

    @Override
    public InputStream getPartialPayload(String id, String payloadName, Long start, Long end) throws CordraException {
        return run(() -> {
            return delegate.getPartialPayload(id, payloadName, start, end);
        });
    }

    @Override
    public InputStream getPayload(String id, String payloadName, String username, String password) throws CordraException {
        return run(() -> {
            return delegate.getPayload(id, payloadName, username, password);
        });
    }

    @Override
    public InputStream getPartialPayload(String id, String payloadName, Long start, Long end, String username, String password) throws CordraException {
        return run(() -> {
            return delegate.getPartialPayload(id, payloadName, start, end, username, password);
        });
    }

    @Override
    public CordraObject create(CordraObject d) throws CordraException {
        return run(() -> {
            return delegate.create(d);
        });
    }

    @Override
    public CordraObject create(CordraObject d, String username, String password) throws CordraException {
        return run(() -> {
            return delegate.create(d, username, password);
        });
    }

    @Override
    public CordraObject update(CordraObject d) throws CordraException {
        return run(() -> {
            return delegate.update(d);
        });
    }

    @Override
    public CordraObject update(CordraObject d, String username, String password) throws CordraException {
        return run(() -> {
            return delegate.update(d, username, password);
        });
    }

    @Override
    public CordraObject create(CordraObject d, boolean isDryRun) throws CordraException {
        return run(() -> {
            return delegate.create(d, isDryRun);
        });
    }

    @Override
    public CordraObject create(CordraObject d, boolean isDryRun, String username, String password) throws CordraException {
        return run(() -> {
            return delegate.create(d, isDryRun, username, password);
        });
    }

    @Override
    public CordraObject update(CordraObject d, boolean isDryRun) throws CordraException {
        return run(() -> {
            return delegate.update(d, isDryRun);
        });
    }

    @Override
    public CordraObject update(CordraObject d, boolean isDryRun, String username, String password) throws CordraException {
        return run(() -> {
            return delegate.update(d, isDryRun, username, password);
        });
    }

    @Override
    public void delete(String id) throws CordraException {
        run(() -> {
            delegate.delete(id);
        });
    }

    @Override
    public void delete(String id, String username, String password) throws CordraException {
        run(() -> {
            delegate.delete(id, username, password);
        });
    }

    @Override
    public SearchResults<CordraObject> search(String query) throws CordraException {
        return run(() -> {
            return delegate.search(query);
        });
    }

    @Override
    public SearchResults<CordraObject> search(String query, String username, String password) throws CordraException {
        return run(() -> {
            return delegate.search(query, username, password);
        });
    }

    @Override
    public SearchResults<CordraObject> search(String query, Options options) throws CordraException {
        return run(() -> {
            return delegate.search(query, options);
        });
    }

    @Override
    public SearchResults<String> searchHandles(String query) throws CordraException {
        return run(() -> {
            return delegate.searchHandles(query);
        });
    }

    @Override
    public SearchResults<String> searchHandles(String query, String username, String password) throws CordraException {
        return run(() -> {
            return delegate.searchHandles(query, username, password);
        });
    }

    @Override
    public SearchResults<String> searchHandles(String query, Options options) throws CordraException {
        return run(() -> {
            return delegate.searchHandles(query, options);
        });
    }

    @Override
    public SearchResults<CordraObject> list() throws CordraException {
        return run(() -> {
            return delegate.list();
        });
    }

    @Override
    public SearchResults<String> listHandles() throws CordraException {
        return run(() -> {
            return delegate.listHandles();
        });
    }

    @Override
    public SearchResults<CordraObject> search(String query, QueryParams params) throws CordraException {
        return run(() -> {
            return delegate.search(query, params);
        });
    }

    @Override
    public SearchResults<CordraObject> search(String query, QueryParams params, String username, String password) throws CordraException {
        return run(() -> {
            return delegate.search(query, params, username, password);
        });
    }

    @Override
    public SearchResults<String> searchHandles(String query, QueryParams params) throws CordraException {
        return run(() -> {
            return delegate.searchHandles(query, params);
        });
    }

    @Override
    public SearchResults<String> searchHandles(String query, QueryParams params, String username, String password) throws CordraException {
        return run(() -> {
            return delegate.searchHandles(query, params, username, password);
        });
    }

    @Override
    public boolean authenticate() throws CordraException {
        return run(() -> {
            return delegate.authenticate();
        });
    }

    @Override
    public boolean authenticate(String username, String password) throws CordraException {
        return run(() -> {
            return delegate.authenticate(username, password);
        });
    }

    @Override
    public AuthResponse authenticateAndGetResponse() throws CordraException {
        return run(() -> {
            return delegate.authenticateAndGetResponse();
        });
    }

    @Override
    public AuthResponse authenticateAndGetResponse(String username, String password) throws CordraException {
        return run(() -> {
            return delegate.authenticateAndGetResponse(username, password);
        });
    }

    @Override
    public AuthResponse authenticateAndGetResponse(Options options) throws CordraException {
        return run(() -> {
            return delegate.authenticateAndGetResponse(options);
        });
    }

    @Override
    public void changePassword(String newPassword) throws CordraException {
        run(() -> {
            delegate.changePassword(newPassword);
        });
    }

    @Override
    public void changePassword(String username, String password, String newPassword) throws CordraException {
        run(() -> {
            delegate.changePassword(username, password, newPassword);
        });
    }

    @Override
    public String getContentAsJson(String id) throws CordraException {
        return run(() -> {
            return delegate.getContentAsJson(id);
        });
    }

    @Override
    public <T> T getContent(String id, Class<T> klass) throws CordraException {
        return run(() -> {
            return delegate.getContent(id, klass);
        });
    }

    @Override
    public CordraObject create(String type, String contentJson) throws CordraException {
        return run(() -> {
            return delegate.create(type, contentJson);
        });
    }

    @Override
    public JsonElement call(String objectId, String methodName, JsonElement params, String username, String password) throws CordraException {
        return run(() -> {
            return delegate.call(objectId, methodName, params, username, password);
        });
    }

    @Override
    public JsonElement call(String objectId, String methodName, JsonElement params, Options options) throws CordraException {
        return run(() -> {
            return delegate.call(objectId, methodName, params, options);
        });
    }

    @Override
    public JsonElement callForType(String type, String methodName, JsonElement params, String username, String password) throws CordraException {
        return run(() -> {
            return delegate.callForType(type, methodName, params, username, password);
        });
    }

    @Override
    public JsonElement callForType(String type, String methodName, JsonElement params, Options options) throws CordraException {
        return run(() -> {
            return delegate.callForType(type, methodName, params, options);
        });
    }

    @Override
    public List<String> listMethods(String objectId, String username, String password) throws CordraException {
        return run(() -> {
            return delegate.listMethods(objectId, username, password);
        });
    }

    @Override
    public List<String> listMethods(String objectId, Options options) throws CordraException {
        return run(() -> {
            return delegate.listMethods(objectId, options);
        });
    }

    @Override
    public List<String> listMethodsForType(String type, boolean isStatic, String username, String password) throws CordraException {
        return run(() -> {
            return delegate.listMethodsForType(type, isStatic, username, password);
        });
    }

    @Override
    public List<String> listMethodsForType(String type, boolean isStatic, Options options) throws CordraException {
        return run(() -> {
            return delegate.listMethodsForType(type, isStatic, options);
        });
    }

    @Override
    public CordraObject create(String type, String contentJson, String username, String password) throws CordraException {
        return run(() -> {
            return delegate.create(type, contentJson, username, password);
        });
    }

    @Override
    public CordraObject update(String id, String contentJson) throws CordraException {
        return run(() -> {
            return delegate.update(id, contentJson);
        });
    }

    @Override
    public CordraObject update(String id, String contentJson, String username, String password) throws CordraException {
        return run(() -> {
            return delegate.update(id, contentJson, username, password);
        });
    }

    @Override
    public CordraObject create(String type, Object content) throws CordraException {
        return run(() -> {
            return delegate.create(type, content);
        });
    }

    @Override
    public CordraObject create(String type, Object content, String username, String password) throws CordraException {
        return run(() -> {
            return delegate.create(type, content, username, password);
        });
    }

    @Override
    public CordraObject update(String id, Object content) throws CordraException {
        return run(() -> {
            return delegate.update(id, content);
        });
    }

    @Override
    public CordraObject create(String type, String contentJson, boolean isDryRun) throws CordraException {
        return run(() -> {
            return delegate.create(type, contentJson, isDryRun);
        });
    }

    @Override
    public CordraObject create(String type, String contentJson, boolean isDryRun, String username, String password) throws CordraException {
        return run(() -> {
            return delegate.create(type, contentJson, isDryRun, username, password);
        });
    }

    @Override
    public CordraObject update(String id, String contentJson, boolean isDryRun) throws CordraException {
        return run(() -> {
            return delegate.update(id, contentJson, isDryRun);
        });
    }

    @Override
    public CordraObject update(String id, String contentJson, boolean isDryRun, String username, String password) throws CordraException {
        return run(() -> {
            return delegate.update(id, contentJson, isDryRun, username, password);
        });
    }

    @Override
    public CordraObject create(String type, Object content, boolean isDryRun) throws CordraException {
        return run(() -> {
            return delegate.create(type, content, isDryRun);
        });
    }

    @Override
    public CordraObject create(String type, Object content, boolean isDryRun, String username, String password) throws CordraException {
        return run(() -> {
            return delegate.create(type, content, isDryRun, username, password);
        });
    }

    @Override
    public CordraObject update(String id, Object content, boolean isDryRun) throws CordraException {
        return run(() -> {
            return delegate.update(id, content, isDryRun);
        });
    }

    @Override
    public List<String> listMethods(String objectId) throws CordraException {
        return run(() -> {
            return delegate.listMethods(objectId);
        });
    }

    @Override
    public List<String> listMethodsForType(String type, boolean isStatic) throws CordraException {
        return run(() -> {
            return delegate.listMethodsForType(type, isStatic);
        });
    }

    @Override
    public JsonElement call(String objectId, String methodName, JsonElement params) throws CordraException {
        return run(() -> {
            return delegate.call(objectId, methodName, params);
        });
    }

    @Override
    public JsonElement callForType(String type, String methodName, JsonElement params) throws CordraException {
        return run(() -> {
            return delegate.callForType(type, methodName, params);
        });
    }

    @Override
    public CordraObject get(String id, Options options) throws CordraException {
        return run(() -> {
            return delegate.get(id, options);
        });
    }

    @Override
    public InputStream getPayload(String id, String payloadName, Options options) throws CordraException {
        return run(() -> {
            return delegate.getPayload(id, payloadName, options);
        });
    }

    @Override
    public InputStream getPartialPayload(String id, String payloadName, Long start, Long end, Options options) throws CordraException {
        return run(() -> {
            return delegate.getPartialPayload(id, payloadName, start, end, options);
        });
    }

    @Override
    public CordraObject create(CordraObject d, Options options) throws CordraException {
        return run(() -> {
            return delegate.create(d, options);
        });
    }

    @Override
    public CordraObject update(CordraObject d, Options options) throws CordraException {
        return run(() -> {
            return delegate.update(d, options);
        });
    }

    @Override
    public void delete(String id, Options options) throws CordraException {
        run(() -> {
            delegate.delete(id, options);
        });
    }

    @Override
    public SearchResults<CordraObject> search(String query, QueryParams params, Options options) throws CordraException {
        return run(() -> {
            return delegate.search(query, params, options);
        });
    }

    @Override
    public SearchResults<String> searchHandles(String query, QueryParams params, Options options) throws CordraException {
        return run(() -> {
            return delegate.searchHandles(query, params, options);
        });
    }

    @Override
    public VersionInfo publishVersion(String objectId, String versionId, boolean clonePayloads) throws CordraException {
        return run(() -> {
            return delegate.publishVersion(objectId, versionId, clonePayloads);
        });
    }

    @Override
    public VersionInfo publishVersion(String objectId, String versionId, boolean clonePayloads, Options options) throws CordraException {
        return run(() -> {
            return delegate.publishVersion(objectId, versionId, clonePayloads, options);
        });
    }

    @Override
    public VersionInfo publishVersion(String objectId, String versionId, boolean clonePayloads, String username, String password) throws CordraException {
        return run(() -> {
            return delegate.publishVersion(objectId, versionId, clonePayloads, username, password);
        });
    }

    @Override
    public List<VersionInfo> getVersionsFor(String objectId) throws CordraException {
        return run(() -> {
            return delegate.getVersionsFor(objectId);
        });
    }

    @Override
    public List<VersionInfo> getVersionsFor(String objectId, String username, String password) throws CordraException {
        return run(() -> {
            return delegate.getVersionsFor(objectId, username, password);
        });
    }

    @Override
    public List<VersionInfo> getVersionsFor(String objectId, Options options) throws CordraException {
        return run(() -> {
            return delegate.getVersionsFor(objectId, options);
        });
    }

    @Override
    public void close() throws IOException, CordraException {
        delegate.close();
    }

    public static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX").withZone(ZoneOffset.UTC);

    public <R> R run(Callable<R> c) throws CordraException {
        long start = System.currentTimeMillis();
        try {
            R result = c.call();
            return result;
        } catch (RuntimeException | Error | CordraException e) {
            throw e;
        } catch (Exception e) {
            throw new AssertionError("Doesn't actually throw this", e);
        } finally {
            long end = System.currentTimeMillis();
            long delta = end - start;
            String caller = getCallingFunction();
            String startTime = dateTimeFormatter.format(Instant.ofEpochMilli(start));
            logger.trace(caller + ": start " + startTime + ", " + delta + "ms");
        }
    }

    public void run(ThrowingRunnable r) throws CordraException {
        long start = System.currentTimeMillis();
        try {
            r.run();
        } catch (RuntimeException | Error | CordraException e) {
            throw e;
        } catch (Exception e) {
            throw new AssertionError("Doesn't actually throw this", e);
        } finally {
            long end = System.currentTimeMillis();
            long delta = end - start;
            String caller = getCallingFunction();
            String startTime = dateTimeFormatter.format(Instant.ofEpochMilli(start));
            logger.trace(caller + ": start " + startTime + ", " + delta + "ms");
        }
    }

    @FunctionalInterface
    public static interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static String getCallingFunction() {
        StackTraceElement[] trace = new Throwable().getStackTrace();
        if (trace.length > 3) {
            String callingFunction = trace[2].getMethodName();
            String serviceInfo = getServiceInfo(trace[3]);
            return callingFunction + " " + serviceInfo;
        } else {
            return "Unknown caller";
        }
    }

    private static String getServiceInfo(StackTraceElement el) {
        String className = el.getClassName();
        if (className.contains(".")) {
            className = className.substring(className.lastIndexOf('.') + 1);
        }
        return className + "." + el.getMethodName() + "(" + el.getFileName() + ":" + el.getLineNumber() + ")";
    }
}
