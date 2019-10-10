package net.cnri.cordra.sync.curator;

import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.cnri.cordra.sync.SignalWatcher;
import net.cnri.microservices.Alerter;
import net.cnri.cordra.api.InternalErrorCordraException;
import net.cnri.cordra.api.CordraException;

public class CuratorSignalWatcher implements SignalWatcher {
    private static final Logger logger = LoggerFactory.getLogger(CuratorSignalWatcher.class);

    private static final String SIGNALS_PATH = "/signals";

    private final Map<Signal, Integer> latestSignal = new EnumMap<>(Signal.class);
    private final CuratorFramework client;
    private final AtomicBoolean signalReceived = new AtomicBoolean(false);
    private final ExecutorService findNewSignalsExecServ;
    private final ScheduledExecutorService delayedRetryExecServ;
    private final Map<Signal, ExecutorService> signalHandlerExecServs;
    private final Alerter alerter;
    private final Watcher watcher = this::watcher;
    private String cordraServiceId;
    private Consumer<Signal> callback;

    public CuratorSignalWatcher(CuratorFramework client, Alerter alerter) throws Exception {
        this.client = client;
        this.findNewSignalsExecServ = newSingleThreadSingletonQueueThreadPoolExecutor();
        this.delayedRetryExecServ = Executors.newScheduledThreadPool(1);
        ((ScheduledThreadPoolExecutor)this.delayedRetryExecServ).setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        this.alerter = alerter;
        this.signalHandlerExecServs = new EnumMap<>(Signal.class);
        for (Signal signal : Signal.values()) {
            this.signalHandlerExecServs.put(signal, newSingleThreadSingletonQueueThreadPoolExecutor());
            CuratorUtil.ensurePath(client, SIGNALS_PATH + "/" + signal);
        }
    }

    private static ThreadPoolExecutor newSingleThreadSingletonQueueThreadPoolExecutor() {
        return new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(1), new ThreadPoolExecutor.DiscardPolicy());
    }

    @Override
    public void start(String cordraServiceIdParam, Consumer<Signal> callbackParam) throws CordraException {
        this.cordraServiceId = cordraServiceIdParam;
        this.callback = callbackParam;
        try {
            latestSignal.putAll(getLatestSignals());
        } catch (Exception e) {
            throw new InternalErrorCordraException(e);
        }
    }

    @Override
    public void sendSignal(Signal signal) throws CordraException {
        try {
            synchronized (signal) {
                Stat stat = client.setData().forPath(SIGNALS_PATH + "/" + signal, cordraServiceId.getBytes(StandardCharsets.UTF_8));
                // try to avoid acting on our own signals: if we have seen the previous signal, skip this one
                int updateVersion = stat.getVersion();
                logger.debug("Sending signal " + signal + " at version " + updateVersion);
                if (latestSignal.replace(signal, updateVersion - 1, updateVersion)) {
                    logger.debug("Saw all previous " + signal + " signals");
                } else {
                    logger.debug("Still waiting for some previous " + signal + " signals");
                }
            }
        } catch (Exception e) {
            throw new InternalErrorCordraException(e);
        }
    }

    @Override
    public void shutdown() {
        delayedRetryExecServ.shutdown();
        findNewSignalsExecServ.shutdown();
        for (ExecutorService signalHandlerExecServ : signalHandlerExecServs.values()) {
            signalHandlerExecServ.shutdown();
        }
        client.clearWatcherReferences(watcher);
    }

    @SuppressWarnings("unused")
    private void watcher(WatchedEvent event) {
        if (!signalReceived.getAndSet(true)) {
            findNewSignalsExecServ.submit(this::handleEvent);
        }
    }

    private void handleEvent() {
        if (!signalReceived.get()) return;
        Map<Signal, Integer> newLatestSignals;
        try {
            // no need to retain other queued tasks
            ((ThreadPoolExecutor)findNewSignalsExecServ).getQueue().clear();
            ((ScheduledThreadPoolExecutor)delayedRetryExecServ).getQueue().clear();
            signalReceived.set(false);
            newLatestSignals = getLatestSignals();
        } catch (Exception e) {
            logger.error("Exception handling signal", e);
            alerter.alert("Exception handling signal" + e);
            delayedRetryExecServ.schedule(() -> watcher(null), 5, TimeUnit.MINUTES);
            return;
        }
        for (Signal signal : Signal.values()) {
            synchronized (signal) {
                int newSignalVersion = newLatestSignals.get(signal);
                int oldSignalVersion = latestSignal.get(signal);
                if (newSignalVersion != oldSignalVersion) {
                    logger.debug("Saw new signal " + signal + " version " + oldSignalVersion + " to " + newSignalVersion);
                    latestSignal.put(signal, newSignalVersion);
                    signalHandlerExecServs.get(signal).submit(() -> callback.accept(signal));
                }
            }
        }
    }

    private Map<Signal, Integer> getLatestSignals() throws Exception {
        Map<Signal, Integer> map = new EnumMap<>(Signal.class);
        for (Signal signal : Signal.values()) {
            Stat stat = client.checkExists().usingWatcher(watcher).forPath(SIGNALS_PATH + "/" + signal);
            int updateVersion = stat.getVersion();
            map.put(signal, updateVersion);
        }
        return map;
    }

}
