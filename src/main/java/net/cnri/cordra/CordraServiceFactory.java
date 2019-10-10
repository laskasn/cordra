/*************************************************************************\
    Copyright (c) 2019 Corporation for National Research Initiatives;
                        All rights reserved.
\*************************************************************************/

package net.cnri.cordra;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.api.CordraObject;
import net.cnri.cordra.api.SearchResults;
import net.cnri.cordra.indexer.CordraIndexer;
import net.cnri.cordra.indexer.IndexerException;
import net.cnri.cordra.indexer.InstrumentedCordraIndexer;
import net.cnri.cordra.indexer.elasticsearch.ElasticsearchIndexer;
import net.cnri.cordra.indexer.lucene.LuceneIndexer;
import net.cnri.cordra.indexer.solr.SolrIndexer;
import net.cnri.cordra.model.*;
import net.cnri.cordra.replication.kafka.*;
import net.cnri.cordra.storage.CordraStorage;
import net.cnri.cordra.storage.InstrumentedCordraStorage;
import net.cnri.cordra.storage.StorageConfig;
import net.cnri.cordra.storage.bdbje.BdbjeStorage;
import net.cnri.cordra.storage.hds.HdsStorage;
import net.cnri.cordra.storage.memory.MemoryStorage;
import net.cnri.cordra.storage.mongodb.MongoDbStorage;
import net.cnri.cordra.storage.multi.MultiCordraStorage;
import net.cnri.cordra.storage.s3.S3Storage;
import net.cnri.cordra.sync.*;
import net.cnri.cordra.sync.curator.CuratorResources;
import net.cnri.cordra.sync.local.LocalSyncObjects;
import net.cnri.cordra.sync.local.ObjectBasedRepoInitProvider;
import net.cnri.microservices.*;
import net.cnri.servletcontainer.sessions.HttpSessionManager;
import net.handle.hdllib.Util;
import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.utils.URIUtils;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.common.settings.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContext;
import java.io.BufferedWriter;
import java.io.Console;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public class CordraServiceFactory {
    private static Logger logger = LoggerFactory.getLogger(CordraServiceFactory.class);

    public static final String HANDLE_PRIVATE_KEY_BIN = "handlePrivateKey.bin";
    public static final String HANDLE_PUBLIC_KEY_BIN = "handlePublicKey.bin";
    private static final int SOLR_SO_TIMEOUT = 300_000;

    private static CordraService cordra = null;

    // for testing
    public synchronized static void reset() {
        cordra = null;
    }

    public synchronized static CordraService getAndInitializeCordraService(ServletContext context) throws Exception {
        if (cordra != null) return cordra;
        String zookeeperConnectionString = context.getInitParameter("zookeeperConnectionString");
        if (zookeeperConnectionString != null) {
            return getZookeeperCordraService(context);
        } else {
            return getSingleInstanceCordraService(context);
        }
    }

    public synchronized static CordraService getCordraService() throws Exception {
        return cordra;
    }

    @SuppressWarnings("resource")
    private static CordraService getZookeeperCordraService(ServletContext context) throws Exception {
        CuratorResources curatorResources = null;
        CordraStorage storage = null;
        CordraIndexer indexer = null;
        ReplicationProducer replicationProducer = null;
        StripedExecutorService stripedTaskRunner = null;
        MultithreadedKafkaConsumer replicationConsumer = null;
        try {
            Alerter alerter = new LoggingAlerter();
            CordraStartupStatus startupStatus = CordraStartupStatus.getInstance();
            startupStatus.details.put("zookeeper", CordraStartupStatus.State.STARTING);
            curatorResources = new CuratorResources(context, alerter);
            startupStatus.details.put("zookeeper", CordraStartupStatus.State.UP);
            CordraConfig cordraConfig = curatorResources.getCordraConfig();
            logger.trace("Completed CordraConfig");
            boolean isReadOnly = cordraConfig.isReadOnly;
            PrivateKey privateKey = curatorResources.getPrivateKey();
            logger.trace("Received /cordra/privatekey");
            Path basePath = getBasePathFromSystemProperty();
            PrivateKey handlePrivateKey = getHandlePrivateKey(basePath);
            PublicKey handlePublicKey = getHandlePublicKey(basePath);
            storage = getStorage(cordraConfig, basePath, true);
            logger.trace("Completed CordraStorage setup");
            indexer = getIndexer(cordraConfig, basePath, storage, curatorResources.getObjectLocker(), alerter);
            logger.trace("Completed Cordra Indexer");
            replicationProducer = getReplicationProducer(cordraConfig);
            logger.trace("Completed Cordra ReplicationProducer");
            stripedTaskRunner = getStripedTaskRunner(cordraConfig, alerter);
            replicationConsumer = getMultithreadedReplicationConsumer(cordraConfig, alerter, stripedTaskRunner);
            logger.trace("Completed Cordra ReplicationConsumer");
            String cordraClusterId = cordraConfig.cordraClusterId;
            HttpSessionManager sessionManager = (HttpSessionManager) context.getAttribute(HttpSessionManager.class.getName());
            DoipSetupProvider doipSetupProvider = new DoipSetupProvider(context, privateKey);
            initializeCordra(storage, indexer, replicationProducer, stripedTaskRunner, replicationConsumer, sessionManager, cordraClusterId, handlePrivateKey, handlePublicKey, privateKey, isReadOnly, curatorResources, curatorResources.getCordraConfig(), doipSetupProvider);
            logger.trace("Completed Initializing Cordra");
            return cordra;
        } catch (Exception e) {
            logger.trace("Error initializing Cordra", e);
            if (cordra != null) {
                try { cordra.shutdown(); } catch (Exception ex) { logger.error("Shutdown error", ex); }
            } else {
                if (curatorResources != null) try { curatorResources.shutdown(); } catch (Exception ex) { logger.error("Shutdown error", ex); }
                if (indexer != null) try { indexer.close(); } catch (Exception ex) { logger.error("Shutdown error", ex); }
                if (storage != null) try { storage.close(); } catch (Exception ex) { logger.error("Shutdown error", ex); }
            }
            throw e;
        }
    }

    private static Path getBasePathFromSystemProperty() {
        String cordraDataPath = System.getProperty(Constants.CORDRA_DATA);
        if (cordraDataPath == null) {
            cordraDataPath = "data";
        }
        return Paths.get(cordraDataPath);
    }

    private static StripedExecutorService getStripedTaskRunner(CordraConfig cordraConfig, Alerter alerter) {
        ReplicationConsumerConfig replicationConsumerConfig = cordraConfig.replicationConsumer;
        if (replicationConsumerConfig == null) {
            return null;
        }
        int numReplicationThreads = getNumReplicationThreads(cordraConfig);
        return new StripedThreadPoolExecutorService(numReplicationThreads, numReplicationThreads, 500, (thread, exception) -> {
            alerter.alert("Exception in stripedTaskRunner " + exception);
            logger.error("Exception in stripedTaskRunner", exception);
        });
    }

    private static MultithreadedKafkaConsumer getMultithreadedReplicationConsumer(CordraConfig cordraConfig, Alerter alerter, StripedExecutorService stripedTaskRunner) {
        ReplicationConsumerConfig replicationConsumerConfig = cordraConfig.replicationConsumer;
        if (replicationConsumerConfig == null) {
            return null;
        } else if ("kafka".equals(replicationConsumerConfig.type)) {
            CordraStartupStatus startupStatus = CordraStartupStatus.getInstance();
            startupStatus.details.put("replicationConsumer", CordraStartupStatus.State.STARTING);
            String groupId = "cordra-replication-consumer";
            Pattern pattern = patternExcluding(cordraConfig.cordraClusterId);
            try {
                return new MultithreadedKafkaConsumer(pattern,
                    groupId, replicationConsumerConfig.consumerConfig,
                    replicationConsumerConfig.kafkaBootstrapServers,
                    alerter, stripedTaskRunner);
            } finally {
                startupStatus.details.put("replicationConsumer", CordraStartupStatus.State.UP);
            }
        } else {
            throw new UnsupportedOperationException("Replication type "+replicationConsumerConfig.type+" not supported");
        }
    }

    /**
     * Returns a Pattern matching all topics beginning with {@code KafkaReplicationProducer.TOPIC_PREFIX}
     * but excluding those which are {@code KafkaReplicationProducer.TOPIC_PREFIX} followed by the given
     * cordraClusterId.  If cordraClusterId is null, the returned pattern will match all topics beginning
     * with KafkaReplicationProducer.TOPIC_PREFIX.
     *
     * @param cordraClusterId The cordraClusterId to exclude, or null to exclude none
     * @return a pattern matching appropriate topics
     */
    public static Pattern patternExcluding(String cordraClusterId) {
        if (cordraClusterId == null) {
            return Pattern.compile("^" + Pattern.quote(KafkaReplicationProducer.TOPIC_PREFIX) + ".*");
        }
        return Pattern.compile("^" + Pattern.quote(KafkaReplicationProducer.TOPIC_PREFIX) +
            "(?!" + Pattern.quote(cordraClusterId) + "$).*");
    }

//    private static TaskConsumer getReplicationConsumer(CordraConfig cordraConfig, Alerter alerter) {
//        ReplicationConsumerConfig replicationConsumerConfig = cordraConfig.replicationConsumer;
//        if (replicationConsumerConfig == null) {
//            return null;
//        } else if ("kafka".equals(replicationConsumerConfig.type)) {
//            String groupId = "cordra-replication-consumer";
//            return new KafkaTaskConsumer(cordraConfig.cordraClusterId, groupId, replicationConsumerConfig.consumerConfig, replicationConsumerConfig.kafkaBootstrapServers, alerter);
//        } else {
//            throw new UnsupportedOperationException("Replication type "+replicationConsumerConfig.type+" not supported");
//        }
//    }

    private static int getNumReplicationThreads(CordraConfig cordraConfig) {
        ReplicationConsumerConfig replicationConsumerConfig = cordraConfig.replicationConsumer;
        if (replicationConsumerConfig == null) {
            return 0;
        } if (replicationConsumerConfig.threads <= 0) {
            return 32;
        } else {
            return replicationConsumerConfig.threads;
        }
    }

    private static ReplicationProducer getReplicationProducer(CordraConfig cordraConfig) {
        List<ReplicationProducerConfig> replicationConfigs = cordraConfig.replicationProducers;
        if (replicationConfigs == null || replicationConfigs.isEmpty()) {
            return null;
        }
        CordraStartupStatus startupStatus = CordraStartupStatus.getInstance();
        startupStatus.details.put("replicationProducer", CordraStartupStatus.State.STARTING);
        ReplicationProducer replicationProducer = null;
        if (replicationConfigs.size() == 1) {
            replicationProducer = replicationProducerFor(cordraConfig.cordraClusterId, replicationConfigs.get(0));
        } else {
            List<ReplicationProducer> producers = new ArrayList<>();
            for (ReplicationProducerConfig replicationConfig : replicationConfigs) {
                ReplicationProducer producer = replicationProducerFor(cordraConfig.cordraClusterId, replicationConfig);
                producers.add(producer);
            }
            MultipleReplicationProducer.Durability durability = cordraConfig.durability;
            if (durability == null) {
                durability = MultipleReplicationProducer.Durability.ALL;
            }
            replicationProducer = new MultipleReplicationProducer(producers, durability);
        }
        if (cordraConfig.traceRequests) {
            replicationProducer = new InstrumentedReplicationProducer(replicationProducer);
        }
        startupStatus.details.put("replicationProducer", CordraStartupStatus.State.UP);
        return replicationProducer;
    }

    private static ReplicationProducer replicationProducerFor(String cordraClusterId, ReplicationProducerConfig replicationConfig) {
        ReplicationProducer result = null;
        if ("kafka".equals(replicationConfig.type)) {
            result = new KafkaReplicationProducer(cordraClusterId, replicationConfig);
        } else {
            throw new UnsupportedOperationException("Replication type "+replicationConfig.type+" not supported");
        }
        return result;
    }

    @SuppressWarnings("resource")
    private static CordraService getSingleInstanceCordraService(ServletContext context) throws Exception {
        SyncObjects syncObjects = null;
        CordraStorage storage = null;
        CordraIndexer indexer = null;
        try {
            PrivateKey privateKey = (PrivateKey) context.getAttribute("net.cnri.cordra.startup.privatekey");
            context.removeAttribute("net.cnri.cordra.startup.privatekey");
            Path basePath = getBasePathFromSystemProperty();
            if (privateKey == null) {
                try {
                    privateKey = Util.getPrivateKeyFromFileWithPassphrase(basePath.resolve("privatekey").toFile(), null);
                } catch (FileNotFoundException e) {
                    // ignore
                } catch (Exception e) {
                    logger.error("Unable to read private key - encryption issues?", e);
                }
            }
            PrivateKey handlePrivateKey = getHandlePrivateKey(basePath);
            PublicKey handlePublicKey = getHandlePublicKey(basePath);
            Alerter alerter = new LoggingAlerter();
            CordraConfig cordraConfig = CordraConfigSource.getConfig(context);
            boolean isReadOnly = cordraConfig.isReadOnly;
            storage = getStorage(cordraConfig, basePath, true);
            boolean inMemoryOnly = false;
            if ("memory".equals(cordraConfig.storage.module)) {
                inMemoryOnly = true;
            }
            syncObjects = new LocalSyncObjects(basePath, isReadOnly, alerter, inMemoryOnly);
            indexer = getIndexer(cordraConfig, basePath, storage, syncObjects.getObjectLocker(), alerter);
            String cordraClusterId = cordraConfig.cordraClusterId;
            HttpSessionManager sessionManager = (HttpSessionManager) context.getAttribute(HttpSessionManager.class.getName());
            DoipSetupProvider doipSetupProvider = new DoipSetupProvider(context, privateKey);
            initializeCordra(storage, indexer, null, null, null, sessionManager, cordraClusterId, handlePrivateKey, handlePublicKey, privateKey, isReadOnly, syncObjects, cordraConfig, doipSetupProvider);
            return cordra;
        } catch (Exception e) {
            if (cordra != null) {
                cordra.shutdown();
            } else {
                if (syncObjects != null) syncObjects.shutdown();
                if (indexer != null) try { indexer.close(); } catch (Exception ex) { logger.error("Shutdown error", ex); }
                if (storage != null) try { storage.close(); } catch (Exception ex) { logger.error("Shutdown error", ex); }
            }
            throw e;
        }
    }

    private static PublicKey getHandlePublicKey(Path basePath) {
        PublicKey handlePublicKey = null;
        try {
            handlePublicKey = Util.getPublicKeyFromFile(basePath.resolve(HANDLE_PUBLIC_KEY_BIN).toString());
        } catch (FileNotFoundException e) {
            // ignore
        } catch (Exception e) {
            logger.error("Unable to read handlePublicKey.bin key", e);
        }
        return handlePublicKey;
    }

    private static PrivateKey getHandlePrivateKey(Path basePath) {
        PrivateKey handlePrivateKey = null;
        try {
            handlePrivateKey = Util.getPrivateKeyFromFileWithPassphrase(basePath.resolve(HANDLE_PRIVATE_KEY_BIN).toFile(), null);
        } catch (FileNotFoundException e) {
            // ignore
        } catch (Exception e) {
            logger.error("Unable to read handlePrivateKey.bin key - encryption issues?", e);
        }
        return handlePrivateKey;
    }

    @SuppressWarnings("resource")
    public static CordraStorage getStorage(CordraConfig cordraConfig, Path basePath, boolean verbose) throws IOException, CordraException {
        CordraStartupStatus startupStatus = CordraStartupStatus.getInstance();
        startupStatus.details.put("storage", CordraStartupStatus.State.STARTING);
        CordraStorage storage = createStorageForConfig(cordraConfig.storage, basePath, verbose);
        if (cordraConfig.traceRequests) {
            storage = new InstrumentedCordraStorage(storage);
        }
        startupStatus.details.put("storage", CordraStartupStatus.State.UP);
        return storage;
    }

    public static CordraStorage createStorageForConfig(StorageConfig storageConfig, Path basePath, boolean verbose) throws IOException, CordraException {
        CordraStorage storage;
        if ("bdbje".equals(storageConfig.module)) {
            if (verbose) {
                logger.info("Storage: bdbje");
                System.out.println("Storage: bdbje");
            }
            storage = createBdbjeCordraStorage(basePath);
        } else if ("mongodb".equals(storageConfig.module)) {
            if (verbose) {
                logger.info("Storage: mongodb");
                System.out.println("Storage: mongodb");
            }
            storage = new MongoDbStorage(storageConfig.options);
        } else if ("s3".equals(storageConfig.module)) {
            if (verbose) {
                logger.info("Storage: s3");
                System.out.println("Storage: s3");
            }
            storage = new S3Storage(storageConfig.options);
        } else if ("multi".equals(storageConfig.module)) {
            if (verbose) {
                logger.info("Storage: multi");
                System.out.println("Storage: multi");
            }
            storage = new MultiCordraStorage(storageConfig.options);
        } else if ("memory".equals(storageConfig.module)) {
            if (verbose) {
                logger.info("Storage: memory");
                System.out.println("Storage: memory");
            }
            storage = new MemoryStorage();
        } else if ("custom".equals(storageConfig.module)) {
            storage = getCustomStorage(storageConfig);
        } else {
            Path storagePath = basePath.resolve("storage");
            if (Files.exists(storagePath)) {
                if (verbose) {
                    logger.info("Storage: hds");
                    System.out.println("Storage: hds");
                }
                storage = new HdsStorage(storagePath.toFile());
            } else {
                if (verbose) {
                    logger.info("Storage: bdbje");
                    System.out.println("Storage: bdbje");
                }
                storage = createBdbjeCordraStorage(basePath);
            }
        }
        return storage;
    }

    private static CordraStorage getCustomStorage(StorageConfig storageConfig) throws CordraException {
        if (storageConfig.className == null) {
            throw new AssertionError("Invalid storage configuration. 'module' is set to 'custom' but no 'className' is specified.");
        }
        CordraStorage storage = null;
        try {
            Class<?> storageClass = Class.forName(storageConfig.className);
            Constructor<?>[] constructors = storageClass.getConstructors();
            Constructor<?> defaultConstructor = null;
            for (Constructor<?> c : constructors) {
                Class<?>[] parameterTypes = c.getParameterTypes();
                if (parameterTypes.length == 0) {
                    defaultConstructor = c;
                } else if ((parameterTypes.length == 1) && (parameterTypes[0] == JsonObject.class)) {
                    storage = (CordraStorage) c.newInstance(storageConfig.options);
                    break;
                }
            }
            if (storage == null && defaultConstructor != null) {
                storage = (CordraStorage) defaultConstructor.newInstance();
            }
            if (storage == null) {
                throw new CordraException("Could not find valid constructor in custom CordraStorage " + storageConfig.className);
            }
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new CordraException(e);
        }
        return storage;
    }

    private static CordraStorage createBdbjeCordraStorage(Path basePath) throws IOException {
        CordraStorage storage;
        Path storagePath = basePath.resolve("cordraStorage");
        Files.createDirectories(storagePath);
        storage = new BdbjeStorage(storagePath.toFile());
        return storage;
    }

    @SuppressWarnings("resource")
    private static CordraIndexer getIndexer(CordraConfig cordraConfig, Path basePath, CordraStorage storage, NameLocker objectLocker, Alerter alerter) throws Exception {
        CordraStartupStatus startupStatus = CordraStartupStatus.getInstance();
        startupStatus.details.put("indexer", CordraStartupStatus.State.STARTING);
        CordraIndexer indexer;
        //XXX isStoreFields
        boolean isStoreFields = false;//Boolean.parseBoolean(cordraConfig.index.options.get("isStoreFields"));
        if ("solr".equalsIgnoreCase(cordraConfig.index.module)) {
            logger.info("Connecting to solr:");
            System.out.println("Connecting to solr:");
            SolrClient solr;
            String configRf = cordraConfig.index.options.get("minRf");
            int minRf = configRf == null ? 1 : Integer.parseInt(configRf);
            String solrBaseUri = cordraConfig.index.options.get("baseUri");
            String zkHosts = cordraConfig.index.options.get("zkHosts");
            if (solrBaseUri == null && zkHosts == null) {
                solrBaseUri = "http://localhost:8983/solr/cordra";
            }
            if (solrBaseUri != null) {
                logger.info("baseUri: " + solrBaseUri);
                System.out.println("baseUri: " + solrBaseUri);
                solr = new HttpSolrClient.Builder().withBaseSolrUrl(solrBaseUri).build();
            } else if (zkHosts != null) {
                logger.info("zkHosts: " + zkHosts);
                System.out.println("zkHosts: " + zkHosts);
                CloudSolrClient cloudSolr = new CloudSolrClient.Builder(Collections.singletonList(zkHosts), Optional.empty())
                    .withSocketTimeout(SOLR_SO_TIMEOUT)
                    .build();
                cloudSolr.setDefaultCollection("cordra");
                solr = cloudSolr;
                if (configRf == null) {
                    cloudSolr.connect();
                    Integer zkRf = cloudSolr.getZkStateReader().getClusterState().getCollection("cordra").getReplicationFactor();
                    minRf = zkRf == null ? 1 : zkRf;
                }
            } else {
                throw new AssertionError("Invalid solr configuration");
            }
            logger.info("Index: solr");
            System.out.println("Index: solr");
            indexer = new SolrIndexer(solr, storage, isStoreFields, minRf, objectLocker, alerter);
        } else if ("memory".equalsIgnoreCase(cordraConfig.index.module)) {
            logger.info("Index: in-memory lucene");
            System.out.println("Index: in-memory lucene");
            indexer = new LuceneIndexer(storage, objectLocker);
        } else if ("elasticsearch".equalsIgnoreCase(cordraConfig.index.module)) {
            logger.info("Index: elasticsearch");
            System.out.println("Index: elasticsearch");
            String address = "localhost";
            String addressScheme = "https";
            int port = 9200;
            String baseUri = null;
            Settings.Builder indexSettingsBuilder = Settings.builder();
            boolean seenTotalFieldsLimit = false;
            for (Map.Entry<String, String> e: cordraConfig.index.options.entrySet()) {
                if ("address".equals(e.getKey())) {
                    address = e.getValue();
                } else if ("port".equals(e.getKey())) {
                    port = Integer.valueOf(e.getValue());
                } else if ("addressScheme".equals(e.getKey())) {
                    addressScheme = e.getValue();
                } else if ("baseUri".equals(e.getKey())) {
                    baseUri = e.getValue();
                } else {
                    String key = e.getKey();
                    if (key.startsWith("index.")) {
                        if ("index.mapping.total_fields.limit".equals(key)) seenTotalFieldsLimit = true;
                        indexSettingsBuilder.put(e.getKey(), e.getValue());
                    }
                }
            }
            if (!seenTotalFieldsLimit) {
                indexSettingsBuilder.put("index.mapping.total_fields.limit", 100000);
            }
            HttpHost host;
            if (baseUri != null) {
                host = URIUtils.extractHost(URI.create(baseUri));
            } else {
                host = new HttpHost(address, port, addressScheme);
            }
            RestClientBuilder clientBuilder = RestClient.builder(host)
                    .setRequestConfigCallback(new RestClientBuilder.RequestConfigCallback() {
                        @Override
                        public RequestConfig.Builder customizeRequestConfig(RequestConfig.Builder requestConfigBuilder) {
                            return requestConfigBuilder
                                    .setConnectTimeout(30000)
                                    .setSocketTimeout(60000);
                        }
                    });
            indexer = new ElasticsearchIndexer(clientBuilder, storage, objectLocker, alerter, indexSettingsBuilder.build());
        } else {
            logger.info("Index: lucene");
            System.out.println("Index: lucene");
            indexer = new LuceneIndexer(basePath.toFile(), storage, isStoreFields, objectLocker);
        }
        if (cordraConfig.traceRequests) {
            indexer = new InstrumentedCordraIndexer(indexer);
        }
        startupStatus.details.put("indexer", CordraStartupStatus.State.UP);
        return indexer;
    }

    public static CordraService getCordraServiceForTesting(CordraStorage storage) throws Exception {
        Alerter alerter = new LoggingAlerter();
        RepoInit repoInit = new RepoInit();
        repoInit.adminPassword = "changeIt";
        RepoInitProvider testingRepoInitProvider = new ObjectBasedRepoInitProvider(repoInit);
        SyncObjects syncObjects = new LocalSyncObjects(testingRepoInitProvider, null, false, alerter, true);
        CordraIndexer indexer = new LuceneIndexer(storage, syncObjects.getObjectLocker());
        initializeCordra(storage, indexer, null, null, null, null, "defaultClusterId", null, null,null, false, syncObjects, new CordraConfig(), null);
        return cordra;
    }

    public static CordraService getZookeeperCordraServiceForTesting(String zookeeperConnectionString, CordraStorage storage) throws Exception {
        Alerter alerter = new LoggingAlerter();
        CuratorResources curatorResources = CuratorResources.getCuratorResourcesForTesting(zookeeperConnectionString, alerter);
        CordraConfig cordraConfig = curatorResources.getCordraConfig();
        PrivateKey privateKey = curatorResources.getPrivateKey();
        PrivateKey handlePrivateKey = null;
        PublicKey handlePublicKey = null;
        CordraIndexer indexer = new LuceneIndexer(storage, curatorResources.getObjectLocker());
        initializeCordra(storage, indexer, null, null, null, null, "defaultClusterId", handlePrivateKey, handlePublicKey, privateKey, false, curatorResources, cordraConfig, null);
        return cordra;
    }

    private static void initializeCordra(CordraStorage storage, CordraIndexer indexer,
                                         ReplicationProducer replicationProducer,
                                         StripedExecutorService stripedTaskRunner,
                                         MultithreadedKafkaConsumer replicationConsumer,
                                         HttpSessionManager sessionManager, String cordraClusterId,
                                         PrivateKey handlePrivateKey, PublicKey handlePublicKey,
                                         PrivateKey privateKey, boolean isReadOnly, SyncObjects syncObjects, CordraConfig cordraConfig,
                                         DoipSetupProvider doipSetupProvider) throws Exception {
        if (cordraConfig.traceRequests) {
            syncObjects = new InstrumentedSyncObjects(syncObjects);
        }

        CheckableLocker startupLocker = syncObjects.getStartupLocker();
        LeadershipManager leadershipManager = syncObjects.getLeadershipManager();
        RepoInitProvider repoInitProvider = syncObjects.getRepoInitProvider();
        Alerter alerter = syncObjects.getAlerter();

        startupLocker.acquire();
        try {
            String cordraServiceId = leadershipManager.getId();
            boolean canBeLeader = !isReadOnly || replicationConsumer != null;
            // Three kinds of Cordra instances.
            // (1) !isReadOnly
            // (2) isReadOnly but has replicationConsumer---this is a secondary region
            // (3) isReadOnly and no replicationConsumer---this is a dedicated read-only instance
            leadershipManager.start(canBeLeader);
            leadershipManager.waitForLeaderToBeElected();

            // Note: in the present incarnation the CordraIndexerRepository is SEARCH-ONLY.
            //CordraIndexerRepository indexedRepo = new CordraIndexerRepository(storage, indexer);

            // During cordra construction, we will not perform search or index functions except by creating schema objects.
            // If no storage or migration from old version, we create schema objects.
            // Claim: this is okay because schema objects don't use the RelationshipsService or search.

            boolean isBrandNewDesignObject = (storage.get(CordraService.DESIGN_OBJECT_ID) == null);
            String adminPasswordFromConsole = null;
            if (isBrandNewDesignObject && !isReadOnly) {
                RepoInit repoInit = repoInitProvider.getRepoInit();
                if (repoInit == null || repoInit.adminPassword == null || repoInit.adminPassword.isEmpty()) {
                    logger.warn("Cordra admin password has not been configured.");
                    logger.warn("Ensure the admin password is set in repoInit.json.");
                    logger.warn("See README.txt or the Technical Manual for more information.");
                    logger.warn("Waiting for user input from console to set admin password.");
                    adminPasswordFromConsole = getPasswordFromConsole();
                }
            }
            cordra = new CordraService(cordraServiceId, cordraClusterId, storage, indexer, replicationProducer, sessionManager, stripedTaskRunner, replicationConsumer, handlePrivateKey, handlePublicKey, privateKey, isReadOnly, syncObjects, cordraConfig, doipSetupProvider);
            cordra.init();

            if (!startupLocker.isLocked()) {
                alerter.alert("Startup lock failure, before critical code");
                throw new Exception("Startup lock failure, before critical code");
            }
            long start = System.currentTimeMillis();
            boolean designObjectUpgraded = false;
            if (!isReadOnly && leadershipManager.isThisInstanceLeader()) {
                designObjectUpgraded = cordra.initializeDesignCordraObjectIfNeeded();
            }
            cordra.loadStatefulData();
            if (leadershipManager.isThisInstanceLeader()) {
                if (!isReadOnly) {
                    try {
                        RepoInit repoInit = repoInitProvider.getRepoInit();
                        if (repoInit != null) {
                            initializeFromRepoInit(repoInit, adminPasswordFromConsole);
                        } else if (adminPasswordFromConsole != null) {
                            cordra.setAdminPassword(adminPasswordFromConsole);
                        }
                        if (isBrandNewDesignObject) {
                            cordra.createDefaultSchemas();
                        }
                    } finally {
                        if (!cordra.isAdminPasswordSet()) {
                            noAdminPasswordError();
                        }
                        repoInitProvider.cleanup();
                    }
                }
                cordra.reindexEverythingIfIndexIsEmpty(isBrandNewDesignObject);
                if (!isReadOnly) {
                    cordra.updateKnownSchemasBySearch();
                    if (!isBrandNewDesignObject) {
                        cordra.updateUserSchemaIfNecessary();
                    }
                }
                if (designObjectUpgraded) {
                    removeOldMetaObjectsFromRepo();
                }
            }
            if (!startupLocker.isLocked()) {
                long duration = System.currentTimeMillis() - start;
                alerter.alert("Startup lock failure, critical code took " + duration + "ms");
                throw new Exception("Startup lock failure, critical code took " + duration + "ms");
            }
            leadershipManager.onGroupMembershipChange(cordra::processPendingTransactions);
            cordra.startReplication();
        } finally {
            startupLocker.release();
        }
    }

    private static void removeOldMetaObjectsFromRepo() {
        try (SearchResults<CordraObject> toRemove = cordra.indexer.search("objatt_meta:true -id:design")) {
            if (toRemove.size() > 0) {
                try {
                    Path backupFile = getObjectBackupFile();
                    JsonObject deletedObjects = new JsonObject();
                    Gson gson = GsonUtility.getPrettyGson();
                    for (CordraObject co : toRemove) {
                        try {
                            CordraObjectWithPayloadsAsStrings cos = CordraObjectWithPayloadsAsStrings.fromCordraObject(co, cordra.storage);
                            deletedObjects.add(co.id, gson.toJsonTree(cos));
                            cordra.delete(co.id, "admin");
                        } catch (CordraException | IOException | ReadOnlyCordraException | InvalidException e) {
                            logger.error("Error deleting obsolete object during migration. ID: " + co.id, e);
                        }
                    }
                    try (BufferedWriter w = Files.newBufferedWriter(backupFile)) {
                        gson.toJson(deletedObjects, w);
                    }
                } catch (IOException e) {
                    logger.error("Error deleting obsolete objects during migration", e);
                    return;
                }
                logger.info("Deleted obsolete objects during migration. Backups saved to \"deleted-objects.json\" in migration folder.");
            }
        } catch (IndexerException e) {
            logger.error("Error searching for obsolete objects during migration", e);
            return;
        }
    }

    private static Path getObjectBackupFile() throws IOException {
        Path basePath = getBasePathFromSystemProperty().resolve("migration_delete_me");
        if (!Files.exists(basePath)) {
            Files.createDirectory(basePath);
        }
        Path backupFile = basePath.resolve("deleted-objects.json");
        if (!Files.exists(backupFile)) {
            Files.createFile(backupFile);
        }
        return backupFile;
    }

    private static String getPasswordFromConsole() {
        Console console = System.console();
        if (console == null) return null;
        logger.debug("No admin password set in repoInit.json. Waiting for user input from console.");
        System.out.println();
        System.out.println("The admin password has not been set.");
        String adminPasswordFromConsole = null;

        while (adminPasswordFromConsole == null) {
            String passwordEntered1 = new String(console.readPassword("Create a new admin password: "));
            String passwordEntered2 = new String(console.readPassword("Enter it again: "));
            if ("".equals(passwordEntered1)) {
                System.out.println("The admin password may not be the empty string.");
            } else if (!passwordEntered1.equals(passwordEntered2)) {
                System.out.println("The passwords you entered do not match. Try again.");
            } else {
                adminPasswordFromConsole = passwordEntered1;
            }
        }
        return adminPasswordFromConsole;
    }

    private static void noAdminPasswordError() throws AdminPasswordCordraException {
        System.out.println();
        System.out.println("Cordra admin password has not been configured.");
        System.out.println("Ensure the admin password is set in repoInit.json.");
        System.out.println("See README.txt or the Technical Manual for more information.");
        System.out.println();
        throw new AdminPasswordCordraException();
    }

    private static void initializeFromRepoInit(RepoInit repoInit, String adminPasswordFromConsole) {
        try {
            String initBaseUri = repoInit.baseUri;
            String handleAdminIdentity = repoInit.handleAdminIdentity;
            String prefix = repoInit.prefix;
            if (initBaseUri != null || handleAdminIdentity != null || prefix != null) {
                HandleMintingConfig handleMintingConfig = cordra.design.handleMintingConfig;
                if (handleMintingConfig == null) handleMintingConfig = HandleMintingConfig.getDefaultConfig();
                if (initBaseUri != null) {
                    handleMintingConfig.baseUri = initBaseUri;
                }
                if (handleAdminIdentity != null) {
                    handleMintingConfig.handleAdminIdentity = handleAdminIdentity;
                }
                if (prefix != null) {
                    if (prefix.toUpperCase(Locale.ENGLISH).startsWith("0.NA/")) {
                        handleMintingConfig.prefix = prefix.substring(5);
                    } else if (prefix.endsWith("/")) {
                        handleMintingConfig.prefix = prefix.substring(0, prefix.length() - 1);
                    } else {
                        handleMintingConfig.prefix = prefix;
                    }
                }
                cordra.updateHandleMintingConfig(handleMintingConfig);
            }
            if (adminPasswordFromConsole != null) {
                cordra.setAdminPassword(adminPasswordFromConsole);
            } else if (repoInit.adminPassword != null && !repoInit.adminPassword.isEmpty()) {
                cordra.setAdminPassword(repoInit.adminPassword);
            }
            if (repoInit.design != null) {
                cordra.updateDesign(repoInit.design);
            }
        } catch (Exception e) {
            logger.error("Error reading repoInit.json", e);
        }
    }
}
