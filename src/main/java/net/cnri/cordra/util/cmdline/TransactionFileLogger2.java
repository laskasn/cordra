package net.cnri.cordra.util.cmdline;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import net.cnri.cordra.CordraServiceFactory;
import net.cnri.cordra.GsonUtility;
import net.cnri.cordra.replication.kafka.ReplicationMessage;
import net.cnri.microservices.Alerter;
import net.cnri.microservices.LoggingAlerter;
import net.cnri.microservices.MultithreadedKafkaConsumer;
import net.cnri.microservices.StripedExecutorService;
import net.cnri.microservices.StripedThreadPoolExecutorService;

public class TransactionFileLogger2 {

    private StripedExecutorService stripedTaskRunner;
    private MultithreadedKafkaConsumer consumer;
    private static Gson gson = GsonUtility.getGson();
    private DailyFileLogger files;

    public static void main(String[] args) throws Exception {
        OptionSet options = parseOptions(args);

        String dirPath = (String) options.valueOf("p");
        String kafkaBootstrapServers = (String) options.valueOf("k");
        String groupId;
        if (options.has("g")) {
            groupId = (String) options.valueOf("g");
        } else {
            groupId = UUID.randomUUID().toString();
        }
        Path path = Paths.get(dirPath);
        Files.createDirectories(path);

        Properties props = null;
        if (options.has("properties")) {
            String filename = (String) options.valueOf("properties");
            try (Reader reader = Files.newBufferedReader(Paths.get(filename)) ) {
                props = new Properties();
                props.load(reader);
            }
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        Map<String, String> propsAsMap = (Map) props;
        TransactionFileLogger2 logger = new TransactionFileLogger2(path, groupId, kafkaBootstrapServers, propsAsMap);
        logger.start();
        Runtime.getRuntime().addShutdownHook(new Thread(logger::stop));
    }

    private static OptionSet parseOptions(String[] args) throws IOException {
        OptionParser parser = new OptionParser();
        parser.acceptsAll(Arrays.asList("h", "help"), "Prints help").forHelp();
        parser.acceptsAll(Arrays.asList("p", "path"), "Path to log directory").withRequiredArg().required();
        parser.acceptsAll(Arrays.asList("k", "kafka"), "Kafka bootstrap servers connection string").withRequiredArg();
        parser.acceptsAll(Arrays.asList("g", "group-id"), "Kafka consumer group.id; if absent, a unique string will be used").withRequiredArg();
        parser.accepts("properties", "properties file to configure Kafka consumer (overrides k and g arguments)").withRequiredArg();
        OptionSet options;
        try {
            options = parser.parse(args);
            if (!options.has("k") && !options.has("properties")) {
                System.out.println("Error parsing options: kafka or properties option required");
                parser.printHelpOn(System.out);
                System.exit(1);
                return null;
            }
        } catch (OptionException e) {
            System.out.println("Error parsing options: " + e.getMessage());
            parser.printHelpOn(System.out);
            System.exit(1);
            return null;
        }
        if (options.has("h")) {
            System.out.println("This tool will read from a kafka and write messages to single line separated json log file");
            parser.printHelpOn(System.out);
            System.exit(1);
            return null;
        }
        return options;
    }

    public TransactionFileLogger2(Path dir, String groupId, String kafkaBootstrapServers, Map<String, String> props) {
        files = new DailyFileLogger(dir);
        int numConsumerThreads = 24;
        Pattern pattern = CordraServiceFactory.patternExcluding("TransactionFileReplayer");
        Alerter alerter = new LoggingAlerter();
        this.stripedTaskRunner = new StripedThreadPoolExecutorService(numConsumerThreads, numConsumerThreads, 500, (thread, exception) -> {
            alerter.alert("Exception in stripedTaskRunner " + exception);
        });
        this.consumer = new MultithreadedKafkaConsumer(pattern, groupId, props, kafkaBootstrapServers, alerter, stripedTaskRunner);
        gson = GsonUtility.getGson();
    }

    public void start() {
        consumer.start(this::consume, this::stripePicker);
    }

    Object stripePicker(String message) {
        ReplicationMessage txn = gson.fromJson(message, ReplicationMessage.class);
        return txn.handle;
    }

    public void consume(String message, long timestamp) {
        String singleLine = toSingleLineJson(message);
        try {
            files.appendLineToFileForTimestamp(singleLine, timestamp);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String toSingleLineJson(String json) {
        JsonParser parser = new JsonParser();
        JsonElement el = parser.parse(json);
        String singleLine = gson.toJson(el);
        return singleLine;
    }

    public void stop() {
        consumer.shutdown();
        stripedTaskRunner.shutdown();
        if (files != null) {
            files.close();
        }
    }
}
