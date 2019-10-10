package net.cnri.cordra.sync.local;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.cnri.cordra.sync.AllHandlesUpdaterSync;
import net.cnri.cordra.sync.CheckableLocker;
import net.cnri.cordra.sync.KeyPairAuthJtiChecker;
import net.cnri.cordra.sync.LeadershipManager;
import net.cnri.cordra.sync.NameLocker;
import net.cnri.cordra.sync.RepoInitProvider;
import net.cnri.cordra.sync.SignalWatcher;
import net.cnri.cordra.sync.SingleThreadReadWriteCheckableLocker;
import net.cnri.cordra.sync.SyncObjects;
import net.cnri.cordra.sync.TransactionManager;
import net.cnri.cordra.sync.TransactionReprocessingQueue;
import net.cnri.microservices.Alerter;

public class LocalSyncObjects implements SyncObjects {
    private static Logger logger = LoggerFactory.getLogger(LocalSyncObjects.class);

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

    public LocalSyncObjects(Path basePath, boolean isReadOnly, Alerter alerter, boolean inMemoryOnly) {
        this(repoInitProviderFromPath(basePath, isReadOnly), basePath, isReadOnly, alerter, inMemoryOnly);
    }

    private static RepoInitProvider repoInitProviderFromPath(Path basePath, boolean isReadOnly) {
        if (isReadOnly) return new FilePathRepoInitProvider(null);
        if (basePath == null) return new FilePathRepoInitProvider(null);
        return new FilePathRepoInitProvider(basePath.resolve("repoInit.json"));
    }

    public LocalSyncObjects(RepoInitProvider repoInitProvider, Path basePath, boolean isReadOnly, Alerter alerter, boolean inMemoryOnly) {
        this.alerter = alerter;
        this.startupLocker = new NoopCheckableLocker();
        this.repoInitProvider = repoInitProvider;
        this.leadershipManager = new SingleInstanceLeadershipManager();
        this.transactionManager = new FileBasedTransactionManager(isReadOnly || inMemoryOnly ? null : basePath);
        this.designLocker = new MemorySingleThreadReadWriteCheckableLocker();
        this.schemaNameLocker = new MemoryCheckableLocker();
        this.usernameLocker = new MemoryCheckableLocker();
        this.objectLocker = new MemoryNameLocker();
        this.allHandlesUpdaterSync = new MemoryAllHandlesUpdaterSync();
        if (isReadOnly) this.signalWatcher = new PollingSignalWatcher();
        else this.signalWatcher = new NoopSignalWatcher();
        this.transactionReprocessingQueue = new LocalTransactionReprocessingQueue(isReadOnly || inMemoryOnly ? null : basePath);
        this.keyPairAuthJtiChecker = new MemoryKeyPairAuthJtiChecker();
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
        transactionReprocessingQueue.shutdown();
    }

}
