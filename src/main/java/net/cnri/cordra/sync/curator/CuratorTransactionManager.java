package net.cnri.cordra.sync.curator;

import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.atomic.DistributedAtomicInteger;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

import net.cnri.cordra.indexer.CordraTransaction;
import net.cnri.cordra.sync.TransactionManager;
import net.cnri.cordra.api.InternalErrorCordraException;
import net.cnri.cordra.api.UncheckedCordraException;
import net.cnri.cordra.GsonUtility;
import net.cnri.cordra.api.CordraException;

public class CuratorTransactionManager implements TransactionManager {
    private static Logger logger = LoggerFactory.getLogger(CuratorTransactionManager.class);
    
//    private static final String NEXT_TXN_ID_PATH = "/txns/txnId";
//    private static final String NEXT_TXN_ID_PROMOTED_TO_LOCK_PATH = "/txns/txnIdLock";
    private static final String OPEN_TXNS_PATH = "/txns/open";
    private static final String REINDEX_PATH = "/txns/reindexInProgress";
    
    private final CuratorFramework client;
    private final ExecutorService execServ;
    private final AtomicInteger nextTxnIdSuffix = new AtomicInteger();
//    private final DistributedAtomicLong nextTxnId;
    private final DistributedAtomicInteger reindexInProgress;
    private final Gson gson = GsonUtility.getGson();
    private int cordraServiceIdAsInt;
    
    public CuratorTransactionManager(CuratorFramework client, ExecutorService execServ) throws Exception {
        this.client = client;
        this.execServ = execServ;
        ExponentialBackoffRetry retryPolicy = new ExponentialBackoffRetry(1000, 3);
//        CuratorUtil.ensurePath(client, NEXT_TXN_ID_PATH);
//        PromotedToLock promotedToLock = PromotedToLock.builder().lockPath(NEXT_TXN_ID_PROMOTED_TO_LOCK_PATH).retryPolicy(retryPolicy).build();
//        this.nextTxnId = new DistributedAtomicLong(client, NEXT_TXN_ID_PATH, retryPolicy, promotedToLock);
        CuratorUtil.ensurePath(client, REINDEX_PATH);
        this.reindexInProgress = new DistributedAtomicInteger(client, REINDEX_PATH, retryPolicy);
    }

//    @Override
//    public long getAndIncrementNextTransactionId() throws CordraException {
//        try {
//            while (true) {
//                AtomicValue<Long> result = nextTxnId.increment();
//                if (result.succeeded()) {
//                    return result.preValue();
//                }
//            }
//        } catch (Exception e) {
//            throw new InternalErrorCordraException(e);
//        }
//    }

    @Override
    public void start(String cordraServiceIdParam) {
        cordraServiceIdAsInt = Integer.parseInt(cordraServiceIdParam);
        if (cordraServiceIdAsInt < 0 || cordraServiceIdAsInt >= 1000) throw new AssertionError("Unexpected cordraServiceId " + cordraServiceIdParam);
    }
    
    @Override
    public long getAndIncrementNextTransactionId() {
        int suffix = nextTxnIdSuffix.getAndIncrement() % 1000;
        while (suffix < 0) suffix += 1000;
        return System.currentTimeMillis() * 1_000_000L + cordraServiceIdAsInt * 1_000L + suffix;
    }

    @Override
    public void openTransaction(long txnId, String cordraServiceId, CordraTransaction txn) throws CordraException {
        try {
            byte[] bytes = gson.toJson(txn).getBytes(StandardCharsets.UTF_8);
            try {
                client.create().creatingParentsIfNeeded().forPath(OPEN_TXNS_PATH + "/" + cordraServiceId + "/" + txnId, bytes);
            } catch (KeeperException.NodeExistsException e) {
                // In principle, this could indicate transaction id re-use---for the same digital object.
                // But if transaction id minting works correctly, it indicates Zookeeper retry logic.
                byte[] existingBytes = client.getData().forPath(OPEN_TXNS_PATH + "/" + cordraServiceId + "/" + txnId);
                if (Arrays.equals(existingBytes, bytes)) {
                    logger.warn("Transaction " + txnId + " already open");
                } else {
                    throw e;
                }
            }
        } catch (Exception e) {
            throw new InternalErrorCordraException(e);
        }
    }

    @Override
    public void closeTransaction(long txnId, String cordraServiceId) throws CordraException {
        try {
            client.delete().forPath(OPEN_TXNS_PATH + "/" + cordraServiceId + "/" + txnId);
        } catch (KeeperException.NoNodeException e) {
            logger.warn("Transaction " + txnId + " already closed");
        } catch (Exception e) {
            throw new InternalErrorCordraException(e);
        }
    }

    @Override
    public List<String> getCordraServiceIdsWithOpenTransactions() throws CordraException {
        try {
            return client.getChildren().forPath(OPEN_TXNS_PATH);
        } catch (KeeperException.NoNodeException e) {
            return Collections.emptyList();
        } catch (Exception e) {
            throw new InternalErrorCordraException(e);
        }
    }

    @Override
    public Iterator<Entry<Long, CordraTransaction>> iterateTransactions(String cordraServiceId) throws CordraException {
        try {
            sync(OPEN_TXNS_PATH + "/" + cordraServiceId);
            List<String> txnIds = client.getChildren().forPath(OPEN_TXNS_PATH + "/" + cordraServiceId);
            return txnIds.stream()
                .map(txnId -> entryOfTxnId(cordraServiceId, txnId))
                .iterator();
        } catch (Exception e) {
            throw new InternalErrorCordraException(e);
        }
    }
    
    private void sync(String path) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        client.sync().inBackground((thisClient, event) -> {
            latch.countDown();
        }, execServ).forPath(path);
        latch.await();
    }

    private Entry<Long, CordraTransaction> entryOfTxnId(String cordraServiceId, String txnId) {
        try {
            byte[] data = client.getData().forPath(OPEN_TXNS_PATH + "/" + cordraServiceId + "/" + txnId);
            return new AbstractMap.SimpleEntry<>(Long.valueOf(txnId),  gson.fromJson(new String(data, StandardCharsets.UTF_8), CordraTransaction.class));
        } catch (Exception e) {
            throw new UncheckedCordraException(new InternalErrorCordraException(e));
        }
    }
    
    @Override
    public void cleanup(String cordraServiceId) throws CordraException {
        try {
            client.delete().forPath(OPEN_TXNS_PATH + "/" + cordraServiceId);
        } catch (KeeperException.NotEmptyException e) {
            logger.warn("Cleaning up txns for " + cordraServiceId + ", but not empty");
        } catch (Exception e) {
            throw new InternalErrorCordraException(e);
        }
    }

    @Override
    public boolean isReindexInProcess() throws CordraException {
        try {
            sync(REINDEX_PATH);
            return this.reindexInProgress.get().preValue() > 0;
        } catch (Exception e) {
            throw new InternalErrorCordraException(e);
        }
    }

    @Override
    public void setReindexInProcess(boolean isReindexInProcess) throws CordraException {
        try {
            this.reindexInProgress.forceSet(isReindexInProcess ? 1 : 0);
        } catch (Exception e) {
            throw new InternalErrorCordraException(e);
        }
    }

    @Override
    public void shutdown() {
        // no-op
    }

}
