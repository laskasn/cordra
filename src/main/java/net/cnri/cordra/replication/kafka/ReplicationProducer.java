package net.cnri.cordra.replication.kafka;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import net.cnri.cordra.api.InternalErrorCordraException;
import net.cnri.cordra.api.CordraException;

public interface ReplicationProducer {
    default void send(String key, String message) throws CordraException {
        Future<?> future = sendAsync(key, message);
        try {
            future.get();
        } catch (ExecutionException e) {
            throw new InternalErrorCordraException(e.getCause());
        } catch (Exception e) {
            throw new InternalErrorCordraException(e);
        }
    }

    CompletableFuture<Void> sendAsync(String key, String message);
    void shutdown() throws Exception;
}
