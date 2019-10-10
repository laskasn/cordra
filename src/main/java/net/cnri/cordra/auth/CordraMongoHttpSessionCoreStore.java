package net.cnri.cordra.auth;

import net.cnri.cordra.CordraConfigSource;
import net.cnri.cordra.model.CordraConfig;
import net.cnri.servletcontainer.sessions.HttpSessionManager;
import net.cnri.servletcontainer.sessions.mongo.MongoSessionCoreStore;

import javax.servlet.ServletContext;

import java.io.IOException;
import java.util.Collections;

public class CordraMongoHttpSessionCoreStore extends MongoSessionCoreStore {

    private String connectionUri;
    private String databaseName;
    private String collectionName;

    @Override
    public void init(HttpSessionManager sessionManager, String sessionCoreStoreInit) {
        ServletContext servletContext = sessionManager.getServletContext();
        CordraConfig.SessionsConfig config;
        try {
            config = CordraConfigSource.getConfig(servletContext).sessions;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (config.options == null) config.options = Collections.emptyMap();
        connectionUri = config.options.getOrDefault("connectionUri", "mongodb://localhost:27017");
        databaseName = config.options.getOrDefault("databaseName", "cordra");
        collectionName = config.options.getOrDefault("collectionName", "sessions");
        super.init(sessionManager, sessionCoreStoreInit);
    }

    @Override
    protected String getConnectionUri(HttpSessionManager sessionManager, String sessionCoreStoreInit) {
        return connectionUri;
    }

    @Override
    protected String getDatabaseName() {
        return databaseName;
    }

    @Override
    protected String getCollectionName() {
        return collectionName;
    }

    @Override
    protected Object serialize(String key, Object value) {
        return value;
    }

    @Override
    protected Object deserialize(String key, Object value) {
        return value;
    }


}
