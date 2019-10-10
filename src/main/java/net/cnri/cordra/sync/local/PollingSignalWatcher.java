package net.cnri.cordra.sync.local;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import net.cnri.cordra.sync.SignalWatcher;

public class PollingSignalWatcher implements SignalWatcher {
    
    private ScheduledExecutorService execServ = null;
    private static final int STATE_POLLING_INTERVAL = 1000*5; // 5 seconds
    
    @Override
    public void start(String cordraServiceId, Consumer<Signal> callback) {
        execServ = Executors.newSingleThreadScheduledExecutor();
        execServ.scheduleAtFixedRate(() -> callback.accept(Signal.DESIGN), STATE_POLLING_INTERVAL, STATE_POLLING_INTERVAL, TimeUnit.MILLISECONDS); 
    }

    @Override
    public void sendSignal(Signal signal) {
        // no-op
    }

    @Override
    public void shutdown() {
        execServ.shutdown();
        try {
            execServ.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
