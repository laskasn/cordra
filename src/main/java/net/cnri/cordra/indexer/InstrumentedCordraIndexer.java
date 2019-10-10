package net.cnri.cordra.indexer;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

import net.cnri.cordra.api.CordraObject;
import net.cnri.cordra.api.QueryParams;
import net.cnri.cordra.api.SearchResults;
import net.cnri.util.LoggingUtil;

public class InstrumentedCordraIndexer extends DelegatingCordraIndexer {

    private static Logger logger = LoggerFactory.getLogger(InstrumentedCordraIndexer.class);
    
    public InstrumentedCordraIndexer(CordraIndexer delegate) {
        super(delegate);
    }
    
    @Override
    public void indexObject(String cordraServiceId, CordraObject co, boolean indexPayloads, Map<String, JsonNode> pointerToSchemaMap) throws IndexerException {
        run(() -> {
            super.indexObject(cordraServiceId, co, indexPayloads, pointerToSchemaMap);
        });
    }

    @Override
    public void indexObjects(String cordraServiceId, List<CordraObjectWithIndexDetails> batchWithDetails) throws IndexerException {
        run(() -> {
            super.indexObjects(cordraServiceId, batchWithDetails);
        });
    }

    @Override
    public void deleteObject(CordraObject co) throws IndexerException {
        run(() -> {
            super.deleteObject(co);
        });
    }
    
    @Override
    public void deleteObject(String handle) throws IndexerException {
        run(() -> {
            super.deleteObject(handle);
        });
    }
    
    @Override
    public SearchResults<CordraObject> search(String query) throws IndexerException {
        return run(() -> {
            return super.search(query);
        });
    }
    
    @Override
    public SearchResults<String> searchHandles(String query) throws IndexerException {
        return run(() -> {
            return super.searchHandles(query);
        });
    }

    @Override
    public SearchResults<IdType> searchIdType(String query) throws IndexerException {
        return run(() -> {
            return super.searchIdType(query);
        });
    }

    @Override
    public SearchResults<CordraObject> search(String query, QueryParams params) throws IndexerException {
        return run(() -> {
            return super.search(query, params);
        });
    }

    @Override
    public SearchResults<String> searchHandles(String query, QueryParams params) throws IndexerException {
        return run(() -> {
            return super.searchHandles(query, params);
        });
    }
    
    @Override
    public void ensureIndexUpToDate() throws IndexerException {
        run(() -> {
            super.ensureIndexUpToDate();
        });
    }
    
    public static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX").withZone(ZoneOffset.UTC);
    
    public <R> R run(Callable<R> c) throws IndexerException {
        long start = System.currentTimeMillis();
        try {
            R result = c.call();
            return result;
        } catch (RuntimeException | Error | IndexerException e) {
            throw e;
        } catch (Exception e) {
            throw new AssertionError("Doesn't actually throw this", e);
        } finally {
            long end = System.currentTimeMillis();
            long delta = end - start;
            String caller = LoggingUtil.getCallingFunction();
            String startTime = dateTimeFormatter.format(Instant.ofEpochMilli(start));
            logger.trace(caller + ": start " + startTime + ", " + delta + "ms");
        }
    }
    
    public void run(ThrowingRunnable r) throws IndexerException {
        long start = System.currentTimeMillis();
        try {
            r.run();
        } catch (RuntimeException | Error | IndexerException e) {
            throw e;
        } catch (Exception e) {
            throw new AssertionError("Doesn't actually throw this", e);
        } finally {
            long end = System.currentTimeMillis();
            long delta = end - start;
            String caller = LoggingUtil.getCallingFunction();
            String startTime = dateTimeFormatter.format(Instant.ofEpochMilli(start));
            logger.trace(caller + ": start " + startTime + ", " + delta + "ms");
        }
    }
    
    @FunctionalInterface
    public static interface ThrowingRunnable {
        void run() throws Exception;
    }
    
}
