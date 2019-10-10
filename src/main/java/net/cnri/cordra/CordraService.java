/*************************************************************************\
    Copyright (c) 2019 Corporation for National Research Initiatives;
                        All rights reserved.
\*************************************************************************/

package net.cnri.cordra;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.google.common.reflect.TypeToken;
import com.google.gson.*;
import com.google.gson.stream.JsonWriter;
import net.cnri.cordra.AllHandlesUpdater.UpdateStatus;
import net.cnri.cordra.api.*;
import net.cnri.cordra.auth.*;
import net.cnri.cordra.doip.CordraClientDoipProcessor;
import net.cnri.cordra.doip.DoipServerConfigWithEnabledFlag;
import net.cnri.cordra.handle.LightWeightHandleServer;
import net.cnri.cordra.indexer.*;
import net.cnri.cordra.javascript.CordraRequireLookup;
import net.cnri.cordra.javascript.JavaScriptLifeCycleHooks;
import net.cnri.cordra.model.*;
import net.cnri.cordra.replication.kafka.CordraObjectWithPayloadsAsStrings;
import net.cnri.cordra.replication.kafka.ReplicationMessage;
import net.cnri.cordra.replication.kafka.ReplicationProducer;
import net.cnri.cordra.storage.CordraStorage;
import net.cnri.cordra.sync.*;
import net.cnri.microservices.Alerter;
import net.cnri.microservices.MultithreadedKafkaConsumer;
import net.cnri.microservices.StripedExecutorService;
import net.cnri.servletcontainer.sessions.HttpSessionManager;
import net.cnri.util.StreamUtil;
import net.cnri.util.ThrottledExecutorService;
import net.cnri.util.javascript.JavaScriptEnvironment;
import net.cnri.util.javascript.JavaScriptRunner;
import net.dona.doip.server.DoipProcessor;
import net.dona.doip.server.DoipServer;
import net.dona.doip.server.DoipServerConfig;
import net.handle.hdllib.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.ScriptException;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

public class CordraService {
    private static Logger logger = LoggerFactory.getLogger(CordraService.class);

    public static final String DESIGN_OBJECT_ID = "design";
    public static final String DESIGN_OBJECT_TYPE = "CordraDesign";
    public static final String HASH_ATTRIBUTE = "admin_password_hash";
    public static final String SALT_ATTRIBUTE = "admin_password_salt";
    public static final String ITERATIONS_ATTRIBUTE = "admin_password_iterations";
    public static final String HASH_ALGORITHM_ATTRIBUTE = "admin_password_hash_algorithm";

    public static final String REPOSITORY_SERVICE_PREFIX_HANDLE = "0.NA/20.5000";

    public static final String TXN_ID = "txnId";

    static final Gson gson = GsonUtility.getPrettyGson();

    final String cordraServiceId;
    final String cordraClusterId;
    final boolean isReadOnly;
    final CordraStorage storage;
    final CordraIndexer indexer;
    final Reindexer reindexer;
    final ReplicationProducer replicationProducer;
    final StripedExecutorService stripedTaskRunner;
    final MultithreadedKafkaConsumer replicationConsumer;
    final PrivateKey privateKey;
    PrivateKey handlePrivateKey;
    PublicKey handlePublicKey;
    final CordraConfig cordraConfig;
    final DoipSetupProvider doipSetupProvider;

    final CordraObjectSchemaValidator validator;
    final VersionManager versionManager;
    private final AllHandlesUpdater handlesUpdater;
    final Alerter alerter;

    // State based on administrative config
    private final HttpSessionManager sessionManager;
    final Authenticator authenticator;
    final AclEnforcer aclEnforcer;
    final HandleMinter handleMinter;
    HandleClient handleClient;
    AuthenticationInfo authInfo;
    LightWeightHandleServer lightWeightHandleServer;
    DoipServer doipServer;
    DoipServerConfigWithEnabledFlag doipServerConfig;
    String doipServiceId;

    // Synchronization
    private final SyncObjects syncObjects;
    private final TransactionManager transactionManager;
    private final LeadershipManager leadershipManager;
    private final SingleThreadReadWriteCheckableLocker designLocker;
    private final SignalWatcher signalWatcher;
    private final CheckableLocker schemaNameLocker;
    private final CheckableLocker usernameLocker;
    final NameLocker objectLocker;
    private final TransactionReprocessingQueue transactionReprocessingQueue;

    // Cached state
    JsonSchemaFactory jsonSchemaFactory;
    volatile DesignPlusSchemas design;
    volatile Map<String, SchemaAndNode> schemas;
    volatile Map<String, CordraObject> schemaCordraObjects;
    private final CordraRequireLookup cordraRequireLookup;
    private final JavaScriptEnvironment javaScriptEnvironment;
    private final JavaScriptLifeCycleHooks javaScriptHooks;

    private final AtomicLong authObjectChangeCount = new AtomicLong();
    private final AtomicLong authObjectChangeIndexed = new AtomicLong();
    private final AuthCache authCache;
    private final ExecutorService preCacheExecutorService = new ThrottledExecutorService(120_000, 10_000);

    public CordraService(String cordraServiceId, String cordraClusterId, CordraStorage storage, CordraIndexer indexer,
                         ReplicationProducer replicationProducer,
                         HttpSessionManager sessionManager, StripedExecutorService stripedTaskRunner,
                         MultithreadedKafkaConsumer replicationConsumer,
                         PrivateKey handlePrivateKey, PublicKey handlePublicKey,
                         PrivateKey privateKey, boolean isReadOnly,
                         SyncObjects syncObjects, CordraConfig cordraConfig,
                         DoipSetupProvider doipSetupProvider) {
        this.cordraConfig = cordraConfig;
        this.cordraServiceId = cordraServiceId;
        this.cordraClusterId = cordraClusterId;
        this.isReadOnly = isReadOnly;
        this.syncObjects = syncObjects;
        this.storage = storage;
        this.indexer = indexer;
        this.replicationProducer = replicationProducer;
        this.stripedTaskRunner = stripedTaskRunner;
        this.replicationConsumer = replicationConsumer;
        this.sessionManager = sessionManager;
        this.authInfo = null;
        this.handlePrivateKey = handlePrivateKey;
        this.handlePublicKey = handlePublicKey;
        this.privateKey = privateKey;
        this.doipSetupProvider = doipSetupProvider;
        this.validator = new CordraObjectSchemaValidator(this);
        this.authCache = new MemoryAuthCache();
        this.aclEnforcer = new AclEnforcer(this, storage, indexer, authCache);
        this.handlesUpdater = new AllHandlesUpdater(syncObjects.getAllHandlesUpdaterSync());
        this.cordraRequireLookup = new CordraRequireLookup(this);
        this.javaScriptEnvironment = new JavaScriptEnvironment(cordraRequireLookup);
        this.javaScriptHooks = new JavaScriptLifeCycleHooks(javaScriptEnvironment, cordraConfig.traceRequests, cordraRequireLookup, this::getDesign);

        AdminPasswordCheckerInterface adminPasswordChecker = new StoredInRepoAdminPasswordChecker(this);
        this.authenticator = new Authenticator(adminPasswordChecker, this, authCache, syncObjects.getKeyPairAuthJtiChecker(), true);
        this.transactionManager = syncObjects.getTransactionManager();
        this.leadershipManager = syncObjects.getLeadershipManager();
        this.designLocker = syncObjects.getDesignLocker();
        this.handleMinter = new HandleMinter(null); // prefix will be set later
        this.versionManager = new VersionManager(storage, indexer, handleMinter);
        this.alerter = syncObjects.getAlerter();
        this.schemaNameLocker = syncObjects.getSchemaNameLocker();
        this.usernameLocker = syncObjects.getUsernameLocker();
        this.objectLocker = syncObjects.getObjectLocker();
        this.signalWatcher = syncObjects.getSignalWatcher();
        this.reindexer = new Reindexer(storage, indexer, transactionManager, cordraConfig, this, cordraServiceId, objectLocker, alerter);

        TransactionReprocessingQueue delegateTransactionReprocessingQueue = syncObjects.getTransactionReprocessingQueue();
        if (delegateTransactionReprocessingQueue == null) {
            this.transactionReprocessingQueue = null;
        } else {
            this.transactionReprocessingQueue = new DelegatingErrorCatchingTransactionReprocessingQueue(delegateTransactionReprocessingQueue);
        }
//        if (replicationConsumer == null) {
//            this.replicationApplier = null;
//        } else {
//            this.replicationApplier = new StripedThreadPoolExecutorService(numReplicationThreads, numReplicationThreads, 500, (thread, exception) -> {
//                this.alerter.alert("Exception in replicationApplier " + exception);
//                logger.error("Exception in replicationApplier", exception);
//            });
//        }
    }

    public void init() throws CordraException {
        preCache();
        indexer.setDesignSupplier(this::getDesign);
        indexer.setObjectTransformer(javaScriptHooks::objectForIndexing);
        transactionManager.start(cordraServiceId);
        signalWatcher.start(cordraServiceId, this::receiveSignal);
        if (transactionReprocessingQueue != null) {
            transactionReprocessingQueue.start(this::processPendingTransaction, transactionManager);
        }
        CordraServiceForLifeCycleHooks cordraServiceForLifeCycleHooks = new CordraServiceForLifeCycleHooks();
        cordraServiceForLifeCycleHooks.init(this);
        javaScriptEnvironment.getScriptEngineAndCompilationCache().put("_cordraReturningStrings", cordraServiceForLifeCycleHooks);
        CordraUtilForLifeCycleHooks cordraUtilForLifeCycleHooks = new CordraUtilForLifeCycleHooks();
        cordraUtilForLifeCycleHooks.init(this);
        javaScriptEnvironment.getScriptEngineAndCompilationCache().put("_cordraUtil", cordraUtilForLifeCycleHooks);
    }

    public void startReplication() {
        if (replicationConsumer != null) {
            replicationConsumer.start(this::applyReplicationMessage, this::stripePicker);
        }
    }

    public void receiveSignal(SignalWatcher.Signal signal) {
        if (signal == SignalWatcher.Signal.DESIGN) {
            try {
                loadStatefulData();
            } catch (CordraException e) {
                alerter.alert("Error refreshing state data: " + e);
                logger.error("Error refreshing state data", e);
            }
        } else if (signal == SignalWatcher.Signal.AUTH_CHANGE) {
            authObjectChangeCount.incrementAndGet();
            authCache.clearAllGroupsForUserValues();
            authCache.clearAllUserIdForUsernameValues();
            preCache();
        } else if (signal == SignalWatcher.Signal.JAVASCRIPT_CLEAR_CACHE) {
            cordraRequireLookup.clearAllObjectIdsForModuleValues();
            javaScriptEnvironment.clearCache();
        }
    }

    private void preCache() {
        preCacheExecutorService.execute(this::preCacheNow);
    }

    public void preCacheNow() {
        //if (true) return;
        try {
            long authObjectChangeCountAtStart = authObjectChangeCount.get();
            ensureIndexUpToDateWhenAuthChange();
            Map<String, List<String>> userToGroupsMap = new HashMap<>();
            String q = "username:[* TO *] users:[* TO *]";
            try (SearchResults<CordraObject> results = searchRepo(q)) {
                for (CordraObject co : results) {
                    if (authObjectChangeCount.get() > authObjectChangeCountAtStart) return;
                    JsonElement username = co.metadata.internalMetadata.get("username");
                    if (username != null) {
                        authCache.setUserIdForUsername(username.getAsString(), co.id);
                    }
                    JsonElement groupMember = co.metadata.internalMetadata.get("users");
                    if (groupMember != null) {
                        for (String member : groupMember.getAsString().split("\n")) {
                            if (member.isEmpty()) continue;
                            userToGroupsMap
                            .computeIfAbsent(member, unused -> new ArrayList<>())
                            .add(co.id);
                        }
                    }
                }
            }
            Map<String, Set<String>> recursiveUserToGroupsMap = calculateRecursiveUserToGroupsMap(userToGroupsMap);
            for (Map.Entry<String, Set<String>> entry : recursiveUserToGroupsMap.entrySet()) {
                if (authObjectChangeCount.get() > authObjectChangeCountAtStart) return;
                authCache.setGroupsForUser(entry.getKey(), entry.getValue());
            }
        } catch (Exception e) {
            // something went wrong, just give up
            return;
        }
    }

    Map<String, Set<String>> calculateRecursiveUserToGroupsMap(Map<String, List<String>> userToGroupsMap) {
        Map<String, Set<String>> recursiveUserToGroupsMap = new HashMap<>();
        for (String memberId : userToGroupsMap.keySet()) {
            Set<String> accumulation = getGroupsForUserRecursive(memberId, userToGroupsMap, recursiveUserToGroupsMap, new HashSet<String>());
            recursiveUserToGroupsMap.put(memberId, accumulation);
        }
        return recursiveUserToGroupsMap;
    }

    Set<String> getGroupsForUserRecursive(String memberId, Map<String, List<String>> userToGroupsMap, Map<String, Set<String>> recursiveUserToGroupsMap, Set<String> seen) {
        Set<String> accumulation = new HashSet<>();
        Set<String> recursiveResult = recursiveUserToGroupsMap.get(memberId);
        if (recursiveResult != null) {
            accumulation.addAll(recursiveResult);
            return accumulation;
        }
        List<String> groupsForUser = userToGroupsMap.get(memberId);
        if (groupsForUser == null || groupsForUser.isEmpty()) {
            recursiveUserToGroupsMap.put(memberId, Collections.emptySet());
        } else {
            for (String groupId : groupsForUser) {
                if (!seen.contains(groupId)) {
                    seen.add(groupId);
                    Set<String> parentGroups = getGroupsForUserRecursive(groupId, userToGroupsMap, recursiveUserToGroupsMap, seen);
                    //recursiveUserToGroupsMap.put(groupId, parentGroups);
                    accumulation.add(groupId);
                    accumulation.addAll(parentGroups);
                }
            }
        }
        return accumulation;
    }

    public void processPendingTransactions() {
        try {
            logger.info("Cordra " + cordraServiceId + " processing pending txns");
            processPendingTransactionsThrowing();
            logger.info("Cordra " + cordraServiceId + " finished processing pending txns");
        } catch (Exception e) {
            alerter.alert("Error processing pending transactions: " + e);
            logger.error("Error processing pending transactions", e);
        }
    }

    private void processPendingTransactionsThrowing() throws CordraException {
        // allowing read-only instances to process transactions for replication
        //        if (isReadOnly) throw new ReadOnlyCordraException();
        logger.info("Cordra " + cordraServiceId + " processing pending payload indexing");
        processPendingPayloadIndexing();

        List<String> oldMembersWithPendingTxns = new ArrayList<>(transactionManager.getCordraServiceIdsWithOpenTransactions());
        oldMembersWithPendingTxns.removeAll(leadershipManager.getGroupMembers());
        for (String oldMember : oldMembersWithPendingTxns) {
            logger.info("Cordra " + cordraServiceId + " processing pending txns for " + oldMember);
            processPendingTransactionsForOneOldMember(oldMember);
        }
    }

    private void processPendingPayloadIndexing() throws CordraException, IndexerException {
        String query = "+(" + CordraIndexer.PAYLOAD_INDEX_STATE + ":" + CordraIndexer.INDEX_IN_PROCESS
            +" objatt_" + CordraIndexer.PAYLOAD_INDEX_STATE + ":" + CordraIndexer.INDEX_IN_PROCESS + ")";
        // exclude live members
        for (String groupMember : leadershipManager.getGroupMembers()) {
            query += " -" + CordraIndexer.PAYLOAD_INDEX_CORDRA_SERVICE_ID + ":" + groupMember
                + " -objatt_" + CordraIndexer.PAYLOAD_INDEX_CORDRA_SERVICE_ID + ":" + groupMember;
        }
        ensureIndexUpToDate();
        try (SearchResults<String> objectsWithPayloadsInProcess = indexer.searchHandles(query)) {
            for (String handle : objectsWithPayloadsInProcess) {
                logger.info("objectsWithPayloadsInProcess " + handle);
                objectLocker.lock(handle);
                try {
                    CordraObject co = storage.get(handle);
                    if (co != null) {
                        boolean indexPayloads = shouldIndexPayloads(co.type);
                        indexObject(co, indexPayloads);
                    } else {
                        indexer.deleteObject(handle);
                    }
                } finally {
                    objectLocker.release(handle);
                }
            }
        }
    }

    private void processPendingTransactionsForOneOldMember(String oldMember) throws CordraException, IndexerException {
        Iterator<Entry<Long, CordraTransaction>> iter = transactionManager.iterateTransactions(oldMember);
        try {
            boolean failure = false;
            while (iter.hasNext()) {
                Entry<Long, CordraTransaction> entry = iter.next();
                CordraTransaction txn = entry.getValue();
                if (failure) {
                    transactionReprocessingQueue.insert(txn, oldMember);
                    transactionManager.closeTransaction(txn.txnId, oldMember);
                } else {
                    try {
                        logger.info("Processing pending txn from ZK " + txn.objectId + " " + txn.txnId);
                        processPendingTransaction(txn);
                        transactionManager.closeTransaction(txn.txnId, oldMember);
                    } catch (Exception e) {
                        failure = true;
                        try {
                            transactionReprocessingQueue.insert(txn, oldMember);
                            transactionManager.closeTransaction(txn.txnId, oldMember);
                        } catch (Exception ex) {
                            logger.error("Exception processing old member txn; followed by reprocessing error", e);
                            throw ex;
                        }
                        logger.error("Exception processing old member txn", e);
                    }
                }
            }
            transactionManager.cleanup(oldMember);
        } finally {
            if (iter instanceof Closeable) try { ((Closeable)iter).close(); } catch (IOException e) { }
        }
    }

    private void processPendingTransaction(CordraTransaction txn) throws CordraException, IndexerException {
        objectLocker.lock(txn.objectId);
        try {
            CordraObject co = storage.get(txn.objectId);
            logger.info("Indexing pending txn " + txn.objectId);
            if (co != null) {
                boolean indexPayloads = shouldIndexPayloads(co.type);
                indexObject(co, indexPayloads);
                if (txn.isNeedToReplicate) sendUpdateReplicationMessage(co);
            } else {
                indexer.deleteObject(txn.objectId);
                if (txn.isNeedToReplicate) sendDeleteReplicationMessage(txn.objectId);
            }
        } finally {
            objectLocker.release(txn.objectId);
        }
    }

    // called only from factory initialization method
    public boolean initializeDesignCordraObjectIfNeeded() throws CordraException {
        designLocker.writeLock().acquire();
        boolean designObjectMigrated = false;
        //        CordraTransaction txn = null;
        try {
            //            txn = makeUpdateTransactionFor(DESIGN_OBJECT_ID);
            CordraObject designObject = getDesignCordraObject();
            if (designObject == null) {
                logger.info("Creating new design object.");
                designObject = createNewDesignCordraObject();
            }
            if (null == designObject.content || designObject.content.isJsonNull()) {
                migrateToDesignObjectFormat3(designObject);
                designObjectMigrated = true;
            }
            //            signalWatcher.sendSignal(SignalWatcher.Signal.DESIGN);
            //            sendUpdateReplicationMessage(designObject);
            //            transactionManager.closeTransaction(txn.txnId, cordraServiceId);
        } catch (ReadOnlyCordraException e) {
            throw new AssertionError();
            //        } catch (Exception e) {
            //            if (txn != null) {
            //                transactionReprocessingQueue.insert(txn, cordraServiceId);
            //                transactionManager.closeTransaction(txn.txnId, cordraServiceId);
            //            }
            //            throw e;
        } finally {
            designLocker.writeLock().release();
        }
        return  designObjectMigrated;
    }

    // called only from factory initialization method, via initializeDesignCordraObjectIfNeeded
    private CordraObject createNewDesignCordraObject() throws CordraException, ReadOnlyCordraException {
        if (isReadOnly) throw new ReadOnlyCordraException();
        CordraObject designObject = new CordraObject();
        designObject.id = DESIGN_OBJECT_ID;
        designObject.type = DESIGN_OBJECT_TYPE;
        Design localDesign = new Design();
        localDesign.uiConfig = gson.fromJson(getDefaultUiConfig(), UiConfig.class);
        localDesign.authConfig = AuthConfigFactory.getDefaultAuthConfig();
        localDesign.handleMintingConfig = HandleMintingConfig.getDefaultConfig();
        localDesign.handleServerConfig = HandleServerConfig.getDefaultNewCordraConfig();
        localDesign.doip = DoipServerConfigWithEnabledFlag.getDefaultNewCordraConfig();
        designObject.setContent(localDesign);
        designObject.metadata = new CordraObject.Metadata();
        designObject.metadata.internalMetadata = new JsonObject();
        designObject.metadata.internalMetadata.addProperty("version", "3");
        persistDesignToCordraObject(designObject, true, localDesign, Collections.emptyMap());
        return designObject;
    }

    private void migrateToDesignObjectFormat3(CordraObject designObject) throws CordraException, ReadOnlyCordraException {
        JsonElement uiConfigElement = designObject.metadata.internalMetadata.get("uiConfig");
        String uiConfigJson = uiConfigElement != null ? uiConfigElement.getAsString() : getDefaultUiConfig();
        UiConfig uiConfig = gson.fromJson(uiConfigJson, UiConfig.class);
        JsonElement authConfigJsonElement = designObject.metadata.internalMetadata.get("authConfig");
        AuthConfig authConfig;
        if (authConfigJsonElement == null) {
            authConfig = AuthConfigFactory.getDefaultAuthConfig();
        } else {
            authConfig = gson.fromJson(authConfigJsonElement.getAsString(), AuthConfig.class);
        }
        HandleMintingConfig handleMintingConfig;
        JsonElement handleMintingConfigElement = designObject.metadata.internalMetadata.get("handleMintingConfig");
        if (handleMintingConfigElement == null) {
            handleMintingConfig = HandleMintingConfig.getDefaultConfig();
        } else {
            handleMintingConfig = gson.fromJson(handleMintingConfigElement.getAsString(), HandleMintingConfig.class);
        }
        Map<String, String> schemaIds;
        JsonElement schemaIdsElement = designObject.metadata.internalMetadata.get("schemas");
        if (schemaIdsElement == null) {
            schemaIds = new HashMap<>();
        } else {
            schemaIds = gson.fromJson(schemaIdsElement.getAsString(), new TypeToken<Map<String, String>>() {}.getType());
        }
        designObject.type = DESIGN_OBJECT_TYPE;
        designObject.metadata.internalMetadata.addProperty("version", "3");
        designObject.metadata.internalMetadata.remove("meta");
        designObject.metadata.internalMetadata.remove("remoteRepositories");
        Design localDesign = new Design();
        localDesign.uiConfig = uiConfig;
        localDesign.authConfig = authConfig;
        localDesign.handleMintingConfig = handleMintingConfig;
        persistDesignToCordraObject(designObject, false, localDesign, schemaIds);
    }

    public void loadStatefulData() throws CordraException {
        designLocker.readLock().acquire();
        try {
            CordraObject designObject = getDesignCordraObject();
            logger.info("Loading stateful data from design object");
            Design designLite;
            if (designObject == null) {
                designLite = new Design();
            } else {
                designLite = GsonUtility.getGson().fromJson(designObject.content, Design.class);
            }
            if (designLite.uiConfig == null) designLite.uiConfig = gson.fromJson(getDefaultUiConfig(), UiConfig.class);
            if (designLite.authConfig == null) designLite.authConfig = AuthConfigFactory.getDefaultAuthConfig();
            if (designLite.handleMintingConfig == null) designLite.handleMintingConfig = HandleMintingConfig.getDefaultConfig();
            if (designLite.doip == null) {
                designLite.doip = cordraConfig.doip;
                // config.json doip did not have enabled flag
                if (designLite.doip != null) designLite.doip.enabled = true;
            }
            fixPrefixOnHandleMintingConfig(designLite.handleMintingConfig);
            boolean isHandleServerConfigChanged = isConfigChanged(design, designLite, des -> des.handleServerConfig);
            boolean isDoipServerConfigChanged = isConfigChanged(design, designLite, des -> des.doip) || isConfigChanged(design, designLite, des -> des.handleMintingConfig.prefix);
            Map<String, String> schemaIds = getSchemaIdsFromDesignObject(designObject);
            this.design = new DesignPlusSchemas(null, designLite, schemaIds);

            List<CordraObject> knownSchemaObjects = objectListFromHandleList(design.schemaIds.keySet());
            rebuildSchemasFromListOfObjects(knownSchemaObjects);

            String oldDesignJavascript = cordraRequireLookup.getDesignJavaScript();
            if ((design.javascript == null && oldDesignJavascript != null) || (design.javascript != null && !design.javascript.equals(oldDesignJavascript))) {
                cordraRequireLookup.setDesignJavaScript(design.javascript);
                javaScriptEnvironment.clearCache();
            }

            aclEnforcer.setAuthConfig(this.design.authConfig);
            this.handleMinter.setPrefix(design.handleMintingConfig.prefix);
            if (design.handleMintingConfig.handleAdminIdentity != null && privateKey != null) {
                ValueReference valRef = ValueReference.fromString(design.handleMintingConfig.handleAdminIdentity);
                authInfo = new PublicKeyAuthenticationInfo(valRef.handle, valRef.index, privateKey);
            }

            String oldhandleJavaScript = cordraRequireLookup.getHandleJavaScript();
            if ((design.handleMintingConfig.javascript == null && oldhandleJavaScript != null)
                    || (design.handleMintingConfig.javascript != null && !design.handleMintingConfig.javascript.equals(oldhandleJavaScript))) {
                cordraRequireLookup.setHandleJavaScript(design.handleMintingConfig.javascript);
                javaScriptEnvironment.clearCache();
            }

            if (!isReadOnly && authInfo != null) {
                if (!design.handleMintingConfig.isMintHandles()) {
                    this.handleClient = null;
                } else {
                    this.handleClient = new HandleClient(authInfo, design.handleMintingConfig, javaScriptHooks, doipServiceId);
                }
            } else {
                this.handleClient = null;
            }
            if (isHandleServerConfigChanged) {
                if (lightWeightHandleServer != null) {
                    lightWeightHandleServer.shutdown();
                    lightWeightHandleServer = null;
                }
                if (design.handleServerConfig != null) {
                    try {
                        if (design.handleServerConfig.enabled == Boolean.TRUE) {
                            if (handlePrivateKey == null && handlePublicKey == null) {
                                createAndSaveHandleKeys();
                            }
                            lightWeightHandleServer = new LightWeightHandleServer(handlePrivateKey, handlePublicKey, design.handleServerConfig, this::getHandleValues);
                            lightWeightHandleServer.startInterfaces();
                        }
                    } catch (Exception e) {
                        logger.error("Could not start internal handle server", e);
                        System.out.println("Could not start internal handle server (see error.log for details)");
                    }
                }
            }
            if (isDoipServerConfigChanged) {
                restartDoipServiceAfterConfigChange();
            }
            this.authenticator.setBackOffEnabled(!Boolean.TRUE.equals(design.disableAuthenticationBackOff));
        } catch (ProcessingException e) {
            throw new InternalErrorCordraException(e);
        } finally {
            boolean stayedLocked = designLocker.readLock().isLocked();
            designLocker.readLock().release();
            if (!stayedLocked) loadStatefulData();
        }
    }

    private void restartDoipServiceAfterConfigChange() {
        if (doipServer != null) {
            doipServer.shutdown();
            doipServer = null;
        }
        if (design.doip != null) {
            try {
                if (design.doip.enabled == Boolean.TRUE && design.doip.port > 0) {
                    doipServerConfig = fixUpDoipServerConfig(design.handleMintingConfig.prefix, design.doip);
                    doipServiceId = doipServerConfig.processorConfig.get("serviceId").getAsString();
                    if (handleClient != null) handleClient.setDoipServiceId(doipServiceId);
                    DoipProcessor processor = new CordraClientDoipProcessor();
                    processor.init(doipServerConfig.processorConfig);
                    doipServer = new DoipServer(doipServerConfig, processor);
                    System.out.println("Initializing DOIP interface on port " + doipServerConfig.port);
                    doipServer.init();
                } else {
                    if (handleClient != null) handleClient.setDoipServiceId(null);
                }
            } catch (Exception e) {
                logger.error("Could not start DOIP server", e);
                System.out.println("Could not start DOIP server (see error.log for details)");
            }
        } else {
            if (handleClient != null) handleClient.setDoipServiceId(null);
        }
    }

    private DoipServerConfigWithEnabledFlag fixUpDoipServerConfig(String prefix, DoipServerConfigWithEnabledFlag doip) {
        DoipServerConfigWithEnabledFlag doipConfig = new DoipServerConfigWithEnabledFlag();
        doipConfig.enabled = true;
        doipConfig.listenAddress = doip.listenAddress;
        doipConfig.port = doip.port;
        doipConfig.tlsConfig = new DoipServerConfig.TlsConfig();
        doipConfig.numThreads = doip.numThreads;
        doipConfig.backlog = doip.backlog;
        doipConfig.maxIdleTimeMillis = doip.maxIdleTimeMillis;
        if (doip.tlsConfig != null) {
            doipConfig.tlsConfig.id = doip.tlsConfig.id;
            doipConfig.tlsConfig.publicKey = doip.tlsConfig.publicKey;
            doipConfig.tlsConfig.privateKey = doip.tlsConfig.privateKey;
            doipConfig.tlsConfig.certificateChain = doip.tlsConfig.certificateChain;
        }
        if (doip.processorConfig != null) {
            doipConfig.processorConfig = doip.processorConfig.deepCopy();
        }
        if (doipConfig.listenAddress == null) {
            doipConfig.listenAddress = doipSetupProvider.getListenAddress();
        }
        if (doipConfig.listenAddress == null) {
            doipConfig.listenAddress = "localhost";
        }
        if (doipConfig.processorConfig == null) {
            doipConfig.processorConfig = new JsonObject();
        }
        if (!doipConfig.processorConfig.has("baseUri")) {
            String baseUri = "http://localhost:" + doipSetupProvider.getInternalListenerPort();
            String cordraContext = doipSetupProvider.getContextPath();
            baseUri += cordraContext + "/";
            doipConfig.processorConfig.addProperty("baseUri", baseUri);
        }
        if (!doipConfig.processorConfig.has("username")) {
            doipConfig.processorConfig.addProperty("username", "admin");
        }
        if (!doipConfig.processorConfig.has("password")) {
            doipConfig.processorConfig.addProperty("password", doipSetupProvider.getInternalPassword());
        }
        String id = doipConfig.tlsConfig.id;
        if (id == null && doipConfig.processorConfig.has("serviceId")) {
            id = doipConfig.processorConfig.get("serviceId").getAsString();
        }
        if (id == null) id = prefix + "/service";
        if (doipConfig.tlsConfig.id == null) doipConfig.tlsConfig.id = id;
        doipSetupProvider.createAndSaveKeysIfNecessary();
        if ((doipConfig.tlsConfig.publicKey == null && doipConfig.tlsConfig.certificateChain == null) || doipConfig.tlsConfig.privateKey == null) {
            doipConfig.tlsConfig.publicKey = doipSetupProvider.getPublicKey();
            doipConfig.tlsConfig.privateKey = doipSetupProvider.getPrivateKey();
            doipConfig.tlsConfig.certificateChain = doipSetupProvider.getCertChain();
        }
        if (doipConfig.tlsConfig.publicKey == null && doipConfig.tlsConfig.certificateChain != null && doipConfig.tlsConfig.certificateChain.length > 0) {
            doipConfig.tlsConfig.publicKey = doipConfig.tlsConfig.certificateChain[0].getPublicKey();
        }
        if (!doipConfig.processorConfig.has("serviceId")) {
            doipConfig.processorConfig.addProperty("serviceId", id);
        }
        if (!doipConfig.processorConfig.has("publicKey") && doipConfig.tlsConfig.publicKey != null) {
            doipConfig.processorConfig.add("publicKey", GsonUtility.getGson().toJsonTree(doipConfig.tlsConfig.publicKey));
        }
        if (!doipConfig.processorConfig.has("address") && doipConfig.listenAddress != null) {
            doipConfig.processorConfig.addProperty("address", doipConfig.listenAddress);
        }
        if (!doipConfig.processorConfig.has("port")) {
            doipConfig.processorConfig.addProperty("port", doipConfig.port);
        }
        return doipConfig;
    }

    private void createAndSaveHandleKeys() throws Exception {
        logger.info("No handle keys found; minting new keypair");
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair keys = kpg.generateKeyPair();
        handlePublicKey = keys.getPublic();
        handlePrivateKey = keys.getPrivate();
        try {
            String cordraDataString = System.getProperty(Constants.CORDRA_DATA);
            if (cordraDataString == null) throw new Exception("cordra.data is null");
            Path cordraDataPath = Paths.get(cordraDataString);
            byte[] privateKeyBytes = Util.encrypt(Util.getBytesFromPrivateKey(handlePrivateKey), null, Common.ENCRYPT_NONE);
            Path privateKeyPath = cordraDataPath.resolve(CordraServiceFactory.HANDLE_PRIVATE_KEY_BIN);
            Files.write(privateKeyPath, privateKeyBytes, StandardOpenOption.CREATE_NEW);
            byte[] publicKeyBytes = Util.getBytesFromPublicKey(handlePublicKey);
            Path publicKeyPath = cordraDataPath.resolve(CordraServiceFactory.HANDLE_PUBLIC_KEY_BIN);
            Files.write(publicKeyPath, publicKeyBytes, StandardOpenOption.CREATE_NEW);
        } catch (Exception e) {
            logger.error("Unable to store newly-minted handle keys", e);
            System.out.println("Unable to store newly-minted handle keys (see error.log for details)");
        }
    }

    private boolean isConfigChanged(Design oldDesign, Design newDesign, Function<Design, Object> accessor) {
        Object oldConfig;
        Object newConfig;
        if (oldDesign == null) {
            oldConfig = null;
        } else {
            oldConfig = accessor.apply(oldDesign);
        }
        if (newDesign == null) {
            newConfig = null;
        } else {
            newConfig = accessor.apply(newDesign);
        }
        if (oldConfig == null) {
            if (newConfig == null) return false;
            else return true;
        }
        else if (newConfig == null) return true;
        else return !(oldConfig.equals(newConfig));
    }

    private Map<String, String> getSchemaIdsFromDesignObject(CordraObject designObject) {
        JsonElement schemaIds = designObject.metadata.internalMetadata.get("schemaIds");
        if (schemaIds == null) {
            return new HashMap<>();
        } else {
            return gson.fromJson(schemaIds.getAsString(), new TypeToken<Map<String,String>>() { }.getType());
        }
    }

    // called only from factory initialization method
    public void reindexEverythingIfIndexIsEmpty(boolean isBrandNewDesignObject) throws CordraException {
        // allow read-only instance to reindex
        //        if (isReadOnly) throw new ReadOnlyCordraException();
        boolean wasReindexInProcess = transactionManager.isReindexInProcess();
        if (wasReindexInProcess) {
            reindexer.reindexEverything(isBrandNewDesignObject);
            return;
        }
        QueryParams params = new QueryParams(0, 2, null);
        try (SearchResults<String> results = indexer.searchHandles("*:*", params)) {
            if (results.size() == 0) {
                reindexer.reindexEverything(isBrandNewDesignObject);
                return;
            }
            if (results.size() == 1) {
                String foundHandle = results.stream().findFirst().orElse(null);
                if (DESIGN_OBJECT_ID.equals(foundHandle)) {
                    reindexer.reindexEverything(isBrandNewDesignObject);
                    return;
                }
            }
        }
    }

    public void reindexBatchIds(List<String> batch, boolean lockObjects) throws CordraException {
        reindexer.indexBatch(batch, lockObjects);
    }

    public CordraStorage getStorage() {
        return storage;
    }

    // called only from factory initialization method
    public void updateKnownSchemasBySearch() throws CordraException, ProcessingException, ReadOnlyCordraException {
        ensureIndexUpToDate();
        designLocker.writeLock().acquire();
        try {
            CordraObject designObject = this.getDesignCordraObject();
            Map<String, String> schemaIds = getSchemaIdsFromDesignObject(designObject);
            Set<String> newSchemaHandles = new HashSet<>();
            try (SearchResults<String> schemasResults = indexer.searchHandles("type:Schema -" + VersionManager.IS_VERSION + ":true")) {
                for (String handle : schemasResults) {
                    newSchemaHandles.add(handle);
                }
            } catch (UncheckedCordraException e) {
                e.throwCause();
            }
            if (newSchemaHandles.equals(schemaIds.keySet())) {
                return;
            }
            // ensure previously cached schemas still included even if search fails
            for (String id : schemaIds.keySet()) {
                CordraObject existingSchema = getCordraObjectOrNull(id);
                if (existingSchema != null && "Schema".equals(existingSchema.type) && !Boolean.TRUE.equals(existingSchema.metadata.isVersion)) {
                    newSchemaHandles.add(id);
                }
            }
            if (newSchemaHandles.equals(schemaIds.keySet())) {
                return;
            }
            logger.info("Schemas found by search differ from those cached in design; rebuilding");
            List<CordraObject> knownSchemaObjects = objectListFromHandleList(newSchemaHandles);
            rebuildSchemasFromListOfObjects(knownSchemaObjects);
            persistDesignToCordraObject(designObject);
        } finally {
            designLocker.writeLock().release();
        }
    }

    // called only from factory initialization method, via initializeDesignCordraObjectIfNeeded
    private static String getDefaultUiConfig() {
        InputStream resource = CordraService.class.getResourceAsStream("uiconfig.json");
        try {
            return StreamUtil.readFully(new InputStreamReader(resource, "UTF-8"));
        } catch (Exception e) {
            throw new AssertionError(e);
        } finally {
            try { resource.close(); } catch (Exception e) { }
        }
    }

    // called only from factory initialization method
    public void createDefaultSchemas() throws CordraException, ProcessingException, ReadOnlyCordraException {
        if (isReadOnly) throw new ReadOnlyCordraException();
        designLocker.writeLock().acquire();
        try {
            List<CordraObject> newSchemas = new ArrayList<>();
            CordraObject user = createSchemaObject("User", DefaultSchemasFactory.getDefaultUserSchema(), DefaultSchemasFactory.getDefaultUserJavaScript());
            newSchemas.add(user);
            CordraObject group = createSchemaObject("Group", DefaultSchemasFactory.getDefaultGroupSchema(), null);
            newSchemas.add(group);
            CordraObject doc = createSchemaObject("Document", DefaultSchemasFactory.getDefaultDocumentSchema(), null);
            newSchemas.add(doc);
            rebuildSchemasFromListOfObjects(newSchemas);
            persistDesignToCordraObject(getDesignCordraObject());
        } finally {
            designLocker.writeLock().release();
        }
    }

    // called only from factory initialization method
    public void updateUserSchemaIfNecessary() throws CordraException, ReadOnlyCordraException, InvalidException {
        if (isReadOnly) throw new ReadOnlyCordraException();
        String schemaId = idFromTypeNoSearch("User");
        if (schemaId == null) return;
        CordraObject co = getCordraObject(schemaId);
        if (!co.content.isJsonObject()) return;
        if (!co.content.getAsJsonObject().has("javascript")) return;
        if (!co.content.getAsJsonObject().get("javascript").isJsonPrimitive()) return;
        String javascript = co.content.getAsJsonObject().get("javascript").getAsString();
        if (!DefaultSchemasFactory.getLegacyDefaultUserJavaScript().trim().equals(javascript.trim())) return;
        co.content.getAsJsonObject().addProperty("javascript", DefaultSchemasFactory.getLegacyUpdateDefaultUserJavaScript());
        writeJsonAndPayloadsIntoCordraObjectIfValidAsUpdate(schemaId, null, co.getContentAsString(), null, null, null, "admin", true, null, false);
    }

    // called only from factory initialization method, via createDefaultSchemas
    private CordraObject createSchemaObject(String schemaName, String schema, String javascript) throws CordraException, ReadOnlyCordraException {
        if (isReadOnly) throw new ReadOnlyCordraException();
        while (true) {
            String handle = handleMinter.mintByTimestamp();
            CordraTransaction txn = makeUpdateTransactionFor(handle);
            try {
                CordraObject co = new CordraObject();
                co.id = handle;
                initializeSchemaObject(co, schemaName, schema, javascript, txn.txnId);
                try {
                    storage.create(co);
                } catch (ConflictCordraException e) {
                    transactionManager.closeTransaction(txn.txnId, cordraServiceId);
                    continue;
                }
                boolean indexPayloads = shouldIndexPayloads("Schema");
                indexObject(co, indexPayloads);
                sendUpdateReplicationMessage(co);
                transactionManager.closeTransaction(txn.txnId, cordraServiceId);
                return co;
            } catch (Exception e) {
                try {
                    transactionReprocessingQueue.insert(txn, cordraServiceId);
                    transactionManager.closeTransaction(txn.txnId, cordraServiceId);
                } catch (Exception ex) {
                    logger.error("Error in createSchemaObject; followed by reprocessing error", e);
                    throw ex;
                }
                throw e;
            }
        }
    }

    // used for testing; otherwise
    // called only from factory initialization method, via createDefaultSchemas
    public static void initializeSchemaObject(CordraObject co, String schemaName, String schema, String javascript, long txnId) {
        SchemaInstance schemaInstance = new SchemaInstance();
        schemaInstance.identifier = co.id;
        schemaInstance.name = schemaName;
        schemaInstance.schema = new JsonParser().parse(schema);
        schemaInstance.javascript = javascript;
        co.type = "Schema";
        co.setContent(schemaInstance);
        co.metadata = new CordraObject.Metadata();
        co.metadata.internalMetadata = new JsonObject();
        co.metadata.createdBy = "admin";
        co.metadata.modifiedBy = "admin";
        co.metadata.internalMetadata.addProperty("schemaName", schemaName);
        co.metadata.txnId = txnId;
        long now = System.currentTimeMillis();
        co.metadata.createdOn = now;
        co.metadata.modifiedOn = now;
    }

    // called only from factory initialization method AND loadStatefulData
    private List<CordraObject> objectListFromHandleList(Collection<String> knownSchemaHandles) throws CordraException {
        List<CordraObject> knownSchemaObjects = new ArrayList<>();
        for (String schemaHandle : knownSchemaHandles) {
            try {
                knownSchemaObjects.add(getCordraObject(schemaHandle));
            } catch (NotFoundCordraException e) {
                logger.warn("Could not find cordra object for listed schema " + schemaHandle);
            }
        }
        return knownSchemaObjects;
    }

    // called only from factory initialization method AND loadStatefulData
    // always called inside designLocker lock
    private void rebuildSchemasFromListOfObjects(List<CordraObject> objects) throws CordraException, ProcessingException {
        //schemaJavaScriptModules.reloadAll(objects);
        ConcurrentMap<String, String> schemaJavaScripts = new ConcurrentHashMap<>();
        Map<String, String> schemaIds = new ConcurrentHashMap<>();
        Map<String, SchemaAndNode> newSchemas = new ConcurrentHashMap<>();
        Map<String, CordraObject> newSchemaCordraObjects = new ConcurrentHashMap<>();
        newSchemas.put("Schema", new SchemaAndNode(SchemaSchemaFactory.getSchema(), SchemaSchemaFactory.getNode()));
        newSchemas.put("CordraDesign", new SchemaAndNode(CordraDesignSchemaFactory.getSchema(), CordraDesignSchemaFactory.getNode()));

        List<String> schemaHandles = new ArrayList<>();
        Map<String, JsonNode> schemaNodes = new HashMap<>();
        ConcurrentMap<String, String> schemaStrings = new ConcurrentHashMap<>();
        jsonSchemaFactory = JsonSchemaFactoryFactory.newJsonSchemaFactory();
        for (CordraObject schemaObject : objects) {
            String schemaHandle = schemaObject.id;
            schemaHandles.add(schemaHandle);
            JsonNode node = JsonUtil.gsonToJackson(schemaObject.content);
            JsonNode schemaNode = JsonUtil.getJsonAtPointer("/schema", node);
            JsonSchema schema = JsonUtil.parseJsonSchema(jsonSchemaFactory, schemaNode);
            String type = JsonUtil.getJsonAtPointer("/name", node).asText();
            String js = JsonUtil.getJsonAtPointer("/javascript", node).asText();
            if (js != null && !js.isEmpty()) {
                schemaJavaScripts.put(type, js);
            }
            schemaStrings.put(type, JsonUtil.printJson(schemaNode));
            newSchemas.put(type, new SchemaAndNode(schema, schemaNode));
            newSchemaCordraObjects.put(type, schemaObject);
            schemaIds.put(schemaHandle, type);
            schemaNodes.put(type, schemaNode);
        }
        boolean needClear = false;
        Map<String, String> oldJavaScripts = cordraRequireLookup.getAllSchemaJavaScripts();
        if (!oldJavaScripts.equals(schemaJavaScripts)) {
            this.cordraRequireLookup.replaceAllSchemaJavaScript(schemaJavaScripts);
            needClear = true;
        }
        Map<String, String> oldSchemas = cordraRequireLookup.getAllSchemas();
        if (!oldSchemas.equals(schemaStrings)) {
            this.cordraRequireLookup.replaceAllSchemas(schemaStrings);
            needClear = true;
        }
        if (needClear) {
            javaScriptEnvironment.clearCache();
        }
        this.schemas = newSchemas;
        this.schemaCordraObjects = newSchemaCordraObjects;
        design.schemas = getSchemaNodes();
        design.schemaIds = schemaIds;
    }

    // admin API
    public void updateHandleMintingConfig(HandleMintingConfig handleMintingConfig) throws CordraException, ReadOnlyCordraException {
        if (isReadOnly) throw new ReadOnlyCordraException();
        fixPrefixOnHandleMintingConfig(handleMintingConfig);
        String baseUri = handleMintingConfig.baseUri;
        designLocker.writeLock().acquire();
        //CordraTransaction txn = null;
        try {
            String oldHandleJavaScript = cordraRequireLookup.getHandleJavaScript();
            if ((handleMintingConfig.javascript == null && oldHandleJavaScript != null)
                    || (handleMintingConfig.javascript != null && !handleMintingConfig.javascript.equals(oldHandleJavaScript))) {
                cordraRequireLookup.setHandleJavaScript(handleMintingConfig.javascript);
                javaScriptEnvironment.clearCache();
            }
            if (this.design.handleMintingConfig.prefix != handleMintingConfig.prefix) {
                restartDoipServiceAfterConfigChange();
            }
            if (!handleMintingConfig.isMintHandles()) {
                handleClient = null;
            } else {
                baseUri = ensureSlash(baseUri);
                handleMintingConfig.baseUri = baseUri;
                if (handleMintingConfig.handleAdminIdentity != null && privateKey != null) {
                    ValueReference valRef = ValueReference.fromString(handleMintingConfig.handleAdminIdentity);
                    authInfo = new PublicKeyAuthenticationInfo(valRef.handle, valRef.index, privateKey);
                }
                if (authInfo != null) {
                    handleClient = new HandleClient(authInfo, handleMintingConfig, javaScriptHooks, doipServiceId);
                }
            }
            this.handleMinter.setPrefix(handleMintingConfig.prefix);
            //txn = makeUpdateTransactionFor(DESIGN_OBJECT_ID);
            CordraObject designObject = getDesignCordraObject();
            design.handleMintingConfig = handleMintingConfig;
            persistDesignToCordraObject(designObject);
            //signalWatcher.sendSignal(SignalWatcher.Signal.DESIGN);
            //sendUpdateReplicationMessage(designObject);
            //transactionManager.closeTransaction(txn.txnId, cordraServiceId);
            //        } catch (Exception e) {
            //            if (txn != null) {
            //                transactionReprocessingQueue.insert(txn, cordraServiceId);
            //                transactionManager.closeTransaction(txn.txnId, cordraServiceId);
            //            }
            //            throw e;
        } finally {
            designLocker.writeLock().release();
        }
    }

    private static void fixPrefixOnHandleMintingConfig(HandleMintingConfig handleMintingConfig) {
        if (handleMintingConfig.prefix == null || handleMintingConfig.prefix.isEmpty()) {
            handleMintingConfig.prefix = "test";
        }
        if (handleMintingConfig.prefix.toUpperCase(Locale.ENGLISH).startsWith("0.NA/")) {
            handleMintingConfig.prefix = handleMintingConfig.prefix.substring(5);
        }
        if (handleMintingConfig.prefix.endsWith("/")) {
            handleMintingConfig.prefix = handleMintingConfig.prefix.substring(0, handleMintingConfig.prefix.length() - 1);
        }
    }

    // admin API
    public void updateDesign(Design designUpdate) throws CordraException, ReadOnlyCordraException {
        if (isReadOnly) throw new ReadOnlyCordraException();
        designLocker.writeLock().acquire();
        try {
            CordraObject designObject = getDesignCordraObject();
            design.merge(designUpdate);
            persistDesignToCordraObject(designObject);
        } finally {
            designLocker.writeLock().release();
        }
    }

    public List<String> getIds() {
        if (design.ids != null) return design.ids;
        if (design.handleMintingConfig != null) {
            String baseUri = design.handleMintingConfig.baseUri;
            if (baseUri != null) {
                return Arrays.asList(ensureSlash(baseUri), ensureNoSlash(baseUri));
            }
        }
        return Collections.emptyList();
    }

    private static String ensureSlash(String s) {
        if (s == null) return null;
        if (s.endsWith("/")) return s;
        else return s + "/";
    }

    private static String ensureNoSlash(String s) {
        if (s == null) return null;
        if (s.endsWith("/")) return s.substring(0, s.length() - 1);
        else return s;
    }

    // admin API
    public void updateAuthConfig(AuthConfig authConfig) throws CordraException, ReadOnlyCordraException {
        if (isReadOnly) throw new ReadOnlyCordraException();
        designLocker.writeLock().acquire();
        try {
            design.authConfig = authConfig;
            aclEnforcer.setAuthConfig(authConfig);
            CordraObject designObject = getDesignCordraObject();
            persistDesignToCordraObject(designObject);
        } finally {
            designLocker.writeLock().release();
        }
    }

    private void persistDesignToCordraObject(CordraObject designObject) throws CordraException, ReadOnlyCordraException {
        persistDesignToCordraObject(designObject, false, design, design.schemaIds);
    }

    private void persistDesignToCordraObject(CordraObject designObject, boolean isCreate, Design designLite, Map<String, String> schemaIds) throws CordraException, ReadOnlyCordraException {
        String designJson = gson.toJson(designLite);
        if (designLite instanceof DesignPlusSchemas) {
            // remove schemas
            designLite = gson.fromJson(designJson, Design.class);
            designJson = gson.toJson(designLite);
        }
        if (designObject.metadata == null) designObject.metadata = new CordraObject.Metadata();
        if (designObject.metadata.internalMetadata == null) designObject.metadata.internalMetadata = new JsonObject();
        designObject.metadata.internalMetadata.addProperty("schemaIds", gson.toJson(schemaIds));
        updateCordraObject(designObject, isCreate, "CordraDesign", designJson, null, null, null, null, "admin", null, false);
        signalWatcher.sendSignal(SignalWatcher.Signal.DESIGN);
    }

    // admin API
    public void setAdminPassword(String password) throws Exception {
        if (isReadOnly) throw new ReadOnlyCordraException();
        if (password == null || password.isEmpty()) {
            throw new BadRequestCordraException("Empty admin password is not allowed");
        }
        HashAndSalt hashAndSalt = new HashAndSalt(password, HashAndSalt.NIST_2017_HASH_ITERATION_COUNT_10K, HashAndSalt.PBKDF2WithHmacSHA1);
        String hash = hashAndSalt.getHashString();
        String salt = hashAndSalt.getSaltString();
        String iterationsString = hashAndSalt.getIterations().toString();
        String alg = hashAndSalt.getAlgorithm();
        designLocker.writeLock().acquire();
        try {
            CordraObject designObject = getDesignCordraObject();
            designObject.metadata.internalMetadata.addProperty(HASH_ATTRIBUTE, hash);
            designObject.metadata.internalMetadata.addProperty(SALT_ATTRIBUTE, salt);
            designObject.metadata.internalMetadata.addProperty(ITERATIONS_ATTRIBUTE, iterationsString);
            designObject.metadata.internalMetadata.addProperty(HASH_ALGORITHM_ATTRIBUTE, alg);
            persistDesignToCordraObject(designObject);
        } finally {
            designLocker.writeLock().release();
        }
    }

    public List<HandleValue> getHandleValues(String handle) throws CordraException {
        CordraObject co = getCordraObjectOrNull(handle);
        if (co == null) {
            if (doipServerConfig != null && doipServerConfig.enabled && handle.equals(doipServiceId)) {
                return HandleClient.createDoipServiceHandleValues(doipServerConfig);
            }
            throw new NotFoundCordraException(handle);
        }
        co = copyOfCordraObjectRemovingInternalMetadata(co);
        JsonNode dataNode = JsonUtil.gsonToJackson(co.content);
        List<HandleValue> result = null;
        try {
            result = HandleClient.createHandleValues(co, co.type, dataNode, design.handleMintingConfig, javaScriptHooks, null, doipServiceId);
        } catch (Exception e) {
            throw new InternalErrorCordraException(e);
        }
        return result;
    }

    // admin API
    public UpdateStatus getHandleUpdateStatus() {
        return this.handlesUpdater.getStatus();
    }

    // admin API
    public void updateAllHandleRecords() throws ReadOnlyCordraException {
        if (isReadOnly) throw new ReadOnlyCordraException();
        handlesUpdater.updateAllHandles(handleClient, this);
    }

    public boolean checkAdminPassword(String password) throws CordraException {
        CordraObject designObject = getDesignCordraObject();
        if (designObject == null) return false;
        JsonElement hash = designObject.metadata.internalMetadata.get(HASH_ATTRIBUTE);
        JsonElement salt = designObject.metadata.internalMetadata.get(SALT_ATTRIBUTE);
        if (hash == null || salt == null) {
            return false;
        }
        String hashString = hash.getAsString();
        String saltString = salt.getAsString();
        JsonElement iterationsElement = designObject.metadata.internalMetadata.get(ITERATIONS_ATTRIBUTE);
        int iterations;
        if (iterationsElement != null) {
            iterations = iterationsElement.getAsInt();
        } else {
            iterations = HashAndSalt.LEGACY_HASH_ITERATION_COUNT_2048;
        }
        JsonElement algorithm = designObject.metadata.internalMetadata.get(HASH_ALGORITHM_ATTRIBUTE);
        String algString = algorithm != null ? algorithm.getAsString() : null;
        HashAndSalt hashAndSalt = new HashAndSalt(hashString, saltString, iterations, algString);
        return hashAndSalt.verifySecret(password);
    }

    public boolean isAdminPasswordSet() throws CordraException {
        CordraObject designObject = getDesignCordraObject();
        if (designObject == null) return false;
        JsonElement hash = designObject.metadata.internalMetadata.get(HASH_ATTRIBUTE);
        JsonElement salt = designObject.metadata.internalMetadata.get(SALT_ATTRIBUTE);
        if (hash == null || salt == null) {
            return false;
        } else {
            return true;
        }
    }

    public Authenticator getAuthenticator() {
        return authenticator;
    }

    public AclEnforcer getAclEnforcer() {
        return aclEnforcer;
    }

    public boolean isKnownType(String type) {
        return type != null && schemas.containsKey(type);
    }

    public SchemaAndNode getSchema(String type) {
        if (type == null) return null;
        return schemas.get(type);
    }

    public String idFromTypeNoSearch(String type) {
        for (Map.Entry<String, String> schemaIdAndName : design.schemaIds.entrySet()) {
            String schemaId = schemaIdAndName.getKey();
            String schemaName = schemaIdAndName.getValue();
            if (schemaName.equals(type)) {
                return schemaId;
            }
        }
        return null;
    }

    public String idFromType(String type) throws CordraException {
        ensureIndexUpToDate();
        try (SearchResults<CordraObject> results = indexer.search("type:Schema AND schemaName:\"" + type + "\"");) {
            for (CordraObject co : results) {
                String name = co.content.getAsJsonObject().get("name").getAsString();
                if (type.equals(name)) {
                    return co.id;
                }
            }
        } catch (UncheckedCordraException e) {
            throw e.getCause();
        }
        return null;
    }

    private CordraObject getDesignCordraObject() throws CordraException {
        return storage.get(DESIGN_OBJECT_ID);
    }

    public DesignPlusSchemas getDesign() {
        return design;
    }

    public String getAllSchemasAsJsonString() throws CordraException {
        try {
            Map<String, JsonNode> schemaNodes = new HashMap<>();
            for (Map.Entry<String, SchemaAndNode> entry : schemas.entrySet()) {
                schemaNodes.put(entry.getKey(), entry.getValue().schemaNode);
            }
            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writeValueAsString(schemaNodes);
            return json;
        } catch (JsonProcessingException e) {
            throw new InternalErrorCordraException(e);
        }
    }

    public String getSchemaAsJsonString(String objectType) throws CordraException {
        try {
            ObjectMapper mapper = new ObjectMapper();
            if (objectType == null) return null;
            SchemaAndNode schema = schemas.get(objectType);
            if (schema == null) return null;
            String json = mapper.writeValueAsString(schema.schemaNode);
            return json;
        } catch (JsonProcessingException e) {
            throw new InternalErrorCordraException(e);
        }
    }

    public InputStream readPayload(String objectId, String payloadName) throws CordraException {
        InputStream res = storage.getPayload(objectId, payloadName);
        if (res == null) throw new NotFoundCordraException(objectId + " payload " + payloadName);
        return res;
    }

    public static net.cnri.cordra.api.Payload getCordraObjectPayloadByName(CordraObject co, String payloadName) {
        if (co.payloads == null) return null;
        for (net.cnri.cordra.api.Payload payload : co.payloads) {
            if (payload.name.equals(payloadName)) {
                return payload;
            }
        }
        return null;
    }

    public JsonElement getObjectFilterByJsonPointers(String objectId, String userId, Set<String> filter, boolean isFull) throws CordraException, InvalidException {
        CordraObject co = getCordraObject(objectId);
        try {
            co = postProcess(userId, co);
        } catch (ScriptException | InterruptedException e) {
            throw new InternalErrorCordraException(e);
        }
        JsonElement result;
        if (isFull) {
            JsonElement jsonElement = gson.toJsonTree(co, CordraObject.class);
            result = JsonUtil.pruneToMatchPointers(jsonElement, filter);
        } else {
            result = JsonUtil.pruneToMatchPointers(co.content, filter);
        }
        return result;
    }

    public JsonNode getAtJsonPointer(String objectId, String userId, String jsonPointer) throws CordraException, InvalidException {
        CordraObject co = getCordraObject(objectId);
        try {
            co = postProcess(userId, co);
        } catch (ScriptException | InterruptedException e) {
            throw new InternalErrorCordraException(e);
        }
        if (co.content == null) throw new InternalErrorCordraException("Missing JSON attribute on " + objectId);
        JsonNode dataNode = JsonUtil.gsonToJackson(co.content);
        if (!JsonUtil.isValidJsonPointer(jsonPointer)) return null;
        JsonNode subNode = dataNode.at(jsonPointer);
        if (subNode.isMissingNode()) return null;
        return subNode;
    }

    @SuppressWarnings("resource")
    public PayloadWithRange getPayloadByName(String objectId, String payloadName, boolean metadata, Long start, Long end) throws CordraException {
        CordraObject co = getCordraObject(objectId);
        net.cnri.cordra.api.Payload payload = getCordraObjectPayloadByName(co, payloadName);
        PayloadWithRange payloadWithRange = null;
        if (payload != null) {
            long size = payload.size;
            InputStream stream = null;
            if (!metadata) {
                if (size <= 0 || (start == null && end == null)) {
                    stream = storage.getPayload(objectId, payloadName);
                } else if (end == null) {
                    end = size - 1;
                    stream = storage.getPartialPayload(objectId, payloadName, start, null);
                } else if (start == null) {
                    start = size - end.longValue();
                    end = size - 1;
                    stream = storage.getPartialPayload(objectId, payloadName, start, null);
                } else if (start > end) {
                    // no stream
                } else {
                    if (end > size - 1) end = size - 1;
                    stream = storage.getPartialPayload(objectId, payloadName, start, end);
                }
            }
            payload.setInputStream(stream);
            payloadWithRange = new PayloadWithRange(payload, new Range(start, end));
        }
        return payloadWithRange;
    }

    public String getObjectJson(String objectId) throws CordraException {
        CordraObject co = getCordraObject(objectId);
        String jsonData = co.getContentAsString();
        if (jsonData == null) throw new InternalErrorCordraException("Missing JSON attribute on " + objectId);
        return jsonData;
    }

    public String getFullObjectJson(String objectId) throws CordraException {
        CordraObject co = getCordraObjectWithNoInternalMetadata(objectId);
        return gson.toJson(co);
    }

    public CordraObject getCordraObject(String objectId) throws CordraException {
        CordraObject co = storage.get(objectId);
        if (co == null) throw new NotFoundCordraException(objectId);
        return co;
    }

    public CordraObject getCordraObjectOrNull(String objectId) throws CordraException {
        return storage.get(objectId);
    }

    public SearchResults<CordraObject> getObjects(Collection<String> ids) throws CordraException {
        return storage.get(ids);
    }

    public boolean doesCordraObjectExist(String objectId) throws CordraException {
        return storage.get(objectId) != null;
    }

    public static CordraObject copyOfCordraObjectRemovingInternalMetadata(CordraObject co) {
        CordraObject res = new CordraObject();
        res.id = co.id;
        res.type = co.type;
        res.content = co.content;
        res.acl = co.acl;
        res.payloads = co.payloads;
        if (co.metadata == null) return res;
        res.metadata = new CordraObject.Metadata();
        res.metadata.createdBy = co.metadata.createdBy;
        res.metadata.createdOn = co.metadata.createdOn;
        res.metadata.isVersion = co.metadata.isVersion;
        res.metadata.modifiedBy = co.metadata.modifiedBy;
        res.metadata.modifiedOn = co.metadata.modifiedOn;
        res.metadata.publishedBy = co.metadata.publishedBy;
        res.metadata.publishedOn = co.metadata.publishedOn;
        res.metadata.hashes = co.metadata.hashes;
        res.metadata.remoteRepository = co.metadata.remoteRepository; // presumably disused; remove from cordra-client.jar if so
        res.metadata.txnId = co.metadata.txnId;
        res.metadata.versionOf = co.metadata.versionOf;
        res.userMetadata = co.userMetadata;
        return res;
    }

    public CordraObject getCordraObjectWithNoInternalMetadata(String objectId) throws CordraException {
        CordraObject co = getCordraObject(objectId);
        //        if (co.metadata != null) {
        //            co.metadata.internalMetadata = null;
        //        }
        //        return co;
        return copyOfCordraObjectRemovingInternalMetadata(co);
    }

    public CordraObject getContentPlusMetaWithPostProcessing(String objectId, String userId) throws CordraException, ScriptException, InterruptedException, InvalidException {
        CordraObject co = getCordraObjectWithNoInternalMetadata(objectId);
        return postProcess(userId, co);
    }

    private CordraObject postProcess(String userId, CordraObject co) throws CordraException, ScriptException, InterruptedException, InvalidException {
        Map<String, Object> context = createContext(userId, co);
        return this.javaScriptHooks.runJavaScriptFunction(co, JavaScriptLifeCycleHooks.ON_OBJECT_RESOLUTION, context);
    }

    private Map<String, Object> createContext(String userId, CordraObject co) throws CordraException {
        Map<String, Object> context = new HashMap<>();
        context.put("objectId", co.id);
        context.put("userId", userId);
        context.put("effectiveAcl", getAclEnforcer().getEffectiveAcl(co));
        context.put("groups", getGroupsForUser(userId));
        return context;
    }

    public CordraObject.AccessControlList getAclFor(String objectId, String userId) throws CordraException, ScriptException, InterruptedException, InvalidException {
        CordraObject co = getContentPlusMetaWithPostProcessing(objectId, userId);
        return co.acl;
    }

    private CordraTransaction makeUpdateTransactionFor(String objectId, boolean isNeedToReplicate) throws CordraException {
        long txnId = transactionManager.getAndIncrementNextTransactionId();
        Long now = System.currentTimeMillis();
        CordraTransaction txn = new CordraTransaction(txnId, now, objectId, CordraTransaction.OP.UPDATE, isNeedToReplicate);
        transactionManager.openTransaction(txnId, cordraServiceId, txn);
        return txn;
    }

    private CordraTransaction makeDeleteTransactionFor(String objectId, boolean isNeedToReplicate) throws CordraException {
        long txnId = transactionManager.getAndIncrementNextTransactionId();
        Long now = System.currentTimeMillis();
        CordraTransaction txn = new CordraTransaction(txnId, now, objectId, CordraTransaction.OP.DELETE, isNeedToReplicate);
        transactionManager.openTransaction(txnId, cordraServiceId, txn);
        return txn;
    }

    private CordraTransaction makeUpdateTransactionFor(String objectId) throws CordraException {
        return makeUpdateTransactionFor(objectId, true);
    }

    public void delete(String objectId, String userId) throws CordraException, ReadOnlyCordraException, InvalidException {
        delete(objectId, userId, true);
    }

    public void delete(String objectId, String userId, boolean isNeedToReplicate) throws CordraException, ReadOnlyCordraException, InvalidException {
        if (isReadOnly && isNeedToReplicate) throw new ReadOnlyCordraException();
        objectLocker.lock(objectId);
        CordraTransaction txn = null;
        try {
            CordraObject co = storage.get(objectId);
            String type = co == null ? null : co.type;
            if (isNeedToReplicate) {
                if (co == null) {
                    throw new NotFoundCordraException(objectId);
                } else if (DESIGN_OBJECT_ID.equals(objectId)) {
                    throw new InternalErrorCordraException("Object not valid for deletion: " + objectId);
                } else {
                    try {
                        Map<String, Object> context = createContext(userId, co);
                        javaScriptHooks.beforeDelete(co, context);
                    } catch (InvalidException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new InternalErrorCordraException(e);
                    }
                }
            }
            txn = this.makeDeleteTransactionFor(objectId, isNeedToReplicate);
            if ("Schema".equals(type)) {
                designLocker.writeLock().acquire();
                try {
                    storage.delete(objectId);
                    deleteFromKnownSchemas(objectId);
                } finally {
                    designLocker.writeLock().release();
                }
            } else if (co != null) {
                storage.delete(objectId);
            }
            if (handleClient != null) {
                try {
                    handleClient.deleteHandle(objectId);
                } catch (HandleException e) {
                    logger.warn("Failure to delete handle " + objectId + ", out of sync", e);
                    // throw new InternalErrorCordraException(e);
                }
            }
            indexer.deleteObject(objectId);
            if (isNeedToReplicate) sendDeleteReplicationMessage(objectId);
            if (isUserOrGroup(co)) {
                authObjectChangeCount.incrementAndGet();
                authCache.clearAllGroupsForUserValues();
                authCache.clearAllUserIdForUsernameValues();
                signalWatcher.sendSignal(SignalWatcher.Signal.AUTH_CHANGE);
                if (co != null && !isUserAccountActive(co)) {
                    invalidateSessionsForUser(co.id);
                }
                preCache();
            }
            transactionManager.closeTransaction(txn.txnId, cordraServiceId);
        } catch (Exception e) {
            if (txn != null) {
                try {
                    transactionReprocessingQueue.insert(txn, cordraServiceId);
                    transactionManager.closeTransaction(txn.txnId, cordraServiceId);
                } catch (Exception ex) {
                    logger.error("Error in delete; followed by reprocessing error", e);
                    throw ex;
                }
            }
            throw e;
        } finally {
            objectLocker.release(objectId);
        }
    }

    public void deleteJsonPointer(String objectId, String jsonPointer, String userId, boolean hasUserObject) throws CordraException, InvalidException, ReadOnlyCordraException {
        if (isReadOnly) throw new ReadOnlyCordraException();
        //        if (jsonPointer == null || jsonPointer.isEmpty()) {
        //            delete(objectId);
        //            return;
        //        }
        objectLocker.lock(objectId);
        try {
            CordraObject co = getCordraObject(objectId);
            JsonNode existingJsonNode = JsonUtil.gsonToJackson(co.content);
            JsonUtil.deletePointer(existingJsonNode, jsonPointer);
            String modifiedJson = existingJsonNode.toString();
            writeJsonAndPayloadsIntoCordraObjectIfValidAsUpdate(objectId, null, modifiedJson, null, null, null, userId, hasUserObject, Collections.singletonList(jsonPointer), false);
        } finally {
            objectLocker.release(objectId);
        }
    }

    public CordraObject modifyObjectAtJsonPointer(String objectId, String jsonPointer, String replacementJsonData, String userId, boolean hasUserObject, boolean isDryRun) throws CordraException, InvalidException, ReadOnlyCordraException {
        if (isReadOnly) throw new ReadOnlyCordraException();
        objectLocker.lock(objectId);
        try {
            CordraObject co = getCordraObject(objectId);
            JsonNode existingJsonNode = JsonUtil.gsonToJackson(co.content);
            JsonNode replacementJsonNode = JsonUtil.parseJson(replacementJsonData);
            JsonUtil.replaceJsonAtPointer(existingJsonNode, jsonPointer, replacementJsonNode);
            String modifiedJson = existingJsonNode.toString();
            co = writeJsonAndPayloadsIntoCordraObjectIfValidAsUpdate(objectId, null, modifiedJson, null, null, null, userId, hasUserObject, Collections.emptyList(), isDryRun);
            return co;
        } finally {
            objectLocker.release(objectId);
        }
    }

    public void deletePayload(String objectId, String payloadName, String userId, boolean hasUserObject) throws CordraException, InvalidException, ReadOnlyCordraException {
        if (isReadOnly) throw new ReadOnlyCordraException();
        objectLocker.lock(objectId);
        try {
            CordraObject co = getCordraObject(objectId);
            String type = co.type;
            if (type == null) {
                throw new NotFoundCordraException(objectId);
            }
            String existingJsonData = co.getContentAsString();
            List<String> payloadsToDelete = Collections.singletonList(payloadName);
            writeJsonAndPayloadsIntoCordraObjectIfValidAsUpdate(objectId, null, existingJsonData, null, null, null, userId, hasUserObject, payloadsToDelete, false);
        } finally {
            objectLocker.release(objectId);
        }
    }

    private List<String> getGroupsForUser(String userId) throws CordraException {
        ensureIndexUpToDateWhenAuthChange();
        return getAclEnforcer().getGroupsForUser(userId);
    }

    public CordraObject writeJsonAndPayloadsIntoCordraObjectIfValidAsUpdate(String objectId, String type, String jsonData, CordraObject.AccessControlList acl, JsonObject userMetadata, List<Payload> newPayloads, String userId, boolean hasUserObject, Collection<String> payloadsToDelete, boolean isDryRun) throws CordraException, InvalidException, ReadOnlyCordraException {
        return writeJsonAndPayloadsIntoCordraObjectIfValidAsUpdate(objectId, type, jsonData, acl, userMetadata, newPayloads, userId, hasUserObject, payloadsToDelete, isDryRun, false);
    }

    public CordraObject writeJsonAndPayloadsIntoCordraObjectIfValidAsUpdateWithoutBeforeSchemaValidation(String objectId, String type, String jsonData, CordraObject.AccessControlList acl, JsonObject userMetadata, List<Payload> newPayloads, String userId, boolean hasUserObject, Collection<String> payloadsToDelete, boolean isDryRun) throws CordraException, InvalidException, ReadOnlyCordraException {
        return writeJsonAndPayloadsIntoCordraObjectIfValidAsUpdate(objectId, type, jsonData, acl, userMetadata, newPayloads, userId, hasUserObject, payloadsToDelete, isDryRun, true);
    }

    private boolean isAttemptToModifyImmutableVersion(CordraObject co) {
        if (co.metadata.isVersion != null && co.metadata.isVersion && design.enableVersionEdits != null && design.enableVersionEdits) {
            return true;
        } else {
            return false;
        }
    }

    private CordraObject writeJsonAndPayloadsIntoCordraObjectIfValidAsUpdate(String objectId, String type, String jsonData, CordraObject.AccessControlList acl, JsonObject userMetadata, List<Payload> newPayloads, String userId, @SuppressWarnings("unused") boolean hasUserObject, Collection<String> payloadsToDelete, boolean isDryRun, boolean bypassBeforeSchemaValidation) throws CordraException, InvalidException, ReadOnlyCordraException {
        if (isReadOnly && !isDryRun) throw new ReadOnlyCordraException();
        objectLocker.lock(objectId);
        try {
            CordraObject co = getCordraObject(objectId);
            if (isAttemptToModifyImmutableVersion(co)) throw new BadRequestCordraException("Objects versions cannot be edited");
//            AclEnforcer.Permission permission = aclEnforcer.permittedOperations(userId, hasUserObject, co);
//            if (!AclEnforcer.doesPermissionAllowOperation(permission, AclEnforcer.Permission.WRITE)) {
//                throw new ForbiddenCordraException("User does not have permission to write.");
//            }
            if (type == null) {
                type = co.type;
            }
            if (type == null) {
                throw new NotFoundCordraException(objectId);
            }
            SchemaAndNode schema = schemas.get(type);
            if (schema == null) {
                throw new InvalidException("Unknown type " + type);
            }
            if (jsonData == null) {
                jsonData = co.getContentAsString();
            }
            if (!bypassBeforeSchemaValidation) {
                try {
                    Map<String, Object> context = new HashMap<>();
                    context.put("objectId", objectId);
                    context.put("userId", userId);
                    context.put("groups", getGroupsForUser(userId));
                    context.put("effectiveAcl", getAclEnforcer().getEffectiveAcl(co));
                    context.put("newPayloads", newPayloads);
                    context.put("payloadsToDelete", payloadsToDelete);
                    context.put("isNew", false);
                    ObjectDelta objectDelta = new ObjectDelta(objectId, type, jsonData, acl, userMetadata, newPayloads, payloadsToDelete);
                    objectDelta = javaScriptHooks.beforeSchemaValidation(type, co, objectDelta, context);
                    // can't change digital object id (beforeSchemaValidation will throw)
                    if (!type.equals(objectDelta.type)) {
                        type = objectDelta.type;
                        schema = schemas.get(type);
                        if (schema == null) {
                            throw new InvalidException("Unknown type " + type);
                        }
                    }
                    jsonData = objectDelta.jsonData;
                    acl = objectDelta.acl;
                    userMetadata = objectDelta.userMetadata;
                    newPayloads = objectDelta.payloads;
                    payloadsToDelete = objectDelta.payloadsToDelete;
                } catch (InvalidException e) {
                    throw e;
                } catch (Exception e) {
                    throw new InternalErrorCordraException(e);
                }
            }
            JsonNode jsonNode = JsonUtil.parseJson(jsonData);
            Map<String, JsonNode> pointerToSchemaMap = validator.schemaValidateAndReturnKeywordsMap(jsonNode, schema.schemaNode, schema.schema);
            validator.postSchemaValidate(jsonNode, pointerToSchemaMap);
            validator.validatePayloads(newPayloads);
            boolean isSchema = "Schema".equals(type);
            boolean isUser = UserProcessor.isUser(pointerToSchemaMap);
            boolean isDesign = DESIGN_OBJECT_ID.equals(objectId);
            if (isSchema) schemaNameLocker.acquire();
            if (isUser) usernameLocker.acquire();
            if (isDesign) designLocker.writeLock().acquire();
            ProcessObjectResult processObjectResult;
            try {
                processObjectResult = processObjectBasedOnJsonAndType(co, type, jsonNode, schema.schemaNode, pointerToSchemaMap, userId, isDryRun);
                if (processObjectResult.changedJson) {
                    jsonData = JsonUtil.printJson(jsonNode);
                }
                updateCordraObject(co, false, type, jsonData, acl, userMetadata, payloadsToDelete, newPayloads, userId, pointerToSchemaMap, isDryRun);
                if (isDesign && !isDryRun) {
                    signalWatcher.sendSignal(SignalWatcher.Signal.DESIGN);
                    loadStatefulData();
                }
            } finally {
                if (isUser) usernameLocker.release();
                if (isSchema) schemaNameLocker.release();
                if (isDesign) designLocker.writeLock().release();
            }
            if (!isDryRun) {
                if (processObjectResult.isUserOrGroup) {
                    authObjectChangeCount.incrementAndGet();
                    authCache.clearAllGroupsForUserValues();
                    authCache.clearAllUserIdForUsernameValues();
                    signalWatcher.sendSignal(SignalWatcher.Signal.AUTH_CHANGE);
                    if (isUser && !isUserAccountActive(co)) {
                        invalidateSessionsForUser(co.id);
                    }
                    preCache();
                }
                if (validator.hasJavaScriptModules(pointerToSchemaMap)) {
                    signalWatcher.sendSignal(SignalWatcher.Signal.JAVASCRIPT_CLEAR_CACHE);
                    cordraRequireLookup.clearAllObjectIdsForModuleValues();
                    javaScriptEnvironment.clearCache();
                }
                if ("Schema".equals(type)) {
                    addToKnownSchemas(co.id);
                }
                if (handleClient != null && !isDesign) {
                    try {
                        handleClient.updateHandleFor(co.id, co, type, jsonNode);
                    } catch (Exception e) {
                        alerter.alert("Failure to update handle after updating object " + co.id + ", out of sync");
                        logger.error("Failure to update handle after updating object " + co.id + ", out of sync", e);
                        throw new InternalErrorCordraException(e);
                    }
                }
            }
            return co;
        } finally {
            objectLocker.release(objectId);
        }
    }

    public CordraObject writeJsonAndPayloadsIntoCordraObjectIfValid(String type, String jsonData, CordraObject.AccessControlList acl, JsonObject userMetadata, List<Payload> payloads, String handle, String creatorId, boolean isDryRun) throws CordraException, InvalidException, ReadOnlyCordraException {
        if (isReadOnly && !isDryRun) throw new ReadOnlyCordraException();
        ObjectDelta objectDelta = new ObjectDelta(handle, type, jsonData, acl, userMetadata, payloads, null);
        try {
            Map<String, Object> context = new HashMap<>();
            context.put("objectId", handle);
            context.put("userId", creatorId);
            context.put("groups", getGroupsForUser(creatorId));
            context.put("aclCreate", Collections.unmodifiableList(getAclEnforcer().getDefaultAcls(type).aclCreate));
            context.put("isNew", true);
            objectDelta = javaScriptHooks.beforeSchemaValidation(type, null, objectDelta, context);
            handle = objectDelta.id;
            type = objectDelta.type;
            jsonData = objectDelta.jsonData;
            acl = objectDelta.acl;
            userMetadata = objectDelta.userMetadata;
            payloads = objectDelta.payloads;
        } catch (CordraException | InvalidException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalErrorCordraException(e);
        }
        if (DESIGN_OBJECT_ID.equals(handle)) {
            throw new InternalErrorCordraException("Object not valid for creation: " + handle);
        }
        SchemaAndNode schema = schemas.get(type);
        if (schema == null) {
            throw new InvalidException("Unknown type " + type);
        }
        JsonNode jsonNode = JsonUtil.parseJson(jsonData);
        Map<String, JsonNode> pointerToSchemaMap = validator.schemaValidateAndReturnKeywordsMap(jsonNode, schema.schemaNode, schema.schema);
        validator.postSchemaValidate(jsonNode, pointerToSchemaMap);
        validator.validatePayloads(payloads);
        preprocessObjectBasedOnJsonAndType(null, type, jsonNode, pointerToSchemaMap, isDryRun);
        CordraObject proto = createNewCordraObjectEnsuringHandleLock(jsonNode, objectDelta, pointerToSchemaMap, handle, creatorId, isDryRun);
        String mintedHandle = proto.id;
        CordraObject co = null;
        try {
            boolean isSchema = "Schema".equals(type);
            boolean isUser = UserProcessor.isUser(pointerToSchemaMap);
            if (isSchema) schemaNameLocker.acquire();
            if (isUser) usernameLocker.acquire();
            ProcessObjectResult processObjectResult;
            try {
                processObjectResult = processObjectBasedOnJsonAndType(proto, type, jsonNode, schema.schemaNode, pointerToSchemaMap, creatorId, isDryRun);
                if (processObjectResult.changedJson) {
                    jsonData = JsonUtil.printJson(jsonNode);
                }
                co = updateCordraObject(proto, true, type, jsonData, acl, userMetadata, Collections.<String>emptyList(), payloads, creatorId, pointerToSchemaMap, isDryRun);

            } finally {
                if (isUser) usernameLocker.release();
                if (isSchema) schemaNameLocker.release();
            }
            if (!isDryRun) {
                if (processObjectResult.isUserOrGroup) {
                    authObjectChangeCount.incrementAndGet();
                    authCache.clearAllGroupsForUserValues();
                    authCache.clearAllUserIdForUsernameValues();
                    signalWatcher.sendSignal(SignalWatcher.Signal.AUTH_CHANGE);
                    if (isUser && !isUserAccountActive(co)) {
                        invalidateSessionsForUser(co.id);
                    }
                    preCache();
                }
                if (validator.hasJavaScriptModules(pointerToSchemaMap)) {
                    signalWatcher.sendSignal(SignalWatcher.Signal.JAVASCRIPT_CLEAR_CACHE);
                    cordraRequireLookup.clearAllObjectIdsForModuleValues();
                    javaScriptEnvironment.clearCache();
                }
                if ("Schema".equals(type)) {
                    addToKnownSchemas(co.id);
                }
                if (handleClient != null) {
                    try {
                        handleClient.registerHandle(co.id, co, type, jsonNode);
                    } catch (Exception e) {
                        if (e instanceof CordraException) throw (CordraException) e;
                        else if (e instanceof InvalidException) throw (InvalidException) e;
                        else throw new InternalErrorCordraException(e);
                    }
                }
            }
        } finally {
            objectLocker.release(mintedHandle);
        }
        return co;
    }

    public String call(String objectId, String type, String userId, boolean hasUserObject, String method, String params) throws CordraException, InterruptedException, ScriptException, InvalidException, ReadOnlyCordraException {
        long start = System.currentTimeMillis();
        boolean isStatic = type != null;
        String coJson;
        CordraObject co = null;
        if (isStatic) {
            String schemaId = idFromTypeNoSearch(type);
            if (schemaId == null) {
                throw new NotFoundCordraException("Type not found: " + type);
            }
            coJson = null;
        } else {
            co = getCordraObject(objectId);
            type = co.type;
            coJson = gson.toJson(co);
        }
        String moduleId = CordraRequireLookup.moduleIdForSchemaType(type);
        if (!cordraRequireLookup.exists(moduleId)) {
            throw new NotFoundCordraException("Schema does not have javascript");
        }
        if (!isStatic) objectLocker.lock(objectId);
        Map<String, Object> context = new HashMap<>();
        if (!isStatic) {
            context.put("objectId", objectId);
            context.put("isNew", false);
            context.put("effectiveAcl", getAclEnforcer().getEffectiveAcl(co));
        }
        context.put("userId", userId);
        context.put("groups", getGroupsForUser(userId));
        context.put("hasUserObject", hasUserObject);

        try {
            JavaScriptLifeCycleHooks.CallResult callResult = this.javaScriptHooks.call(method, moduleId, isStatic, coJson, params, context);
            String result = callResult.result;
            String before = callResult.before;
            String after = callResult.after;
            if (!isStatic && before != null) {
                // if changed the digital object, perform an update
                if (!before.equals(after)) {
                    ObjectDelta delta = ObjectDelta.fromStringifiedCordraObjectForUpdate(co, after, null);
                    writeJsonAndPayloadsIntoCordraObjectIfValidAsUpdateWithoutBeforeSchemaValidation(objectId, delta.type, delta.jsonData, delta.acl, delta.userMetadata, delta.payloads, userId, hasUserObject, delta.payloadsToDelete, false);
                }
            }
            return result;
        } finally {
            if (cordraConfig.traceRequests) {
                long end = System.currentTimeMillis();
                long delta = end - start;
                String startTime = dateTimeFormatter.format(Instant.ofEpochMilli(start));
                logger.trace(method + " call: start " + startTime + ", " + delta + "ms");
            }
            if (!isStatic) objectLocker.release(objectId);
        }
    }

    public List<String> listMethods(String type, String objectId, boolean isStatic) throws InterruptedException, ScriptException, CordraException {
        if (type == null) {
            CordraObject obj = getCordraObject(objectId);
            type = obj.type;
            isStatic = false;
        }
        if (getSchema(type) == null) {
            throw new NotFoundCordraException("Type not found " + type);
        }
        String moduleId = CordraRequireLookup.moduleIdForSchemaType(type);
        if (!cordraRequireLookup.exists(moduleId)) {
            //schema does not have javascript
            return Collections.emptyList();
        }
        return javaScriptHooks.listMethods(isStatic, moduleId);
    }

    public static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX").withZone(ZoneOffset.UTC);

    public PublicKey getAdminPublicKey() {
        return design.adminPublicKey;
    }

    public void updatePasswordForUser(String newPassword, String userId) throws Exception {
        if ("admin".equals(userId)) {
            setAdminPassword(newPassword);
            return;
        }
        objectLocker.lock(userId);
        try {
            CordraObject user = getCordraObject(userId);
            if (checkUserPassword(user, newPassword)) {
                throw new BadRequestCordraException("New password can not be the same as previous password.");
            }
            JsonNode jsonNode = JsonUtil.gsonToJackson(user.content);
            String type = user.type;
            Map<String, JsonNode> pointerToSchemaMap = getPointerToSchemaMap(type, jsonNode);
            updatePasswordForUser(newPassword, userId, jsonNode, pointerToSchemaMap);
        } finally {
            objectLocker.release(userId);
        }
    }

    public boolean checkUserPassword(CordraObject user, String password) {
        if (user == null) return false;
        JsonElement hash = user.metadata.internalMetadata.get("hash");
        JsonElement salt = user.metadata.internalMetadata.get("salt");
        if (hash == null || salt == null) {
            return false;
        }
        String hashString = hash.getAsString();
        String saltString = salt.getAsString();
        JsonElement iterationsElement = user.metadata.internalMetadata.get("iterations");
        int iterations;
        if (iterationsElement != null) {
            iterations = iterationsElement.getAsInt();
        } else {
            iterations = HashAndSalt.LEGACY_HASH_ITERATION_COUNT_2048;
        }
        JsonElement algorithm = user.metadata.internalMetadata.get("algorithm");
        String algString = algorithm != null ? algorithm.getAsString() : null;
        HashAndSalt hashAndSalt = new HashAndSalt(hashString, saltString, iterations, algString);
        return hashAndSalt.verifySecret(password);
    }

    private void updatePasswordForUser(String newPassword, String userId, JsonNode jsonNode, Map<String, JsonNode> pointerToSchemaMap) throws CordraException, ReadOnlyCordraException, InvalidException {
        PasswordProcessor passwordProcessor = new PasswordProcessor();
        passwordProcessor.setPasswordIntoJson(newPassword, jsonNode, pointerToSchemaMap);
        passwordProcessor.setRequirePasswordChangeFlag(false, jsonNode, pointerToSchemaMap);
        String jsonData = jsonNode.toString();
        CordraObject.AccessControlList acl = null;
        List<String> payloadsToDelete = new ArrayList<>();
        List<Payload> payloads = null;
        JsonObject userMetadata = null;
        writeJsonAndPayloadsIntoCordraObjectIfValidAsUpdate(userId, null, jsonData, acl, userMetadata, payloads, userId, true, payloadsToDelete, false);
    }

    public boolean isRequirePasswordChange(JsonNode jsonNode, Map<String, JsonNode> pointerToSchemaMap) {
        PasswordProcessor passwordProcessor = new PasswordProcessor();
        return passwordProcessor.getRequirePasswordChangeFlag(jsonNode, pointerToSchemaMap);
    }

    public boolean isUserAccountActive(String userId) throws CordraException {
        CordraObject user = getCordraObjectOrNull(userId);
        return isUserAccountActive(user);
    }

    public boolean isUserAccountActive(CordraObject user) {
        if (user == null) return true;
        JsonNode jsonNode = JsonUtil.gsonToJackson(user.content);
        String type = user.type;
        try {
            Map<String, JsonNode> pointerToSchemaMap = getPointerToSchemaMap(type, jsonNode);
            UserProcessor userProcessor = new UserProcessor(this);
            return userProcessor.isUserAccountActive(jsonNode, pointerToSchemaMap);
        } catch (InvalidException e) {
            // this can happen for example if the User schema has been deleted
            return false;
        }
    }

    private void preprocessObjectBasedOnJsonAndType(String handle, String type, JsonNode jsonNode, Map<String, JsonNode> pointerToSchemaMap, boolean isDryRun) throws CordraException, InvalidException, ReadOnlyCordraException {
        if (isReadOnly && !isDryRun) throw new ReadOnlyCordraException();
        SchemaNameProcessor schemaNameProcessor = new SchemaNameProcessor(this);
        schemaNameProcessor.preprocess(type, handle, jsonNode);
        UserProcessor userProcessor = new UserProcessor(this);
        userProcessor.preprocess(handle, jsonNode, pointerToSchemaMap);
        PasswordProcessor passwordProcessor = new PasswordProcessor();
        passwordProcessor.preprocess(jsonNode, pointerToSchemaMap);
    }

    public boolean verifySecureProperty(CordraObject cordraObject, String jsonPointer, String secretToVerify) {
        if (cordraObject == null) return false;
        SecurePropertiesProcessor securePropertiesProcessor = new SecurePropertiesProcessor();
        return securePropertiesProcessor.verifySecureProperty(cordraObject, jsonPointer, secretToVerify);
    }

    public boolean isPasswordChangeRequired(CordraObject user) throws InvalidException {
        if (user == null) return false;
        JsonNode jsonNode = JsonUtil.gsonToJackson(user.content);
        String type = user.type;
        Map<String, JsonNode> pointerToSchemaMap = getPointerToSchemaMap(type, jsonNode);
        boolean requiresPasswordChange = isRequirePasswordChange(jsonNode, pointerToSchemaMap);
        return requiresPasswordChange;
    }

    static class ProcessObjectResult {
        boolean isUserOrGroup;
        boolean changedJson;
    }

    private ProcessObjectResult processObjectBasedOnJsonAndType(CordraObject co, String type, JsonNode jsonNode, JsonNode schemaNode, Map<String, JsonNode> pointerToSchemaMap, String creatorId, boolean isDryRun) throws CordraException, InvalidException, ReadOnlyCordraException {
        if (isReadOnly && !isDryRun) throw new ReadOnlyCordraException();
        SchemaNameProcessor schemaNameProcessor = new SchemaNameProcessor(this);
        schemaNameProcessor.process(type, co, jsonNode);
        UserProcessor userProcessor = new UserProcessor(this);
        boolean usernameChange = userProcessor.process(co, jsonNode, pointerToSchemaMap);
        PasswordProcessor passwordProcessor = new PasswordProcessor();
        boolean changedJsonForPassword = passwordProcessor.process(co, jsonNode, pointerToSchemaMap);
        UsersListProcessor usersListProcessor = new UsersListProcessor();
        boolean usersListChange = usersListProcessor.process(co, jsonNode, pointerToSchemaMap, design.handleMintingConfig.prefix);
        SecurePropertiesProcessor securePropertiesProcessor = new SecurePropertiesProcessor();
        boolean changedJsonForSecureProperties = securePropertiesProcessor.process(co, jsonNode, pointerToSchemaMap);
        ProcessObjectResult result = new ProcessObjectResult();
        result.isUserOrGroup = usernameChange || usersListChange;
        JsonAugmenter jsonAugmenter = new JsonAugmenter();
        boolean changedJsonForJsonAugmenter = jsonAugmenter.augment(co, jsonNode, pointerToSchemaMap, creatorId, design.handleMintingConfig.prefix);
        boolean changedForOrder = SchemaUtil.changePropertyOrder(jsonNode, schemaNode);
        result.changedJson = changedJsonForPassword || changedJsonForJsonAugmenter || changedForOrder || changedJsonForSecureProperties;
        return result;
    }

    private void addToKnownSchemas(String handle) throws CordraException, ReadOnlyCordraException {
        designLocker.writeLock().acquire();
        try {
            CordraObject designObject = this.getDesignCordraObject();
            Map<String, String> schemaIds = getSchemaIdsFromDesignObject(designObject);
            if (!schemaIds.equals(design.schemaIds)) {
                List<CordraObject> knownSchemaObjects = objectListFromHandleList(schemaIds.keySet());
                rebuildSchemasFromListOfObjects(knownSchemaObjects);
            }
            String oldType = schemaIds.get(handle);
            CordraObject schemaObject = getCordraObject(handle);
            JsonNode node = JsonUtil.gsonToJackson(schemaObject.content);
            JsonNode schemaNode = JsonUtil.getJsonAtPointer("/schema", node);
            String schemaString = JsonUtil.printJson(schemaNode);
            if (jsonSchemaFactory == null) jsonSchemaFactory = JsonSchemaFactoryFactory.newJsonSchemaFactory();
            JsonSchema schema = JsonUtil.parseJsonSchema(jsonSchemaFactory, schemaNode);
            String type = JsonUtil.getJsonAtPointer("/name", node).asText();
            String js = JsonUtil.getJsonAtPointer("/javascript", node).asText();
            if (js != null && js.isEmpty()) js = null;
            if (!designLocker.writeLock().isLocked()) {
                addToKnownSchemas(handle);
                return;
            }
            long start = System.currentTimeMillis();
            if (oldType != null && !type.equals(oldType)) {
                schemas.remove(oldType);
                schemaCordraObjects.remove(oldType);
                cordraRequireLookup.removeSchemaJavaScript(oldType);
                cordraRequireLookup.removeSchema(oldType);
            }
            schemas.put(type, new SchemaAndNode(schema, schemaNode));
            schemaCordraObjects.put(type, schemaObject);
            design.schemaIds.put(handle, type);
            design.schemas = getSchemaNodes();
            if (!isReadOnly && designObject != null) {
                persistDesignToCordraObject(designObject);
            }
            if (!designLocker.writeLock().isLocked()) {
                long duration = System.currentTimeMillis() - start;
                alerter.alert("Design lock failure adding " + handle + ", critical code took " + duration + "ms");
            }
            boolean needClear = false;
            String oldJs = cordraRequireLookup.getSchemaJavaScript(type);
            if ((js == null && oldJs != null) || (js != null && !js.equals(oldJs))) {
                cordraRequireLookup.putSchemaJavaScript(type, js);
                needClear = true;
            }
            String oldSchema = cordraRequireLookup.getSchema(type);
            if (!schemaString.equals(oldSchema)) {
                cordraRequireLookup.putSchema(type, schemaString);
                needClear = true;
            }
            if (needClear) {
                javaScriptEnvironment.clearCache();
            }
            signalWatcher.sendSignal(SignalWatcher.Signal.DESIGN);
        } catch (ProcessingException e) {
            throw new InternalErrorCordraException(e);
        } finally {
            designLocker.writeLock().release();
        }
    }

    private void deleteFromKnownSchemas(String handle) throws CordraException, ReadOnlyCordraException {
        designLocker.writeLock().acquire();
        try {
            CordraObject designObject = this.getDesignCordraObject();
            Map<String, String> schemaIds = getSchemaIdsFromDesignObject(designObject);
            if (!schemaIds.equals(design.schemaIds)) {
                List<CordraObject> knownSchemaObjects = objectListFromHandleList(schemaIds.keySet());
                rebuildSchemasFromListOfObjects(knownSchemaObjects);
            }
            String oldType = schemaIds.get(handle);
            if (oldType == null) {
                alerter.alert("Deleting schema " + handle + " not in design");
                return;
            }
            if (!designLocker.writeLock().isLocked()) {
                deleteFromKnownSchemas(handle);
                return;
            }
            long start = System.currentTimeMillis();
            schemas.remove(oldType);
            schemaCordraObjects.remove(oldType);
            cordraRequireLookup.removeSchemaJavaScript(oldType);
            cordraRequireLookup.removeSchema(oldType);
            design.schemaIds.remove(handle);
            design.schemas = getSchemaNodes();
            if (!isReadOnly && designObject != null) {
                persistDesignToCordraObject(designObject);
            }
            if (!designLocker.writeLock().isLocked()) {
                long duration = System.currentTimeMillis() - start;
                alerter.alert("Design lock failure deleting " + handle + ", critical code took " + duration + "ms");
            }
            signalWatcher.sendSignal(SignalWatcher.Signal.DESIGN);
        } catch (ProcessingException e) {
            throw new InternalErrorCordraException(e);
        } finally {
            designLocker.writeLock().release();
        }
    }

    private Map<String, JsonNode> getSchemaNodes() {
        Map<String, JsonNode> result = new HashMap<>();
        for (Map.Entry<String, SchemaAndNode> entry : schemas.entrySet()) {
            String type = entry.getKey();
            result.put(type, entry.getValue().schemaNode);
        }
        return result;
    }

    public String getHandleForSuffix(String suffix) {
        return handleMinter.mintWithSuffix(suffix);
    }

    CordraObject createNewCordraObjectEnsuringHandleLock(JsonNode jsonNode, ObjectDelta objectDelta, Map<String, JsonNode> pointerToSchemaMap, String handle, String creatorId, boolean isDryRun) throws CordraException, InvalidException, ReadOnlyCordraException {
        if (isReadOnly && !isDryRun) throw new ReadOnlyCordraException();
        CordraObject result;
        if (handle != null && !handle.isEmpty()) {
            result = lockAndCreateMemoryObjectIfHandleAvailable(handle, true);
        } else {
            result = createNewCordraObjectEnsuringHandleLockUsingGenerateIdJavaScript(objectDelta, creatorId);
        }
        if (result == null) {
            String primaryData = getPrimaryData(jsonNode, pointerToSchemaMap);
            if (primaryData != null) {
                handle = handleMinter.mint(primaryData);
                result = lockAndCreateMemoryObjectIfHandleAvailable(handle, false);
            }
            while (result == null) {
                handle = handleMinter.mintByTimestamp();
                result = lockAndCreateMemoryObjectIfHandleAvailable(handle, false);
            }
        }
        try {
            if (creatorId == null) {
                creatorId = "anonymous";
            }
            long now = System.currentTimeMillis();
            result.metadata.createdOn = now;
            result.metadata.modifiedOn = now;
            result.metadata.createdBy = creatorId;
            result.metadata.modifiedBy = creatorId;
            return result;
        } catch (Exception e) {
            objectLocker.release(handle);
            throw e;
        }
    }

    private CordraObject createNewCordraObjectEnsuringHandleLockUsingGenerateIdJavaScript(ObjectDelta objectDelta, String creatorId) throws CordraException, InvalidException {
        JavaScriptRunner runner = javaScriptHooks.getJavascriptRunner();
        try {
            JavaScriptLifeCycleHooks.GenerateIdJavaScriptStatus generateIdJavaScriptStatus = javaScriptHooks.hasJavaScriptGenerateIdFunction(runner);
            if (!generateIdJavaScriptStatus.hasFunction) return null;
            CordraObject co = objectDelta.asCordraObjectForCreate();
            Map<String, Object> context = createContext(creatorId, co);
            String handle = javaScriptHooks.generateIdFromJavaScript(runner, co, context);
            if (handle == null || handle.isEmpty()) return null;
            boolean throwIfNotAvailable = !generateIdJavaScriptStatus.isLoopable;
            CordraObject result = lockAndCreateMemoryObjectIfHandleAvailable(handle, throwIfNotAvailable);
            if (generateIdJavaScriptStatus.isLoopable) {
                while (result == null) {
                    handle = javaScriptHooks.generateIdFromJavaScript(runner, co, context);
                    if (handle == null || handle.isEmpty()) return null;
                    result = lockAndCreateMemoryObjectIfHandleAvailable(handle, false);
                }
            }
            return result;
        } catch (CordraException e) {
            throw e;
        } catch (InvalidException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalErrorCordraException(e);
        } finally {
            javaScriptHooks.recycleJavascriptRunner(runner);
        }
    }

    public static boolean isValidHandle(String handle) {
        if (handle == null || handle.isEmpty()) return false;
        if (handle.startsWith("/") || handle.startsWith(".")) return false;
        if (!handle.contains("/")) return false;
        return true;
    }

    private CordraObject lockAndCreateMemoryObjectIfHandleAvailable(String handle, boolean throwIfNotAvailable) throws CordraException {
        if (handle == null || handle.isEmpty()) return null;
        if (!isValidHandle(handle)) {
            throw new BadRequestCordraException("Invalid handle: " + handle);
        }
        objectLocker.lock(handle);
        try {
            if (storage.get(handle) == null) {
                CordraObject res = new CordraObject();
                res.id = handle;
                res.metadata = new CordraObject.Metadata();
                res.metadata.internalMetadata = new JsonObject();
                return res;
            }
        } catch (Exception e) {
            objectLocker.release(handle);
            throw e;
        }
        objectLocker.release(handle);
        if (throwIfNotAvailable) {
            throw new ConflictCordraException("Object already exists: " + handle);
        } else {
            return null;
        }
    }

    public CordraObject publishVersion(String objectId, String versionId, boolean clonePayloads, String userId) throws CordraException, VersionException, ReadOnlyCordraException, ConflictCordraException {
        if (isReadOnly) throw new ReadOnlyCordraException();
        objectLocker.lock(objectId);
        CordraTransaction txn = null;
        try {
            txn = makeUpdateTransactionFor(objectId);
            CordraObject result = versionManager.publishVersion(objectId, versionId, clonePayloads, userId, txn.txnId);
            if (handleClient != null) {
                try {
                    JsonNode dataNode = JsonUtil.gsonToJackson(result.content);
                    String type = result.type;
                    handleClient.registerHandle(result.id, result, type, dataNode);
                } catch (Exception e) {
                    try {
                        storage.delete(result.id);
                    } catch (CordraException ex) {
                        alerter.alert("Failure to delete new object after failure to register handle " + result.id + ", out of sync");
                        logger.error("Failure to delete new object after failure to register handle " + result.id + ", out of sync", e);
                    }
                    throw new InternalErrorCordraException(e);
                }
            }
            boolean indexPayloads = shouldIndexPayloads(result.type);
            indexObject(result, indexPayloads);
            sendUpdateReplicationMessage(result);
            transactionManager.closeTransaction(txn.txnId, cordraServiceId);
            return result;
        } catch (Exception e) {
            if (txn != null) {
                try {
                    transactionReprocessingQueue.insert(txn, cordraServiceId);
                    transactionManager.closeTransaction(txn.txnId, cordraServiceId);
                } catch (Exception ex) {
                    logger.error("Error in publishVersion; followed by reprocessing error", e);
                    throw ex;
                }
            }
            throw e;
        } finally {
            objectLocker.release(objectId);
        }
    }

    public List<CordraObject> getVersionsFor(String objectId, String userId, boolean hasUserObject) throws CordraException {
        ensureIndexUpToDate();
        List<String> groupIds = getAclEnforcer().getGroupsForUser(userId);
        return versionManager.getVersionsFor(objectId, userId, hasUserObject, groupIds, design.authConfig);
    }

    private CordraObject updateCordraObject(CordraObject co, boolean isCreate, String objectType, String jsonData, CordraObject.AccessControlList acl, JsonObject userMetadata, Collection<String> payloadsToDelete, List<Payload> payloads, String userId, Map<String, JsonNode> pointerToSchemaMap, boolean isDryRun) throws CordraException, ReadOnlyCordraException {
        if (isReadOnly && !isDryRun) throw new ReadOnlyCordraException();
        String handle = co.id;
        CordraTransaction txn = null;
        boolean success = false;
        boolean stored = false;
        boolean possiblyStored = false;
        try {
            if (!isDryRun) {
                txn = makeUpdateTransactionFor(handle);
            }
            co.type = objectType;
            co.setContent(jsonData);
            if (co.metadata == null) co.metadata = new CordraObject.Metadata();
            if (co.metadata.internalMetadata == null) co.metadata.internalMetadata = new JsonObject();
            if (!isDryRun) {
                if (txn == null) throw new AssertionError();
                co.metadata.txnId = txn.txnId;
            }
            if (userId == null) {
                userId = "anonymous";
            }
            co.metadata.modifiedBy = userId;
            if (acl != null) {
                co.acl = acl;
            }
            if (userMetadata != null) {
                co.userMetadata = userMetadata;
            }
            if (!isCreate) {
                co.metadata.modifiedOn = System.currentTimeMillis();
            }
            addAndDeletePayloads(co, payloadsToDelete, payloads);
            if (shouldHashObject(objectType)) {
                CordraObjectHasher hasher = new CordraObjectHasher();
                hasher.generateAllHashesAndSetThemOnTheCordraObject(co, storage);
            }
            if (!isDryRun) {
                possiblyStored = true;
                co = storeCordraObject(co, isCreate);
                stored = true;
                indexCordraObject(co, objectType, pointerToSchemaMap);
            }
            cleanupPayloads(co);
            if (!isDryRun) {
                if (txn == null) throw new AssertionError();
                sendUpdateReplicationMessage(co);
                success = true;
                transactionManager.closeTransaction(txn.txnId, cordraServiceId);
            }
            return co;
        } catch (Exception e) {
            if (!success && isCreate && !isDryRun) {
                // created object
                try {
                    if (stored || (possiblyStored && storage.get(co.id) != null)) {
                        storage.delete(co.id);
                        logger.warn("Deleted object after error " + handle);
                    }
                } catch (Exception ex) {
                    alerter.alert("Failure to delete new object after creation error: " + handle + ", out of sync");
                    logger.error("Failure to delete new object after creation error: " + handle + ", out of sync", ex);
                }
            }
            if (txn != null) {
                try {
                    transactionReprocessingQueue.insert(txn, cordraServiceId);
                    transactionManager.closeTransaction(txn.txnId, cordraServiceId);
                } catch (Exception ex) {
                    logger.error("Error in updateCordraObject; followed by reprocessing error", e);
                    throw ex;
                }
            }
            throw e;
        }
    }

    private void addAndDeletePayloads(CordraObject co, Collection<String> payloadsToDelete, List<Payload> payloads) {
        if (payloadsToDelete != null) {
            for (String payloadName : payloadsToDelete) {
                co.deletePayload(payloadName);
            }
        }
        if (payloads != null) {
            for (Payload payload : payloads) {
                addPayloadToCordraObject(co, payload.name, payload.filename, payload.mediaType, payload.getInputStream());
            }
        }
    }

    private CordraObject storeCordraObject(CordraObject co, boolean isCreate) throws CordraException, IndexerException {
        if (isCreate) {
            co = storage.create(co);
        } else {
            co = storage.update(co);
        }
        return co;
    }

    private void indexCordraObject(CordraObject co, String objectType, Map<String, JsonNode> pointerToSchemaMap) throws CordraException, IndexerException {
        boolean indexPayloads = shouldIndexPayloads(objectType);
        if (pointerToSchemaMap == null) {
            indexObject(co, indexPayloads);
        } else {
            indexer.indexObject(cordraServiceId, co, indexPayloads, pointerToSchemaMap);
        }
    }

    private void cleanupPayloads(CordraObject co) {
        co.clearPayloadsToDelete();
        if (co.payloads != null) {
            for (Payload payload : co.payloads) {
                if (payload.getInputStream() != null) {
                    try {
                        payload.getInputStream().close();
                    } catch (Exception e) {
                        // ignore
                    }
                    payload.setInputStream(null);
                }
            }
        }
    }

    private void addPayloadToCordraObject(CordraObject co, String name, String filename, String mediaType, InputStream inputStream) {
        if (co.payloads == null) {
            co.payloads = new ArrayList<>();
        } else {
            for (Payload p : co.payloads) {
                if (p.name.equals(name)) {
                    p.filename = filename;
                    p.mediaType = mediaType;
                    p.setInputStream(inputStream);
                    return;
                }
            }
        }
        Payload p = new Payload();
        p.name = name;
        p.filename = filename;
        p.mediaType = mediaType;
        p.setInputStream(inputStream);
        co.payloads.add(p);
    }

    public SearchResults<CordraObject> searchRepo(String query) throws CordraException {
        return indexer.search(query);
    }

    public SearchResults<String> searchRepoHandles(String query) throws CordraException {
        return indexer.searchHandles(query);
    }

    private QueryParams queryParamsFor(int pageNum, int pageSize, String sortFieldsString) {
        List<SortField> sortFields = null;
        if (sortFieldsString != null) {
            sortFields = getSortFieldsFromParam(sortFieldsString);
        }
        if (pageSize == 0 && Boolean.TRUE.equals(design.useLegacySearchPageSizeZeroReturnsAll)) {
            pageSize = -1;
        }
        QueryParams params = new QueryParams(pageNum, pageSize, sortFields);
        return params;
    }

    SearchResults<String> searchHandles(String query, int pageNum, int pageSize, String sortFieldsString) throws CordraException {
        QueryParams params = queryParamsFor(pageNum, pageSize, sortFieldsString);
        String q = "valid:true AND (" + query + ")";
        return indexer.searchHandles(q, params);
    }

    SearchResults<IdType> searchIdType(String query, int pageNum, int pageSize, String sortFieldsString) throws CordraException {
        QueryParams params = queryParamsFor(pageNum, pageSize, sortFieldsString);
        String q = "valid:true AND (" + query + ")";
        return indexer.searchIdType(q, params);
    }

    SearchResults<CordraObject> search(String query, int pageNum, int pageSize, String sortFieldsString) throws CordraException {
        QueryParams params = queryParamsFor(pageNum, pageSize, sortFieldsString);
        String q = "valid:true AND (" + query + ")";
        return indexer.search(q, params);
    }

    public void searchHandles(String query, int pageNum, int pageSize, String sortFieldsString, Writer printWriter, boolean isPostProcess, String userId) throws CordraException, IOException, ScriptException, InterruptedException {
        try (SearchResults<IdType> results = searchIdType(query, pageNum, pageSize, sortFieldsString)) {
            @SuppressWarnings("resource")
            JsonWriter writer = new JsonWriter(printWriter);
            writer.setIndent("  ");
            writer.beginObject();
            writer.name("pageNum").value(pageNum);
            writer.name("pageSize").value(pageSize);
            writer.name("size").value(results.size());
            writer.name("results").beginArray();
            for (IdType result : results) {
                if (isPostProcess) {
                    if (result.type == null || javaScriptHooks.typeHasJavaScriptFunction(result.type, JavaScriptLifeCycleHooks.ON_OBJECT_RESOLUTION)) {
                        CordraObject co = storage.get(result.id);
                        co = copyOfCordraObjectRemovingInternalMetadata(co);
                        try {
                            co = postProcess(userId, co);
                        } catch (InvalidException e) {
                            // onObjectResolution enrichment may have forbidden access.
                            // Just skip it; don't reveal anything about the skipped object.
                            // Note that the page size will be potentially messed up by this.
                            continue;
                        }
                    }
                }
                gson.toJson(result.id, String.class, writer);
            }
            writer.endArray();
            writer.endObject();
            writer.flush();
        } catch (InvalidException e) {
            throw new InternalErrorCordraException(e);
        }
    }

    public void search(String query, int pageNum, int pageSize, String sortFieldsString, Writer printWriter, boolean isPostProcess, String userId) throws CordraException, IOException, ScriptException, InterruptedException {
        search(query, pageNum, pageSize, sortFieldsString, printWriter, isPostProcess, userId, null, true);
    }

    public void search(String query, int pageNum, int pageSize, String sortFieldsString, Writer printWriter, boolean isPostProcess, String userId, boolean isFull) throws CordraException, IOException, ScriptException, InterruptedException {
        search(query, pageNum, pageSize, sortFieldsString, printWriter, isPostProcess, userId, null, isFull);
    }

    public void search(String query, int pageNum, int pageSize, String sortFieldsString, Writer printWriter, boolean isPostProcess, String userId, Set<String> pointers, boolean isFull) throws CordraException, IOException, ScriptException, InterruptedException {
        try (SearchResults<CordraObject> results = search(query, pageNum, pageSize, sortFieldsString)) {
            @SuppressWarnings("resource")
            JsonWriter writer = new JsonWriter(printWriter);
            writer.setIndent("  ");
            writer.beginObject();
            writer.name("pageNum").value(pageNum);
            writer.name("pageSize").value(pageSize);
            writer.name("size").value(results.size());
            writer.name("results").beginArray();
            for (CordraObject co : results) {
                co = copyOfCordraObjectRemovingInternalMetadata(co);
                if (isPostProcess) {
                    try {
                        co = postProcess(userId, co);
                    } catch (InvalidException e) {
                        // onObjectResolution enrichment may have forbidden access.
                        // Just skip it; don't reveal anything about the skipped object.
                        // Note that the page size will be potentially messed up by this.
                        continue;
                    }
                }
                if (isFull) {
                    if (pointers != null) {
                        JsonElement jsonElement = gson.toJsonTree(co, CordraObject.class);
                        jsonElement = JsonUtil.pruneToMatchPointers(jsonElement, pointers);
                        gson.toJson(jsonElement, writer);
                    } else {
                        gson.toJson(co, CordraObject.class, writer);
                    }
                } else {
                    if (pointers != null) {
                        JsonElement jsonElement = co.content;
                        jsonElement = JsonUtil.pruneToMatchPointers(jsonElement, pointers);
                        gson.toJson(jsonElement, writer);
                    } else {
                        gson.toJson(co.content, writer);
                    }
                }
            }
            writer.endArray();
            writer.endObject();
            writer.flush();
        }
    }

    private List<SortField> getSortFieldsFromParam(String sortFields) {
        if (sortFields == null || "".equals(sortFields)) {
            return null;
        } else {
            List<SortField> result = new ArrayList<>();
            List<String> sortFieldStrings = getFieldsFromString(sortFields);
            for (String sortFieldString : sortFieldStrings) {
                result.add(getSortFieldFromString(sortFieldString));
            }
            return result;
        }
    }

    private SortField getSortFieldFromString(String sortFieldString) {
        String[] terms = sortFieldString.split(" ");
        boolean reverse = false;
        if (terms.length > 1) {
            String direction = terms[1];
            if ("DESC".equalsIgnoreCase(direction)) reverse = true;
        }
        String fieldName = terms[0];
        return new SortField(fieldName, reverse);
    }

    private List<String> getFieldsFromString(String s) {
        return Arrays.asList(s.split(","));
    }

    private void indexObject(CordraObject co, boolean indexPayloads) throws CordraException {
        Map<String, JsonNode> pointerToSchemaMap = getPointerToSchemaMapForIndexing(co, reindexer.getIsReindexInProcess());
        indexer.indexObject(cordraServiceId, co, indexPayloads, pointerToSchemaMap);
    }

    public Map<String, JsonNode> getPointerToSchemaMapForIndexing(CordraObject co, boolean isReindexInProcess) {
        Map<String, JsonNode> pointerToSchemaMap;
        String type = co.type;
        if (isReindexInProcess && (type == null || schemas.get(type) == null)) {
            // don't log missing types on reindex
            pointerToSchemaMap = Collections.emptyMap();
        } else {
            try {
                JsonNode jsonNode = JsonUtil.gsonToJackson(co.content);
                if (DESIGN_OBJECT_ID.equals(co.id)) {
                    pointerToSchemaMap = Collections.emptyMap();
                } else {
                    pointerToSchemaMap = getPointerToSchemaMap(type, jsonNode);
                }
            } catch (InvalidException e) {
                logger.warn("Unexpected exception indexing " + co.id, e);
                pointerToSchemaMap = Collections.emptyMap();
            }
        }
        return pointerToSchemaMap;
    }

    public Map<String, JsonNode> getPointerToSchemaMap(String type, JsonNode jsonNode) throws InvalidException {
        SchemaAndNode schema;
        schema = schemas.get(type);
        if (schema == null) {
            throw new InvalidException("Unknown type " + type);
        }
        Map<String, JsonNode> keywordsMap = validator.schemaValidateAndReturnKeywordsMap(jsonNode, schema.schemaNode, schema.schema);
        return keywordsMap;
    }

    void validateForTesting(String objectType, String jsonData) throws InvalidException, CordraException {
        JsonNode jsonNode = JsonUtil.parseJson(jsonData);
        Map<String, JsonNode> keywordsMap = getPointerToSchemaMap(objectType, jsonNode);
        validator.postSchemaValidate(jsonNode, keywordsMap);
    }

    static String getPrimaryData(JsonNode jsonNode, Map<String, JsonNode> pointerToSchemaMap) {
        for (Map.Entry<String, JsonNode> entry : pointerToSchemaMap.entrySet()) {
            String jsonPointer = entry.getKey();
            JsonNode subSchema = entry.getValue();
            JsonNode isPrimaryNode = SchemaUtil.getDeepCordraSchemaProperty(subSchema, "preview", "isPrimary");
            if (isPrimaryNode == null) continue;
            if (isPrimaryNode.asBoolean()) {
                JsonNode referenceNode = jsonNode.at(jsonPointer);
                if (referenceNode == null) {
                    logger.warn("Unexpected missing isPrimary node " + jsonPointer);
                } else {
                    return referenceNode.asText();
                }
            }
        }
        return null;
    }

    public String getMediaType(String type, JsonNode jsonNode, String jsonPointer) throws InvalidException {
        Map<String, JsonNode> pointerToSchemaMap = getPointerToSchemaMap(type, jsonNode);
        JsonNode subSchema = pointerToSchemaMap.get(jsonPointer);
        if (subSchema == null) return null;
        JsonNode mediaType = SchemaUtil.getDeepCordraSchemaProperty(subSchema, "response", "mediaType");
        if (mediaType == null) return null;
        return mediaType.asText();
    }

    public void ensureIndexUpToDate() throws CordraException {
        long authObjectChangeCountAtStart = authObjectChangeCount.get();
        indexer.ensureIndexUpToDate();
        authObjectChangeIndexed.getAndAccumulate(authObjectChangeCountAtStart, Math::max);
    }

    public void ensureIndexUpToDateWhenAuthChange() throws CordraException {
        if (authObjectChangeCount.get() > authObjectChangeIndexed.get()) {
            ensureIndexUpToDate();
        }
    }

    public void updateAcls(String objectId, CordraObject.AccessControlList sAcl, String userId, boolean hasUserObject) throws CordraException, ReadOnlyCordraException, InvalidException {
        if (isReadOnly) throw new ReadOnlyCordraException();
        writeJsonAndPayloadsIntoCordraObjectIfValidAsUpdate(objectId, null, null, sAcl, null, null, userId, hasUserObject, null, false);
    }

    public boolean shouldIndexPayloads(String type) {
        if (schemas == null) return true; // for creating new Schema objects during bootstrapping
        SchemaAndNode schemaAndNode = getSchema(type);
        if (schemaAndNode == null) return true;
        return shouldIndexPayloads(schemaAndNode.schemaNode);
    }

    public boolean shouldHashObject(String type) {
        if (schemaCordraObjects == null) return false;
        if (type == null) return false;
        CordraObject schemaObject = schemaCordraObjects.get(type);
        if (schemaObject == null) return false;
        JsonObject content = schemaObject.content.getAsJsonObject();
        if (content.has("hashObject")) {
            return content.get("hashObject").getAsBoolean();
        } else {
            return false;
        }
    }

    private static boolean shouldIndexPayloads(JsonNode schemaNode) {
        JsonNode indexPayloadsProperty = SchemaUtil.getDeepCordraSchemaProperty(schemaNode, "indexPayloads");
        if (indexPayloadsProperty == null) return true;
        if (!indexPayloadsProperty.isBoolean()) return true;
        return indexPayloadsProperty.asBoolean();
    }

    public List<String> getTypesPermittedToCreate(String userId, boolean hasUserObject) throws CordraException {
        ensureIndexUpToDate();
        List<String> allTypes = new ArrayList<>(design.schemas.keySet());
        List<String> result = aclEnforcer.filterTypesPermittedToCreate(userId, hasUserObject, allTypes);
        return result;
    }

    public boolean isReadOnly() {
        return isReadOnly;
    }

    public void shutdown() {
        if (lightWeightHandleServer != null) {
            lightWeightHandleServer.shutdown();
        }
        if (reindexer != null) {
            reindexer.shutdown();
        }
        if (replicationConsumer != null) {
            try { replicationConsumer.shutdown(); } catch (Exception e) { logger.error("Shutdown error", e); }
        }
        if (stripedTaskRunner != null) {
            stripedTaskRunner.shutdown();
        }
        if (replicationProducer != null) {
            try { replicationProducer.shutdown(); } catch (Exception e) { logger.error("Shutdown error", e); }
        }
        try { handlesUpdater.shutdown(); } catch (Exception e) { logger.error("Shutdown error", e); }
        try { javaScriptEnvironment.shutdown(); } catch (Exception e) { logger.error("Shutdown error", e); }
        try { preCacheExecutorService.shutdown(); } catch (Exception e) { logger.error("Shutdown error", e); }
        try { indexer.close(); } catch (Exception e) { logger.error("Shutdown error", e); }
        if (transactionReprocessingQueue != null) {
            try { transactionReprocessingQueue.shutdown(); } catch (Exception e) { logger.error("Shutdown error", e); }
        }
        syncObjects.shutdown();
        try { storage.close(); } catch (Exception e) { logger.error("Shutdown error", e); }
    }

    private void sendUpdateReplicationMessage(CordraObject co) throws CordraException {
        if (replicationProducer == null) {
            return;
        }
        CordraObjectWithPayloadsAsStrings cos;
        try {
            boolean includePayloads = false;
            if (design != null && design.includePayloadsInReplicationMessages != null) {
                includePayloads = design.includePayloadsInReplicationMessages;
            }
            cos = CordraObjectWithPayloadsAsStrings.fromCordraObject(co, storage, includePayloads);
            ReplicationMessage replicationMessage = new ReplicationMessage();
            replicationMessage.cordraClusterId = this.cordraClusterId;
            replicationMessage.type = ReplicationMessage.Type.UPDATE;
            replicationMessage.object = cos;
            replicationMessage.handle = co.id;
            String message = gson.toJson(replicationMessage);
            replicationProducer.send(co.id, message);
        } catch (IOException e) {
            throw new InternalErrorCordraException(e);
        }
    }

    private void sendDeleteReplicationMessage(String id) throws CordraException {
        if (replicationProducer == null) {
            return;
        }
        ReplicationMessage replicationMessage = new ReplicationMessage();
        replicationMessage.type = ReplicationMessage.Type.DELETE;
        replicationMessage.cordraClusterId = this.cordraClusterId;
        replicationMessage.handle = id;
        String message = gson.toJson(replicationMessage);
        replicationProducer.send(id, message);
    }

    Object stripePicker(String message) {
        ReplicationMessage txn = gson.fromJson(message, ReplicationMessage.class);
        return txn.handle;
    }

    void applyReplicationMessage(String message) {
        ReplicationMessage txn = gson.fromJson(message, ReplicationMessage.class);
        if (cordraClusterId.equals(txn.cordraClusterId)) {
            return;
        }
        try {
            if (DESIGN_OBJECT_ID.equals(txn.handle)) {
                CordraObjectWithPayloadsAsStrings cos = txn.object;
                replicateDesignObject(cos);
            } else if (txn.type == ReplicationMessage.Type.DELETE) {
                String userId = null;
                delete(txn.handle, userId, false);
            } else {
                //UPDATE
                CordraObjectWithPayloadsAsStrings cos = txn.object;
                replicateCordraObject(cos);
            }
        } catch (Exception e) {
            alerter.alert("Error replicating " + txn.handle + ": " + e);
            logger.error("Error replicating " + txn.handle, e);
        }
    }

    private void replicateCordraObject(CordraObjectWithPayloadsAsStrings cos) throws CordraException, InvalidException, ReadOnlyCordraException {
        String id = cos.cordraObject.id;
        String type = cos.cordraObject.type;
        objectLocker.lock(id);
        CordraTransaction txn = null;
        try {
            txn = makeUpdateTransactionFor(id, false);
            CordraObject existingCo = storage.get(id);
            CordraObject co = cos.cordraObject;
            List<Payload> payloads = getPayloadsFromReplicatedObject(cos);
            List<String> payloadsToDelete = new ArrayList<>();
            if (existingCo != null && existingCo.payloads != null) {
                for (Payload payload : existingCo.payloads) {
                    if (getCordraObjectPayloadByName(co, payload.name) != null) {
                        payloadsToDelete.add(payload.name);
                    }
                }
            }
            boolean isDryRun = false;
            Map<String, JsonNode> pointerToSchemaMap = getPointerToSchemaMap(type, JsonUtil.gsonToJackson(co.content));
            boolean isCreate = existingCo == null;
            addAndDeletePayloads(co, payloadsToDelete, payloads);
            if (!isDryRun) {
                co = storeCordraObject(co, isCreate);
                indexCordraObject(co, type, pointerToSchemaMap);
            }
            cleanupPayloads(co);
            if (isUserOrGroup(co)) {
                authObjectChangeCount.incrementAndGet();
                authCache.clearAllGroupsForUserValues();
                authCache.clearAllUserIdForUsernameValues();
                signalWatcher.sendSignal(SignalWatcher.Signal.AUTH_CHANGE);
                if (!isUserAccountActive(co)) {
                    invalidateSessionsForUser(co.id);
                }
                preCache();
            }
            if (validator.hasJavaScriptModules(pointerToSchemaMap)) {
                signalWatcher.sendSignal(SignalWatcher.Signal.JAVASCRIPT_CLEAR_CACHE);
                cordraRequireLookup.clearAllObjectIdsForModuleValues();
                javaScriptEnvironment.clearCache();
            }
            if ("Schema".equals(type)) {
                addToKnownSchemas(id);
            }
            transactionManager.closeTransaction(txn.txnId, cordraServiceId);
        } catch (Exception e) {
            if (txn != null) {
                try {
                    transactionReprocessingQueue.insert(txn, cordraServiceId);
                    transactionManager.closeTransaction(txn.txnId, cordraServiceId);
                } catch (Exception ex) {
                    logger.error("Error in replicateCordraObject; followed by reprocessing error", e);
                    throw ex;
                }
            }
            throw e;
        } finally {
            objectLocker.release(id);
        }
    }

    private void invalidateSessionsForUser(String userId) {
        if (userId == null || sessionManager == null) return;
        sessionManager.getSessionsByKeyValue("userId", userId)
                .forEach(sessionManager::ensureInvalid);
    }

    private boolean isUserOrGroup(CordraObject co) {
        if (co.metadata == null || co.metadata.internalMetadata == null) return false;
        if (co.metadata.internalMetadata.has("username")) return true;
        if (co.metadata.internalMetadata.has("users")) return true;
        return false;
    }

    private List<Payload> getPayloadsFromReplicatedObject(CordraObjectWithPayloadsAsStrings cos) {
        List<Payload> cordraObjectPayloads = cos.cordraObject.payloads;
        if (cordraObjectPayloads == null) return null;
        for (Payload payload : cordraObjectPayloads) {
            InputStream in;
            if (cos.payloads == null) {
                in = new ByteArrayInputStream(new byte[0]);
            } else {
                String base64OfPayload = cos.payloads.get(payload.name);
                if (base64OfPayload == null) {
                    in = new ByteArrayInputStream(new byte[0]);
                } else {
                    in = new ByteArrayInputStream(Base64.getDecoder().decode(base64OfPayload));
                }
            }
            payload.setInputStream(in);
        }
        return cordraObjectPayloads;
    }

    private void replicateDesignObject(CordraObjectWithPayloadsAsStrings cos) throws CordraException {
        designLocker.writeLock().acquire();
        try {
            cos.writeIntoStorage(storage);
            signalWatcher.sendSignal(SignalWatcher.Signal.DESIGN);
            loadStatefulData();
        } finally {
            designLocker.writeLock().release();
        }
    }
}
