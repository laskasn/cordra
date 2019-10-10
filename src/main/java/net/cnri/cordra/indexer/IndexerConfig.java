package net.cnri.cordra.indexer;

import java.util.HashMap;
import java.util.Map;

public class IndexerConfig {
    public String module = "lucene"; // solr | lucene | elasticsearch | memory
    public Map<String, String> options = new HashMap<>();
//    baseUri; //optional used by solr single instance
//    zkHosts; //optional used by solr cluster e.g."zkServerA:2181,zkServerB:2181,zkServerC:2181"
//    isStoreFields = true;

    public static IndexerConfig getNewDefaultInstance() {
        IndexerConfig indexerConfig = new IndexerConfig();
        indexerConfig.module = "lucene";
        indexerConfig.options.put("isStoreFields", "true");
        return indexerConfig;
    }
}
