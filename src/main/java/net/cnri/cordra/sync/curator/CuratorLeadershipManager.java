package net.cnri.cordra.sync.curator;

import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.listen.ListenerContainer;
import org.apache.curator.framework.recipes.atomic.AtomicValue;
import org.apache.curator.framework.recipes.atomic.DistributedAtomicInteger;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.apache.curator.framework.recipes.leader.LeaderSelector;
import org.apache.curator.framework.recipes.leader.LeaderSelectorListenerAdapter;
import org.apache.curator.framework.recipes.nodes.GroupMember;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.cnri.cordra.sync.LeadershipManager;
import net.cnri.cordra.api.InternalErrorCordraException;
import net.cnri.cordra.api.CordraException;

public class CuratorLeadershipManager implements LeadershipManager {
    private static final Logger logger = LoggerFactory.getLogger(CuratorLeadershipManager.class);
    
    private static final String NEXT_ID_PATH = "/group/nextId";
    private static final String LEADER_SELECTOR_PATH = "/group/leader";
    private static final String GROUP_MEMBERSHIP_PATH = "/group/members";
    private static final long DELAY_SECONDS = 10;
    
    private final CuratorFramework client;
    private final String id;
    private final ExecutorService cachedExecServ;
    private final ScheduledExecutorService singleThreadedExecServ;
    private final LeaderSelector leaderSelector;
    private final GroupMember groupMembership;
    private /*final*/ ListenerContainer<PathChildrenCacheListener> groupMembershipListenable;
    private boolean canBeLeader;
    private Runnable groupMembershipCallback;
    private volatile boolean scheduled;
    private volatile boolean shutdown;
    
    public CuratorLeadershipManager(CuratorFramework client, ExecutorService cachedExecServ) throws Exception {
        this.client = client;
        this.cachedExecServ = cachedExecServ;
        this.id = initializeId();
        this.singleThreadedExecServ = Executors.newScheduledThreadPool(1);
        ((ScheduledThreadPoolExecutor)this.singleThreadedExecServ).setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        CuratorUtil.ensurePath(client, LEADER_SELECTOR_PATH);
        CuratorUtil.ensurePath(client, GROUP_MEMBERSHIP_PATH);
        this.leaderSelector = new LeaderSelector(client, LEADER_SELECTOR_PATH, cachedExecServ, new InnerLeaderSelectorListener());
        this.groupMembership = new GroupMember(client, GROUP_MEMBERSHIP_PATH, id) {
            @Override
            protected PathChildrenCache newPathChildrenCache(CuratorFramework clientParam, String membershipPath) {
                PathChildrenCache result = super.newPathChildrenCache(clientParam, membershipPath);
                groupMembershipListenable = result.getListenable();
                return result;
            }
        };
    }
    
    private String initializeId() throws Exception {
        ExponentialBackoffRetry retryPolicy = new ExponentialBackoffRetry(1000, 3);
        DistributedAtomicInteger nextId = new DistributedAtomicInteger(client, NEXT_ID_PATH, retryPolicy);
        while (true) {
            AtomicValue<Integer> result = nextId.increment();
            if (result.succeeded()) {
                int preValue = result.preValue();
                if (isCurrentMember(preValue)) continue;
                if (preValue >= 1000) {
                    while (preValue >= 1000) {
                        AtomicValue<Integer> casResult = nextId.compareAndSet(preValue, 0);
                        if (casResult.succeeded()) break;
                        preValue = casResult.preValue();
                    }
                    continue;
                }
                return String.valueOf(preValue);
            }
        }    
    }
    
    private boolean isCurrentMember(int checkId) throws Exception {
        // In principle, this could succeed, but 1000 members would come and go before creating the node.
        // That would lead to a startup error in groupMembership.start() so does not cause a real problem.
        return client.checkExists().forPath(GROUP_MEMBERSHIP_PATH + "/" + checkId) != null;
    }
    
    private class InnerLeaderSelectorListener extends LeaderSelectorListenerAdapter {
        @Override
        public void takeLeadership(CuratorFramework clientParam) throws Exception {
            if (groupMembershipCallback != null && !scheduled) {
                scheduled = true;
                singleThreadedExecServ.schedule(changeScheduledThen(groupMembershipCallback), DELAY_SECONDS, TimeUnit.SECONDS);
            }
            // will be interrupted when leadership lost
            singleThreadedExecServ.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
            if (shutdown) {
                logger.info("Lost leadership expected due to shutdown (Curator InterruptedException can be ignored)");
            } else {
                logger.warn("Lost leadership");
            }
        }
    }
    
    @SuppressWarnings("unused") 
    private void groupMembershipListener(CuratorFramework clientParam, PathChildrenCacheEvent event) {
        if (isThisInstanceLeader() && groupMembershipCallback != null && !scheduled) {
            scheduled = true;
            singleThreadedExecServ.schedule(changeScheduledThen(groupMembershipCallback), DELAY_SECONDS, TimeUnit.SECONDS);
        }
    }
    
    @Override
    public boolean isThisInstanceLeader() {
        if (!canBeLeader) return false;
        return leaderSelector.hasLeadership();
    }

    private Runnable changeScheduledThen(Runnable runnable) {
        return () -> {
            scheduled = false;
            runnable.run();
        };
    }

    @Override
    public void onGroupMembershipChange(Runnable callback) {
        groupMembershipCallback = callback;
        if (isThisInstanceLeader() && !scheduled) {
            scheduled = true;
            singleThreadedExecServ.schedule(changeScheduledThen(callback), DELAY_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void start(boolean canBeLeaderParam) {
        groupMembership.start();
        this.canBeLeader = canBeLeaderParam;
        if (canBeLeader) {
            leaderSelector.setId(id);
            leaderSelector.autoRequeue();
            leaderSelector.start();
            groupMembershipListenable.addListener(this::groupMembershipListener, cachedExecServ);
        }
    }

    @Override
    public Collection<String> getGroupMembers() {
        return groupMembership.getCurrentMembers().keySet();
    }

    @Override
    public void waitForLeaderToBeElected() throws CordraException {
        // read-only instances don't wait
        if (!canBeLeader) return;
        if (isThisInstanceLeader()) return;
        try {
            if (leaderSelector.getLeader().isLeader()) return;
            for (int i = 0; i < 25; i++) {
                Thread.sleep(200);
                if (isThisInstanceLeader()) return;
                if (leaderSelector.getLeader().isLeader()) return;
            }
        } catch (Exception e) {
            throw new InternalErrorCordraException(e);
        }
        throw new InternalErrorCordraException("Waited to long for leader to be present");
    }

    @Override
    public void shutdown() throws Exception {
        shutdown = true;
        groupMembershipListenable.clear();
        groupMembership.close();
        singleThreadedExecServ.shutdown();
        leaderSelector.close();
    }

}
