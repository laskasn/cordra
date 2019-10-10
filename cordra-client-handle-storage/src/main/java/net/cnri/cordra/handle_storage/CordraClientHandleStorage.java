/*************************************************************************\
    Copyright (c) 2019 Corporation for National Research Initiatives;
                        All rights reserved.
\*************************************************************************/

package net.cnri.cordra.handle_storage;

import com.google.gson.Gson;
import net.cnri.cordra.api.*;
import net.cnri.util.StreamTable;
import net.handle.hdllib.*;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class CordraClientHandleStorage implements HandleStorage {

    private final Gson gson = GsonUtility.getGson();

    private CordraClient cordra;
    private List<String> prefixes;
    private boolean allowAllPrefixes;
    private HandleValuesGenerator handleValuesGenerator;
    private HandleMintingConfigWatcher configWatcher;

    /**
     * This method is called in handle-in-cordra.
     */
    @SuppressWarnings("hiding")
    public void init(CordraClient cordra, List<String> prefixes, boolean allowAllPrefixes) {
        this.cordra = cordra;
        this.prefixes = prefixes;
        this.allowAllPrefixes = allowAllPrefixes;
        this.handleValuesGenerator = new HandleValuesGenerator(null);
        this.configWatcher = new HandleMintingConfigWatcher(cordra, handleValuesGenerator);
        configWatcher.start();
    }

    /**
     * This method is called when Handle server is configured to use CordraClientHandleStorage
     */
    @Override
    public void init(StreamTable configTable) throws Exception {
        String serverDir = configTable.getStr("serverDir");
        String configFile = configTable.getStr("configFile");
        HandleMintingConfig config;
        if (configFile != null) {
            Path path = Paths.get(serverDir).resolve(configFile);
            String configString = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            config = gson.fromJson(configString, HandleMintingConfig.class);
        } else {
            String configString = gson.toJson(configTable);
            config = gson.fromJson(configString, HandleMintingConfig.class);
        }

        if (config.prefixes != null) {
            prefixes = config.prefixes.stream().map(this::fixPrefix).collect(Collectors.toList());
        } else if (config.prefix != null) {
            prefixes = Collections.singletonList(fixPrefix(config.prefix));
        } else {
            allowAllPrefixes = true;
        }

        String cordraBaseUri = config.cordra.baseUri;
        String username = config.cordra.username;
        String password = config.cordra.password;
        cordra = new TokenUsingHttpCordraClient(cordraBaseUri, username, password);

        if (config.baseUri == null) config.baseUri = cordraBaseUri;
        if (config.javascript != null && config.javascript.startsWith("@")) {
            String filename = config.javascript.substring(1);
            Path path = Paths.get(serverDir).resolve(filename);
            config.javascript = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        }
        handleValuesGenerator = new HandleValuesGenerator(config);
        configWatcher = new HandleMintingConfigWatcher(cordra, handleValuesGenerator);
        configWatcher.start();
    }

    private String fixPrefix(String prefix) {
        if (!prefix.contains("/")) {
            prefix = "0.NA/" + prefix;
        }
        prefix = prefix.toUpperCase(Locale.ROOT);
        return prefix;
    }

    @Override
    public boolean haveNA(byte[] authHandle) throws HandleException {
        if (allowAllPrefixes) return true;
        String input = Util.decodeString(authHandle).toUpperCase(Locale.ROOT);
        return prefixes.contains(input);
    }

    @Override
    public byte[][] getRawHandleValues(byte[] handle, int[] indexList, byte[][] typeList) throws HandleException {
        try {
            String handleString = Util.decodeString(handle);
            CordraObject cordraObject = cordra.get(handleString);
            if (cordraObject == null) return null;
            HandleValue[] values = handleValuesGenerator.generate(cordraObject);
            values = Util.filterValues(values, indexList, typeList);
            return Encoder.encodeHandleValues(values);
        } catch (CordraException e) {
            throw new HandleException(HandleException.INTERNAL_ERROR, e);
        }
    }

    @Override
    public void setHaveNA(byte[] authHandle, boolean flag) throws HandleException {
        throw new HandleException(HandleException.STORAGE_RDONLY, "Storage is read-only");
    }

    @Override
    public void createHandle(byte[] handle, HandleValue[] values) throws HandleException {
        throw new HandleException(HandleException.STORAGE_RDONLY, "Storage is read-only");
    }

    @Override
    public boolean deleteHandle(byte[] handle) throws HandleException {
        throw new HandleException(HandleException.STORAGE_RDONLY, "Storage is read-only");
    }

    @Override
    public void updateValue(byte[] handle, HandleValue[] value) throws HandleException {
        throw new HandleException(HandleException.STORAGE_RDONLY, "Storage is read-only");
    }

    @Override
    public void scanHandles(ScanCallback callback) throws HandleException {
        try (SearchResults<String> results = cordra.searchHandles("*:*")) {
            for (String result : results) {
                callback.scanHandle(Util.encodeString(result));
            }
        } catch (CordraException e) {
            throw new HandleException(HandleException.INTERNAL_ERROR, e);
        }
    }

    @Override
    public void scanNAs(ScanCallback callback) throws HandleException {
        if (!allowAllPrefixes) {
            for (String prefix : prefixes) {
                callback.scanHandle(Util.encodeString(prefix));
            }
        }
    }

    @Override
    @SuppressWarnings("resource")
    public Enumeration<byte[]> getHandlesForNA(byte[] naHdl) throws HandleException {
        try {
            SearchResults<String> results = cordra.searchHandles("id:" + Util.decodeString(naHdl) + "/*");
            return new CloseableEnumeration(results);
        } catch (CordraException e) {
            throw new HandleException(HandleException.INTERNAL_ERROR, e);
        }
    }

    @Override
    public void deleteAllRecords() throws HandleException {
        throw new HandleException(HandleException.STORAGE_RDONLY, "Storage is read-only");
    }

    @Override
    public void checkpointDatabase() throws HandleException {
        throw new HandleException(HandleException.STORAGE_RDONLY, "Storage is read-only");
    }

    @Override
    public void shutdown() {
        try {
            cordra.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (CordraException e) {
            throw new UncheckedCordraException(e);
        }
        handleValuesGenerator.shutdown();
        configWatcher.shutdown();
    }

    private static class CloseableEnumeration implements Enumeration<byte[]>, Closeable {
        final SearchResults<String> results;
        final Iterator<String> iter;

        public CloseableEnumeration(SearchResults<String> results) {
            this.results = results;
            this.iter = results.iterator();
        }

        @Override
        public boolean hasMoreElements() {
            return iter.hasNext();
        }

        @Override
        public byte[] nextElement() {
            return Util.encodeString(iter.next());
        }

        @Override
        public void close() {
            results.close();
        }
    }
}
