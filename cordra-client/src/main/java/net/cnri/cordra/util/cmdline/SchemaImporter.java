package net.cnri.cordra.util.cmdline;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import net.cnri.cordra.api.BadRequestCordraException;
import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.api.CordraObject;
import net.cnri.cordra.api.HttpCordraClient;
import net.cnri.cordra.api.SearchResults;

public class SchemaImporter {

    private String baseUri;
    private String username;
    private String password;
    private String name;
    private String schema;
    private String javascript;
    private String fullObject;
    private boolean isUpdate;

    public static void main(String[] args) throws Exception {
        new SchemaImporter().run(args);
    }

    void run(String[] args) throws Exception {
        OptionSet options = parseOptions(args);
        extractOptions(options);
        JsonObject schemaInstanceJsonObject = buildSchemaInstanceJsonObject();
        if (isUpdate) {
            updateSchemaOnCordra(schemaInstanceJsonObject);
        } else {
            createSchemaOnCordra(schemaInstanceJsonObject);
        }
        System.out.println("Success");
    }

    private OptionSet parseOptions(String[] args) throws IOException {
        OptionParser parser = new OptionParser();
        parser.acceptsAll(Arrays.asList("h", "help")).forHelp();
        parser.acceptsAll(Arrays.asList("b", "base-uri")).withRequiredArg().required();
        parser.acceptsAll(Arrays.asList("u", "username")).withRequiredArg().required();
        parser.acceptsAll(Arrays.asList("p", "password"), "Can be entered as standard input").withRequiredArg();
        parser.acceptsAll(Arrays.asList("o", "object"), "Object file (contains name, schema, optional JavaScript)").withRequiredArg();
        parser.acceptsAll(Arrays.asList("n", "name"), "Name of schema").requiredUnless("o").withRequiredArg();
        parser.acceptsAll(Arrays.asList("s", "schema"), "JSON schema file").requiredUnless("o").withRequiredArg();
        parser.acceptsAll(Arrays.asList("j", "javascript"), "Enrichment JavaScript file, optional").withRequiredArg();
        parser.acceptsAll(Arrays.asList("update"), "Update existing schema");
        OptionSet options;
        try {
            options = parser.parse(args);
            if (!options.has("h") && options.has("o")) {
                if (options.has("n") || options.has("s") || options.has("j")) {
                    throw new Exception("o option disallows n, s, j options");
                }
            }
        } catch (Exception e) {
            System.out.println("Error parsing options: " + e.getMessage());
            parser.printHelpOn(System.out);
            System.exit(1);
            return null;
        }
        if (options.has("h")) {
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
        if (options.has("o")) {
            String objectFilename = (String)options.valueOf("o");
            try {
                fullObject = new String(Files.readAllBytes(Paths.get(objectFilename)), StandardCharsets.UTF_8);
            } catch (Exception e) {
                System.err.println("Exception reading object file: " + objectFilename);
                throw e;
            }
            return;
        }
        name = (String)options.valueOf("name");
        String schemaFilename = (String)options.valueOf("schema");
        String javascriptFilename = (String)options.valueOf("javascript");
        try {
            schema = new String(Files.readAllBytes(Paths.get(schemaFilename)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("Exception reading schema file: " + schemaFilename);
            throw e;
        }
        javascript = null;
        if (javascriptFilename != null) {
            try {
                javascript = new String(Files.readAllBytes(Paths.get(javascriptFilename)), StandardCharsets.UTF_8);
            } catch (Exception e) {
                System.err.println("Exception reading javascript file: " + javascriptFilename);
                throw e;
            }
        }
        isUpdate = options.has("update");
    }

    private JsonObject buildSchemaInstanceJsonObject() {
        if (fullObject != null) {
            JsonObject fullJsonObject = new JsonParser().parse(fullObject).getAsJsonObject();
            if (fullJsonObject.has("content")) return fullJsonObject.get("content").getAsJsonObject();
            return fullJsonObject;
        }
        JsonObject schemaInstanceJsonObject = new JsonObject();
        schemaInstanceJsonObject.addProperty("identifier", "");
        schemaInstanceJsonObject.addProperty("name", name);
        schemaInstanceJsonObject.add("schema", new JsonParser().parse(schema));
        if (javascript != null) {
            schemaInstanceJsonObject.addProperty("javascript", javascript);
        }
        return schemaInstanceJsonObject;
    }

    private void createSchemaOnCordra(JsonObject schemaInstanceJsonObject) throws CordraException, IOException {
        try (HttpCordraClient cordraClient = new HttpCordraClient(baseUri, username, password)) {
            cordraClient.create("Schema", schemaInstanceJsonObject);
        }
    }

    private void updateSchemaOnCordra(JsonObject schemaInstanceJsonObject) throws CordraException, IOException {
        try (
            HttpCordraClient cordraClient = new HttpCordraClient(baseUri, username, password);
            SearchResults<CordraObject> searchResults = cordraClient.search("+type:Schema +/name:\"" + name + "\"");
        ) {
            for (CordraObject co : searchResults) {
                if (name.equals(co.content.getAsJsonObject().get("name").getAsString())) {
                    cordraClient.update(co.id, schemaInstanceJsonObject);
                    return;
                }
            }
            throw new BadRequestCordraException("Could not find existing schema named " + name);
        }
    }
}
