package net.cnri.cordra.util.cmdline;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.bson.Document;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

public class ListObjectIdsFromMongoDbTool {

    private static final Document ID_PROJECTION = new Document().append("id", 1).append("_id", 0);

    public static void main(String[] args) throws Exception {
        OptionSet options = parseOptions(args);
        String outFile = (String) options.valueOf("o");
        boolean stdout = "-".equals(outFile);
        String queryJson = (String) options.valueOf("q");
        String connectionUri = (String) options.valueOf("c");
        Document query = Document.parse(queryJson);
        int count = 0;
        try (
            MongoClient mongoClient = new MongoClient(new MongoClientURI(connectionUri));
        ) {
            MongoDatabase db = mongoClient.getDatabase("cordra");
            MongoCollection<Document> collection = db.getCollection("cordra");
            FindIterable<Document> findIter = collection.find(query).projection(ID_PROJECTION);
            try (
                MongoCursor<Document> cursor = findIter.iterator();
                PrintWriter writer = getOutput(outFile);
            ) {
                while (cursor.hasNext()) {
                    writer.println(cursor.next().getString("id"));
                    count++;
                    if (!stdout) {
                        System.out.print("Count: " + count +  "\r");
                    }
                }
            }
        }
        if (!stdout) {
            System.out.println("Success. " + count + " ids written to " + outFile);
        }
    }

    private static PrintWriter getOutput(String outFile) throws IOException {
        if ("-".equals(outFile)) return new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8)));
        return new PrintWriter(Files.newBufferedWriter(Paths.get(outFile)));
    }

    private static OptionSet parseOptions(String[] args) throws IOException {
        String queryJsonDefault = "{ 'metadata.createdOn': { '$gt': 0000000000000 } }".replace("'", "\"");

        OptionParser parser = new OptionParser();
        parser.acceptsAll(Arrays.asList("h", "help"), "Prints help").forHelp();
        parser.acceptsAll(Arrays.asList("o", "output"), "Path to output file (- for stdout)").withRequiredArg().required();
        parser.acceptsAll(Arrays.asList("c", "connection-uri"), "MongoDb connection uri").withRequiredArg().defaultsTo("mongodb://localhost:27017");
        parser.acceptsAll(Arrays.asList("q", "query"), "MongoDb JSON query object").withRequiredArg().defaultsTo(queryJsonDefault);

        OptionSet options;
        try {
            options = parser.parse(args);
        } catch (OptionException e) {
            System.out.println("Error parsing options: " + e.getMessage());
            parser.printHelpOn(System.out);
            System.exit(1);
            return null;
        }
        if (options.has("h")) {
            System.out.println("This tool makes a connection to a MongoDb service defined in the connection-uri. It runs the supplied query and stores the object ids in the specified output file.");
            parser.printHelpOn(System.out);
            System.exit(1);
            return null;
        }
        return options;
    }
}
