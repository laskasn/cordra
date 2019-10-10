package net.cnri.cordra.sync;

import net.cnri.microservices.Alerter;

public interface SyncObjects {
    CheckableLocker getStartupLocker();
    RepoInitProvider getRepoInitProvider();
    LeadershipManager getLeadershipManager();
    TransactionManager getTransactionManager();
    SingleThreadReadWriteCheckableLocker getDesignLocker();
    CheckableLocker getSchemaNameLocker();
    CheckableLocker getUsernameLocker();
    NameLocker getObjectLocker();
    AllHandlesUpdaterSync getAllHandlesUpdaterSync();
    SignalWatcher getSignalWatcher();
    TransactionReprocessingQueue getTransactionReprocessingQueue();
    KeyPairAuthJtiChecker getKeyPairAuthJtiChecker();
    Alerter getAlerter();
    void shutdown();
}
