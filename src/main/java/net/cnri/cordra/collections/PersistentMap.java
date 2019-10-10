/*************************************************************************\
    Copyright (c) 2019 Corporation for National Research Initiatives;
                        All rights reserved.
\*************************************************************************/

package net.cnri.cordra.collections;

import com.google.gson.Gson;
import com.sleepycat.je.*;
import net.cnri.cordra.GsonUtility;
import net.cnri.microservices.ConcurrentCountingHashMap;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class PersistentMap<K, V> extends AbstractMap<K, V> implements Map<K,V>, ConcurrentMap<K, V>, Closeable {
    private static Object environmentsLock = new Object();
    private static ConcurrentMap<String, Environment> environments = new ConcurrentHashMap<>();
    private static ConcurrentCountingHashMap<String> databasesOpen = new ConcurrentCountingHashMap<>();

    private String path;
    private Environment dbEnvironment = null;
    private Database db = null;
    private Class<K> keyClass;
    private Class<V> valueClass;
    private Gson gson;
    private static final String DEFAULT_DB_NAME = "persistentMap";

    transient volatile CloseableSet<Map.Entry<K, V>> entrySet;
    transient volatile CloseableSet<K> keySet;
    transient volatile CloseableCollection<V> values;

    public PersistentMap(File dir, Class<K> keyClass, Class<V> valueClass) {
        this(dir, DEFAULT_DB_NAME, keyClass, valueClass);
    }

    public PersistentMap(File dir, String dbName, Class<K> keyClass, Class<V> valueClass, boolean readOnly) {
        this.gson = GsonUtility.getGson();
        this.keyClass = keyClass;
        this.valueClass = valueClass;
        path = dir.getAbsolutePath();
        dbEnvironment = getOrCreateEnvironment(dir, readOnly);
        DatabaseConfig dbConfig = new DatabaseConfig();
        boolean allowCreate = !readOnly;
        dbConfig.setAllowCreate(allowCreate);
        dbConfig.setTransactional(true);
        dbConfig.setReadOnly(readOnly);
        db = dbEnvironment.openDatabase(null, dbName, dbConfig);
    }

    public PersistentMap(File dir, String dbName, Class<K> keyClass, Class<V> valueClass) {
        this(dir, dbName, keyClass, valueClass, false);
    }

    private static Environment getOrCreateEnvironment(File dir, boolean readOnly) {
        String path = dir.getAbsolutePath();
        synchronized (environmentsLock) {
            databasesOpen.incrementAndGet(path);
            Environment existingEnvironment = environments.get(path);
            if (existingEnvironment == null) {
                File dbDir = new File(dir, "db");
                if(!dbDir.exists()) {
                    dbDir.mkdirs();
                }
                EnvironmentConfig envConfig = new EnvironmentConfig();
                boolean allowCreate = !readOnly;
                envConfig.setAllowCreate(allowCreate);
                envConfig.setSharedCache(true);
                envConfig.setTransactional(true);
                envConfig.setReadOnly(readOnly);
                envConfig.setConfigParam(EnvironmentConfig.FREE_DISK, "0");
                try {
                    Environment newEnvironment = new Environment(dbDir.getCanonicalFile(), envConfig);
                    environments.put(path, newEnvironment);
                    return newEnvironment;
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            } else {
                return existingEnvironment;
            }
        }
    }

    @Override
    public void close() {
        db.close();
        synchronized (environmentsLock) {
            int leftOpen = databasesOpen.decrementAndGet(path);
            if (leftOpen == 0) {
                dbEnvironment.close();
                environments.remove(path);
            }
        }
    }

    @Override
    public CloseableSet<java.util.Map.Entry<K, V>> entrySet() {
        return new EntrySet();
    }

    @Override
    public V get(Object key) {
        @SuppressWarnings("unchecked")
        DatabaseEntry keyDbEntry = fromKey((K) key);
        DatabaseEntry valueDbEntry = new DatabaseEntry();
        OperationStatus getStatus = db.get(null, keyDbEntry, valueDbEntry, null);
        V res;
        if (getStatus == OperationStatus.NOTFOUND) {
            res = null;
        } else {
            res = toValue(valueDbEntry);
        }
        return res;
    }

    @Override
    public boolean containsKey(Object key) {
        @SuppressWarnings("unchecked")
        DatabaseEntry keyDbEntry = fromKey((K) key);
        DatabaseEntry valueDbEntry = new DatabaseEntry();
        OperationStatus getStatus = db.get(null, keyDbEntry, valueDbEntry, null);
        if (getStatus == OperationStatus.NOTFOUND) {
            return false;
        } else {
            return true;
        }
    }

    @Override
    public boolean isEmpty() {
        Cursor cursor = db.openCursor(null, null);
        try {
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry data = new DatabaseEntry();
            OperationStatus status = cursor.getFirst(key, data, null);
            return status == OperationStatus.NOTFOUND;
        } finally {
            cursor.close();
        }
    }

    @Override
    public V put(K key, V value) {
        Transaction txn = dbEnvironment.beginTransaction(null, null);
        V res;
        try {
            DatabaseEntry keyDbEntry = fromKey(key);
            DatabaseEntry oldValueDbEntry = new DatabaseEntry();
            OperationStatus getStatus = db.get(txn, keyDbEntry, oldValueDbEntry, LockMode.RMW);
            if (getStatus == OperationStatus.NOTFOUND) {
                res = null;
            } else {
                res = toValue(oldValueDbEntry);
            }
            DatabaseEntry dataDbEntry = fromValue(value);
            db.put(txn, keyDbEntry, dataDbEntry);
            txn.commit();
        } catch (Exception e) {
            txn.abort();
            throw e;
        }
        return res;
    }

    @Override
    public V remove(Object key) {
        Transaction txn = dbEnvironment.beginTransaction(null, null);
        V res;
        try {
            @SuppressWarnings("unchecked")
            DatabaseEntry keyDbEntry = fromKey((K)key);
            DatabaseEntry oldValueDbEntry = new DatabaseEntry();
            OperationStatus getStatus = db.get(txn, keyDbEntry, oldValueDbEntry, LockMode.RMW);
            if (getStatus == OperationStatus.NOTFOUND) {
                res = null;
            } else {
                res = toValue(oldValueDbEntry);
            }
            db.delete(txn, keyDbEntry);
            txn.commit();
        } catch (Exception e) {
            txn.abort();
            throw e;
        }
        return res;
    }

    @Override
    public V putIfAbsent(K key, V value) {
        OperationStatus status = db.putNoOverwrite(null, fromKey(key), fromValue(value));
        if (status == OperationStatus.SUCCESS) {
           return null;
        }
        Transaction txn = dbEnvironment.beginTransaction(null, null);
        V res;
        try {
            DatabaseEntry keyDbEntry = fromKey(key);
            DatabaseEntry oldValueDbEntry = new DatabaseEntry();
            OperationStatus getStatus = db.get(txn, keyDbEntry, oldValueDbEntry, LockMode.RMW);
            if (getStatus == OperationStatus.NOTFOUND) {
                res = null;
                DatabaseEntry dataDbEntry = fromValue(value);
                db.put(txn, keyDbEntry, dataDbEntry);
            } else {
                res = toValue(oldValueDbEntry);
            }
            txn.commit();
        } catch (Exception e) {
            txn.abort();
            throw e;
        }
        return res;
    }

    @Override
    public boolean remove(Object key, Object value) {
        Transaction txn = dbEnvironment.beginTransaction(null, null);
        boolean res;
        try {
            @SuppressWarnings("unchecked")
            DatabaseEntry keyDbEntry = fromKey((K)key);
            DatabaseEntry oldValueDbEntry = new DatabaseEntry();
            OperationStatus getStatus = db.get(txn, keyDbEntry, oldValueDbEntry, LockMode.RMW);
            if (getStatus == OperationStatus.NOTFOUND) {
                res = false;
            } else {
                db.delete(txn, keyDbEntry);
                res = true;
            }
            txn.commit();
        } catch (Exception e) {
            txn.abort();
            throw e;
        }
        return res;
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        Transaction txn = dbEnvironment.beginTransaction(null, null);
        boolean res;
        try {
            DatabaseEntry keyDbEntry = fromKey(key);
            DatabaseEntry oldValueDbEntry = new DatabaseEntry();
            OperationStatus getStatus = db.get(txn, keyDbEntry, oldValueDbEntry, LockMode.RMW);
            if (getStatus == OperationStatus.NOTFOUND) {
                res = false;
            } else {
                V actualOldValue = toValue(oldValueDbEntry);
                if (oldValue.equals(actualOldValue)) {
                    DatabaseEntry dataDbEntry = fromValue(newValue);
                    db.put(txn, keyDbEntry, dataDbEntry);
                    res = true;
                } else {
                    res = false;
                }
            }
            txn.commit();
        } catch (Exception e) {
            txn.abort();
            throw e;
        }
        return res;
    }

    @Override
    public V replace(K key, V value) {
        Transaction txn = dbEnvironment.beginTransaction(null, null);
        V res;
        try {
            DatabaseEntry keyDbEntry = fromKey(key);
            DatabaseEntry oldValueDbEntry = new DatabaseEntry();
            OperationStatus getStatus = db.get(txn, keyDbEntry, oldValueDbEntry, LockMode.RMW);
            if (getStatus == OperationStatus.NOTFOUND) {
                res = null;
            } else {
                res = toValue(oldValueDbEntry);
                DatabaseEntry dataDbEntry = fromValue(value);
                db.put(txn, keyDbEntry, dataDbEntry);
            }
            txn.commit();
        } catch (Exception e) {
            txn.abort();
            throw e;
        }
        return res;
    }

    @Override
    public void clear() {
        Transaction txn = dbEnvironment.beginTransaction(null, null);
        try {
            Cursor cursor = db.openCursor(txn, null);
            try {
                DatabaseEntry key = new DatabaseEntry();
                DatabaseEntry data = new DatabaseEntry();
                OperationStatus status = cursor.getFirst(key, data, null);
                while (status == OperationStatus.SUCCESS) {
                    cursor.delete();
                    status = cursor.getNext(key, data, null);
                }
            } finally {
                cursor.close();
            }
            txn.commit();
        } catch (Exception e) {
            txn.abort();
            throw e;
        }
    }

    @Override
    public boolean containsValue(Object value) {
        CloseableIterator<Map.Entry<K, V>> iter = entrySet().iterator();
        try {
            while (iter.hasNext()) {
                Map.Entry<K, V> entry = iter.next();
                if (value.equals(entry.getValue())) {
                    return true;
                }
            }
            return false;
        } finally {
            iter.close();
        }
    }

    @Override
    public CloseableSet<K> keySet() {
        if (keySet == null) keySet = new KeySet();
        return keySet;
    }

    @Override
    public CloseableCollection<V> values() {
        if (values == null) values = new ValuesCollection();
        return values;
    }

    private class EntrySet extends AbstractSet<Map.Entry<K, V>> implements CloseableSet<Map.Entry<K, V>> {
        @Override
        public CloseableIterator<java.util.Map.Entry<K, V>> iterator() {
            return new EntryIterator();
        }

        @Override
        public int size() {
            return (int) db.count();
        }
    }

    private class KeySet extends AbstractSet<K> implements CloseableSet<K> {
        @Override
        public CloseableIterator<K> iterator() {
            return new KeyIterator();
        }

        @Override
        public int size() {
            return PersistentMap.this.size();
        }

        @Override
        public boolean isEmpty() {
            return PersistentMap.this.isEmpty();
        }

        @Override
        public void clear() {
            PersistentMap.this.clear();
        }

        @Override
        public boolean contains(Object k) {
            return PersistentMap.this.containsKey(k);
        }
    }

    private class ValuesCollection extends AbstractCollection<V> implements CloseableCollection<V> {
        @Override
        public CloseableIterator<V> iterator() {
            return new ValuesIterator();
        }

        @Override
        public int size() {
            return PersistentMap.this.size();
        }

        @Override
        public boolean isEmpty() {
            return PersistentMap.this.isEmpty();
        }

        @Override
        public void clear() {
            PersistentMap.this.clear();
        }

        @Override
        public boolean contains(Object v) {
            return PersistentMap.this.containsValue(v);
        }
    }

    private class EntryIterator implements CloseableIterator<Map.Entry<K, V>> {
        private Cursor cursor;
        private K lastKey = null;
        private DatabaseEntry key = new DatabaseEntry();
        private DatabaseEntry data = new DatabaseEntry();
        private boolean done = false;

        public EntryIterator() {
            cursor = db.openCursor(null, null);
            OperationStatus status = cursor.getFirst(key, data, null);
            if (status == OperationStatus.NOTFOUND) {
                done = true;
                cursor.close();
            }
        }

        @Override
        public void close() {
            cursor.close();
        }

        @Override
        public boolean hasNext() {
            return !done;
        }

        @Override
        public java.util.Map.Entry<K, V> next() {
            if (done) throw new NoSuchElementException();
            Map.Entry<K, V> res = toEntry(key, data);
            OperationStatus status = cursor.getNext(key, data, null);
            if (status == OperationStatus.NOTFOUND) {
                done = true;
                cursor.close();
            }
            lastKey = res.getKey();
            return res;
        }

        @Override
        public void remove() {
            if (lastKey == null) throw new IllegalStateException();
            db.delete(null, fromKey(lastKey));
        }
    }

    private class KeyIterator implements CloseableIterator<K> {
        private Cursor cursor;
        private K lastKey = null;
        private DatabaseEntry key = new DatabaseEntry();
        private DatabaseEntry data = new DatabaseEntry();
        private boolean done = false;

        public KeyIterator() {
            data.setPartial(0, 0, true);
            cursor = db.openCursor(null, null);
            OperationStatus status = cursor.getFirst(key, data, null);
            if (status == OperationStatus.NOTFOUND) {
                done = true;
                cursor.close();
            }
        }

        @Override
        public void close() {
            cursor.close();
        }

        @Override
        public boolean hasNext() {
            return !done;
        }

        @Override
        public K next() {
            if (done) throw new NoSuchElementException();
            K res = toKey(key);
            OperationStatus status = cursor.getNext(key, data, null);
            if (status == OperationStatus.NOTFOUND) {
                done = true;
                cursor.close();
            }
            lastKey = res;
            return res;
        }

        @Override
        public void remove() {
            if (lastKey == null) throw new IllegalStateException();
            db.delete(null, fromKey(lastKey));
        }
    }

    private class ValuesIterator implements CloseableIterator<V> {
        private Cursor cursor;
        private K lastKey = null;
        private DatabaseEntry key = new DatabaseEntry();
        private DatabaseEntry data = new DatabaseEntry();
        private boolean done = false;

        public ValuesIterator() {
            cursor = db.openCursor(null, null);
            OperationStatus status = cursor.getFirst(key, data, null);
            if (status == OperationStatus.NOTFOUND) {
                done = true;
                cursor.close();
            }
        }

        @Override
        public void close() {
            cursor.close();
        }

        @Override
        public boolean hasNext() {
            return !done;
        }

        @Override
        public V next() {
            if (done) throw new NoSuchElementException();
            V res = toValue(data);
            lastKey = toKey(key);
            OperationStatus status = cursor.getNext(key, data, null);
            if (status == OperationStatus.NOTFOUND) {
                done = true;
                cursor.close();
            }
            return res;
        }

        @Override
        public void remove() {
            if (lastKey == null) throw new IllegalStateException();
            db.delete(null, fromKey(lastKey));
        }
    }

    @SuppressWarnings("unchecked")
    K toKey(DatabaseEntry keyDatabaseEntry) {
        if (keyClass == Integer.class) {
            return (K)intFromByteArray(keyDatabaseEntry.getData());
        } else if (keyClass == Long.class) {
            return (K)longFromByteArray(keyDatabaseEntry.getData());
        } else if (keyClass == String.class) {
            return (K)stringFromByteArray(keyDatabaseEntry.getData());
        } else {
            throw new AssertionError("Unexpected keyClass");
        }
    }

    DatabaseEntry fromKey(K key) {
        if (keyClass == Integer.class) {
            return new DatabaseEntry(toByteArray((Integer)key));
        } else if (keyClass == Long.class) {
            return new DatabaseEntry(toByteArray((Long)key));
        } else if (keyClass == String.class) {
            return new DatabaseEntry(toByteArray((String)key));
        } else {
            throw new AssertionError("Unexpected keyClass");
        }
    }

    V toValue(DatabaseEntry valueDatabaseEntry) {
        return gson.fromJson(stringFromByteArray(valueDatabaseEntry.getData()), valueClass);
    }

    DatabaseEntry fromValue(V value) {
        return new DatabaseEntry(toByteArray(gson.toJson(value)));
    }

    Map.Entry<K, V> toEntry(DatabaseEntry keyDatabaseEntry, DatabaseEntry valueDatabaseEntry) {
        K key = toKey(keyDatabaseEntry);
        V value = toValue(valueDatabaseEntry);
        return new AbstractMap.SimpleEntry<K, V>(key, value) {
            @Override
            public V setValue(V valueParam) {
                put(key, valueParam);
                return super.setValue(valueParam);
            }
        };
    }

    static byte[] toByteArray(int data) {
        return new byte[] {
        (byte)((data >> 24) & 0xff),
        (byte)((data >> 16) & 0xff),
        (byte)((data >> 8) & 0xff),
        (byte)((data >> 0) & 0xff),
        };
    }

    static Integer intFromByteArray(byte[] bytes) {
        return (bytes[0] & 0xFF) << 24 | (bytes[1] & 0xFF) << 16 | (bytes[2] & 0xFF) << 8 | (bytes[3] & 0xFF);
    }

    static byte[] toByteArray(long data) {
        return new byte[] {
        (byte)((data >> 56) & 0xff),
        (byte)((data >> 48) & 0xff),
        (byte)((data >> 40) & 0xff),
        (byte)((data >> 32) & 0xff),
        (byte)((data >> 24) & 0xff),
        (byte)((data >> 16) & 0xff),
        (byte)((data >> 8) & 0xff),
        (byte)((data >> 0) & 0xff),
        };
    }

    static Long longFromByteArray(byte[] bytes) {
        long result = 0;
        for (int i = 0; i < bytes.length; i++) {
            result = (result << 8) + (bytes[i] & 0xff);
        }
        return result;
    }

    static byte[] toByteArray(String s) {
        try {
            return s.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }

    static String stringFromByteArray(byte[] bytes) {
        try {
            return new String(bytes, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }

    public static interface CloseableIterator<T> extends Iterator<T>, Closeable {
        @Override
        public void close();
    }
    public static interface CloseableSet<T> extends Set<T> {
        @Override
        public CloseableIterator<T> iterator();
    }
    public static interface CloseableCollection<T> extends Collection<T> {
        @Override
        public CloseableIterator<T> iterator();
    }

    /**
     * A convenience method to use an iterator in a for-each loop.  To correctly use with a {@link CloseableIterator}:
     * <code><pre>
        CloseableIterator&lt;K&gt; iter = map.keySet().iterator();
        try {
            for(K key : PersistentMap.forEach(iter)) {
                // ...
            }
        } finally {
            iter.close();
        }
     * </pre></code>
     *
     * Note that the returned Iterable does not allow its {@code iterator()} method to return a fresh iterator each time; the iteration is once-only.
     *
     * @param iter an iterator
     */
    public static <T> Iterable<T> forEach(final Iterator<T> iter) {
        return new Iterable<T>() {
            @Override
            public Iterator<T> iterator() {
                return iter;
            }
        };
    }
}
