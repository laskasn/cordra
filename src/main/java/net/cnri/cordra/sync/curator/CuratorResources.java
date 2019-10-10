package net.cnri.cordra.sync.curator;

import net.cnri.cordra.CordraConfigSource;
import net.cnri.cordra.model.CordraConfig;
import net.cnri.cordra.model.ReprocessingQueueConfig;
import net.cnri.cordra.model.SignalWatcherConfig;
import net.cnri.cordra.sync.*;
import net.cnri.cordra.sync.local.MemoryKeyPairAuthJtiChecker;
import net.cnri.microservices.Alerter;
import net.handle.hdllib.Util;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContext;
import java.security.PrivateKey;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CuratorResources implements SyncObjects {
    private static final Logger logger = LoggerFactory.getLogger(CuratorResources.class);

    private static final String PRIVATE_KEY_PATH = "/privatekey";
    private static final String PRIVATE_KEY_BASE64_PATH = "/privatekey.base64";
    private static final String REPO_INIT_PATH = "/repoInit.json";
    private static final String STARTUP_LOCKER_PATH = "/locks/startup";
    private static final String DESIGN_LOCKER_PATH = "/locks/design";
    private static final String SCHEMA_NAME_LOCKER_PATH = "/locks/schemaName";
    private static final String USERNAME_LOCKER_PATH = "/locks/username";
    private static final String OBJECT_LOCKER_PATH = "/locks/objects";

    private final CuratorFramework client;
    private final ExecutorService execServ;
    private final Alerter alerter;
    private final CheckableLocker startupLocker;
    private final RepoInitProvider repoInitProvider;
    private final TransactionManager transactionManager;
    private final LeadershipManager leadershipManager;
    private final SingleThreadReadWriteCheckableLocker designLocker;
    private final SignalWatcher signalWatcher;
    private final CheckableLocker schemaNameLocker;
    private final CheckableLocker usernameLocker;
    private final NameLocker objectLocker;
    private final AllHandlesUpdaterSync allHandlesUpdaterSync;
    private final TransactionReprocessingQueue transactionReprocessingQueue;
    private final KeyPairAuthJtiChecker keyPairAuthJtiChecker;
    private final CordraConfig cordraConfig;

    public CuratorResources(ServletContext context, Alerter alerter) throws Exception {
        String zookeeperConnectionString = context.getInitParameter("zookeeperConnectionString");
        zookeeperConnectionString = addChrootIfNeeded(zookeeperConnectionString, "cordra");
        ExponentialBackoffRetry retryPolicy = new ExponentialBackoffRetry(1000, 3);
        client = CuratorFrameworkFactory.newClient(zookeeperConnectionString, retryPolicy);
        client.start();
        execServ = Executors.newCachedThreadPool();
        this.alerter = alerter;
        this.startupLocker = new CuratorCheckableLocker(client, STARTUP_LOCKER_PATH, execServ, alerter);
        this.schemaNameLocker = new CuratorCheckableLocker(client, SCHEMA_NAME_LOCKER_PATH, execServ, alerter);
        this.usernameLocker = new CuratorCheckableLocker(client, USERNAME_LOCKER_PATH, execServ, alerter);
        this.objectLocker = new CuratorNameLocker(client, OBJECT_LOCKER_PATH, execServ, alerter);
        this.repoInitProvider = new CuratorRepoInitProvider(client, REPO_INIT_PATH);
        this.designLocker = new CuratorSingleThreadReadWriteCheckableLocker(client, DESIGN_LOCKER_PATH, execServ, alerter);
        this.allHandlesUpdaterSync = new CuratorAllHandlesUpdaterSync(client);
        this.transactionManager = new CuratorTransactionManager(client, execServ);
        this.leadershipManager = new CuratorLeadershipManager(client, execServ);
        this.cordraConfig = CordraConfigSource.getConfig(context);
        SignalWatcherConfig signalWatcherConfig = getSignalWatcherConfig(cordraConfig);
        if (signalWatcherConfig == null || "zk".equals(signalWatcherConfig.type)) {
            this.signalWatcher = new CuratorSignalWatcher(client, alerter);
        } else {
            this.signalWatcher = new KafkaSignalWatcher(signalWatcherConfig);
        }
        ReprocessingQueueConfig reprocessQueueConfig = getReprocessQueueConfig(cordraConfig);
        if (reprocessQueueConfig == null) {
            this.transactionReprocessingQueue = null;
        } else {
            this.transactionReprocessingQueue = new KafkaTransactionReprocessingQueue(reprocessQueueConfig);
        }

        this.keyPairAuthJtiChecker = new MemoryKeyPairAuthJtiChecker();
    }

    private CuratorResources(String zookeeperConnectionString, Alerter alerter) throws Exception {
        zookeeperConnectionString = addChrootIfNeeded(zookeeperConnectionString, "cordra");
        ExponentialBackoffRetry retryPolicy = new ExponentialBackoffRetry(1000, 3);
        client = CuratorFrameworkFactory.newClient(zookeeperConnectionString, retryPolicy);
        client.start();
        execServ = Executors.newCachedThreadPool();
        this.alerter = alerter;
        this.startupLocker = new CuratorCheckableLocker(client, STARTUP_LOCKER_PATH, execServ, alerter);
        this.schemaNameLocker = new CuratorCheckableLocker(client, SCHEMA_NAME_LOCKER_PATH, execServ, alerter);
        this.usernameLocker = new CuratorCheckableLocker(client, USERNAME_LOCKER_PATH, execServ, alerter);
        this.objectLocker = new CuratorNameLocker(client, OBJECT_LOCKER_PATH, execServ, alerter);
        this.repoInitProvider = new CuratorRepoInitProvider(client, REPO_INIT_PATH);
        this.designLocker = new CuratorSingleThreadReadWriteCheckableLocker(client, DESIGN_LOCKER_PATH, execServ, alerter);
        this.allHandlesUpdaterSync = new CuratorAllHandlesUpdaterSync(client);
        this.transactionManager = new CuratorTransactionManager(client, execServ);
        this.leadershipManager = new CuratorLeadershipManager(client, execServ);
        this.cordraConfig = CordraConfigSource.getConfigForTesting(zookeeperConnectionString);
        SignalWatcherConfig signalWatcherConfig = getSignalWatcherConfig(cordraConfig);
        if (signalWatcherConfig == null || "zk".equals(signalWatcherConfig.type)) {
            this.signalWatcher = new CuratorSignalWatcher(client, alerter);
        } else {
            this.signalWatcher = new KafkaSignalWatcher(signalWatcherConfig);
        }
        ReprocessingQueueConfig reprocessQueueConfig = getReprocessQueueConfig(cordraConfig);
        if (reprocessQueueConfig == null) {
            this.transactionReprocessingQueue = null;
        } else {
            this.transactionReprocessingQueue = new KafkaTransactionReprocessingQueue(reprocessQueueConfig);
        }

        this.keyPairAuthJtiChecker = new MemoryKeyPairAuthJtiChecker();
    }

    public static CuratorResources getCuratorResourcesForTesting(String zookeeperConnectionString, Alerter alerter) throws Exception {
        return new CuratorResources(zookeeperConnectionString, alerter);
    }

    private static SignalWatcherConfig getSignalWatcherConfig(CordraConfig cordraConfig) {
        SignalWatcherConfig signalWatcherConfig = cordraConfig.signalWatcher;
        if (signalWatcherConfig != null) {
            if ("kafka".equals(signalWatcherConfig.type) || "zk".equals(signalWatcherConfig.type)) {
                return signalWatcherConfig;
            } else {
                throw new UnsupportedOperationException("signalWatcher type "+signalWatcherConfig.type+" not supported");
            }
        } else if (cordraConfig.reprocessingQueue != null && "kafka".equals(cordraConfig.reprocessingQueue.type)) {
            signalWatcherConfig = new SignalWatcherConfig();
            signalWatcherConfig.type = "kafka";
            signalWatcherConfig.kafkaBootstrapServers = cordraConfig.reprocessingQueue.kafkaBootstrapServers;
            signalWatcherConfig.consumerConfig = cordraConfig.reprocessingQueue.consumerConfig;
            signalWatcherConfig.producerConfig = cordraConfig.reprocessingQueue.producerConfig;
            return signalWatcherConfig;
        } else {
            return null;
        }
    }

    private static ReprocessingQueueConfig getReprocessQueueConfig(CordraConfig cordraConfig) {
        ReprocessingQueueConfig reprocessingQueueConfig = cordraConfig.reprocessingQueue;
        if (reprocessingQueueConfig != null) {
            if ("kafka".equals(reprocessingQueueConfig.type)) {
                return reprocessingQueueConfig;
            } else {
                throw new UnsupportedOperationException("reprocessQueue type "+reprocessingQueueConfig.type+" not supported");
            }
        }
        if (cordraConfig.isReadOnly) return null; // if read only and not replicating, nothing to reprocess
        throw new UnsupportedOperationException("reprocessingQueue must be specified for reprocessing queue");
    }

    private static String addChrootIfNeeded(String zookeeperConnectionString, String chroot) {
        int slash = zookeeperConnectionString.indexOf('/');
        if (slash < 0) return zookeeperConnectionString + "/" + chroot;
        if (slash + 1 == zookeeperConnectionString.length()) return zookeeperConnectionString + chroot;
        return zookeeperConnectionString;
    }

    public CordraConfig getCordraConfig() {
        return cordraConfig;
    }

    public PrivateKey getPrivateKey() throws Exception {
        byte[] pkBytes;
        try {
            pkBytes = client.getData().forPath(PRIVATE_KEY_PATH);
        } catch (KeeperException.NoNodeException e) {
            try {
                pkBytes = Base64.getMimeDecoder().decode(client.getData().forPath(PRIVATE_KEY_BASE64_PATH));
            } catch (KeeperException.NoNodeException ex) {
                return null;
            }
        }
        if (Util.requiresSecretKey(pkBytes)) {
            throw new Exception("Private key is encrypted, cannot start");
        }
        return net.handle.hdllib.Util.getPrivateKeyFromBytes(Util.decrypt(pkBytes, null));
    }

    @Override
    public CheckableLocker getStartupLocker() {
        return startupLocker;
    }

    @Override
    public RepoInitProvider getRepoInitProvider() {
        return repoInitProvider;
    }

    @Override
    public LeadershipManager getLeadershipManager() {
        return leadershipManager;
    }

    @Override
    public TransactionManager getTransactionManager() {
        return transactionManager;
    }

    @Override
    public SingleThreadReadWriteCheckableLocker getDesignLocker() {
        return designLocker;
    }

    @Override
    public CheckableLocker getSchemaNameLocker() {
        return schemaNameLocker;
    }

    @Override
    public CheckableLocker getUsernameLocker() {
        return usernameLocker;
    }

    @Override
    public NameLocker getObjectLocker() {
        return objectLocker;
    }

    @Override
    public AllHandlesUpdaterSync getAllHandlesUpdaterSync() {
        return allHandlesUpdaterSync;
    }

    @Override
    public SignalWatcher getSignalWatcher() {
        return signalWatcher;
    }

    @Override
    public TransactionReprocessingQueue getTransactionReprocessingQueue() {
        return transactionReprocessingQueue;
    }

    @Override
    public KeyPairAuthJtiChecker getKeyPairAuthJtiChecker() {
        return keyPairAuthJtiChecker;
    }

    @Override
    public Alerter getAlerter() {
        return alerter;
    }

    @Override
    public void shutdown() {
        signalWatcher.shutdown();
        try { leadershipManager.shutdown(); } catch (Exception e) { logger.error("Shutdown error", e); }
        transactionManager.shutdown();
        if (transactionReprocessingQueue != null) transactionReprocessingQueue.shutdown();
        execServ.shutdown();
        client.close();
    }

}
