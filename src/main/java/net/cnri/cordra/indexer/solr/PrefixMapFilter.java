package net.cnri.cordra.indexer.solr;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Map;
import java.util.Set;
import java.util.Iterator;

public class PrefixMapFilter extends AbstractMap<String,String> {
    private final Map<String, Object> map;
    private final String prefix;

    public PrefixMapFilter(Map<String, Object> map, String prefix) {
        this.map = map;
        this.prefix = prefix;
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int size() {
        int i = 0;
        for(String key : map.keySet()) {
            if(key.startsWith(prefix)) i++;
        }
        return i;
    }

    @Override
    public boolean containsKey(Object key) {
        if(!(key instanceof String)) return false;
        String s = (String)key;
        return map.get(prefix + s) != null;
    }

    @Override
    public String get(Object key) {
        if(!(key instanceof String)) return null;
        String s = (String)key;
        return (String) map.get(prefix + s);
    }

    @Override
    public Set<Map.Entry<String,String>> entrySet() {
        return new AbstractSet<Map.Entry<String,String>>() {
            @Override
            public Iterator<Map.Entry<String,String>> iterator() {
                return new Iterator<Map.Entry<String,String>>() {
                    Iterator<Map.Entry<String, Object>> iter = map.entrySet().iterator();
                    Map.Entry<String,String> next;

                    private void advanceToNext() {
                        if(next!=null) return;
                        while(iter.hasNext()) {
                            Map.Entry<String, Object> item = iter.next();
                            String nextHeaderName = item.getKey();
                            if(nextHeaderName.startsWith(prefix)) {
                                String nextName = nextHeaderName.substring(prefix.length());
                                next = new MapEntry(nextName, (String) item.getValue());
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
                return PrefixMapFilter.this.size();
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
