package net.cnri.cordra.model;

import net.cnri.cordra.auth.CordraSessionIdFunction;
import net.cnri.cordra.doip.DoipServerConfigWithEnabledFlag;
import net.cnri.cordra.indexer.IndexerConfig;
import net.cnri.cordra.replication.kafka.MultipleReplicationProducer;
import net.cnri.cordra.storage.StorageConfig;
import net.cnri.servletcontainer.sessions.HttpSessionManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CordraConfig {
    public boolean isReadOnly;
    public String cordraClusterId = "default";
    public MultipleReplicationProducer.Durability durability;
    public List<ReplicationProducerConfig> replicationProducers = new ArrayList<>();
    public ReplicationConsumerConfig replicationConsumer;
    public ReprocessingQueueConfig reprocessingQueue;
    public SignalWatcherConfig signalWatcher;
    public IndexerConfig index;
    public StorageConfig storage;
    public boolean traceRequests = false;
    public Reindexing reindexing = new Reindexing();
    public SessionsConfig sessions = new SessionsConfig();
    public DoipServerConfigWithEnabledFlag doip;

    public static CordraConfig getNewDefaultInstance() {
        CordraConfig config = new CordraConfig();
        config.index = IndexerConfig.getNewDefaultInstance();
        config.storage = StorageConfig.getNewDefaultInstance();
        return config;
    }

    public static class Reindexing {
        public Integer numThreads = 32;
        public List<String> priorityTypes = null;
        public Boolean async = false;
        public Integer batchSize = 16;
        public Integer logChunkSize = 10000;
        public Boolean logProgressToConsole = false;
        public Boolean lockDuringBackgroundReindex = true;
    }

    public static class SessionsConfig {
        public String module = "servlet"; // servlet | memory | mongo
        public int timeout = HttpSessionManager.DEFAULT_SESSION_TIMEOUT_MINUTES;
        public String function = CordraSessionIdFunction.class.getName();
        public Map<String, String> options = new HashMap<>();
    }
}
