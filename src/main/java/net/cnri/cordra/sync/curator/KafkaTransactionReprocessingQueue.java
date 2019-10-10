package net.cnri.cordra.sync.curator;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

import net.cnri.cordra.indexer.CordraTransaction;
import net.cnri.cordra.model.ReprocessingQueueConfig;
import net.cnri.cordra.sync.TransactionManager;
import net.cnri.cordra.sync.TransactionReprocessingQueue;
import net.cnri.cordra.api.InternalErrorCordraException;
import net.cnri.cordra.GsonUtility;
import net.cnri.cordra.api.CordraException;

public class KafkaTransactionReprocessingQueue implements TransactionReprocessingQueue {
    private static final Logger logger = LoggerFactory.getLogger(KafkaTransactionReprocessingQueue.class);

    public static final String TOPIC = "CordraReprocessing";
    public final long DELAY = 1000 * 60 * 2; // 2 mins
    private final ScheduledExecutorService execServ;
    private final KafkaConsumer<String,String> consumer;
    private final KafkaProducer<String,String> producer;
    private final Gson gson;

    private ThrowingConsumer<CordraTransaction> callback;
    private volatile boolean running;
    private volatile boolean closed;

    public KafkaTransactionReprocessingQueue(ReprocessingQueueConfig config) {
        gson = GsonUtility.getGson();
        consumer = constructConsumer(config);
        producer = constructProducer(config);
        execServ = Executors.newScheduledThreadPool(1);
    }

    private KafkaProducer<String,String> constructProducer(ReprocessingQueueConfig config) {
        Properties props = new Properties();
        if (config.producerConfig != null) props.putAll(config.producerConfig);
        props.putIfAbsent("bootstrap.servers", config.kafkaBootstrapServers);
        props.putIfAbsent("key.serializer", StringSerializer.class.getName());
        props.putIfAbsent("value.serializer", StringSerializer.class.getName());
        props.putIfAbsent("acks", "all");
        props.putIfAbsent("client.id", "cordra-" + UUID.randomUUID().toString());
        return new KafkaProducer<>(props);
    }

    private KafkaConsumer<String,String> constructConsumer(ReprocessingQueueConfig config) {
        String groupId = "cordra-reprocessing-consumer";
        Properties props = new Properties();
        if (config.consumerConfig != null) props.putAll(config.consumerConfig);
        props.putIfAbsent("bootstrap.servers", config.kafkaBootstrapServers);
        props.putIfAbsent("group.id", groupId);
        props.putIfAbsent("key.deserializer", StringDeserializer.class.getName());
        props.putIfAbsent("value.deserializer", StringDeserializer.class.getName());
        props.putIfAbsent("enable.auto.commit", "false");
        props.putIfAbsent("auto.offset.reset", "earliest");
        props.putIfAbsent("metadata.max.age.ms", 5000);
        return new KafkaConsumer<>(props);
    }

    @Override
    public void insert(CordraTransaction txn, String cordraServiceId) throws CordraException {
        String message = gson.toJson(txn);
        String key = txn.objectId;
        Future<RecordMetadata> future = producer.send(new ProducerRecord<>(TOPIC, key, message));
        try {
            future.get();
        } catch (ExecutionException e) {
            throw new InternalErrorCordraException(e.getCause());
        } catch (Exception e) {
            throw new InternalErrorCordraException(e);
        }
    }

    @Override
    public void start(@SuppressWarnings("hiding") ThrowingConsumer<CordraTransaction> callback, TransactionManager transactionManager) throws CordraException {
        this.callback = callback;
        if (!running) {
            running = true;
            consumer.subscribe(Collections.singletonList(TOPIC));
            execServ.scheduleWithFixedDelay(this::poll, 1, 1, TimeUnit.MINUTES);
        }
    }

    public void poll() {
        long now = System.currentTimeMillis();
        long cutoffTimestamp = now - DELAY;
        while (true) {
            if (!running) break;
            // Since polling doesn't always return all new messages,
            // we keep polling until we run out of messages we can handle.
            // We don't try to handle messages that are newer than cutoffTimestamp
            boolean found = false;
            try {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                for (TopicPartition topicPartition : records.partitions()) {
                    if (!running) break;
                    if (handleTransactionsFromOnePartition(records, topicPartition, cutoffTimestamp)) {
                        found = true;
                    }
                }
            } catch (Exception e) {
                logger.error("Exception polling reprocessing queue", e);
            }
            if (!found) break;
        }
    }

    private boolean handleTransactionsFromOnePartition(ConsumerRecords<String, String> records, TopicPartition topicPartition, long cutoffTimestamp) {
        long nextOffset = -1;
        long rewindOffset = -1;
        for (ConsumerRecord<String, String> record : records.records(topicPartition)) {
            if (!running) break;
            // skip old transactions recently inserted into Kafka
            if (record.timestamp() > cutoffTimestamp) {
                rewindOffset = record.offset();
                break;
            }
            String msg = record.value();
            CordraTransaction txn = gson.fromJson(msg, CordraTransaction.class);
            // also skip recent transactions regardless of when inserted into Kafka
            if (txn.timestamp > cutoffTimestamp) {
                rewindOffset = record.offset();
                break;
            }
            try {
                logger.info("Reprocessing txn from Kafka " + txn.objectId + " " + txn.txnId);
                callback.accept(txn);
                nextOffset = record.offset() + 1;
            } catch (Exception e) {
                logger.error("Exception reprocessing transactions", e);
                rewindOffset = record.offset();
                break;
            }
        }
        if (nextOffset >= 0) {
            consumer.commitSync(Collections.singletonMap(topicPartition, new OffsetAndMetadata(nextOffset)));
        }
        if (rewindOffset >= 0) {
            consumer.seek(topicPartition, rewindOffset);
            return false;
        }
        if (nextOffset >= 0) {
            return true;
        }
        return false;
    }

    @Override
    public void shutdown() {
        if (closed) return;
        running = false;
        execServ.shutdown();
        try {
            execServ.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        producer.close();
        consumer.close();
        closed = true;
    }

}
