package net.cnri.cordra.api;

import com.google.gson.JsonObject;

import java.security.PrivateKey;

public class Options {

    public String userId;
    public String username;
    public String password;
    public String asUserId;
    public PrivateKey privateKey;
    public String token;
    public boolean isDryRun;
    public boolean useDefaultCredentials = false;
    public boolean reindexBatchLockObjects = true;
    public JsonObject requestContext;

    public Options() { }

    public String getUsername() {
        return username;
    }

    public Options setUsername(String username) {
        this.username = username;
        return this;
    }

    public String getPassword() {
        return password;
    }

    public Options setPassword(String password) {
        this.password = password;
        return this;
    }

    public String getAsUserId() {
        return asUserId;
    }

    public Options setAsUserId(String asUserId) {
        this.asUserId = asUserId;
        return this;
    }

    public boolean isDryRun() {
        return isDryRun;
    }

    public Options setDryRun(boolean isDryRun) {
        this.isDryRun = isDryRun;
        return this;
    }

    public boolean isUseDefaultCredentials() {
        return useDefaultCredentials;
    }

    public Options setUseDefaultCredentials(boolean useDefaultCredentials) {
        this.useDefaultCredentials = useDefaultCredentials;
        return this;
    }

    public String getUserId() {
        return userId;
    }

    public Options setUserId(String userId) {
        this.userId = userId;
        return this;
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    public Options setPrivateKey(PrivateKey privateKey) {
        this.privateKey = privateKey;
        return this;
    }

    public String getToken() {
        return token;
    }

    public Options setToken(String token) {
        this.token = token;
        return this;
    }

    public Options setRequestContext(JsonObject requestContext) {
        this.requestContext = requestContext;
        return this;
    }
}
