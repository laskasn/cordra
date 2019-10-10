package net.cnri.cordra.util.cmdline;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.api.HttpCordraClient;
import net.cnri.cordra.api.SearchResults;

public class DeleteByQuery {

    private String baseUri;
    private String username;
    private String password;
    private String query;
    private int nThreads = 10;

    public static void main(String[] args) throws Exception {
        new DeleteByQuery().run(args);
    }

    void run(String[] args) throws Exception {
        OptionSet options = parseOptions(args);
        extractOptions(options);
        deleteObjects();
    }

    private OptionSet parseOptions(String[] args) throws IOException {
        OptionParser parser = new OptionParser();
        parser.acceptsAll(Arrays.asList("h", "help")).forHelp();
        parser.acceptsAll(Arrays.asList("b", "base-uri")).withRequiredArg().required();
        parser.acceptsAll(Arrays.asList("u", "username")).withRequiredArg().required();
        parser.acceptsAll(Arrays.asList("p", "password"), "Can be entered as standard input").withRequiredArg();
        parser.acceptsAll(Arrays.asList("q", "query"), "Query string").withRequiredArg().required();
        parser.acceptsAll(Arrays.asList("n", "num-threads")).withRequiredArg();
        OptionSet options;
        try {
            options = parser.parse(args);
        } catch (OptionException e) {
            System.out.println("Error parsing options: " + e.getMessage());
            System.out.println("This tool will delete all objects matching a given query.");
            System.out.println("WARNING: There is no undo.  Check the results of your query first.");
            parser.printHelpOn(System.out);
            System.exit(1);
            return null;
        }
        if (options.has("h")) {
            System.out.println("This tool will delete all objects matching a given query.");
            System.out.println("WARNING: There is no undo.  Check the results of your query first.");
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
        query = (String)options.valueOf("query");
        if (options.has("num-threads")) {
            nThreads = Integer.parseInt((String)options.valueOf("num-threads"));
        }
    }

    private void deleteObjects() throws CordraException, IOException, InterruptedException {
        ExecutorService execServ = Executors.newFixedThreadPool(nThreads);
        AtomicInteger counter = new AtomicInteger();
        try (
            HttpCordraClient cordraClient = new HttpCordraClient(baseUri, username, password);
            SearchResults<String> results = cordraClient.searchHandles(query);
        ) {
            for (String handle : results) {
                execServ.submit(() -> {
                    try {
                        cordraClient.delete(handle);
                    } catch (Exception e) {
                        System.err.println("Error deleting " + handle);
                        e.printStackTrace();
                    } finally {
                        int completed = counter.incrementAndGet();
                        if (completed % 1000 == 0) {
                            System.out.println("Deleted " + completed);
                        }
                    }
                });
            }
            execServ.shutdown();
            execServ.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        }
        System.out.println("Deleted " + counter.get() + " objects");
    }
}
