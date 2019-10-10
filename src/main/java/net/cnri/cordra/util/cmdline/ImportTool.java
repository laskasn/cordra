/*************************************************************************\
    Copyright (c) 2019 Corporation for National Research Initiatives;
                        All rights reserved.
\*************************************************************************/

package net.cnri.cordra.util.cmdline;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import net.cnri.cordra.CordraConfigSource;
import net.cnri.cordra.CordraServiceFactory;
import net.cnri.cordra.GsonUtility;
import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.api.UncheckedCordraException;
import net.cnri.cordra.model.CordraConfig;
import net.cnri.cordra.replication.kafka.CordraObjectWithPayloadsAsStrings;
import net.cnri.cordra.storage.CordraStorage;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;

public class ImportTool {

    private CordraStorage storage;
    private Path inputDir;
    private static Gson gson = GsonUtility.getPrettyGson();
    private long count = 0;
    private final boolean progress;

    public ImportTool(CordraConfig config, Path basePath, Path inputDir, boolean progress) throws IOException, CordraException {
        storage = CordraServiceFactory.getStorage(config, basePath, false);
        this.inputDir = inputDir;
        this.progress = progress;
    }

    public void run() throws IOException, CordraException {
        if (inputDir != null) {
            Files.walk(inputDir)
                    .filter(Files::isRegularFile)
                    .forEach(this::importPath);
            if (count == 1) {
                System.out.println("Imported " + count + " object.");
            } else {
                System.out.println("Imported " + count + " objects.");
            }
        } else {
            processInputFromStandardIn();
        }
    }

    private void processInputFromStandardIn() throws CordraException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line;
        while ((line = br.readLine()) != null) {
            CordraObjectWithPayloadsAsStrings cos = gson.fromJson(line, CordraObjectWithPayloadsAsStrings.class);
            cos.writeIntoStorage(storage);
        }
    }

    private void importPath(Path p) {
        try (BufferedReader br = Files.newBufferedReader(p); ) {
            CordraObjectWithPayloadsAsStrings cos = gson.fromJson(br, CordraObjectWithPayloadsAsStrings.class);
            cos.writeIntoStorage(storage);
            count++;
            if (progress) System.out.print("Progress: " + count + " \r");
        } catch (Exception e) {
            throw new UncheckedCordraException(new CordraException(e));
        }
    }

    public static void main(String[] args) throws IOException, CordraException {
        OptionSet options = parseOptions(args);
        String config = (String) options.valueOf("c");
        Path basePath = null;
        if (options.has("d")) {
            String basePathString = (String) options.valueOf("d");
            basePath = Paths.get(basePathString);
        }
        gson = new GsonBuilder().setPrettyPrinting().create();
        CordraConfig cordraConfig = gson.fromJson(Files.newBufferedReader(Paths.get(config)), CordraConfig.class);
        cordraConfig = CordraConfigSource.getDefaultConfigIfNull(cordraConfig);
        String storageModule = cordraConfig.storage.module;
        boolean needsDataDirectory = ("hds".equals(storageModule) || "bdbje".equals(storageModule));
        if (needsDataDirectory && basePath == null) {
            System.out.println("Error. To use this config.json you need to specify the /data directory with the -d option");
            System.exit(1);
        }
        Path inputDir = null;
        if (options.has("i")) {
            String input = (String) options.valueOf("i");
            inputDir = Paths.get(input);
        }
        ImportTool importTool = new ImportTool(cordraConfig, basePath, inputDir, options.has("progress"));
        importTool.run();
    }

    private static OptionSet parseOptions(String[] args) throws IOException {
        OptionParser parser = new OptionParser();
        parser.acceptsAll(Arrays.asList("h", "help"), "Prints help").forHelp();
        parser.acceptsAll(Arrays.asList("c", "config"), "Path Cordra's config.json").withRequiredArg().required();
        parser.acceptsAll(Arrays.asList("i", "input"), "Path to input directory").withRequiredArg();
        parser.acceptsAll(Arrays.asList("s"), "Read input as newline sparated json from stdin");
        parser.acceptsAll(Arrays.asList("d", "data"), "Path to cordra data directory, necessary if using bdbje storage").withRequiredArg();
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
            System.out.println("This tool connects directly to a Cordra storage backend and imports full Cordra objects from the specified directory," +
                    "includes payloads and internal metadata.");
            parser.printHelpOn(System.out);
            System.exit(1);
            return null;
        }
        if (!options.has("s") && !options.has("i")) {
            System.out.println("Error. Either the 's' or 'i' option must be provided");
            parser.printHelpOn(System.out);
            System.exit(1);
        }
        return options;
    }
}
