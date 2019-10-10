package net.cnri.cordra.sync.local;

import java.util.function.Consumer;

import net.cnri.cordra.sync.SignalWatcher;

public class NoopSignalWatcher implements SignalWatcher {

    @Override
    public void start(String cordraServiceId, Consumer<Signal> callback) {
        // no-op
    }

    @Override
    public void sendSignal(Signal signal) {
        // no-op
    }

    @Override
    public void shutdown() {
        // no-op
    }

}
