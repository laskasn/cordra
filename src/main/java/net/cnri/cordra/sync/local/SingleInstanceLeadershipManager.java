package net.cnri.cordra.sync.local;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import net.cnri.cordra.sync.LeadershipManager;

public class SingleInstanceLeadershipManager implements LeadershipManager {

    private String id = String.valueOf(System.currentTimeMillis());
    private boolean canBeLeader;
    private ExecutorService execServ;
    private Runnable leadershipCallback;

    @Override
    public boolean isThisInstanceLeader() {
        return canBeLeader;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void start(boolean canBeLeaderParam) {
        this.canBeLeader = canBeLeaderParam;
        if (canBeLeader) {
            this.execServ = Executors.newSingleThreadExecutor();
            if (leadershipCallback != null) {
                execServ.submit(leadershipCallback);
            }
        }
    }

    @Override
    public void onGroupMembershipChange(Runnable callbackParam) {
        this.leadershipCallback = callbackParam;
        if (execServ != null) {
            execServ.submit(leadershipCallback);
        }
    }

    @Override
    public void waitForLeaderToBeElected() {
        // no-op
    }

    @Override
    public List<String> getGroupMembers() {
        return Collections.singletonList(id);
    }

    @Override
    public void shutdown() throws Exception {
        if (execServ == null) return;
        execServ.shutdown();
        execServ.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
    }

}
