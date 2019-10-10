package net.cnri.cordra.replication.kafka;

import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import net.cnri.cordra.model.ReplicationProducerConfig;

public class KafkaReplicationProducer implements ReplicationProducer {

    public static final String TOPIC_PREFIX = "CordraReplication-";
    
    private final String topic;
    private final KafkaProducer<String, String> producer;
    
    public KafkaReplicationProducer(String cordraClusterId, ReplicationProducerConfig config) {
        this.topic = TOPIC_PREFIX + cordraClusterId;
        Properties props = new Properties();
        if (config.producerConfig != null) props.putAll(config.producerConfig);
        props.putIfAbsent("bootstrap.servers", config.kafkaBootstrapServers);
        props.putIfAbsent("key.serializer", StringSerializer.class.getName());
        props.putIfAbsent("value.serializer", StringSerializer.class.getName());
        props.putIfAbsent("acks", "all");
        props.putIfAbsent("client.id", "cordra-" + UUID.randomUUID().toString()); 
        producer = new KafkaProducer<>(props);
    }
    
    @Override
    public CompletableFuture<Void> sendAsync(String key, String message) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        producer.send(new ProducerRecord<>(topic, key, message), (recordMetadata, exception) -> {
            if (exception == null) {
                future.complete(null);
            } else {
                future.completeExceptionally(exception);
            }
        });
        return future;
    }

    @Override
    public void shutdown() throws Exception {
        producer.close();
    }
}
