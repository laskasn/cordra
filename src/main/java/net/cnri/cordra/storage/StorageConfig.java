package net.cnri.cordra.storage;

import com.google.gson.JsonObject;

public class StorageConfig {
    public String module = "hds"; //mongodb | bdbje | hds | s3 | custom
    public String className; //fully qualified class to custom implementation of CordraStorage, only used when module is "custom"
    public JsonObject options = new JsonObject();

    public static StorageConfig getNewDefaultInstance() {
        StorageConfig storageConfig = new StorageConfig();
        return storageConfig;
    }
}
