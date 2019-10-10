package net.cnri.cordra.sync;

import java.util.function.Consumer;

import net.cnri.cordra.api.CordraException;

public interface SignalWatcher {
    enum Signal { DESIGN, AUTH_CHANGE, JAVASCRIPT_CLEAR_CACHE }
    
    void start(String cordraServiceId, Consumer<Signal> callback) throws CordraException;
    void sendSignal(Signal signal) throws CordraException;
    void shutdown();
}
