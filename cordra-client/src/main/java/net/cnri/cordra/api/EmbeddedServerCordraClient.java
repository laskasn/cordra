/*************************************************************************\
    Copyright (c) 2019 Corporation for National Research Initiatives;
                        All rights reserved.
\*************************************************************************/

package net.cnri.cordra.api;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.eclipse.jetty.annotations.AnnotationConfiguration;
import org.eclipse.jetty.server.NetworkConnector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.webapp.Configuration;
import org.eclipse.jetty.webapp.JettyWebXmlConfiguration;
import org.eclipse.jetty.webapp.WebAppContext;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

public class EmbeddedServerCordraClient implements CordraClient {

    // retain for legacy
    @Deprecated
    public static EmbeddedServerCordraClient newInstance(URL cordraWarResource) throws Exception {
        Path cordraWarPath = Paths.get(cordraWarResource.toURI());
        return newInstance(cordraWarPath);
    }

    public static EmbeddedServerCordraClient newInstance(Path cordraWarPath) throws Exception {
        return newInstance(cordraWarPath, -1, null);
    }

    public static EmbeddedServerCordraClient newInstance(Path cordraWarPath, int mongoPort, Function<String, CordraClient> clientProvider) throws Exception {
        String mongoConfig = null;
        if (mongoPort > -1) {
            mongoConfig = "{\"storage\": { \"module\" : \"mongodb\", \"options\" : {\"connectionUri\" : \"mongodb://localhost:" + mongoPort + "\"}}," +
                "\"sessions\": { \"module\" : \"mongodb\", \"options\" : {\"connectionUri\" : \"mongodb://localhost:" + mongoPort + "\"}}}";
        }
        return newInstanceWithConfig(cordraWarPath, mongoConfig, clientProvider);
    }

    @SuppressWarnings("resource")
    public static EmbeddedServerCordraClient newInstanceWithConfig(Path cordraWarPath, String config, Function<String, CordraClient> clientProvider) throws Exception {
        Path tempDir = Files.createTempDirectory("cordra.data");
        System.setProperty("cordra.data", tempDir.toString());

        Path repoInitJsonPath = tempDir.resolve("repoInit.json");
        JsonObject repoInit = new JsonObject();
        repoInit.addProperty("adminPassword", "changeit");
        JsonObject design = new JsonObject();
        design.addProperty("allowInsecureAuthentication", true);
        repoInit.add("design", design);
        JsonObject adminPublicKey = new JsonObject();
        design.add("adminPublicKey", adminPublicKey);
        adminPublicKey.addProperty("kty", "RSA");
        adminPublicKey.addProperty("n", "rLt4enTZI4QnM8mHE_nIIzaN8ZROcHl07H4tSgSRFHATq_ZKfelVngTM-3_bHK-Z-f_bbIbScuQ0an7LiKdJUR-rXEJpXHT11DSL1CDKEWylWELiG-pMO01HtIH96anb2N5JUFffhyidaOElWdTVIL6XmU_Uimif6335xbV_Ubk");
        adminPublicKey.addProperty("e", "AQAB");
        String repoInitJson = repoInit.toString();
        Files.write(repoInitJsonPath, repoInitJson.getBytes(StandardCharsets.UTF_8));

        if (config != null) {
            Path cordraConfigPath = tempDir.resolve("config.json");
            Files.write(cordraConfigPath, config.getBytes(StandardCharsets.UTF_8));
        }

        Server server = new Server(0);
        Configuration.ClassList classlist = Configuration.ClassList.setServerDefault(server);
        classlist.addBefore(JettyWebXmlConfiguration.class.getName(), AnnotationConfiguration.class.getName());
        WebAppContext webapp = new WebAppContext();
// parentLoaderPriority may be useful for some classpath issues in testing, but let's make it an option if we never need it, and not change the classloading behavior on external users of this class
//        webapp.setParentLoaderPriority(true);
        webapp.setContextPath("/cordra");
        webapp.setWar(cordraWarPath.toString());
        webapp.getSystemClasspathPattern().add("org.slf4j.");
        webapp.getSystemClasspathPattern().add("org.apache.log4j.");
        webapp.getSystemClasspathPattern().add("org.apache.logging.");
        webapp.getSystemClasspathPattern().add("org.apache.commons.logging.");
        webapp.getSystemClasspathPattern().add("com.google.gson.");
        webapp.getSystemClasspathPattern().add("net.cnri.cordra.");
        ContextHandlerCollection handlers = new ContextHandlerCollection();
        handlers.addHandler(webapp);
        server.setHandler(handlers);
        server.start();
        int port = ((NetworkConnector) server.getConnectors()[0]).getLocalPort();
        String cordraUri = "http://localhost:" + port + "/cordra/";
        if (clientProvider == null) {
            clientProvider = uri -> {
                try {
                    return new TokenUsingHttpCordraClient(uri, "admin", "changeit");
                } catch (CordraException e) {
                    throw new RuntimeException(e);
                }
            };
        }
        CordraClient client = clientProvider.apply(cordraUri);
        return new EmbeddedServerCordraClient(tempDir, port, server, client);
    }

    private final Server server;
    private final Path dataDir;
    private final String baseUri;
    private final CordraClient client;
    private boolean closed = false;

    private EmbeddedServerCordraClient(Path dataDir, int port, Server server, CordraClient client) {
        this.baseUri = "http://localhost:" + port + "/cordra/";
        this.server = server;
        this.dataDir = dataDir;
        this.client = client;
    }

    public String getBaseUri() {
        return baseUri;
    }

    public Server getServer() {
        return server;
    }

    @Override
    public void close() throws IOException, CordraException {
        client.close();
        synchronized (this) {
            if (!closed) {
                closed = true;
                try {
                    server.stop();
                    deleteDirectory(dataDir);
                } catch (IOException e) {
                    throw e;
                } catch (Exception e) {
                    throw new IOException(e);
                }
            }
        }
    }

    private static void deleteDirectory(Path dataDir) throws IOException {
        Files.walkFileTree(dataDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

//    public static void main(String[] args) throws Exception {
//        BasicConfigurator.configure();
//        EmbeddedServerCordraClient.newInstance(Paths.get("/Users/bhadden/cordra.war"));
//        // close
//    }


    @Override
    public Gson getGson() {
        return client.getGson();
    }

    @Override
    public void setGson(Gson gson) {
        client.setGson(gson);
    }

    @Override
    public CordraObject get(String id) throws CordraException {
        return client.get(id);
    }

    @Override
    public CordraObject get(String id, String username, String password) throws CordraException {
        return client.get(id, username, password);
    }

    @Override
    public CordraObject get(String id, Options options) throws CordraException {
        return client.get(id, options);
    }

    @Override
    public InputStream getPayload(String id, String payloadName) throws CordraException {
        return client.getPayload(id, payloadName);
    }

    @Override
    public InputStream getPayload(String id, String payloadName, String username, String password) throws CordraException {
        return client.getPayload(id, payloadName, username, password);
    }

    @Override
    public InputStream getPayload(String id, String payloadName, Options options) throws CordraException {
        return client.getPayload(id, payloadName, options);
    }

    @Override
    public InputStream getPartialPayload(String id, String payloadName, Long start, Long end) throws CordraException {
        return client.getPartialPayload(id, payloadName, start, end);
    }

    @Override
    public InputStream getPartialPayload(String id, String payloadName, Long start, Long end, String username, String password) throws CordraException {
        return client.getPartialPayload(id, payloadName, start, end, username, password);
    }

    @Override
    public InputStream getPartialPayload(String id, String payloadName, Long start, Long end, Options options) throws CordraException {
        return client.getPartialPayload(id, payloadName, start, end, options);
    }

    @Override
    public CordraObject create(CordraObject d) throws CordraException {
        return client.create(d);
    }

    @Override
    public CordraObject create(CordraObject d, String username, String password) throws CordraException {
        return client.create(d, username, password);
    }

    @Override
    public CordraObject create(CordraObject d, boolean isDryRun, String username, String password) throws CordraException {
        return client.create(d, isDryRun, username, password);
    }

    @Override
    public CordraObject create(CordraObject d, boolean isDryRun) throws CordraException {
        return client.create(d, isDryRun);
    }

    @Override
    public CordraObject create(CordraObject d, Options options) throws CordraException {
        return client.create(d, options);
    }

    @Override
    public CordraObject update(CordraObject d) throws CordraException {
        return client.update(d);
    }

    @Override
    public CordraObject update(CordraObject d, String username, String password) throws CordraException {
        return client.update(d, username, password);
    }

    @Override
    public CordraObject update(CordraObject d, boolean isDryRun, String username, String password) throws CordraException {
        return client.update(d, isDryRun, username, password);
    }

    @Override
    public CordraObject update(CordraObject d, boolean isDryRun) throws CordraException {
        return client.update(d, isDryRun);
    }

    @Override
    public CordraObject update(CordraObject d, Options options) throws CordraException {
        return client.update(d, options);
    }

    @Override
    public List<String> listMethods(String objectId) throws CordraException {
        return client.listMethods(objectId);
    }

    @Override
    public List<String> listMethods(String objectId, String username, String password) throws CordraException {
        return client.listMethods(objectId, username, password);
    }

    @Override
    public List<String> listMethods(String objectId, Options options) throws CordraException {
        return client.listMethods(objectId, options);
    }

    @Override
    public List<String> listMethodsForType(String type, boolean isStatic) throws CordraException {
        return client.listMethodsForType(type, isStatic);
    }

    @Override
    public List<String> listMethodsForType(String type, boolean isStatic, String username, String password) throws CordraException {
        return client.listMethodsForType(type, isStatic, username, password);
    }

    @Override
    public List<String> listMethodsForType(String type, boolean isStatic, Options options) throws CordraException {
        return client.listMethodsForType(type, isStatic, options);
    }

    @Override
    public JsonElement call(String objectId, String methodName, JsonElement params) throws CordraException {
        return client.call(objectId, methodName, params);
    }

    @Override
    public JsonElement call(String objectId, String methodName, JsonElement params, String username, String password) throws CordraException {
        return client.call(objectId, methodName, params, username, password);
    }

    @Override
    public JsonElement call(String objectId, String methodName, JsonElement params, Options options) throws CordraException {
        return client.call(objectId, methodName, params, options);
    }

    @Override
    public JsonElement callForType(String type, String methodName, JsonElement params) throws CordraException {
        return client.callForType(type, methodName, params);
    }

    @Override
    public JsonElement callForType(String type, String methodName, JsonElement params, String username, String password) throws CordraException {
        return client.callForType(type, methodName, params, username, password);
    }

    @Override
    public JsonElement callForType(String type, String methodName, JsonElement params, Options options) throws CordraException {
        return client.callForType(type, methodName, params, options);
    }

    @Override
    public VersionInfo publishVersion(String objectId, String versionId, boolean clonePayloads) throws CordraException {
        return client.publishVersion(objectId, versionId, clonePayloads);
    }

    @Override
    public VersionInfo publishVersion(String objectId, String versionId, boolean clonePayloads, String username, String password) throws CordraException {
        return client.publishVersion(objectId, versionId, clonePayloads, username, password);
    }

    @Override
    public VersionInfo publishVersion(String objectId, String versionId, boolean clonePayloads, Options options) throws CordraException {
        return client.publishVersion(objectId, versionId, clonePayloads, options);
    }

    @Override
    public List<VersionInfo> getVersionsFor(String objectId) throws CordraException {
        return client.getVersionsFor(objectId);
    }

    @Override
    public List<VersionInfo> getVersionsFor(String objectId, String username, String password) throws CordraException {
        return client.getVersionsFor(objectId, username, password);
    }

    @Override
    public List<VersionInfo> getVersionsFor(String id, Options options) throws CordraException {
        return client.getVersionsFor(id, options);
    }

    @Override
    public void delete(String id) throws CordraException {
        client.delete(id);
    }

    @Override
    public void delete(String id, String username, String password) throws CordraException {
        client.delete(id, username, password);
    }

    @Override
    public void delete(String id, Options options) throws CordraException {
        client.delete(id, options);
    }

    @Override
    public SearchResults<CordraObject> list() throws CordraException {
        return client.list();
    }

    @Override
    public SearchResults<String> listHandles() throws CordraException {
        return client.listHandles();
    }

    @Override
    public SearchResults<CordraObject> list(Options options) throws CordraException {
        return client.list(options);
    }

    @Override
    public SearchResults<String> listHandles(Options options) throws CordraException {
        return client.listHandles(options);
    }

    @Override
    public SearchResults<CordraObject> search(String query) throws CordraException {
        return client.search(query);
    }

    @Override
    public SearchResults<CordraObject> search(String query, String username, String password) throws CordraException {
        return client.search(query, username, password);
    }

    @Override
    public SearchResults<CordraObject> search(String query, Options options) throws CordraException {
        return client.search(query, options);
    }

    @Override
    public SearchResults<CordraObject> search(String query, QueryParams params) throws CordraException {
        return client.search(query, params);
    }

    @Override
    public SearchResults<CordraObject> search(String query, QueryParams params, String username, String password) throws CordraException {
        return client.search(query, params, username, password);
    }

    @Override
    public SearchResults<CordraObject> search(String query, QueryParams params, Options options) throws CordraException {
        return client.search(query, params, options);
    }

    @Override
    public SearchResults<String> searchHandles(String query) throws CordraException {
        return client.searchHandles(query);
    }

    @Override
    public SearchResults<String> searchHandles(String query, String username, String password) throws CordraException {
        return client.searchHandles(query, username, password);
    }

    @Override
    public SearchResults<String> searchHandles(String query, Options options) throws CordraException {
        return client.searchHandles(query, options);
    }

    @Override
    public SearchResults<String> searchHandles(String query, QueryParams params) throws CordraException {
        return client.searchHandles(query, params);
    }

    @Override
    public SearchResults<String> searchHandles(String query, QueryParams params, String username, String password) throws CordraException {
        return client.searchHandles(query, params, username, password);
    }

    @Override
    public SearchResults<String> searchHandles(String query, QueryParams params, Options options) throws CordraException {
        return client.searchHandles(query, params, options);
    }

    @Override
    public boolean authenticate() throws CordraException {
        return client.authenticate();
    }

    @Override
    public boolean authenticate(String username, String password) throws CordraException {
        return client.authenticate(username, password);
    }

    @Override
    public AuthResponse authenticateAndGetResponse() throws CordraException {
        return client.authenticateAndGetResponse();
    }

    @Override
    public AuthResponse authenticateAndGetResponse(String username, String password) throws CordraException {
        return client.authenticateAndGetResponse(username, password);
    }

    @Override
    public AuthResponse authenticateAndGetResponse(Options options) throws CordraException {
        return client.authenticateAndGetResponse(options);
    }

    @Override
    public void changePassword(String newPassword) throws CordraException {
        client.changePassword(newPassword);
    }

    @Override
    public void changePassword(String username, String password, String newPassword) throws CordraException {
        client.changePassword(username, password, newPassword);
    }

    @Override
    public String getContentAsJson(String id) throws CordraException {
        return client.getContentAsJson(id);
    }

    @Override
    public <T> T getContent(String id, Class<T> klass) throws CordraException {
        return client.getContent(id, klass);
    }

    @Override
    public CordraObject create(String type, String contentJson) throws CordraException {
        return client.create(type, contentJson);
    }

    @Override
    public CordraObject create(String type, String contentJson, String username, String password) throws CordraException {
        return client.create(type, contentJson, username, password);
    }

    @Override
    public CordraObject update(String id, String contentJson) throws CordraException {
        return client.update(id, contentJson);
    }

    @Override
    public CordraObject update(String id, String contentJson, String username, String password) throws CordraException {
        return client.update(id, contentJson, username, password);
    }

    @Override
    public CordraObject create(String type, Object content) throws CordraException {
        return client.create(type, content);
    }

    @Override
    public CordraObject create(String type, Object content, String username, String password) throws CordraException {
        return client.create(type, content, username, password);
    }

    @Override
    public CordraObject update(String id, Object content) throws CordraException {
        return client.update(id, content);
    }

    @Override
    public CordraObject create(String type, String contentJson, boolean isDryRun) throws CordraException {
        return client.create(type, contentJson, isDryRun);
    }

    @Override
    public CordraObject create(String type, String contentJson, boolean isDryRun, String username, String password) throws CordraException {
        return client.create(type, contentJson, isDryRun, username, password);
    }

    @Override
    public CordraObject update(String id, String contentJson, boolean isDryRun) throws CordraException {
        return client.update(id, contentJson, isDryRun);
    }

    @Override
    public CordraObject update(String id, String contentJson, boolean isDryRun, String username, String password) throws CordraException {
        return client.update(id, contentJson, isDryRun, username, password);
    }

    @Override
    public CordraObject create(String type, Object content, boolean isDryRun) throws CordraException {
        return client.create(type, content, isDryRun);
    }

    @Override
    public CordraObject create(String type, Object content, boolean isDryRun, String username, String password) throws CordraException {
        return client.create(type, content, isDryRun, username, password);
    }

    @Override
    public CordraObject update(String id, Object content, boolean isDryRun) throws CordraException {
        return client.update(id, content, isDryRun);
    }

    @Override
    public void reindexBatch(List<String> batchIds, Options options) throws CordraException {
        client.reindexBatch(batchIds, options);
    }

    @Override
    public SearchResults<CordraObject> get(Collection<String> ids) throws CordraException {
        return client.get(ids);
    }

    @Override
    public SearchResults<CordraObject> listByType(List<String> types) throws CordraException {
        return client.listByType(types);
    }

    @Override
    public SearchResults<String> listHandlesByType(List<String> types) throws CordraException {
        return client.listHandlesByType(types);
    }
}
