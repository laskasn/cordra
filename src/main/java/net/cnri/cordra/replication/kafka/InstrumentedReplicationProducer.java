package net.cnri.cordra.replication.kafka;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.cnri.cordra.api.CordraException;
import net.cnri.util.LoggingUtil;

public class InstrumentedReplicationProducer implements ReplicationProducer {

    private static Logger logger = LoggerFactory.getLogger(InstrumentedReplicationProducer.class);
    
    private final ReplicationProducer delegate;
    
    public InstrumentedReplicationProducer(ReplicationProducer delegate) {
        this.delegate = delegate;
    }
    
    public static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX").withZone(ZoneOffset.UTC);
    
    @Override
    public void send(String key, String message) throws CordraException {
        long start = System.currentTimeMillis();
        try {
            delegate.send(key, message);
        } finally {
            long end = System.currentTimeMillis();
            long delta = end - start;
            String caller = LoggingUtil.getCallingFunctionWhenCalledDirectly();
            String startTime = dateTimeFormatter.format(Instant.ofEpochMilli(start));
            logger.trace(caller + ": start " + startTime + ", " + delta + "ms");
        }
    }
    
    @Override
    public CompletableFuture<Void> sendAsync(String key, String message) {
        return delegate.sendAsync(key, message);
    }

    @Override
    public void shutdown() throws Exception {
        delegate.shutdown();
    }

}
