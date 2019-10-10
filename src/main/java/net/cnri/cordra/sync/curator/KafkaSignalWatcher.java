package net.cnri.cordra.sync.curator;

import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

import net.cnri.cordra.GsonUtility;
import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.api.InternalErrorCordraException;
import net.cnri.cordra.model.SignalWatcherConfig;
import net.cnri.cordra.sync.SignalWatcher;

public class KafkaSignalWatcher implements SignalWatcher {
    private static final Logger logger = LoggerFactory.getLogger(KafkaSignalWatcher.class);

    public static final String TOPIC = "CordraSignals";

    private KafkaConsumer<String,String> consumer;
    private final KafkaProducer<String,String> producer;
    private Consumer<Signal> callback;
    private volatile boolean running;
    private final ExecutorService exec;
    private final Gson gson;
    private String cordraServiceId;
    private final SignalWatcherConfig config;

    public KafkaSignalWatcher(SignalWatcherConfig config) {
        this.config = config;
        this.exec = Executors.newSingleThreadExecutor();
        this.producer = constructProducer(config);
        this.gson = GsonUtility.getGson();
    }

    private static KafkaProducer<String,String> constructProducer(SignalWatcherConfig config) {
        Properties props = new Properties();
        if (config.producerConfig != null) props.putAll(config.producerConfig);
        props.putIfAbsent("bootstrap.servers", config.kafkaBootstrapServers);
        props.putIfAbsent("key.serializer", StringSerializer.class.getName());
        props.putIfAbsent("value.serializer", StringSerializer.class.getName());
        props.putIfAbsent("acks", "all");
        props.putIfAbsent("client.id", "cordra-" + UUID.randomUUID().toString());
        return new KafkaProducer<>(props);
    }

    private static KafkaConsumer<String,String> constructConsumer(String cordraServiceId, SignalWatcherConfig config) {
        String groupId = cordraServiceId;
        Properties props = new Properties();
        if (config.consumerConfig != null) props.putAll(config.consumerConfig);
        props.putIfAbsent("bootstrap.servers", config.kafkaBootstrapServers);
        props.putIfAbsent("group.id", groupId);
        props.putIfAbsent("key.deserializer", StringDeserializer.class.getName());
        props.putIfAbsent("value.deserializer", StringDeserializer.class.getName());
        props.putIfAbsent("enable.auto.commit", "false");
        props.putIfAbsent("auto.offset.reset", "latest");
        props.putIfAbsent("metadata.max.age.ms", 5000);
        return new KafkaConsumer<>(props);
    }

    @Override
    @SuppressWarnings("hiding")
    public void start(String cordraServiceId, Consumer<Signal> callback) throws CordraException {
        if (running) throw new IllegalStateException();
        logger.info("Subscribing to " + TOPIC);
        this.callback = callback;
        this.cordraServiceId = cordraServiceId;
        this.consumer = constructConsumer(cordraServiceId, config);
        submitAndWait(() -> consumer.subscribe(Arrays.asList(TOPIC)));
        running = true;
        exec.execute(this::runAndLogErrors);
    }

    private void submitAndWait(Runnable r) {
        try {
            exec.submit(r).get();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException) throw (RuntimeException)e.getCause();
            throw new RuntimeException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void runAndLogErrors() {
        try {
            run();
        } catch (WakeupException e) {
            // ignore
        } catch (Throwable e) {
            logger.error("Fatal error in KafkaSignalWatcher", e);
        }
    }

    public void run() {
        while (running) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
            for (TopicPartition topicPartition : records.partitions()) {
                for (ConsumerRecord<String, String> record : records.records(topicPartition)) {
                    if (!running) return;
                    String msg = record.value();
                    SignalMessage event = gson.fromJson(msg, SignalMessage.class);
                    if (!event.cordraServiceId.equals(this.cordraServiceId)) {
                        callback.accept(event.signal);
                    }
                }
                consumer.commitSync();
            }
        }
    }

    @Override
    public void sendSignal(Signal signal) throws CordraException {
        SignalMessage event = new SignalMessage();
        event.cordraServiceId = this.cordraServiceId;
        event.signal = signal;
        String message = gson.toJson(event);
        Future<RecordMetadata> future = producer.send(new ProducerRecord<>(TOPIC, null, message));
        try {
            future.get();
        } catch (Exception e) {
            throw new InternalErrorCordraException(e);
        }
    }

    @Override
    public void shutdown() {
        running = false;
        producer.close();
        consumer.wakeup();
        exec.execute(consumer::close);
        exec.shutdown();
        try {
            exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static class SignalMessage {
        public SignalWatcher.Signal signal;
        public String cordraServiceId;
    }

}
