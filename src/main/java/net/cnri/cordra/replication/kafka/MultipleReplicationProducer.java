package net.cnri.cordra.replication.kafka;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MultipleReplicationProducer implements ReplicationProducer {

    public static enum Durability {ALL, MAJORITY, ONE, NONE}
    
    private final List<ReplicationProducer> producers;
    private final Durability durability;
    @SuppressWarnings("unused") 
    private final int target;
    
    public MultipleReplicationProducer(List<ReplicationProducer> producers) {
        this(producers, Durability.ALL);
    }
    
    public MultipleReplicationProducer(List<ReplicationProducer> producers, Durability durability) {
        if (durability != Durability.ALL) {
            throw new UnsupportedOperationException("At this time only a durability of ALL is supported.");
        }
        this.producers = producers;
        this.durability = durability;
        this.target = calculateTarget(this.durability, producers.size());
    }
    
    public static int calculateTarget(Durability durability, int n) {
        if (durability == Durability.ALL) {
            return n;
        } else if (durability == Durability.NONE) {
            return 0;
        } else if (durability == Durability.ONE) {
            return 1;
        } else {
            double size = n;
            double half = size / 2d;
            int majority = (int) Math.ceil(half);
            return majority;
        }
    }
    
    @Override
    public CompletableFuture<Void> sendAsync(String key, String message) {
        List<CompletableFuture<Void>> tasks = new ArrayList<>();
        for (ReplicationProducer producer : producers) {
            tasks.add(producer.sendAsync(key, message));
        }
        return CompletableFuture.allOf(tasks.toArray(new CompletableFuture[tasks.size()]));
    }
    
    @Override
    public void shutdown() throws Exception {
        for (ReplicationProducer p : producers) {
            p.shutdown();
        }
    }

}
