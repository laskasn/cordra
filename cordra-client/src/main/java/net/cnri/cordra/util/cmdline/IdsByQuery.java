package net.cnri.cordra.util.cmdline;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.api.HttpCordraClient;
import net.cnri.cordra.api.SearchResults;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public class IdsByQuery {

    private String baseUri;
    private String username;
    private String password;
    private String query;
    private Path outputPath = null;

    public static void main(String[] args) throws Exception {
        new IdsByQuery().run(args);
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
        parser.acceptsAll(Arrays.asList("o", "output"), "Path to output file").withRequiredArg();

        OptionSet options;
        try {
            options = parser.parse(args);
        } catch (OptionException e) {
            System.out.println("Error parsing options: " + e.getMessage());
            System.out.println("This tool will export object ids matching a query,");
            parser.printHelpOn(System.out);
            System.exit(1);
            return null;
        }
        if (options.has("h")) {
            System.out.println("This tool will export object ids matching a query,");
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
        if (options.has("o")) {
            String output = (String) options.valueOf("o");
            if (!"-".equals(output)) {
                outputPath = Paths.get(output);
            }
        }
    }

    private void export() throws CordraException, IOException {
        PrintWriter pw = null;
        try {
            if (outputPath != null) {
                 pw = new PrintWriter(Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8));
            }
            try (HttpCordraClient cordraClient = new HttpCordraClient(baseUri, username, password)) {
                try (SearchResults<String> results = cordraClient.searchHandles(query)) {
                    for (String id : results) {
                        if (pw != null) {
                            pw.println(id);
                        } else {
                            System.out.println(id);
                        }
                    }
                }
            }
        } finally {
            if (pw != null) {
                pw.close();
            }
        }
    }
}
