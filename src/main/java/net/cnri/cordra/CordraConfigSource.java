package net.cnri.cordra;

import net.cnri.cordra.indexer.IndexerConfig;
import net.cnri.cordra.model.CordraConfig;
import net.cnri.cordra.storage.StorageConfig;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.KeeperException;

import javax.servlet.ServletContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CordraConfigSource {
    private static final String ZK_CONFIG_PATH = "/config.json";
    private static CordraConfig cordraConfig = null;

    public synchronized static CordraConfig getConfig(ServletContext context) throws IOException {
        if (cordraConfig != null) {
            return cordraConfig;
        }
        CordraConfig config = findAndGetConfigFromZooKeeper(context);
        if (config == null) config = getConfigFromContextAttribute(context);
        if (config == null) config = getConfigFromWar(context);
        if (config == null) config = getConfigFromPropertyOrEnv();
        if (config == null) config = getConfigFromPathInPropertyOrEnv(context);
        if (config == null) config = getConfigFromCordraData();
        cordraConfig = getDefaultConfigIfNull(config);
        return cordraConfig;
    }

    public static CordraConfig getConfigForTesting(String zookeeperConnectionString) throws Exception {
        if (zookeeperConnectionString != null) {
            return getConfigFromZooKeeper(zookeeperConnectionString, null);
        } else {
            return getDefaultConfigIfNull(null);
        }
    }

    public static CordraConfig getDefaultConfigIfNull(CordraConfig config) {
        if (config == null) config = CordraConfig.getNewDefaultInstance();
        if (config.index == null) {
            config.index = IndexerConfig.getNewDefaultInstance();
        }
        if (config.storage == null) {
            config.storage = StorageConfig.getNewDefaultInstance();
        }
        return config;
    }

    private static CordraConfig findAndGetConfigFromZooKeeper(ServletContext context) {
        CordraConfig config = null;
        String zookeeperConnectionString = getZkConnectionString(context);
        if (zookeeperConnectionString != null) {
            String configName = getZkConfigName(context);
            try {
                config = getConfigFromZooKeeper(zookeeperConnectionString, configName);
            } catch (Exception e) {
                // no-op, continue on
            }
        }
        return config;
    }

    private static String getZkConfigName(ServletContext context) {
        String configName = context.getInitParameter("configName");
        if (configName == null) configName = System.getProperty("configName");
        if (configName == null) configName = System.getenv("configName");
        if (configName == null) configName = context.getInitParameter("config_name");
        if (configName == null) configName = System.getProperty("config_name");
        if (configName == null) configName = System.getenv("config_name");
        return configName;
    }

    private static String getZkConnectionString(ServletContext context) {
        String zookeeperConnectionString = context.getInitParameter("zookeeperConnectionString");
        if (zookeeperConnectionString == null) {
            zookeeperConnectionString = System.getProperty("zookeeperConnectionString");
        }
        if (zookeeperConnectionString == null) {
            zookeeperConnectionString = System.getenv("zookeeperConnectionString");
        }
        if (zookeeperConnectionString == null) {
            zookeeperConnectionString = context.getInitParameter("zookeeper_connection_string");
        }
        if (zookeeperConnectionString == null) {
            zookeeperConnectionString = System.getProperty("zookeeper_connection_string");
        }
        if (zookeeperConnectionString == null) {
            zookeeperConnectionString = System.getenv("zookeeper_connection_string");
        }
        return zookeeperConnectionString;
    }

    private static CordraConfig getConfigFromCordraData() throws IOException {
        CordraConfig config = null;
        String dataDir = System.getProperty(Constants.CORDRA_DATA);
        if (dataDir == null) dataDir = System.getenv(Constants.CORDRA_DATA);
        if (dataDir == null) {
            String dataWithUnderscores = Constants.CORDRA_DATA.replace(".", "_");
            dataDir = System.getProperty(dataWithUnderscores);
            if (dataDir == null) dataDir = System.getenv(dataWithUnderscores);
        }
        if (dataDir != null) {
            Path path = Paths.get(dataDir).resolve("config.json");
            if (Files.exists(path)) {
                try (Reader in = Files.newBufferedReader(path)) {
                    config = GsonUtility.getGson().fromJson(in, CordraConfig.class);
                }
            }
        }
        return config;
    }

    private static CordraConfig getConfigFromPathInPropertyOrEnv(ServletContext context) throws IOException {
        CordraConfig config = null;
        String configJsonPath = context.getInitParameter("config.json.path");
        if (configJsonPath == null) configJsonPath = System.getProperty("config.json.path");
        if (configJsonPath == null) configJsonPath = System.getenv("config.json.path");
        if (configJsonPath == null) configJsonPath = context.getInitParameter("config_json_path");
        if (configJsonPath == null) configJsonPath = System.getProperty("config_json_path");
        if (configJsonPath == null) configJsonPath = System.getenv("config_json_path");
        if (configJsonPath != null) {
            try (Reader in = Files.newBufferedReader(Paths.get(configJsonPath))) {
                config = GsonUtility.getGson().fromJson(in, CordraConfig.class);
            }
        }
        return config;
    }

    private static CordraConfig getConfigFromPropertyOrEnv() {
        CordraConfig config = null;
        String configJson = System.getProperty("config.json");
        if (configJson == null) configJson = System.getenv("config.json");
        if (configJson == null) configJson = System.getProperty("config_json");
        if (configJson == null) configJson = System.getenv("config_json");
        if (configJson != null) {
            config = GsonUtility.getGson().fromJson(configJson, CordraConfig.class);
        }
        return config;
    }

    private static CordraConfig getConfigFromWar(ServletContext context) throws IOException {
        CordraConfig config = null;
        try (InputStream in = context.getResourceAsStream("/WEB-INF/config.json")) {
            if (in != null) {
                try (InputStreamReader isr = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    config = GsonUtility.getGson().fromJson(isr, CordraConfig.class);
                }
            }
        }
        return config;
    }

    private static CordraConfig getConfigFromContextAttribute(ServletContext context) {
        CordraConfig config = null;
        String configJson = (String) context.getAttribute("config.json");
        if (configJson == null) configJson = (String) context.getAttribute("config_json");
        if (configJson != null) {
            config = GsonUtility.getGson().fromJson(configJson, CordraConfig.class);
        }
        return config;
    }

    private static CordraConfig getConfigFromZooKeeper(String zookeeperConnectionString, String configName) throws Exception {
        ExponentialBackoffRetry retryPolicy = new ExponentialBackoffRetry(1000, 3);
        CuratorFramework client = CuratorFrameworkFactory.newClient(zookeeperConnectionString, retryPolicy);
        client.start();
        if (configName == null || configName.isEmpty()) configName = ZK_CONFIG_PATH;
        if (!configName.startsWith("/")) configName = "/" + configName;
        CordraConfig config;
        try {
            byte[] configBytes = client.getData().forPath(configName);
            config = GsonUtility.getGson().fromJson(new String(configBytes, StandardCharsets.UTF_8), CordraConfig.class);
        } catch (KeeperException.NoNodeException e) {
            config = null;
        }
        client.close();
        return config;
    }
}
