package net.cnri.cordra.util.cmdline;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.api.CordraObject;
import net.cnri.cordra.api.HttpCordraClient;
import net.cnri.cordra.api.SearchResults;
import org.bson.Document;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class ExportByQuery {
    private static long MAX_TIME_MS = 30_000;

    private static Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    private String baseUri;
    private String username;
    private String password;
    private String query;
    private String mongoConnectionString;

    public static void main(String[] args) throws Exception {
        new ExportByQuery().run(args);
    }

    void run(String[] args) throws Exception {
        OptionSet options = parseOptions(args);
        extractOptions(options);
        export();
    }

    private OptionSet parseOptions(String[] args) throws IOException {
        OptionParser parser = new OptionParser();
        parser.acceptsAll(Arrays.asList("h", "help")).forHelp();
        parser.acceptsAll(Arrays.asList("b", "base-uri")).withRequiredArg().required();
        parser.acceptsAll(Arrays.asList("u", "username")).withRequiredArg().required();
        parser.acceptsAll(Arrays.asList("p", "password"), "Can be entered as standard input").withRequiredArg();
        parser.acceptsAll(Arrays.asList("q", "query"), "Query string").withRequiredArg().required();
        parser.acceptsAll(Arrays.asList("m", "mongo"), "Mongo connection string; if present get internal metadata").withRequiredArg();
        OptionSet options;
        try {
            options = parser.parse(args);
        } catch (OptionException e) {
            System.out.println("Error parsing options: " + e.getMessage());
            System.out.println("This tool will export objects matching a query,");
            System.out.println("as newline-separated JSON objects.");
            parser.printHelpOn(System.out);
            System.exit(1);
            return null;
        }
        if (options.has("h")) {
            System.out.println("This tool will export objects matching a query,");
            System.out.println("as newline-separated JSON objects.");
            parser.printHelpOn(System.out);
            System.exit(1);
            return null;
        }
        return options;
    }

    private void extractOptions(OptionSet options) throws IOException {
        baseUri = (String)options.valueOf("base-uri");
        username = (String)options.valueOf("username");
        password = (String)options.valueOf("password");
        query = (String)options.valueOf("query");
        if (password == null) {
            System.out.print("Password: ");
            try (
                InputStreamReader isr = new InputStreamReader(System.in, StandardCharsets.UTF_8);
                BufferedReader reader = new BufferedReader(isr);
            ) {
                password = reader.readLine();
            }
        }
        if (options.has("mongo")) {
            mongoConnectionString = (String)options.valueOf("mongo");
        }
    }

    private void export() throws CordraException, IOException {
        try (
            MongoClient mongoClient = getMongoClient();
            HttpCordraClient cordraClient = new HttpCordraClient(baseUri, username, password)
        ) {
            MongoCollection<Document> collection = null;
            if (mongoClient != null) {
                MongoDatabase db = mongoClient.getDatabase("cordra");
                collection = db.getCollection("cordra");
            }
            try (SearchResults<CordraObject> results = cordraClient.search(query)) {
                for (CordraObject co : results) {
                    if (co.payloads != null && co.payloads.isEmpty()) co.payloads = null;
                    if (Boolean.FALSE.equals(co.metadata.isVersion)) co.metadata.isVersion = null;
                    if (collection != null) {
                        Document doc = collection.find(new Document("id", co.id)).maxTime(MAX_TIME_MS, TimeUnit.MILLISECONDS).first();
                        co.metadata.internalMetadata = getInternalMetadata(doc);
                    }
                    System.out.println(gson.toJson(co));
                }
            }
        }
    }

    private MongoClient getMongoClient() {
        if (mongoConnectionString != null) {
            return new MongoClient(new MongoClientURI(mongoConnectionString));
        } else {
            return null;
        }
    }

    private JsonObject getInternalMetadata(Document doc) {
        if (doc == null) return null;
        Object metadata = doc.get("metadata");
        if (!(metadata instanceof Document)) return null;
        Object internalMetadata = ((Document)metadata).get("internalMetadata");
        if (!(internalMetadata instanceof Document)) return null;
        String json = ((Document) internalMetadata).toJson();
        return new JsonParser().parse(json).getAsJsonObject();
    }


}
