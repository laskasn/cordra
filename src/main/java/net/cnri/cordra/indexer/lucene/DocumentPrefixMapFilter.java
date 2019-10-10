package net.cnri.cordra.indexer.lucene;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;

public class DocumentPrefixMapFilter extends AbstractMap<String,String> {
    private final Document doc;
    private final String prefix;

    public DocumentPrefixMapFilter(Document doc, String prefix) {
        this.doc = doc;
        this.prefix = prefix;
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int size() {
        int i = 0;
        for (IndexableField field : doc.getFields()) {
            if(field.name().startsWith(prefix)) {
                i++;
            }
        }
        return i;
    }

    @Override
    public boolean containsKey(Object key) {
        if(!(key instanceof String)) return false;
        String s = (String)key;
        return doc.get(prefix + s) != null;
    }

    @Override
    public String get(Object key) {
        if(!(key instanceof String)) return null;
        String s = (String)key;
        return doc.get(prefix + s);
    }

    @Override
    public Set<Map.Entry<String,String>> entrySet() {
        return new AbstractSet<Map.Entry<String,String>>() {
            @Override
            public Iterator<Map.Entry<String,String>> iterator() {
                return new Iterator<Map.Entry<String,String>>() {
                    Iterator<IndexableField> iter = doc.iterator();
                    Map.Entry<String,String> next;

                    private void advanceToNext() {
                        if (next != null) return;
                        while(iter.hasNext()) {
                            IndexableField field = iter.next();
                            String fieldName = field.name();
                            if (fieldName.startsWith(prefix)) {
                                String nextName = fieldName.substring(prefix.length());
                                next = new MapEntry(nextName, field.stringValue());
                                return;
                            }
                        }
                    }

                    @Override
                    public boolean hasNext() {
                        advanceToNext();
                        return next!=null;
                    }

                    @Override
                    public Map.Entry<String,String> next() {
                        advanceToNext();
                        Map.Entry<String,String> res = next;
                        next = null;
                        return res;
                    }

                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }

            @Override
            public int size() {
                return DocumentPrefixMapFilter.this.size();
            }
        };
    }

    private static class MapEntry implements Map.Entry<String,String> {
        final String key, value;

        MapEntry(String key, String value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public String getKey() {
            return key;
        }

        @Override
        public String getValue() {
            return value;
        }

        @Override
        public String setValue(String value) {
            throw new UnsupportedOperationException();
        }
    }
}
