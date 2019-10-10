package net.cnri.cordra.util.cmdline;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Updates;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.api.CordraObject;
import net.cnri.cordra.api.HttpCordraClient;
import net.cnri.cordra.util.JsonUtil;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class ImportObjects {
    private static long MAX_TIME_MS = 30_000;

    private static Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    private String baseUri;
    private String username;
    private String password;
    private String mongoConnectionString;
    private String file = "-";
    private boolean generatePassword;
    private String passwordPointer;

    public static void main(String[] args) throws Exception {
        new ImportObjects().run(args);
    }

    void run(String[] args) throws Exception {
        OptionSet options = parseOptions(args);
        extractOptions(options);
        importObjects();
    }

    private OptionSet parseOptions(String[] args) throws IOException {
        OptionParser parser = new OptionParser();
        parser.acceptsAll(Arrays.asList("h", "help")).forHelp();
        parser.acceptsAll(Arrays.asList("b", "base-uri")).withRequiredArg().required();
        parser.acceptsAll(Arrays.asList("u", "username")).withRequiredArg().required();
        parser.acceptsAll(Arrays.asList("p", "password"), "Can be entered as standard input").withRequiredArg();
        parser.acceptsAll(Arrays.asList("g", "generate-passwords"), "Send acceptable non-blank password to Cordra (use with --mongo to store hash and salt)");
        parser.acceptsAll(Arrays.asList("password-json-pointer"), "JSON Pointer to password field for use with -g (default /password)").withRequiredArg().defaultsTo("/password");
        parser.acceptsAll(Arrays.asList("m", "mongo"), "Mongo connection string; if present store hash and salt").withRequiredArg();
        parser.acceptsAll(Arrays.asList("f", "file"), "File to import from; - is standard input").withRequiredArg().required();
        OptionSet options;
        try {
            options = parser.parse(args);
        } catch (OptionException e) {
            System.out.println("Error parsing options: " + e.getMessage());
            System.out.println("This tool will import objects given");
            System.out.println("as newline-separated JSON objects.");
            parser.printHelpOn(System.out);
            System.exit(1);
            return null;
        }
        if (options.has("h")) {
            System.out.println("This tool will import objects given");
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
        if (options.has("file")) {
            file = (String)options.valueOf("file");
        }
        generatePassword = options.has("mongo") && options.has("g");
        passwordPointer = (String) options.valueOf("password-json-pointer");
    }

    private void importObjects() throws CordraException, IOException {
        try (
            InputStream in = "-".equals(file) ? System.in : Files.newInputStream(Paths.get(file));
            InputStreamReader isr = new InputStreamReader(in, StandardCharsets.UTF_8);
            BufferedReader reader = new BufferedReader(isr);
            MongoClient mongoClient = getMongoClient();
            HttpCordraClient cordraClient = new HttpCordraClient(baseUri, username, password)
        ) {
            MongoCollection<Document> collection = null;
            if (mongoClient != null) {
                MongoDatabase db = mongoClient.getDatabase("cordra");
                collection = db.getCollection("cordra");
            }
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                CordraObject co = gson.fromJson(line, CordraObject.class);
                try {
                    JsonObject internalMetadata = getInternalMetadata(co);
                    if (generatePassword && isPasswordSet(internalMetadata)) {
                        JsonUtil.setJsonAtPointer(co.content, passwordPointer, new JsonPrimitive(randomPassword()));
                    }
                    CordraObject outCo = cordraClient.create(co);
                    if (outCo.payloads != null && outCo.payloads.isEmpty()) outCo.payloads = null;
                    if (Boolean.FALSE.equals(outCo.metadata.isVersion)) outCo.metadata.isVersion = null;
                    if (collection != null && internalMetadata != null) {
                        Bson update = Updates.set("metadata.internalMetadata", Document.parse(gson.toJson(internalMetadata)));
                        collection.findOneAndUpdate(new Document("id", outCo.id), update, new FindOneAndUpdateOptions().maxTime(MAX_TIME_MS, TimeUnit.MILLISECONDS));
                    }
                    System.out.println(gson.toJson(outCo));
                } catch (Exception e) {
                    e.printStackTrace(System.out);
                }
            }
        }
    }

    private String randomPassword() {
        return UUID.randomUUID().toString();
    }

    private MongoClient getMongoClient() {
        if (mongoConnectionString != null) {
            return new MongoClient(new MongoClientURI(mongoConnectionString));
        } else {
            return null;
        }
    }

    private JsonObject getInternalMetadata(CordraObject co) {
        if (co.metadata == null) return null;
        return co.metadata.internalMetadata;
    }

    private boolean isPasswordSet(JsonObject internalMetadata) {
        if (internalMetadata == null) return false;
        return internalMetadata.get("hash") != null;
    }

}
