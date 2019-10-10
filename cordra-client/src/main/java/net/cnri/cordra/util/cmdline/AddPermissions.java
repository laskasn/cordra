package net.cnri.cordra.util.cmdline;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.http.HttpEntity;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.DefaultConfiguration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import net.cnri.cordra.api.BadRequestCordraException;
import net.cnri.cordra.api.CordraClient;
import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.api.CordraObject;
import net.cnri.cordra.api.HttpCordraClient;
import net.cnri.cordra.api.InternalErrorCordraException;
import net.cnri.cordra.api.SearchResults;
import net.cnri.cordra.util.JsonUtil;

public class AddPermissions {

    private Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    private String baseUri;
    private String username;
    private String password;

    public static void main(String[] args) throws Exception {
        configureLogging();
        new AddPermissions().run(args);
    }

    private static void configureLogging() {
        try {
            Configurator.initialize(new DefaultConfiguration());
            Configurator.setRootLevel(Level.WARN);
            Configurator.setLevel("net.cnri", Level.TRACE);
        } catch (Throwable t) {
            // ignore
        }
    }

    void run(String[] args) throws Exception {
        OptionSet options = parseOptions(args);
        extractOptions(options);
        addPermissions(options);
    }

    private OptionSet parseOptions(String[] args) throws Exception {
        OptionParser parser = new OptionParser();
        parser.acceptsAll(Arrays.asList("h", "help")).forHelp();
        parser.acceptsAll(Arrays.asList("b", "base-uri")).withRequiredArg().required();
        parser.acceptsAll(Arrays.asList("u", "username"), "Username to talk to Cordra").withRequiredArg().required();
        parser.acceptsAll(Arrays.asList("p", "password"), "Can be entered as standard input").withRequiredArg();
        parser.acceptsAll(Arrays.asList("g", "group-id"), "Id of group").withRequiredArg();
        parser.acceptsAll(Arrays.asList("group-name"), "Name of group").withRequiredArg();
        parser.acceptsAll(Arrays.asList("group-name-json-pointer"), "Field to query group name (default /groupName)").withRequiredArg().defaultsTo("/groupName");
        parser.acceptsAll(Arrays.asList("s", "schema-name"), "Name of schema").withRequiredArg().required();
        parser.acceptsAll(Arrays.asList("a", "add-permission"), "Permission to add (read, write, create, read-schema, write-schema)").withRequiredArg().required();
        OptionSet options;
        try {
            options = parser.parse(args);
            if (!options.has("h")) {
                if (!options.has("group-id") && !options.has("group-name")) {
                    throw new Exception("At least one of group-id, group-name must be specified");
                }
            }
        } catch (Exception e) {
            System.out.println("Error parsing options: " + e.getMessage());
            System.out.println("This tool will add permissions for a group.");
            System.out.println("You can specify multiple groups, schemas, and permissions.");
            parser.printHelpOn(System.out);
            System.exit(1);
            return null;
        }
        if (options.has("h")) {
            System.out.println("This tool will add permissions for a group.");
            System.out.println("You can specify multiple groups, schemas, and permissions.");
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
    }

    private void addPermissions(OptionSet options) throws CordraException, IOException {
        try (HttpCordraClient cordraClient = new HttpCordraClient(baseUri, username, password)) {
            addPermissions(cordraClient, cordraClient.getHttpClient(), options);
        }
    }

    private void addPermissions(CordraClient cordraClient, CloseableHttpClient httpClient, OptionSet options) throws CordraException, IOException {
        @SuppressWarnings("unchecked")
        List<String> permissions = (List<String>)options.valuesOf("add-permission");
        boolean read = false, write = false, create = false, readSchema = false, writeSchema = false;
        for (String perm : permissions) {
            if ("read".equalsIgnoreCase(perm)) read = true;
            else if ("write".equalsIgnoreCase(perm)) write = true;
            else if ("create".equalsIgnoreCase(perm)) create = true;
            else if ("read-schema".equalsIgnoreCase(perm)) readSchema = true;
            else if ("write-schema".equalsIgnoreCase(perm)) writeSchema = true;
            else throw new BadRequestCordraException("Unknown permission " + perm);
        }

        @SuppressWarnings("unchecked")
        List<String> schemaNames = (List<String>)options.valuesOf("schema-name");

        Collection<String> groupIds = getGroups(cordraClient, options);
        if (groupIds.isEmpty()) throw new BadRequestCordraException("No groups found");

        AuthConfig authConfig = getAuthConfig(httpClient);
        if (authConfig == null) authConfig = new AuthConfig();
        DefaultAcls defaultDefault = authConfig.defaultAcls;
        if (defaultDefault == null) defaultDefault = new DefaultAcls();
        if (read || write || create) {
            for (String schemaName : schemaNames) {
                if (!authConfig.schemaAcls.containsKey(schemaName)) {
                    authConfig.schemaAcls.put(schemaName, cloneDefaultAcls(defaultDefault));
                }
                DefaultAcls theAcls = authConfig.schemaAcls.get(schemaName);
                if (read) {
                    theAcls.defaultAclRead = addToListOmitPublicOrAuthenticated(theAcls.defaultAclRead, groupIds);
                }
                if (write) {
                    theAcls.defaultAclWrite = addToListOmitPublicOrAuthenticated(theAcls.defaultAclWrite, groupIds);
                }
                if (create) {
                    theAcls.aclCreate = addToListOmitPublicOrAuthenticated(theAcls.aclCreate, groupIds);
                }
            }
            setAuthConfig(httpClient, authConfig);
        }

        if (readSchema || writeSchema) {
            DefaultAcls schemaDefaultAcls = authConfig.schemaAcls.get("Schema");
            List<String> schemaDefaultAclRead = null;
            List<String> schemaDefaultAclWrite = null;
            if (schemaDefaultAcls != null) {
                schemaDefaultAclRead = schemaDefaultAcls.defaultAclRead;
                schemaDefaultAclWrite = schemaDefaultAcls.defaultAclWrite;
            }
            if (schemaDefaultAclRead == null) {
                schemaDefaultAclRead = defaultDefault.defaultAclRead;
            }
            if (schemaDefaultAclWrite == null) {
                schemaDefaultAclWrite = defaultDefault.defaultAclWrite;
            }

            List<CordraObject> updates = new ArrayList<>();
            for (CordraObject schemaObject : getSchemaObjects(cordraClient, schemaNames)) {
                if (readSchema) {
                    if (schemaObject.acl == null) schemaObject.acl = new CordraObject.AccessControlList();
                    if (schemaObject.acl.readers == null) {
                        schemaObject.acl.readers = addToListOmitPublicOrAuthenticated(schemaDefaultAclRead, groupIds);
                    } else {
                        schemaObject.acl.readers = addToListOmitPublicOrAuthenticated(schemaObject.acl.readers, groupIds);
                    }
                }
                if (writeSchema) {
                    if (schemaObject.acl == null) schemaObject.acl = new CordraObject.AccessControlList();
                    if (schemaObject.acl.writers == null) {
                        schemaObject.acl.writers = addToListOmitPublicOrAuthenticated(schemaDefaultAclWrite, groupIds);
                    } else {
                        schemaObject.acl.writers = addToListOmitPublicOrAuthenticated(schemaObject.acl.writers, groupIds);
                    }
                }
                updates.add(schemaObject);
            }
            for (CordraObject update : updates) {
                cordraClient.update(update);
            }
        }
    }

    private static List<String> addToListOmitPublicOrAuthenticated(List<String> start, Collection<String> rest) {
        Set<String> res = new LinkedHashSet<>();
        if (start != null) res.addAll(start);
        if (rest != null && !rest.isEmpty()) {
            res.remove("authenticated");
            res.remove("public");
            res.addAll(rest);
        }
        return new ArrayList<>(res);
    }

    private static DefaultAcls cloneDefaultAcls(DefaultAcls given) {
        DefaultAcls res = new DefaultAcls();
        if (given.aclCreate != null) {
            res.aclCreate = new ArrayList<>();
            res.aclCreate.addAll(given.aclCreate);
        }
        if (given.defaultAclRead != null) {
            res.defaultAclRead = new ArrayList<>();
            res.defaultAclRead.addAll(given.defaultAclRead);
        }
        if (given.defaultAclWrite != null) {
            res.defaultAclWrite = new ArrayList<>();
            res.defaultAclWrite.addAll(given.defaultAclWrite);
        }
        return res;
    }

    private AuthConfig getAuthConfig(CloseableHttpClient httpClient) throws IOException, ClientProtocolException {
        HttpGet designGet = new HttpGet(ensureSlash(baseUri) + "design");
        try (CloseableHttpResponse resp = httpClient.execute(designGet)) {
            HttpEntity entity = resp.getEntity();
            try (
                InputStream in = entity.getContent();
                InputStreamReader isr = new InputStreamReader(in, StandardCharsets.UTF_8);
                BufferedReader reader = new BufferedReader(isr);
                JsonReader jsonReader = new JsonReader(reader);
            ) {
                jsonReader.beginObject();
                while (jsonReader.hasNext()) {
                    String name = jsonReader.nextName();
                    if ("authConfig".equals(name)) {
                        return gson.fromJson(jsonReader, AuthConfig.class);
                    } else {
                        jsonReader.skipValue();
                    }
                }
            } finally {
                EntityUtils.consumeQuietly(entity);
            }
        }
        throw new AssertionError("No authConfig found!");
    }

    private void setAuthConfig(CloseableHttpClient httpClient, AuthConfig authConfig) throws IOException, CordraException {
        HttpPut req = new HttpPut(ensureSlash(baseUri) + "authConfig");
        UsernamePasswordCredentials creds = new UsernamePasswordCredentials(username, password);
        try {
            req.addHeader(new BasicScheme().authenticate(creds, req, null));
        } catch (AuthenticationException e) {
            throw new AssertionError(e);
        }
        req.setEntity(new StringEntity(gson.toJson(authConfig), StandardCharsets.UTF_8));
        try (CloseableHttpResponse resp = httpClient.execute(req)) {
            HttpEntity entity = resp.getEntity();
            try {
                if (resp.getStatusLine().getStatusCode() != 200) {
                    throw new InternalErrorCordraException("Unable to set authConfig: " + EntityUtils.toString(entity));
                }
            } finally {
                EntityUtils.consumeQuietly(entity);
            }
        }
    }

    private static String ensureSlash(String s) {
        if (s.endsWith("/")) return s;
        else return s + "/";
    }

    private Collection<String> getGroups(CordraClient cordraClient, OptionSet options) throws CordraException {
        Set<String> res = new LinkedHashSet<>();
        if (options.has("group-id")) {
            for (Object groupIdObj : options.valuesOf("group-id")) {
                res.add((String)groupIdObj);
            }
        }
        if (options.has("group-name")) {
            @SuppressWarnings("unchecked")
            List<String> groupNames = (List<String>) options.valuesOf("group-name");
            String fieldName = (String) options.valueOf("group-name-json-pointer");
            String query = "";
            for (String groupName : groupNames) {
                query += escape(fieldName) + ":\"" + escape(groupName) + "\" ";
            }
            try (SearchResults<CordraObject> groupSearchResults = cordraClient.search(query)) {
                for (CordraObject co : groupSearchResults) {
                    String foundGroupName = JsonUtil.getJsonAtPointer(co.content, fieldName).getAsString();
                    if (groupNames.contains(foundGroupName)) {
                        res.add(co.id);
                    }
                }
            }
        }
        return res;
    }

    private Collection<CordraObject> getSchemaObjects(CordraClient cordraClient, List<String> schemaNames) throws CordraException {
        String query = "";
        for (String schemaName : schemaNames) {
            query += "schemaName:\"" + escape(schemaName) + "\" ";
        }
        List<CordraObject> res = new ArrayList<>();
        try (SearchResults<CordraObject> searchResults = cordraClient.search(query)) {
            for (CordraObject co : searchResults) {
                String foundSchemaName = JsonUtil.getJsonAtPointer(co.content, "/name").getAsString();
                if (schemaNames.contains(foundSchemaName)) {
                    res.add(co);
                }            }
        }
        return res;
    }

    /**
     * Returns a String where those characters that QueryParser
     * expects to be escaped are escaped by a preceding <code>\</code>.
     */
    public static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            // These characters are part of the query syntax and must be escaped
            if (c == '\\' || c == '+' || c == '-' || c == '!' || c == '(' || c == ')' || c == ':'
                || c == '^' || c == '[' || c == ']' || c == '\"' || c == '{' || c == '}' || c == '~'
                || c == '*' || c == '?' || c == '|' || c == '&' || c == '/') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
