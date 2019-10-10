package net.cnri.cordra.util.cmdline;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.DefaultConfiguration;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import net.cnri.cordra.api.BadRequestCordraException;
import net.cnri.cordra.api.CordraClient;
import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.api.CordraObject;
import net.cnri.cordra.api.HttpCordraClient;
import net.cnri.cordra.api.SearchResults;
import net.cnri.cordra.util.JsonUtil;

public class AddUser {

    private String baseUri;
    private String username;
    private String password;
    private boolean isRemove;

    public static void main(String[] args) throws Exception {
        configureLogging();
        new AddUser().run(args);
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
        addUsers(options);
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
        parser.acceptsAll(Arrays.asList("group-users-json-pointer"), "JSON pointer to group members (default /users)").withRequiredArg().defaultsTo("/users");
        parser.acceptsAll(Arrays.asList("username-json-pointer"), "JSON pointer to username (default /username)").withRequiredArg().defaultsTo("/username");
        parser.acceptsAll(Arrays.asList("a", "add-user-id"), "Id of user to add (allows multiple)").withRequiredArg();
        parser.acceptsAll(Arrays.asList("add-username"), "Username of user to add (allows multiple)").withRequiredArg();
        parser.acceptsAll(Arrays.asList("f", "add-user-id-file"), "File of newline-separated ids of users to add (- for stdin)").withRequiredArg();
        parser.acceptsAll(Arrays.asList("add-username-file"), "File of newline-separated usernames of users to add (- for stdin)").withRequiredArg();
        parser.acceptsAll(Arrays.asList("remove"), "Remove instead of adding");
        OptionSet options;
        try {
            options = parser.parse(args);
            if (!options.has("h")) {
                if ((options.has("group-id") && options.has("group-name")) ||
                    (!options.has("group-id") && !options.has("group-name"))) {
                    throw new Exception("Exactly one of group-id, group-name must be specified");
                }
            }
        } catch (Exception e) {
            System.out.println("Error parsing options: " + e.getMessage());
            System.out.println("This tool will add users to a group.");
            parser.printHelpOn(System.out);
            System.exit(1);
            return null;
        }
        if (options.has("h")) {
            System.out.println("This tool will add users to a group.");
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
        isRemove = options.has("remove");
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

    private void addUsers(OptionSet options) throws CordraException, IOException {
        try (HttpCordraClient cordraClient = new HttpCordraClient(baseUri, username, password)) {
            addUsers(cordraClient, options);
        }
    }

    private void addUsers(CordraClient cordraClient, OptionSet options) throws CordraException, IOException {
        String usernameJsonPointer = (String) options.valueOf("username-json-pointer");
        CordraObject groupObj = getGroup(cordraClient, options);
        String usersPointer = (String) options.valueOf("group-users-json-pointer");
        JsonElement existingUsersElem = JsonUtil.getJsonAtPointer(groupObj.content, usersPointer);
        JsonArray existingUsers;
        if (existingUsersElem == null) {
            existingUsers = new JsonArray();
            JsonUtil.setJsonAtPointer(groupObj.content, usersPointer, existingUsers);
        } else {
            existingUsers = existingUsersElem.getAsJsonArray();
        }
        Set<String> existingUsersSet = new HashSet<>();
        for (JsonElement el : existingUsers) {
            existingUsersSet.add(el.getAsString());
        }
        if (options.has("add-user-id")) {
            for (Object userIdObj : options.valuesOf("add-user-id")) {
                addUserId(existingUsers, existingUsersSet, (String) userIdObj);
            }
        }
        if (options.has("add-username")) {
            for (Object usernameObj : options.valuesOf("add-username")) {
                addUsername(cordraClient, usernameJsonPointer, existingUsers, existingUsersSet, (String) usernameObj);
            }
        }
        if (options.has("add-user-id-file")) {
            String addUserIdFile = (String) options.valueOf("add-user-id-file");
            try (
                InputStream in = (addUserIdFile.equals("-")) ? System.in : Files.newInputStream(Paths.get(addUserIdFile));
                InputStreamReader isr = new InputStreamReader(in, StandardCharsets.UTF_8);
                BufferedReader reader = new BufferedReader(isr);
            ) {
                String userId;
                while ((userId = reader.readLine()) != null) {
                    userId = userId.trim();
                    if (userId.isEmpty()) continue;
                    addUserId(existingUsers, existingUsersSet, userId);
                }
            }
        }
        if (options.has("add-username-file")) {
            String addUsernameFile = (String) options.valueOf("add-username-file");
            try (
                InputStream in = (addUsernameFile.equals("-")) ? System.in : Files.newInputStream(Paths.get(addUsernameFile));
                InputStreamReader isr = new InputStreamReader(in, StandardCharsets.UTF_8);
                BufferedReader reader = new BufferedReader(isr);
            ) {
                String addUsername;
                while ((addUsername = reader.readLine()) != null) {
                    addUsername = addUsername.trim();
                    if (addUsername.isEmpty()) continue;
                    addUsername(cordraClient, usernameJsonPointer, existingUsers, existingUsersSet, addUsername);
                }
            }
        }
        cordraClient.update(groupObj.id, groupObj.content);
    }

    private void addUsername(CordraClient cordraClient, String usernameJsonPointer, JsonArray existingUsers, Set<String> existingUsersSet, String addUsername) throws CordraException {
        String userId = getIdOfUser(cordraClient, addUsername, usernameJsonPointer);
        if (userId == null) {
            System.err.println("Could not find user id for " + addUsername);
        } else {
            if (isRemove) {
                if (existingUsersSet.remove(userId)) {
                    existingUsers.remove(new JsonPrimitive(userId));
                    System.out.println("Removing " + userId + " (" + addUsername + ")");
                }
            } else if (existingUsersSet.add(userId)) {
                existingUsers.add(new JsonPrimitive(userId));
                System.out.println("Adding " + userId + " (" + addUsername + ")");
            }
        }
    }

    private void addUserId(JsonArray existingUsers, Set<String> existingUsersSet, String userId) {
        if (isRemove) {
            if (existingUsersSet.remove(userId)) {
                existingUsers.remove(new JsonPrimitive(userId));
                System.out.println("Removing " + userId);
            }
        } else if (existingUsersSet.add(userId)) {
            existingUsers.add(new JsonPrimitive(userId));
            System.out.println("Adding " + userId);
        }
    }

    private String getIdOfUser(CordraClient cordraClient, String addUsername, String usernameJsonPointer) throws CordraException {
        String query = "username:\"" + escape(addUsername) + "\"";
        try (SearchResults<CordraObject> usernameSearchResults = cordraClient.search(query)) {
            for (CordraObject co : usernameSearchResults) {
                String foundUsername = JsonUtil.getJsonAtPointer(co.content, usernameJsonPointer).getAsString();
                if (addUsername.equals(foundUsername)) {
                    return co.id;
                }
            }
        }
        return null;
    }

    private CordraObject getGroup(CordraClient cordraClient, OptionSet options) throws CordraException, BadRequestCordraException {
        CordraObject groupObj = null;
        if (options.has("group-id")) {
            String groupId = (String) options.valueOf("group-id");
            groupObj = cordraClient.get(groupId);
        } else {
            String groupName = (String) options.valueOf("group-name");
            String fieldName = (String) options.valueOf("group-name-json-pointer");
            String query = escape(fieldName) + ":\"" + escape(groupName) + "\"";
            try (SearchResults<CordraObject> groupSearchResults = cordraClient.search(query)) {
                for (CordraObject co : groupSearchResults) {
                    String foundGroupName = JsonUtil.getJsonAtPointer(co.content, fieldName).getAsString();
                    if (groupName.equals(foundGroupName)) {
                        groupObj = co;
                        break;
                    }
                }
            }
        }
        if (groupObj == null) {
            throw new BadRequestCordraException("Could not find group");
        }
        return groupObj;
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
