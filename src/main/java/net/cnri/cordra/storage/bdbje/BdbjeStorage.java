package net.cnri.cordra.storage.bdbje;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;

import com.google.gson.JsonObject;
import net.cnri.cordra.Constants;
import net.cnri.cordra.util.cmdline.HashDirectory;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import net.cnri.cordra.api.ConflictCordraException;
import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.api.CordraObject;
import net.cnri.cordra.api.NotFoundCordraException;
import net.cnri.cordra.api.Payload;
import net.cnri.cordra.api.SearchResults;
import net.cnri.cordra.collections.AbstractSearchResults;
import net.cnri.cordra.collections.PersistentMap;
import net.cnri.cordra.storage.CordraStorage;
import net.cnri.cordra.storage.LimitedInputStream;

public class BdbjeStorage implements CordraStorage {
    private PersistentMap<String, CordraObject> objects;
    private File payloadsDir;

    public BdbjeStorage(File baseDir) {
        initialize(baseDir);
    }

    public BdbjeStorage(@SuppressWarnings("unused") JsonObject options) throws IOException {
        Path basePath = getBasePathFromSystemProperty();
        Path storagePath = basePath.resolve("cordraStorage");
        Files.createDirectories(storagePath);
        initialize(storagePath.toFile());
    }

    private void initialize(File dir) {
        this.payloadsDir = new File(dir, "payloads");
        payloadsDir.mkdirs();
        objects = new PersistentMap<>(dir, "objects", String.class, CordraObject.class);
    }

    private static Path getBasePathFromSystemProperty() {
        String cordraDataPath = System.getProperty(Constants.CORDRA_DATA);
        if (cordraDataPath == null) {
            cordraDataPath = "data";
        }
        return Paths.get(cordraDataPath);
    }

    @Override
    public CordraObject get(String id) throws CordraException {
        return objects.get(id);
    }

    @Override
    public InputStream getPayload(String id, String payloadName) throws CordraException {
        File objDir = getObjectDir(id);
        File payloadFile = new File(objDir, HashDirectory.convertToFileName(payloadName));
        try {
            return new FileInputStream(payloadFile);
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    @Override
    @SuppressWarnings("null")
    public InputStream getPartialPayload(String id, String payloadName, Long start, Long end) throws CordraException {
        File objDir = getObjectDir(id);
        File payloadFile = new File(objDir, HashDirectory.convertToFileName(payloadName));
        InputStream in;
        try {
            in = new FileInputStream(payloadFile);
        } catch (FileNotFoundException e) {
            return null;
        }
        if (start == null && end == null) {
            return in;
        }
        long size = payloadFile.length();
        if (end == null) {
            return new LimitedInputStream(in, start, Long.MAX_VALUE);
        }
        if (start == null) {
            start = size - end;
            if (start <= 0) return in;
            return new LimitedInputStream(in, start, Long.MAX_VALUE);
        }
        long length = end - start + 1;
        if (length < 0) length = 0;
        return new LimitedInputStream(in, start, length);
    }

    @Override
    public CordraObject create(CordraObject d) throws CordraException {
        if (objects.get(d.id) != null) {
            throw new ConflictCordraException("Object already exists: " + d.id);
        }
        if (d.payloads != null) {
            for (Payload p : d.payloads) {
                long length;
                try (InputStream in = p.getInputStream();) {
                    length = writeInputStreamToPayloadStorage(in, d.id, p.name);
                    p.size = length;
                } catch (IOException e) {
                    throw new CordraException(e);
                } finally {
                    p.setInputStream(null);
                }
            }
            if (d.payloads.isEmpty()) {
                d.payloads = null;
            }
        }
        objects.put(d.id, d);
        return objects.get(d.id);
    }

    private long writeInputStreamToPayloadStorage(InputStream in, String id, String name) throws IOException {
        File objDir = getObjectDir(id);
        objDir.mkdirs();
        File payloadFile = new File(objDir, HashDirectory.convertToFileName(name));
        try (OutputStream out = new FileOutputStream(payloadFile);) {
            long length = IOUtils.copy(in, out);
            return length;
        }
    }

    @Override
    public CordraObject update(CordraObject d) throws CordraException {
        if (objects.get(d.id) == null) {
            throw new NotFoundCordraException("Object does not exist: " + d.id);
        }
        List<String> payloadsToDelete = d.getPayloadsToDelete();
        String id = d.id;
        if (payloadsToDelete != null) {
            File objDir = getObjectDir(id);
            for (String payloadName : payloadsToDelete) {
                File payloadFile = new File(objDir, HashDirectory.convertToFileName(payloadName));
                if (payloadFile.exists()) {
                    payloadFile.delete();
                }
            }
        }
        d.clearPayloadsToDelete();
        if (d.payloads != null) {
            for (Payload p : d.payloads) {
                if (p.getInputStream() != null) {
                    try (InputStream in = p.getInputStream()) {
                        p.size = writeInputStreamToPayloadStorage(in, d.id, p.name);
                    } catch (IOException e) {
                        throw new CordraException(e);
                    } finally {
                        p.setInputStream(null);
                    }
                }
            }
            if (d.payloads.isEmpty()) {
                d.payloads = null;
            }
        }
        objects.put(d.id, d);
        return objects.get(d.id);
    }

    @Override
    public void delete(String id) throws CordraException {
        CordraObject result = objects.remove(id);
        if (result == null) {
            throw new NotFoundCordraException("Object does not exist: " + id);
        }
        File objDir = getObjectDir(id);
        if (objDir.exists()) {
            try {
                FileUtils.deleteDirectory(objDir);
                deleteEmptyDirectoriesStartingAt(objDir.getParentFile());
            } catch (IOException e) {
                throw new CordraException("Error deleting payloads", e);
            }
        }
    }

    private void deleteEmptyDirectoriesStartingAt(File parent) {
        while (true) {
            File toDelete = parent;
            parent = parent.getParentFile();
            File files[] = toDelete.listFiles();

            int numFiles = 0;
            for (int i = 0; files != null && i < files.length; i++) {
                if (files[i] == null)
                    continue;
                if (files[i].getName().equals(".DS_Store")) {
                    files[i].delete();
                    continue;
                }
                numFiles++;
            }

            if (numFiles <= 0) {
                toDelete.delete();
            } else {
                break;
            }
        }
    }

    private static final int HASH_LENGTH = 15;
    private static final int SEGMENT_LENGTH = 3;

    private File getObjectDir(String id) {
        return new File(payloadsDir, calculateObjectDir(id));
    }

    static String calculateObjectDir(String id) {
        String path = HashDirectory.hashPathFor(id, HASH_LENGTH, SEGMENT_LENGTH);
        String idAsFilename = HashDirectory.convertToFileName(id);
        String result = path + idAsFilename + "/";
        return result;
    }

    @Override
    public SearchResults<CordraObject> list() throws CordraException {
        return new AbstractSearchResults<CordraObject>() {

            private Iterator<CordraObject> objectsIter = objects.values().iterator();
            private int size = objects.size();

            @Override
            public int size() {
                return size;
            }

            @Override
            protected CordraObject computeNext() {
                if (objectsIter.hasNext()) {
                    return objectsIter.next();
                } else {
                    return null;
                }
            }

            @Override
            protected void closeOnlyOnce() {
                try {
                    ((Closeable) objectsIter).close();
                } catch (IOException e) {
                    // ignore
                }
            }
        };
    }

    @Override
    public SearchResults<String> listHandles() throws CordraException {
        return new AbstractSearchResults<String>() {

            private Iterator<String> objectsIter = objects.keySet().iterator();
            private int size = objects.size();

            @Override
            public int size() {
                return size;
            }

            @Override
            protected String computeNext() {
                if (objectsIter.hasNext()) {
                    return objectsIter.next();
                } else {
                    return null;
                }
            }

            @Override
            protected void closeOnlyOnce() {
                try {
                    ((Closeable) objectsIter).close();
                } catch (IOException e) {
                    // ignore
                }
            }
        };
    }

    @Override
    public void close() throws CordraException {
        if (objects != null) {
            objects.close();
        }
    }
}
