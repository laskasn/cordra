package net.cnri.cordra.indexer.elasticsearch;

import net.cnri.cordra.api.InternalErrorCordraException;
import net.cnri.cordra.api.UncheckedCordraException;
import net.cnri.cordra.collections.AbstractSearchResults;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.SearchHit;

import java.io.IOException;
import java.util.Iterator;

public class ElasticScrollableSearchResults extends AbstractSearchResults<SearchHit> {

    private SearchResponse results;
    private Iterator<SearchHit> hitsIter;
    private RestHighLevelClient client;

    public ElasticScrollableSearchResults(SearchResponse results, RestHighLevelClient client) {
        this.results = results;
        this.client = client;
    }

    @Override
    public int size() {
        return (int) results.getHits().getTotalHits();
    }

    @Override
    protected SearchHit computeNext() {
        if (hitsIter == null) {
            hitsIter = results.getHits().iterator();
        }
        if (hitsIter.hasNext()) {
            return hitsIter.next();
        } else {
            try {
                results = getNextScrollResults(results.getScrollId());
                if (results == null) {
                    return null;
                } else {
                    hitsIter = results.getHits().iterator();
                    if (hitsIter.hasNext()) {
                        return hitsIter.next();
                    } else {
                        return null;
                    }
                }
            } catch (Exception e) {
                throw new UncheckedCordraException(new InternalErrorCordraException(e));
            }
        }
    }

    private SearchResponse getNextScrollResults(String scrollId) throws IOException {
        if (scrollId == null) {
            return null;
        }
        SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollId);
        scrollRequest.scroll("60s");
        return client.scroll(scrollRequest, RequestOptions.DEFAULT);
    }

    @Override
    protected void closeOnlyOnce() {
        if (results != null) {
            String scrollId = results.getScrollId();
            if (scrollId != null) {
                ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
                clearScrollRequest.addScrollId(scrollId);
                try {
                    client.clearScroll(clearScrollRequest, RequestOptions.DEFAULT);
                } catch (Exception e) {
                    // ignore
                }
            }
        }
    }
}
