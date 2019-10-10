package net.cnri.cordra.util.cmdline;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.google.gson.Gson;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import net.cnri.cordra.replication.kafka.KafkaReplicationProducer;
import net.cnri.cordra.replication.kafka.ReplicationMessage;
import net.cnri.cordra.GsonUtility;
import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.model.ReplicationProducerConfig;

public class TransactionFileReplayer {
    static Gson gson = GsonUtility.getGson();
    
    public static void main(String[] args) throws Exception {
        OptionSet options = parseOptions(args);
        String pathString = (String) options.valueOf("p");
        String kafkaBootstrapServers = (String) options.valueOf("k");
        boolean displayProgressPercent = options.has("s");
        boolean preserveClusterId = options.has("preserve-clusterid");
        String clusterId = preserveClusterId ? null : (String) options.valueOf("c");
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
        ReplicationProducerConfig config = new ReplicationProducerConfig();
        config.type = "kafka";
        config.kafkaBootstrapServers = kafkaBootstrapServers;
        config.producerConfig = propsAsMap;
        KafkaReplicationProducer producer = new KafkaReplicationProducer("TransactionFileReplayer", config);
        Path path = Paths.get(pathString);
        List<Path> logFilePaths = getLogFilePathsFromFileOrDir(path);
        Collections.sort(logFilePaths);
        for (Path filePath : logFilePaths) {
            processFile(filePath, displayProgressPercent, clusterId, producer);
        }
    }
    
    private static OptionSet parseOptions(String[] args) throws IOException {
        OptionParser parser = new OptionParser();
        parser.acceptsAll(Arrays.asList("h", "help"), "Prints help").forHelp();
        parser.acceptsAll(Arrays.asList("p", "path"), "Path to log file or dir containing log files").withRequiredArg().required();
        parser.acceptsAll(Arrays.asList("k", "kafka"), "Kafka bootstrap servers connection string").withRequiredArg();
        parser.acceptsAll(Arrays.asList("c", "clusterid"), "Used to overwrite the cordraClusterId in the ReplicationMessage").withRequiredArg().defaultsTo("TransactionFileReplayer");
        parser.accepts("preserve-clusterid", "If set the existing cordraClusterId will not be overwritten");
        parser.acceptsAll(Arrays.asList("s", "status"), "Counts number of lines before processing");
        parser.accepts("properties", "properties file to configure Kafka producer (overrides k argument)").withRequiredArg();
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
            System.out.println("This tool will read a directory of Cordra transactions log files and replay them into a kafka cluster for reprocessing.");
            parser.printHelpOn(System.out);
            System.exit(1);
            return null;
        }
        return options;
    }
    
    public static List<Path> getLogFilePathsFromFileOrDir(Path path) throws IOException {
        if (isPathDirectory(path)) {
            return getTxnLogFilesFromDir(path);
        } else {
            List<Path> result = new ArrayList<>();
            result.add(path);
            return result;
        }
    }
    
    public static List<Path> getTxnLogFilesFromDir(Path dir) throws IOException {
        List<Path> result = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.*")) {
            for (Path path: stream) {
                if (isLogFileName(path)) {
                    result.add(path);
                }
            }
            return result;
        }
    }
    
    public static boolean isLogFileName(Path path) {
        //e.g. YYYYMMDD.log
        // taking anything .log in case we decide to use finer granulation
        return path.getFileName().toString().matches(".*\\.log");
    }
    
    public static boolean isPathDirectory(Path path) {
        if (path == null || !Files.exists(path)) {
            return false;
        } else {
            return Files.isDirectory(path);
        }
    }

    public static void processFile(Path filenamePath, boolean displayProgressPercent, String clusterId, KafkaReplicationProducer producer)
            throws FileNotFoundException, IOException, CordraException {
        System.out.println("Processing file: " + filenamePath.getFileName());
        long totalLines = 0;
        if (displayProgressPercent) {
            totalLines = countLines(filenamePath);
        }
        int progress = 0;
        boolean modifyClusterId = (clusterId != null);
        try (BufferedReader br = Files.newBufferedReader(filenamePath)) {
            String line;
            while ((line = br.readLine()) != null) {  
                ReplicationMessage message = gson.fromJson(line, ReplicationMessage.class);
                if (modifyClusterId) {
                    line = modifyClusterId(message, clusterId);
                }
                String key = message.handle;
                producer.send(key, line);
                progress++;
                if (progress % 1000 == 0) {
                    if (displayProgressPercent) {
                        System.out.print("Processed: " + progress +  " of " + totalLines + "\r");
                    } else {
                        System.out.print("Processed: " + progress +  "\r");
                    }
                }
            }
        }
    }
    
    private static String modifyClusterId(ReplicationMessage message, String clusterId) {
        message.cordraClusterId = clusterId;
        return gson.toJson(message);
    }
 
    public static long countLines(Path filenamePath) throws FileNotFoundException, IOException {
        try (BufferedReader br = Files.newBufferedReader(filenamePath)) {
            return br.lines().count();
        }
    }
}
