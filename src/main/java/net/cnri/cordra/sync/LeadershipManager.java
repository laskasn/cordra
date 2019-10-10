package net.cnri.cordra.sync;

import java.util.Collection;

import net.cnri.cordra.api.CordraException;

public interface LeadershipManager {
    boolean isThisInstanceLeader();
    void onGroupMembershipChange(Runnable callback);

    String getId();
    void start(boolean canBeLeader);
    Collection<String> getGroupMembers();
    void waitForLeaderToBeElected() throws CordraException;
    void shutdown() throws Exception;
}
