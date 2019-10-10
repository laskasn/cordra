/*************************************************************************\
    Copyright (c) 2019 Corporation for National Research Initiatives;
                        All rights reserved.
\*************************************************************************/

package net.cnri.cordra.util.cmdline;

import com.google.gson.Gson;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import net.cnri.cordra.CordraConfigSource;
import net.cnri.cordra.CordraServiceFactory;
import net.cnri.cordra.GsonUtility;
import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.api.CordraObject;
import net.cnri.cordra.api.SearchResults;
import net.cnri.cordra.model.CordraConfig;
import net.cnri.cordra.replication.kafka.CordraObjectWithPayloadsAsStrings;
import net.cnri.cordra.storage.CordraStorage;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class ExportTool {

    private CordraStorage storage;
    private Path outputDir;
    private boolean hashDirs;
    private long count = 0;
    private List<String> ids;
    private final boolean progress;

    public ExportTool(CordraConfig config, Path basePath, Path outputDir, boolean hashDirs, List<String> ids, boolean progress) throws IOException, CordraException {
        storage = CordraServiceFactory.getStorage(config, basePath, false);
        this.outputDir = outputDir;
        this.hashDirs = hashDirs;
        this.ids = ids;
        this.progress = progress;
    }

    public void run() throws CordraException, IOException {
        if (outputDir != null) {
            if (ids == null) {
                writeAllObjectsToDirectory();
            } else {
                writeLimitedObjectsToDirectory(ids);
            }
            if (count == 1) {
                System.out.println("Exported " + count + " object.");
            } else {
                System.out.println("Exported " + count + " objects.");
            }
        } else {
            if (ids == null) {
                writeAllObjectsToToStandardOut();
            } else {
                writeLimitedObjectsToStandardOut(ids);
            }
            System.out.close();
        }
    }

    private void writeAllObjectsToDirectory() throws CordraException, IOException {
        try (SearchResults<CordraObject> results = storage.list()) {
            writeResultsToDirectory(results);
        }
    }

    private void writeLimitedObjectsToDirectory(List<String> limitedIds) throws CordraException, IOException {
        try (SearchResults<CordraObject> results = storage.get(limitedIds)) {
            writeResultsToDirectory(results);
        }
    }

    private void writeAllObjectsToToStandardOut() throws CordraException, IOException {
        try (SearchResults<CordraObject> results = storage.list()) {
            writeResultsToStandardOut(results);
        }
        System.out.close();
    }

    private void writeLimitedObjectsToStandardOut(List<String> limitedIds) throws CordraException, IOException {
        try (SearchResults<CordraObject> results = storage.get(limitedIds)) {
            writeResultsToStandardOut(results);
        }
    }

    private void writeResultsToDirectory(SearchResults<CordraObject> results) throws IOException, CordraException {
        Gson gson = GsonUtility.getPrettyGson();
        for (CordraObject co : results) {
            CordraObjectWithPayloadsAsStrings cos = CordraObjectWithPayloadsAsStrings.fromCordraObject(co, storage);
            String filename = HashDirectory.convertToFileName(co.id);
            Path path;
            if (hashDirs) {
                String hashPath = HashDirectory.hashPathFor(co.id, 6, 3);
                Path hashDir = outputDir.resolve(hashPath);
                Files.createDirectories(hashDir);
                path = hashDir.resolve(filename);
            } else {
                path = outputDir.resolve(filename);
            }
            try (BufferedWriter w = Files.newBufferedWriter(path)) {
                gson.toJson(cos, w);
            }
            count++;
            if (progress) System.out.print("Progress: " + count + " \r");
        }
    }

    private void writeResultsToStandardOut(SearchResults<CordraObject> results) throws CordraException, IOException {
        Gson gson = GsonUtility.getGson();
        for (CordraObject co : results) {
            CordraObjectWithPayloadsAsStrings cos = CordraObjectWithPayloadsAsStrings.fromCordraObject(co, storage);
            gson.toJson(cos, System.out);
            System.out.println();
            count++;
        }
    }

    public static void main(String[] args) throws Exception {
        OptionSet options = parseOptions(args);
        String config = (String) options.valueOf("c");
        Path basePath = null;
        if (options.has("d")) {
            String basePathString = (String) options.valueOf("d");
            basePath = Paths.get(basePathString);
        }
        boolean hashDirs = options.has("t");
        Gson gson = GsonUtility.getPrettyGson();
        CordraConfig cordraConfig = gson.fromJson(Files.newBufferedReader(Paths.get(config)), CordraConfig.class);
        cordraConfig = CordraConfigSource.getDefaultConfigIfNull(cordraConfig);
        String storageModule = cordraConfig.storage.module;
        boolean needsDataDirectory = ("hds".equals(storageModule) || "bdbje".equals(storageModule));
        if (needsDataDirectory && basePath == null) {
            System.out.println("Error. To use this config.json you need to specify the /data directory with the -d option");
            System.exit(1);
        }
        Path outputDir = null;
        if (options.has("o")) {
            String output = (String) options.valueOf("o");
            outputDir = Paths.get(output);
            Files.createDirectories(outputDir);
        }
        List<String> ids = null;
        if (options.has("l")) {
            Path idsPath = Paths.get((String)options.valueOf("l"));
            ids = Files.readAllLines(idsPath);
        }
        if (options.has("i")) {
            @SuppressWarnings("unchecked")
            List<String> idsFromArgs = (List<String>) options.valuesOf("i");
            if (ids != null) {
                ids.addAll(idsFromArgs);
            } else {
                ids = idsFromArgs;
            }
        }
        ExportTool exportTool = new ExportTool(cordraConfig, basePath, outputDir, hashDirs, ids, options.has("progress"));
        exportTool.run();
    }

    private static OptionSet parseOptions(String[] args) throws IOException {
        OptionParser parser = new OptionParser();
        parser.acceptsAll(Arrays.asList("h", "help"), "Prints help").forHelp();
        parser.acceptsAll(Arrays.asList("c", "config"), "Path Cordra's config.json").withRequiredArg().required();
        parser.acceptsAll(Arrays.asList("o", "output"), "Path to output directory").withRequiredArg();
        parser.acceptsAll(Arrays.asList("s", "stdout"), "Write output as newline sparated json to stdout");
        parser.acceptsAll(Arrays.asList("d", "data"), "Path to cordra data directory, necessary if using bdbje storage").withRequiredArg();
        parser.acceptsAll(Arrays.asList("t", "tree"), "Splits directories into a tree based on the hash of the object id").withRequiredArg();
        parser.acceptsAll(Arrays.asList("i", "id"), "Id of object to export. Multiple can be used").withRequiredArg();
        parser.acceptsAll(Arrays.asList("l", "ids"), "Path to a file containing a newline separated list of object ids to export").withRequiredArg();
        parser.acceptsAll(Arrays.asList("P", "progress"), "Show progress");
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
            System.out.println("This tool connects directly to a Cordra storage backend and exports full Cordra objects," +
                    "includes payloads and internal metadata.");
            parser.printHelpOn(System.out);
            System.exit(1);
            return null;
        }
        if (!options.has("s") && !options.has("o")) {
            System.out.println("Error. Either the 's' or 'o' option must be provided");
            parser.printHelpOn(System.out);
            System.exit(1);
        }
        return options;
    }
}
