package net.cnri.cordra.sync;

import net.cnri.microservices.Alerter;

public class InstrumentedSyncObjects implements SyncObjects {

    private final SyncObjects delegate;
    private final NameLocker objectLocker;
    private final TransactionManager transactionManager;

    public InstrumentedSyncObjects(SyncObjects delegate) {
        this.delegate = delegate;
        this.objectLocker = new InstrumentedNameLocker(delegate.getObjectLocker());
        this.transactionManager = new InstrumentedTransactionManager(delegate.getTransactionManager());
    }

    @Override
    public CheckableLocker getStartupLocker() {
        return delegate.getStartupLocker();
    }

    @Override
    public RepoInitProvider getRepoInitProvider() {
        return delegate.getRepoInitProvider();
    }

    @Override
    public LeadershipManager getLeadershipManager() {
        return delegate.getLeadershipManager();
    }

    @Override
    public TransactionManager getTransactionManager() {
        return this.transactionManager;
    }

    @Override
    public SingleThreadReadWriteCheckableLocker getDesignLocker() {
        return delegate.getDesignLocker();
    }

    @Override
    public CheckableLocker getSchemaNameLocker() {
        return delegate.getSchemaNameLocker();
    }

    @Override
    public CheckableLocker getUsernameLocker() {
        return delegate.getUsernameLocker();
    }

    @Override
    public NameLocker getObjectLocker() {
        return this.objectLocker;
    }

    @Override
    public AllHandlesUpdaterSync getAllHandlesUpdaterSync() {
        return delegate.getAllHandlesUpdaterSync();
    }

    @Override
    public SignalWatcher getSignalWatcher() {
        return delegate.getSignalWatcher();
    }

    @Override
    public TransactionReprocessingQueue getTransactionReprocessingQueue() {
        return delegate.getTransactionReprocessingQueue();
    }

    @Override
    public KeyPairAuthJtiChecker getKeyPairAuthJtiChecker() {
        return delegate.getKeyPairAuthJtiChecker();
    }

    @Override
    public Alerter getAlerter() {
        return delegate.getAlerter();
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }
}
