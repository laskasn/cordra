package net.cnri.cordra.storage.hds;

import com.sleepycat.je.*;
import net.cnri.cordra.api.ConflictCordraException;
import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.api.InternalErrorCordraException;
import net.cnri.cordra.api.NotFoundCordraException;
import net.cnri.cordra.storage.LimitedInputStream;
import net.handle.hdllib.Encoder;
import net.handle.hdllib.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.channels.Channels;
import java.security.MessageDigest;
import java.util.*;

/**
 * HashDirectoryStorage stores digital objects and associated data elements
 * using the file system. In order to support ridiculously large numbers of
 * digital objects on systems that have limited directory sizes, the digital
 * object IDs are hashed into a hexadecimal form, of which every 4 digits
 * represents a part of the path to the directory containing the digital
 * object's data elements.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class HashDirectoryStorage {
    public static final String DATE_CREATED_ATTRIBUTE = "internal.created";
    public static final String DATE_MODIFIED_ATTRIBUTE = "internal.modified";
    public static final String SIZE_ATTRIBUTE = "internal.size";

    private static final String INTERNAL_ELEMENT_FILE = "internal.elementFile";
    private static final int DEFAULT_MAX_DB_ELEMENT_SIZE = 1048576;
    static final Logger logger = LoggerFactory.getLogger(HashDirectoryStorage.class);

    private static final Object EMPTY_LOCK_VAL = new Object();

    // The separator between directory/file names
    private static final char FILE_SEPARATOR = File.separatorChar;

    // The algorithm used to compute paths to DOs.
    // We are using MD5 instead of SHA1 because it is faster.
    private String HASH_ALG = "MD5";

    // the forced length of the hexadecimal-encoded hash string
    private int HASH_LENGTH = 15;

    // the length of each segment of the hash string.
    // a segment_size of 4 will put (at most) 65,536 entries in a directory
    // a segment_size of 3 will put (at most) 4,096 entries in a directory
    private int SEGMENT_SIZE = 3;

    private File baseDirectory;
    private String baseDirectoryPath;
    private MessageDigest hash;
    private Properties props;
    private int maxDbElementSize = DEFAULT_MAX_DB_ELEMENT_SIZE;

    // lock tables to keep track of currently-being-written and
    // currently-being-read data
    private Hashtable locks = new Hashtable();

    // the Berkeley DB database environment
    private Environment environment = null;

    // the Berkeley DB index database
    private Database indexDB;

    // the database for elements
    private Database elementDb;

    private boolean readOnly;

    public HashDirectoryStorage() {
    }

    /**
     * Initializes the storage for use with server based in the given storage
     * directory.
     */
    @SuppressWarnings("hiding")
    public void initWithDirectory(File baseDirectory) throws CordraException {
        initWithDirectory(baseDirectory, false);
    }

    @SuppressWarnings("hiding")
    public void initWithDirectory(File baseDirectory, boolean readOnly) throws CordraException {
        this.baseDirectory = baseDirectory;
        this.readOnly = readOnly;
        this.props = new Properties();

        try {
            if (!baseDirectory.exists()) {
                if (readOnly) {
                    throw new InternalErrorCordraException("DO Storage directory (" + baseDirectory + ") does not exist");
                }
                baseDirectory.mkdirs();
            } else {
                try {
                    File propsFile = new File(baseDirectory, "storage_properties");
                    if (propsFile.exists()) {
                        InputStream in = new FileInputStream(propsFile);
                        try {
                            props.load(in);
                        } finally {
                            in.close();
                        }
                    }
                } catch (Exception e) {
                    logger.error("Error loading storage properties", e);
                    throw new InternalErrorCordraException("Error reading storage properties", e);
                }
            }

            if (!readOnly && !baseDirectory.canWrite()) {
                throw new InternalErrorCordraException("DO Storage directory (" + baseDirectory + ") is not writable");
            }

            baseDirectoryPath = baseDirectory.getCanonicalPath();

        } catch (Exception e) {
            if (e instanceof CordraException)
                throw (CordraException) e;
            throw new InternalErrorCordraException("Unable to access storage directory: " + e);
        }

        try {
            hash = MessageDigest.getInstance(HASH_ALG);
        } catch (Exception e) {
            throw new InternalErrorCordraException("Unable to initialize hash");
        }

        HASH_ALG = props.getProperty("hash_alg", HASH_ALG);
        try {
            HASH_LENGTH = Integer.parseInt(props.getProperty("hash_len", String.valueOf(HASH_LENGTH)));
        } catch (Exception e) {
            logger.error("Invalid hash length: " + props.getProperty("hash_len", String.valueOf(HASH_LENGTH)), e);
            throw new InternalErrorCordraException("Invalid hash length in storage properties: "
                    + props.getProperty("hash_len", String.valueOf(HASH_LENGTH)), e);
        }

        try {
            SEGMENT_SIZE = Integer.parseInt(props.getProperty("segment_size", String.valueOf(SEGMENT_SIZE)));
            if (SEGMENT_SIZE <= 0 || SEGMENT_SIZE > HASH_LENGTH)
                throw new Exception("Invalid segment size: " + SEGMENT_SIZE);
        } catch (Exception e) {
            logger.error("Invalid segment size: " + props.getProperty("segment_size", String.valueOf(SEGMENT_SIZE)), e);
            throw new InternalErrorCordraException("Invalid segment size in storage properties: "
                    + props.getProperty("segment_size", String.valueOf(SEGMENT_SIZE)), e);
        }

        try {
            maxDbElementSize = Integer
                    .parseInt(props.getProperty("max_db_element_size", String.valueOf(DEFAULT_MAX_DB_ELEMENT_SIZE)));
        } catch (Exception e) {
            logger.error("Invalid max_db_element_size: "
                    + props.getProperty("max_db_element_size", String.valueOf(DEFAULT_MAX_DB_ELEMENT_SIZE)), e);
            throw new InternalErrorCordraException("Invalid segment size in storage properties: "
                    + props.getProperty("max_db_element_size", String.valueOf(DEFAULT_MAX_DB_ELEMENT_SIZE)), e);
        }

        // create the database environment
        try {
            File indexDir = new File(baseDirectory, "index");
            if (!readOnly && !indexDir.exists())
                indexDir.mkdirs();

            EnvironmentConfig envConfig = new EnvironmentConfig();
            envConfig.setTransactional(true);
            envConfig.setAllowCreate(!readOnly);
            envConfig.setSharedCache(true);
            envConfig.setReadOnly(readOnly);
            envConfig.setConfigParam(EnvironmentConfig.FREE_DISK, "0");
            environment = JeUpgradeTool.openEnvironment(indexDir, envConfig);

            com.sleepycat.je.Transaction openTxn = environment.beginTransaction(null, null);
            DatabaseConfig dbConfig = new DatabaseConfig();
            dbConfig.setTransactional(true);
            dbConfig.setAllowCreate(!readOnly);
            dbConfig.setSortedDuplicates(false);
            dbConfig.setReadOnly(readOnly);
            indexDB = environment.openDatabase(openTxn, "objindex", dbConfig);
            elementDb = environment.openDatabase(openTxn, "elementDb", dbConfig);
            // if(logger.isDebugEnabled()) {
            // logger.debug("DB Stats: "+indexDB.getStats(null));
            // logger.debug("ElementDb Stats: "+elementDb.getStats(null));
            // logger.debug("DB Dir: "+indexDir.getAbsolutePath());
            // }
            openTxn.commitSync();
        } catch (Exception e) {
            logger.error("Error loading storage index", e);
            throw new InternalErrorCordraException("Error loading storage index", e);
        }
    }

    public void close() {
        if (indexDB != null)
            try {
                indexDB.close();
            } catch (Exception e) {
                logger.error("Exception closing", e);
            }
        if (elementDb != null)
            try {
                elementDb.close();
            } catch (Exception e) {
                logger.error("Exception closing", e);
            }
        if (environment != null)
            try {
                environment.close();
            } catch (Exception e) {
                logger.error("Exception closing", e);
            }
    }

    /**
     * Creates a new digital object with the given ID, if one does not already
     * exist. Returns the object identifier.
     */
    public String createObject(String id) throws CordraException {
        try {
            if (id == null) {
                throw new UnsupportedOperationException("Id must not be null.");
            }

            synchronized (id.toLowerCase().intern()) {
                DOMetadata metadata = getObjectInfo(id, null);

                // make sure that the digital object doesn't already exist
                if (metadata.objectExists()) {
                    throw new ConflictCordraException("Object already exists: " + id);
                }

                long creationTime = System.currentTimeMillis();

                // record the metadata, indicating that the digital object now exists
                long recordedCreationTime = Math.max(metadata.getDateCreated(), creationTime);
                metadata.setDateCreated(recordedCreationTime);
                metadata.updateModification(recordedCreationTime);
                setObjectInfo(metadata);
                return id;
            }
        } catch (Exception e) {
            if (e instanceof CordraException) {
                throw (CordraException) e;
            } else {
                logger.error("HashDirectoryStorage.createObject", e);
                throw new InternalErrorCordraException("HashDirectoryStorage.createObject", e);
            }
        }
    }

    /**
     * Deletes the given object.
     */
    public void deleteObject(String objectID) throws CordraException {
        long dateCreated;
        long deletionTime;
        boolean filesToDelete = false;
        try {
            synchronized (objectID.toLowerCase().intern()) {
                DOMetadata metadata = getObjectInfo(objectID, null);
                long dateDeleted = metadata.getDateDeleted();
                dateCreated = metadata.getDateCreated();
                if (dateCreated > 0 && dateDeleted > 0 && dateDeleted > dateCreated) {
                    throw new NotFoundCordraException("Object " + objectID + " has already been deleted");
                }

                deletionTime = System.currentTimeMillis();
                if (deletionTime <= dateCreated) {
                    deletionTime = dateCreated + 1;
                }

                String timestampPrefix = getAttTSKey(null, "");
                String valuePrefix = getAttValKey(null, "");
                HashMap tagsToSet = new HashMap();
                ArrayList tagsToDelete = new ArrayList();

                // remove any attributes
                for (Iterator it = metadata.getTagNames(); it.hasNext();) {
                    String tagName = (String) it.next();
                    // delete all elements
                    if (tagName.startsWith("de-exists."))
                        tagsToDelete.add(tagName);
                    if (tagName.startsWith("de-size."))
                        tagsToDelete.add(tagName);
                    if (tagName.startsWith("de-file.")) {
                        tagsToDelete.add(tagName);
                        filesToDelete = true;
                    }
                    if (tagName.startsWith(timestampPrefix)) {
                        String timestampVal = metadata.getTag(tagName, null);
                        if (timestampVal == null)
                            continue; // can't happen
                        try {
                            String valueKey = valuePrefix + tagName.substring(timestampPrefix.length());
                            tagsToDelete.add(valueKey);
                            tagsToSet.put(tagName, String.valueOf(deletionTime));
                        } catch (Exception e) {
                            // *very* unlikely to happen
                            logger.error("Error checking attribute timestamp", e);
                        }
                    }
                }

                for (Iterator iter = tagsToDelete.iterator(); iter.hasNext();) {
                    metadata.setTag((String) iter.next(), null);
                }
                for (Iterator iter = tagsToSet.keySet().iterator(); iter.hasNext();) {
                    String key = (String) iter.next();
                    metadata.setTag(key, (String) tagsToSet.get(key));
                }

                metadata.setDateDeleted(deletionTime);
                metadata.updateModification(deletionTime);
                setObjectInfo(metadata);
            }
        } catch (Exception e) {
            if (e instanceof CordraException) {
                logger.error("error recording object deletion", e);
                throw (CordraException) e;
            } else {
                throw new InternalErrorCordraException("error recording object deletion", e);
            }
        }
        deleteDbElementsForObject(objectID);
        if (filesToDelete) {
            // clean up the object storage...
            File objDir = null;
            try {
                objDir = getObjectDir(objectID);
                File parent = objDir.getParentFile();
                removeAllFiles(objDir);
                deleteEmptyDirectoriesStartingAt(parent);
            } catch (Exception e) {
                logger.error("Unable to finish deleting object " + objDir + " but the transaction was logged.  "
                        + (objDir == null ? "" : ("Check database consistency for the following path: " + objDir.getAbsolutePath())), e);
            }
        }
    }

    public static final void removeAllFiles(File dirToRemove) throws IOException {
        File files[] = dirToRemove.listFiles();
        if (files != null) {
            for (int i = 0; i < files.length; i++) {
                if (files[i].isDirectory())
                    removeAllFiles(files[i]);
                else
                    files[i].delete();
            }
        }
        dirToRemove.delete();
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

    /**
     * Returns any known metadata for the digital object with the given
     * identifier. If the given DOMetadata object is non-null then the metadata
     * is stored in that object which is also returned. Otherwise, a new
     * DOMetadata instance is constructed and returned. Note that this method
     * returns a non-null value whether or not the object exists.
     */
    public DOMetadata getObjectInfo(String objectID, DOMetadata metadata) throws CordraException {
        return getObjectInfo(objectID, metadata, false);
    }

    private DOMetadata getObjectInfo(String objectID, DOMetadata metadata, boolean migrating) throws CordraException {
        if (metadata == null)
            metadata = new DOMetadata();

        metadata.resetFields();
        metadata.setObjectID(objectID);

        // get the metadata database entry
        try {
            DatabaseEntry dbVal = new DatabaseEntry();
            // Use unlocked reading. Puts to indexDB are autocommit.
            // This function is locked by the object id for every write.
            // It is also locked for element reads.
            // There is a small chance of getting spurious information about
            // object existence, element existence, or attributes
            // if this is performed at just the same time as an autocommit put
            // fails; in this case it should just look the same
            // as if the put succeeded and was then reversed, which I consider
            // acceptable.
            if (indexDB.get(null, new DatabaseEntry(Util.encodeString(objectID.toLowerCase())), dbVal,
                    LockMode.READ_UNCOMMITTED) == OperationStatus.SUCCESS) {
                HeaderSet headers = new HeaderSet();
                headers.readHeadersFromBytes(dbVal.getData());
                String mdID = headers.getStringHeader("id", objectID);
                if (!mdID.equalsIgnoreCase(objectID))
                    throw new InternalErrorCordraException("Metadata ID does not match database ID: " + mdID + " != " + objectID);
                metadata.setObjectID(mdID);
                metadata.setDateCreated(headers.getLongHeader("date_created", 0));
                metadata.setDateDeleted(headers.getLongHeader("date_deleted", 0));
                for (Iterator headerItems = headers.iterator(); headerItems.hasNext();) {
                    HeaderItem item = (HeaderItem) headerItems.next();
                    if (item.getName().startsWith("md.")) {
                        metadata.setTag(item.getName().substring(3), item.getValue());
                    }
                }
                if (!readOnly && !migrating && null == metadata.getTag("elDb", null)) {
                    metadata = migrateObject(objectID);
                }
            } else {
                metadata.setTag("elDb", "");
            }
        } catch (Exception e) {
            if (e instanceof CordraException)
                throw (CordraException) e;
            else
                throw new InternalErrorCordraException("Unknown error getting metadata: ", e);
        }
        return metadata;
    }

    /** Sets the metadata for the given digital object. */
    public void setObjectInfo(DOMetadata metadata) throws CordraException {
        HeaderSet headers = new HeaderSet();
        String id = metadata.getObjectID();
        long dateCreated = metadata.getDateCreated();
        long dateDeleted = metadata.getDateDeleted();

        headers.addHeader("id", id);
        if (dateCreated != 0)
            headers.addHeader("date_created", dateCreated);
        if (dateDeleted != 0)
            headers.addHeader("date_deleted", dateDeleted);
        for (Iterator tagKeys = metadata.getTagNames(); tagKeys.hasNext();) {
            String tagName = (String) tagKeys.next();
            headers.addHeader("md." + tagName, metadata.getTag(tagName, ""));
        }

        // store the updated info in the index database
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        try {
            headers.writeHeaders(bout);

            DatabaseEntry key = new DatabaseEntry(Util.encodeString(id.toLowerCase()));
            DatabaseEntry data = new DatabaseEntry(bout.toByteArray());
            // long start = System.currentTimeMillis();
            boolean success;
            try {
                success = indexDB.put(null, key, data) == OperationStatus.SUCCESS;
            } catch (Exception e) {
                throw new InternalErrorCordraException("Unknown error setting metadata", e);
            }
            // long end = System.currentTimeMillis();
            // if (end-start > 500) {
            // logger.warn("Long transaction (" + (end-start) + " = " + end +
            // "-" + start + ") for " + Thread.currentThread().getName());
            // }
            if (!success) {
                throw new InternalErrorCordraException("Unexpected status upon database 'put'");
            }
        } catch (Exception e) {
            logger.error("Unknown error setting metadata", e);
            if (e instanceof CordraException)
                throw (CordraException) e;
            else
                throw new InternalErrorCordraException("Unknown error setting metadata", e);
        }
    }

    /**
     * Return the metadata key that refers to the attribute timestamp for the
     * given attribute key
     */
    private static final String getAttTSKey(String elementID, String attributeKey) {
        if (elementID == null)
            return "objatt-ts." + attributeKey;
        return "elatt-ts." + escapeElementID(elementID) + "." + attributeKey;
    }

    /**
     * Return the metadata key that refers to the attribute value for the given
     * attribute key
     */
    private static final String getAttValKey(String elementID, String attributeKey) {
        if (elementID == null)
            return "objatt." + attributeKey;
        return "elatt." + escapeElementID(elementID) + "." + attributeKey;
    }

    /**
     * Remove the attributes with the given keys from the object or data
     * element. If the elementID is null then the attributes are removed from
     * the object, otherwise they are removed from the element.
     */
    public void deleteAttributes(String objectID, String elementID, String attributeKeys[]) throws CordraException {
        if (attributeKeys == null)
            throw new NullPointerException();

        // sync on the object ID so that we have exclusive access to the object
        // metadata
        synchronized (objectID.toLowerCase().intern()) {
            long timestamp = System.currentTimeMillis();

            HeaderSet removedAtts = new HeaderSet();
            DOMetadata metadata = getObjectInfo(objectID, null);
            for (int i = 0; i < attributeKeys.length; i++) {
                String key = attributeKeys[i];
                String timestampKey = getAttTSKey(elementID, key);
                metadata.setTag(timestampKey, String.valueOf(timestamp));
                metadata.setTag(getAttValKey(elementID, key), null);
                removedAtts.addHeader(key, "");
            }

            metadata.updateModification(timestamp);
            setObjectInfo(metadata);
        }
    }

    /**
     * Add the given key-value attribute to the object, replacing any existing
     * attribute that has the same key. If the elementID is non-null then the
     * attribute is associated with the identified element within the object.
     */
    public void setAttributes(String objectID, String elementID, HeaderSet headers) throws CordraException {
        synchronized (objectID.toLowerCase().intern()) {
            DOMetadata metadata = getObjectInfo(objectID, null);
            HeaderSet txnAttributes = new HeaderSet();
            long timestamp = System.currentTimeMillis();
            for (Iterator it = headers.iterator(); it.hasNext();) {
                HeaderItem item = (HeaderItem) it.next();
                String key = item.getName();
                String value = item.getValue();

                String timestampKey = getAttTSKey(elementID, key);
                String valueKey = getAttValKey(elementID, key);

                metadata.setTag(timestampKey, String.valueOf(timestamp));
                metadata.setTag(valueKey, value);
                txnAttributes.addHeader(key, value);
            }

            metadata.updateModification(timestamp);
            setObjectInfo(metadata);
        }
    }

    /**
     * Get the value that has been associated with the given key. If no value
     * has been associated with the key then this will return null. If the given
     * elementID is null then this will return object-level attributes.
     * Otherwise it will return attributes for the given element.
     */
    public HeaderSet getAttributes(String objectID, String elementID, HeaderSet container) throws CordraException {
        if (container == null)
            container = new HeaderSet();
        DOMetadata md = getObjectInfo(objectID, null);
        String prefix = getAttValKey(elementID, "");
        int prefixLen = prefix.length();
        for (Iterator it = md.getTagNames(); it.hasNext();) {
            String tag = (String) it.next();
            if (!tag.startsWith(prefix))
                continue;
            container.addHeader(tag.substring(prefixLen), md.getTag(tag, null));
        }
        // add the internal element attributes
        if (elementID == null) {
            // add the object timestamps
            if (!container.hasHeader(DATE_CREATED_ATTRIBUTE)) {
                container.addHeader(DATE_CREATED_ATTRIBUTE, md.getDateCreated());
            }
            String modTS = md.getTag("objmodified", null);
            if (modTS != null && !container.hasHeader(DATE_MODIFIED_ATTRIBUTE)) {
                container.addHeader(DATE_MODIFIED_ATTRIBUTE, modTS);
            }
        } else {
            // add the element timestamps
            String elementTS = md.getTag("de." + elementID, null);
            if (elementTS != null && !container.hasHeader(DATE_MODIFIED_ATTRIBUTE)) {
                container.addHeader(DATE_MODIFIED_ATTRIBUTE, elementTS);
            }
            String elementCreatedTS = md.getTag("de-created." + elementID, null);
            if (elementCreatedTS != null && !container.hasHeader(DATE_CREATED_ATTRIBUTE)) {
                container.addHeader(DATE_CREATED_ATTRIBUTE, elementCreatedTS);
            }
            if (!container.hasHeader(SIZE_ATTRIBUTE)) {
                if (md.getTag("de-file." + elementID, null) != null) {
                    File objDir = getObjectDir(objectID);
                    File elFile = new File(objDir, convertToFileName(elementID));
                    container.addHeader(SIZE_ATTRIBUTE, elFile.length());
                } else {
                    String sizeString = md.getTag("de-size." + elementID, null);
                    if (sizeString != null) {
                        long size = Long.parseLong(sizeString);
                        container.addHeader(SIZE_ATTRIBUTE, size);
                    }
                }
            }
        }

        return container;
    }

    /**
     * Returns true if the given digital object exists.
     */
    public boolean doesObjectExist(String objectID) throws CordraException {
        return getObjectInfo(objectID, null).objectExists();
    }

    /**
     * Returns true if the given data element exists
     */
    public boolean doesDataElementExist(String objectID, String elementID) throws CordraException {
        try {
            DOMetadata metadata = getObjectInfo(objectID, null);
            if (!metadata.objectExists())
                return false;

            String deExists = metadata.getTag("de-exists." + elementID, null);
            // if (deExists != null) // now getObjectInfo ensures that de-exists
            // is correct
            return Boolean.parseBoolean(deExists);
        } catch (Exception e) {
            if (e instanceof CordraException)
                throw (CordraException) e;
            else
                throw new InternalErrorCordraException(String.valueOf(e));
        }
    }

    /**
     * Returns the File in which the given data element is stored, if any. This
     * can return null on servers where data elements are not stored in files.
     * This is used where operators need to do more with a data element than
     * simple read and write operations. Examples inlude indexes, databases,
     * etc.
     */
    @SuppressWarnings("unused")
    public File getFileForDataElement(String objectID, String elementID) throws CordraException {
        return null;
    }

    /** Iterator over the object IDs in the Berkeley DB database. */
    public class DBEnumerator implements Enumeration<String>, Closeable {
        private Cursor cursor = null;
        private DatabaseEntry keyEntry = new DatabaseEntry();
        private DatabaseEntry valEntry = new DatabaseEntry();
        private OperationStatus lastStatus = null;
        private HeaderSet mdInfo;
        private DOMetadata currentMD;

        public DBEnumerator() {
            mdInfo = new HeaderSet();
            currentMD = new DOMetadata();
            try {
                // for logic of READ_UNCOMMITTED, see indexDB.put in
                // getObjectInfo()
                cursor = indexDB.openCursor(null, CursorConfig.READ_UNCOMMITTED);
                preFetchNextItem();
            } catch (Exception e) {
                logger.error("Error in DBEnumerator()", e);
                cursor = null;
                lastStatus = null;
            }
        }

        private void preFetchNextItem() {
            // fetch the next item....
            while (true) {
                try {
                    lastStatus = cursor.getNext(keyEntry, valEntry, null);
                    if (lastStatus == null || lastStatus != OperationStatus.SUCCESS) {
                        cursor.close();
                        break;
                    }
                    loadObjectMD();
                    if (!currentMD.objectExists()) { // the object no longer
                                                        // exists. keep scanning
                        continue;
                    } else {
                        break;
                    }
                } catch (Exception e) {
                    logger.error("Error scanning object index", e);
                    try {
                        cursor.close();
                    } catch (Throwable t) {
                    }
                    cursor = null;
                    lastStatus = null;
                    break;
                }
            }
        }

        @Override
        public synchronized boolean hasMoreElements() {
            return lastStatus == OperationStatus.SUCCESS;
        }

        private void loadObjectMD() throws Exception {
            mdInfo.readHeaders(new ByteArrayInputStream(valEntry.getData()));
            String objectID = Util.decodeString(keyEntry.getData());
            String mdID = mdInfo.getStringHeader("id", objectID);
            if (!mdID.equalsIgnoreCase(objectID)) {
                logger.error(
                        "Error listing objects: Metadata ID does not match database ID: " + mdID + " != " + objectID);
                mdID = objectID;
            }
            currentMD.setObjectID(mdID);
            currentMD.setDateCreated(mdInfo.getLongHeader("date_created", 0));
            currentMD.setDateDeleted(mdInfo.getLongHeader("date_deleted", 0));
        }

        @Override
        public synchronized String nextElement() throws java.util.NoSuchElementException {
            if (cursor == null || lastStatus == null || lastStatus != OperationStatus.SUCCESS)
                throw new java.util.NoSuchElementException();

            String objectID = currentMD.getObjectID();
            preFetchNextItem();
            return objectID;
        }

        @Override
        public void close() {
            try {
                if (cursor != null)
                    cursor.close();
            } catch (Throwable t) {
            }
        }

//        public void finalize() throws Throwable {
//            close();
//            super.finalize();
//        }
    }

    /**
     * Returns an Enumeration of all of the objects in the repository.
     */
    @SuppressWarnings("unused")
    public Enumeration<String> listObjects() throws CordraException {
        return new DBEnumerator();
    }

    public List<String> listDataElements(String objectID) throws CordraException {
        try {
            DOMetadata metadata = getObjectInfo(objectID, null);
            if (!metadata.objectExists()) {
                // Used to throw NO_SUCH_OBJECT; this caused issues due to lack
                // of transactional semantics.
                // Calls to this method are generally indirectly preceded by
                // doesObjectExist.
                return Collections.emptyList();
            }

            List<String> v = new ArrayList<>();
            for (Iterator it = metadata.getTagNames(); it.hasNext();) {
                String tag = (String) it.next();
                if (!tag.startsWith("de-exists."))
                    continue;
                if (Boolean.parseBoolean(metadata.getTag(tag, null))) {
                    v.add(tag.substring("de-exists.".length()));
                }
            }
            return v;
        } catch (Exception e) {
            if (e instanceof CordraException)
                throw (CordraException) e;
            else
                throw new InternalErrorCordraException(String.valueOf(e));
        }

    }

    /**
     * Returns the identified data element for the given object.
     */
    public InputStream getDataElement(String objectID, String elementID) throws CordraException {
        return getDataElement(objectID, elementID, 0, -1);
    }

    @SuppressWarnings({"resource", "null"})
    public InputStream getDataElement(String objectID, String elementID, long start, long len) throws CordraException {
        String lockID = null;
        ReadLock lock = null;
        boolean returnedSuccessfully = false;
        try {
            DOMetadata metadata = getObjectInfo(objectID, null);
            if (!metadata.objectExists()) {
                // Used to throw NO_SUCH_OBJECT; this caused issues due to lack
                // of transactional semantics.
                // Calls to this method are generally indirectly preceded by
                // doesObjectExist.
                return null;
            }

            String deExists = metadata.getTag("de-exists." + elementID, null);
            if (!Boolean.parseBoolean(deExists)) {
                return null;
            }

            // Wait for any write operations to finish with the data alement
            // and then register ourselves as reading it so that won't be
            // written
            // by others until we are finished
            lockID = getLockID(objectID, elementID);
            synchronized (lockID) {
                while (true) {
                    Object lockObj = locks.get(lockID);
                    if (lockObj == null) {
                        lock = new ReadLock(lockID);
                        locks.put(lockID, lock);
                        break;
                    } else if (lockObj instanceof ReadLock) {
                        lock = (ReadLock) lockObj;
                        break;
                    } else {
                        // someone else has a write lock...
                        // wait for the lock to become available again
                        lockID.wait();
                    }
                }
                lock.readerCount++;
            }

            metadata = getObjectInfo(objectID, metadata);
            deExists = metadata.getTag("de-exists." + elementID, null);
            if (!Boolean.parseBoolean(deExists)) {
                return null;
            }
            boolean deFile = null != metadata.getTag("de-file." + elementID, null);
            if (start < 0)
                start = 0;
            if (deFile) {
                File objDir = getObjectDir(objectID);
                File elementFile = new File(objDir, convertToFileName(elementID));
                if (!elementFile.exists()) {
                    return new ByteArrayInputStream(new byte[0]);
                }
                RandomAccessFile randomAccessElementFile = new RandomAccessFile(elementFile, "r");
                InputStream in = Channels.newInputStream(randomAccessElementFile.getChannel().position(start));
                if (len >= 0) {
                    in = new LimitedInputStream(in, 0, len);
                }
                InputStream lockedIn = new LockedInputStream(in, lock, randomAccessElementFile);
                returnedSuccessfully = true;
                return lockedIn;
            } else {
                byte[] bytes = getElementFromDb(objectID, elementID);
                if (start >= bytes.length) {
                    return new ByteArrayInputStream(new byte[0]);
                }
                if (start > 0 || len >= 0) {
                    long end;
                    if (len < 0)
                        end = bytes.length;
                    else
                        end = start + len;
                    if (end > bytes.length) {
                        end = bytes.length;
                    }
                    bytes = Arrays.copyOfRange(bytes, (int) start, (int) end);
                }
                return new ByteArrayInputStream(bytes);
            }
        } catch (Exception e) {
            if (e instanceof CordraException)
                throw (CordraException) e;
            else {
                logger.error("Error getting data element: objectID='" + objectID + "'; element='" + elementID, e);
                throw new InternalErrorCordraException("Error getting data element: objectID='" + objectID + "'; element='" + elementID, e);
            }
        } finally {
            if (lock != null && !returnedSuccessfully) {
                lock.releaseLock();
            }
        }
    }

    /**
     * InputStream subclass that decrements the lock (semaphore) when the stream
     * is closed.
     */
    @SuppressWarnings("sync-override")
    private static class LockedInputStream extends BufferedInputStream {
        private ReadLock lock;
        private boolean hasBeenReleased = false;
        private RandomAccessFile randomAccessElementFile;

        LockedInputStream(InputStream in, ReadLock lock, RandomAccessFile randomAccessElementFile) {
            super(in);
            this.lock = lock;
            this.randomAccessElementFile = randomAccessElementFile;
        }

        synchronized void releaseLock() {
            if (hasBeenReleased)
                return;
            lock.releaseLock();
            hasBeenReleased = true;
        }

        // Override read(...) methods to release lock when EOF reached.
        // Just a safety net in case of poorly-behaved client.
        // Note that BufferedInputStream guarantees that the
        // underlying input is finished, even if the client uses
        // mark() and reset().

        @Override
        public int read() throws IOException {
            int res = super.read();
            if (res < 0)
                releaseLock();
            return res;
        }

        @Override
        public int read(byte[] b) throws IOException {
            int res = super.read(b);
            if (res < 0)
                releaseLock();
            return res;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int res = super.read(b, off, len);
            if (res < 0)
                releaseLock();
            return res;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
                this.randomAccessElementFile.close();
            } finally {
                releaseLock();
            }
        }

        @Override
        public synchronized void finalize() {
            if (!hasBeenReleased) {
                System.err.println(
                        "Warning: LockedInputStream with lock id " + lock.id + " finalized before being closed");
                releaseLock();
            }
        }
    }

    private class ReadLock {
        String id;
        int readerCount = 0;

        ReadLock(String id) {
            this.id = id;
        }

        void releaseLock() {
            synchronized (id) {
                readerCount--;
                if (readerCount == 0) {
                    locks.remove(id);
                }
                id.notifyAll();
            }
        }
    }

    /**
     * Stores the data read from the given InputStream into the given data
     * element for the object identified by objectID. This reads from the
     * InputStream until the end of the stream has been reached.
     */
    @SuppressWarnings("resource")
    public void storeDataElement(String objectID, String elementID, InputStream input, boolean append) throws CordraException {
        OutputStream fout = null;
        int n = 0;

        String lockID = null;
        try {
            // make sure the object exists
            if (!doesObjectExist(objectID)) {
                throw new NotFoundCordraException("Object '" + objectID + "' does not exist");
            }

            // Wait for everyone else to finish with the data element (reading
            // and writing)
            // and then lock it so that it cannot be read or written by others
            // until we are done
            String tmpLockID = getLockID(objectID, elementID);
            synchronized (tmpLockID) {
                while (locks.containsKey(tmpLockID)) {
                    // wait until there are no more readers...
                    tmpLockID.wait();
                }

                lockID = tmpLockID;
                locks.put(tmpLockID, EMPTY_LOCK_VAL);
            }

            long modificationTime = 0;

            boolean alreadyExisted;
            boolean deFile = false;
            boolean deForceFile = false;
            synchronized (objectID.toLowerCase().intern()) {
                DOMetadata metadata = getObjectInfo(objectID, null);
                String mdTagName = "de." + elementID;
                long oldTimestamp = 0;
                try {
                    oldTimestamp = Long.parseLong(metadata.getTag(mdTagName, "0"));
                } catch (Exception e) {
                }
                alreadyExisted = oldTimestamp > 0;
                modificationTime = System.currentTimeMillis();
                metadata.setTag(mdTagName, String.valueOf(modificationTime));
                metadata.setTag("de-exists." + elementID, "true");
                deFile = null != metadata.getTag("de-file." + elementID, null);
                deForceFile = Boolean
                        .parseBoolean(metadata.getTag(getAttValKey(elementID, INTERNAL_ELEMENT_FILE), null));

                String createdTagName = "de-created." + elementID;
                if (!alreadyExisted && metadata.getTag(createdTagName, null) == null)
                    metadata.setTag(createdTagName, String.valueOf(modificationTime));

                metadata.updateModification(modificationTime);
                setObjectInfo(metadata);
            }
            // (over)write the data element
            try {
                if (deFile) {
                    File objDir = getObjectDir(objectID);
                    objDir.mkdirs();

                    File elementFile = new File(objDir, convertToFileName(elementID));
                    fout = new FileOutputStream(elementFile, append);
                    byte buf[] = new byte[4096];
                    int r;
                    n = 0;
                    while ((r = input.read(buf)) >= 0) {
                        fout.write(buf, 0, r);
                        n += r;
                    }
                    fout.close();
                    if (!deForceFile && !append && n < maxDbElementSize / 2) {
                        migrateElementFromFileToDb(null, null, objectID, elementID);
                    }
                } else {
                    storeElementMaybeInDb(objectID, elementID, input, append, deForceFile);
                }
            } finally {

            }
        } catch (Exception e) {
            logger.error("Got error writing element", e);
            if (e instanceof CordraException)
                throw (CordraException) e;
            else
                throw new InternalErrorCordraException("Got error writing element", e);
        } finally {
            try {
                if (fout != null) fout.close();
            } catch (Exception e) {
            }
            if (lockID != null) { // the data element was locked... unlock it
                synchronized (lockID) {
                    locks.remove(lockID);
                    lockID.notifyAll();
                }
            }
        }
    }

    /**
     * Deletes the specified data element from the given object. Returns true if
     * the specified data element ever existed in the first place.
     */
    public boolean deleteDataElement(String objectID, String elementID) throws CordraException {
        try {
            // make sure the object exists
            DOMetadata metadata = getObjectInfo(objectID, null);
            if (!metadata.objectExists()) {
                throw new NotFoundCordraException("Object '" + objectID + "' does not exist");
            }
            if (!Boolean.parseBoolean(metadata.getTag("de-exists." + elementID, null))) {
                return false;
            } else {
                // get an exclusive write lock on the object metadata
                long actualTime;
                synchronized (objectID.toLowerCase().intern()) {
                    metadata = getObjectInfo(objectID, metadata);
                    actualTime = System.currentTimeMillis();

                    String mdTagName = "de." + elementID;
                    metadata.setTag(mdTagName, String.valueOf(actualTime));
                    metadata.setTag("de-exists." + elementID, null);
                    metadata.setTag("de-size." + elementID, null);

                    boolean deFile = null != metadata.getTag("de-file." + elementID, null);
                    if (deFile) {
                        File objDir = getObjectDir(objectID);
                        File elementFile = new File(objDir, convertToFileName(elementID));
                        // delete the actual file
                        if (elementFile.exists() && !elementFile.delete()) {
                            throw new InternalErrorCordraException("Unable to delete data element");
                        }
                        deleteEmptyDirectoriesStartingAt(objDir);
                    } else {
                        deleteElementFromDb(objectID, elementID);
                    }
                    metadata.setTag("de-file." + elementID, null);

                    String timestampPrefix = getAttTSKey(elementID, "");
                    String valuePrefix = getAttValKey(elementID, "");
                    HashMap tagsToSet = new HashMap();
                    ArrayList tagsToDelete = new ArrayList();

                    // remove any attributes
                    for (Iterator it = metadata.getTagNames(); it.hasNext();) {
                        String tagName = (String) it.next();
                        if (tagName.startsWith(timestampPrefix)) {
                            String timestampVal = metadata.getTag(tagName, null);
                            if (timestampVal == null)
                                continue; // can't happen
                            try {
                                String valueKey = valuePrefix + tagName.substring(timestampPrefix.length());
                                tagsToDelete.add(valueKey);
                                tagsToSet.put(tagName, String.valueOf(actualTime));
                            } catch (Exception e) {
                                // *very* unlikely to happen
                                logger.error("Error checking attribute timestamp", e);
                            }
                        }
                    }

                    for (Iterator iter = tagsToDelete.iterator(); iter.hasNext();) {
                        metadata.setTag((String) iter.next(), null);
                    }
                    for (Iterator iter = tagsToSet.keySet().iterator(); iter.hasNext();) {
                        String key = (String) iter.next();
                        metadata.setTag(key, (String) tagsToSet.get(key));
                    }

                    // update the metadata and attributes
                    metadata.updateModification(actualTime);
                    setObjectInfo(metadata);
                }
                return true;
            }
        } catch (Exception e) {
            if (e instanceof CordraException)
                throw (CordraException) e;
            else
                throw new InternalErrorCordraException(String.valueOf(e));
        }
    }

    private File getObjectDir(String objectID) throws CordraException {
        return new File(baseDirectory, calculateObjectPath(objectID));
    }

    /**
     * Get a key that identifies a unique lock object for the given object and
     * data element
     */
    private final String getLockID(String objectID, String elementID) {
        return (convertToFileName(elementID) + "$" + objectID).intern();
    }

    private String calculateObjectPath(String objectID) throws CordraException {
        // this assumes we are using case insensitive storage!
        objectID = objectID.toLowerCase();

        try {
            byte buf[] = null;
            synchronized (hash) {
                hash.reset();
                buf = hash.digest(objectID.getBytes("UTF8"));
            }

            StringBuffer sb = new StringBuffer(buf.length * 2);
            encodeHex(buf, 0, buf.length, sb);
            if (sb.length() > HASH_LENGTH)
                sb.setLength(HASH_LENGTH);

            int i = SEGMENT_SIZE;
            while (i < sb.length()) {
                sb.insert(i, FILE_SEPARATOR);

                i += SEGMENT_SIZE + 1;
            }

            if (sb.charAt(sb.length() - 1) != FILE_SEPARATOR)
                sb.append(FILE_SEPARATOR);

            sb.append(convertToFileName(objectID));
            return sb.toString();
        } catch (Exception e) {
            if (e instanceof CordraException)
                throw (CordraException) e;
            throw new InternalErrorCordraException("Error computing object path: " + e);
        }
    }

    @Override
    public String toString() {
        return "HashDirectoryStorage: " + baseDirectoryPath;
    }

    private static void printUsage() {
        System.err.println("java net.cnri.apps.doserver.HashDirectoryStorage <dir> (list|getpath|getmetadata <id>)>");
    }

    public static void main(String argv[]) throws Exception {
        if (argv.length < 2) {
            printUsage();
            return;
        }
        String dir = argv[0];
        String cmd = argv[1];

        HashDirectoryStorage hds = new HashDirectoryStorage();
        hds.initWithDirectory(new File(dir), true);
        if (cmd.equalsIgnoreCase("list")) {
            System.out.println("listing elements...");
            for (Enumeration en = hds.listObjects(); en.hasMoreElements();) {
                System.out.println(String.valueOf(en.nextElement()));
            }
            System.out.println("done listing elements...");
        } else if (cmd.equalsIgnoreCase("getpath")) {
            for (int i = 2; i < argv.length; i++) {
                System.out.println(" '" + argv[i] + "' -> '" + hds.calculateObjectPath(argv[i]) + "'");
            }
        } else if (cmd.equalsIgnoreCase("getmetadata")) {
            for (int i = 2; i < argv.length; i++) {
                System.out.println(argv[i] + ": " + hds.getObjectInfo(argv[i], null));
            }
        } else {
            printUsage();
            System.err.println("Unrecognized command: " + cmd);
            System.exit(1);
        }
        System.exit(0);
    }

    private static final char HEX_VALUES[] = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D',
            'E', 'F' };

    /**
     * Encode the given byte array into a hexadecimal string.
     */
    public static final void encodeHex(byte buf[], int offset, int length, StringBuffer strBuf) {
        if (buf == null)
            return;

        int n = 0;
        for (int i = offset; i < buf.length && n < length; i++) {
            strBuf.append(HEX_VALUES[(buf[i] & 0xF0) >>> 4]);
            strBuf.append(HEX_VALUES[(buf[i] & 0xF)]);
            n++;
        }
    }

    /**
     * Decode the given two hexadecimal characters into the byte that they
     * represent.
     */
    public static final byte decodeHex(char b1, char b2) {
        b1 = Character.toUpperCase(b1);
        b2 = Character.toUpperCase(b2);

        byte result = (byte) 0;

        if (b1 >= '0' && b1 <= '9') {
            result = (byte) ((b1 - '0') << 4);
        } else if (b1 >= 'A' && b1 <= 'F') {
            result = (byte) ((b1 - 'A' + 10) << 4);
        }
        if (b2 >= '0' && b2 <= '9') {
            result |= b2 - '0';
        } else if (b2 >= 'A' && b2 <= 'F') {
            result |= b2 - 'A' + 10;
        }
        return result;
    }

    /**
     * Performs a conversion from the given encoded file name to the string that
     * was encoded to get the file name.
     */
    public static final String convertFromFileName(String fileName) {
        if (fileName == null)
            return "";
        fileName = fileName.trim();
        if (fileName.length() <= 0)
            return "";

        int strLen = fileName.length();
        byte utf8Buf[] = new byte[strLen];
        int buflen = 0;
        for (int i = 0; i < strLen; i++) {
            char ch = fileName.charAt(i);
            if (ch == '.') {
                utf8Buf[buflen++] = decodeHex(fileName.charAt(i + 1), fileName.charAt(i + 2));
                i += 2;
            } else {
                utf8Buf[buflen++] = (byte) ch;
            }
        }
        try {
            return new String(utf8Buf, 0, buflen, "UTF8");
        } catch (Exception e) {
            return new String(utf8Buf, 0, buflen);
        }
    }

    /**
     * Performs a conversion from the given string to an encoded version that
     * can be used as a file/URI name.
     */
    public static final String convertToFileName(String str) {
        byte buf[];
        try {
            buf = str.getBytes("UTF8");
        } catch (Exception e) {
            buf = str.getBytes();
        }

        StringBuffer sb = new StringBuffer(buf.length + 10);
        for (int i = 0; i < buf.length; i++) {
            byte b = buf[i];
            if ((b >= 'A' && b <= 'Z') || (b >= 'a' && b <= 'z') || (b >= '0' && b <= '9') || b == '_' || b == '-') {
                sb.append((char) b);
            } else {
                sb.append('.');
                encodeHex(buf, i, 1, sb);
            }
        }
        return sb.toString();
    }

    private static final String escapeElementID(String elementID) {
        int len = elementID.length();
        StringBuffer sb = new StringBuffer(len + 10);

        for (int i = 0; i < len; i++) {
            char ch = elementID.charAt(i);
            switch (ch) {
            case '%':
                sb.append("%25");
                break;
            case '.':
                sb.append("%2E");
                break;
            default:
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private static byte[] getElementDbKey(String objectID, String elementID) {
        byte[] objIdBytes = Util.encodeString(objectID.toLowerCase(Locale.ENGLISH));
        byte[] elIdBytes = Util.encodeString(elementID);
        byte[] key = new byte[8 + objIdBytes.length + elIdBytes.length];
        int offset = Encoder.writeByteArray(key, 0, objIdBytes);
        Encoder.writeByteArray(key, offset, elIdBytes);
        return key;
    }

    private byte[] getElementDbSearchKeyForObject(String objectID) {
        byte[] objIdBytes = Util.encodeString(objectID.toLowerCase(Locale.ENGLISH));
        byte[] key = new byte[4 + objIdBytes.length];
        Encoder.writeByteArray(key, 0, objIdBytes);
        return key;
    }

    private void deleteElementFromDb(String objectID, String elementID) throws CordraException {
        byte[] key = getElementDbKey(objectID, elementID);
        try {
            elementDb.delete(null, new DatabaseEntry(key));
        } catch (Exception e) {
            logger.error("Error deleting data element " + elementID + " of " + objectID, e);
            throw new InternalErrorCordraException("Error deleting data element " + elementID + " of " + objectID, e);
        }
    }

    private byte[] getElementFromDb(String objectID, String elementID) throws CordraException {
        byte[] key = getElementDbKey(objectID, elementID);
        try {
            DatabaseEntry dbVal = new DatabaseEntry();
            OperationStatus status = elementDb.get(null, new DatabaseEntry(key), dbVal, null);
            if (status == OperationStatus.NOTFOUND) {
                return new byte[0];
            } else if (status == OperationStatus.SUCCESS) {
                return dbVal.getData();
            } else {
                throw new InternalErrorCordraException("Unexpected status " + status + " retrieving data element " + elementID + " of " + objectID);
            }
        } catch (Exception e) {
            if (e instanceof CordraException)
                throw (CordraException) e;
            throw new InternalErrorCordraException("Error retrieving data element " + elementID + " of " + objectID, e);
        }
    }

    @SuppressWarnings("resource")
    private void deleteDbElementsForObject(String objectID) throws CordraException {
        com.sleepycat.je.Transaction dbtxn = null;
        Cursor cursor = null;
        try {
            dbtxn = environment.beginTransaction(null, null);
            cursor = elementDb.openCursor(dbtxn, null);
            byte[] objectKey = getElementDbSearchKeyForObject(objectID);
            DatabaseEntry key = new DatabaseEntry(objectKey);
            DatabaseEntry data = new DatabaseEntry();
            data.setPartial(0, 0, true);
            OperationStatus status = cursor.getSearchKeyRange(key, data, LockMode.RMW);
            while (status == OperationStatus.SUCCESS) {
                if (Util.startsWith(key.getData(), objectKey)) {
                    cursor.delete();
                } else {
                    break;
                }
                status = cursor.getNext(key, data, LockMode.RMW);
            }
            if (status != OperationStatus.SUCCESS && status != OperationStatus.NOTFOUND) {
                throw new InternalErrorCordraException("Unexpected status " + status + " deleting data elements of " + objectID);
            }
            cursor.close();
            cursor = null;
            dbtxn.commit();
        } catch (Exception e) {
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (DatabaseException ex) {
                    logger.error("Exception closing", ex);
                }
            }
            if (dbtxn != null) {
                try {
                    dbtxn.abort();
                } catch (DatabaseException ex) {
                    logger.error("Exception closing", ex);
                }
            }
            logger.error("Error deleting data elements of " + objectID, e);
            if (e instanceof CordraException)
                throw (CordraException) e;
            throw new InternalErrorCordraException("Error deleting data elements of " + objectID, e);
        }
    }

    private DOMetadata migrateObject(String objectID) throws CordraException {
        com.sleepycat.je.Transaction dbtxn = null;
        try {
            synchronized (objectID.toLowerCase().intern()) {
                DOMetadata metadata = getObjectInfo(objectID, null, true);
                File objDir = getObjectDir(objectID);
                String files[] = objDir.list();
                if (files != null) {
                    dbtxn = environment.beginTransaction(null, null);
                    for (String filename : files) {
                        File file = new File(filename);
                        if (file.length() < maxDbElementSize / 2) {
                            String elementID = convertFromFileName(filename);
                            boolean deForceFile = Boolean.parseBoolean(
                                    metadata.getTag(getAttValKey(elementID, INTERNAL_ELEMENT_FILE), null));
                            if (!deForceFile)
                                migrateElementFromFileToDb(metadata, dbtxn, objectID, elementID);
                        }
                    }
                }
                deleteEmptyDirectoriesStartingAt(objDir);
                metadata.setTag("elDb", "");
                setObjectInfo(metadata);
                if (dbtxn != null) {
                    dbtxn.commit();
                    dbtxn = null;
                }
                return metadata;
            }
        } catch (Exception e) {
            if (dbtxn != null) {
                try {
                    dbtxn.abort();
                } catch (DatabaseException ex) {
                    logger.error("Exception closing", ex);
                }
            }
            if (e instanceof CordraException)
                throw (CordraException) e;
            throw new InternalErrorCordraException("Error migrating data elements of " + objectID, e);
        }
    }

    private void migrateElementFromFileToDb(DOMetadata metadata, com.sleepycat.je.Transaction dbtxn, String objectID,
            String elementID) throws CordraException {
        InputStream in = null;
        try {
            File objDir = getObjectDir(objectID);
            File elementFile = new File(objDir, convertToFileName(elementID));
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            if (elementFile.exists()) {
                in = new BufferedInputStream(new FileInputStream(elementFile));
                byte[] buf = new byte[8192];
                int r;
                while ((r = in.read(buf)) > 0) {
                    bout.write(buf, 0, r);
                }
                in.close();
                in = null;
            }
            byte[] key = getElementDbKey(objectID, elementID);
            OperationStatus status = elementDb.put(dbtxn, new DatabaseEntry(key),
                    new DatabaseEntry(bout.toByteArray()));
            if (status != OperationStatus.SUCCESS) {
                throw new InternalErrorCordraException("Unexpected status " + status + " migrating data element " + elementID + " of " + objectID);
            }
            elementFile.delete();
            if (metadata == null) {
                deleteEmptyDirectoriesStartingAt(objDir);
                synchronized (objectID.toLowerCase().intern()) {
                    metadata = getObjectInfo(objectID, null);
                    metadata.setTag("de-exists." + elementID, "true");
                    metadata.setTag("de-file." + elementID, null);
                    metadata.setTag("de-size." + elementID, String.valueOf(bout.size()));
                    setObjectInfo(metadata);
                }
            } else {
                metadata.setTag("de-exists." + elementID, "true");
                metadata.setTag("de-file." + elementID, null);
                metadata.setTag("de-size." + elementID, String.valueOf(bout.size()));
            }
        } catch (Exception e) {
            if (e instanceof CordraException)
                throw (CordraException) e;
            throw new InternalErrorCordraException("Error migrating data element " + elementID + " of " + objectID, e);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    logger.error("Error closing", e);
                }
            }
        }
    }

    private void storeElementMaybeInDb(String objectID, String elementID, InputStream input, boolean append,
            boolean forceFile) throws CordraException {
        com.sleepycat.je.Transaction dbtxn = null;
        FileOutputStream fout = null;
        File elementFile = null;
        try {
            dbtxn = environment.beginTransaction(null, null);
            byte[] key = getElementDbKey(objectID, elementID);
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            if (append) {
                DatabaseEntry dbVal = new DatabaseEntry();
                OperationStatus status = elementDb.get(dbtxn, new DatabaseEntry(key), dbVal, LockMode.RMW);
                if (status == OperationStatus.SUCCESS) {
                    bout.write(dbVal.getData());
                } else if (status != OperationStatus.NOTFOUND) {
                    throw new InternalErrorCordraException("Unexpected status " + status
                            + " retrieving data element " + elementID + " of " + objectID);
                }
            }
            byte[] buf = new byte[8192];
            int r;
            boolean deFile = forceFile || bout.size() > maxDbElementSize;
            if (!deFile) {
                while ((r = input.read(buf)) > 0) {
                    bout.write(buf, 0, r);
                    if (bout.size() > maxDbElementSize) {
                        deFile = true;
                        break;
                    }
                }
            }
            if (deFile) {
                OperationStatus status = elementDb.delete(dbtxn, new DatabaseEntry(key));
                if (status != OperationStatus.SUCCESS && status != OperationStatus.NOTFOUND) {
                    throw new InternalErrorCordraException("Unexpected status " + status + " migrating data element " + elementID + " of " + objectID);
                }
                File objDir = getObjectDir(objectID);
                objDir.mkdirs();
                elementFile = new File(objDir, convertToFileName(elementID));
                fout = new FileOutputStream(elementFile);
                fout.write(bout.toByteArray());
                while ((r = input.read(buf)) > 0) {
                    fout.write(buf, 0, r);
                }
                fout.close();
                fout = null;
                synchronized (objectID.toLowerCase().intern()) {
                    DOMetadata metadata = getObjectInfo(objectID, null);
                    metadata.setTag("de-file." + elementID, "");
                    metadata.setTag("de-size." + elementID, null);
                    setObjectInfo(metadata);
                }
            } else {
                OperationStatus status = elementDb.put(dbtxn, new DatabaseEntry(key),
                        new DatabaseEntry(bout.toByteArray()));
                if (status != OperationStatus.SUCCESS) {
                    throw new InternalErrorCordraException("Unexpected status " + status + " storing data element " + elementID + " of " + objectID);
                }
                synchronized (objectID.toLowerCase().intern()) {
                    DOMetadata metadata = getObjectInfo(objectID, null);
                    metadata.setTag("de-file." + elementID, null);
                    metadata.setTag("de-size." + elementID, String.valueOf(bout.size()));
                    setObjectInfo(metadata);
                }
            }
            dbtxn.commit();
            dbtxn = null;
        } catch (Exception e) {
            if (fout != null) {
                try {
                    fout.close();
                } catch (IOException ex) {
                    logger.error("Exception closing", ex);
                }
            }
            if (elementFile != null) {
                elementFile.delete();
            }
            if (dbtxn != null) {
                try {
                    dbtxn.abort();
                } catch (DatabaseException ex) {
                    logger.error("Exception closing", ex);
                }
            }
            if (e instanceof CordraException)
                throw (CordraException) e;
            throw new InternalErrorCordraException("Error storing data element " + elementID + " of " + objectID, e);
        }
    }

}
