package net.cnri.cordra.storage.s3;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.List;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.*;

import com.google.gson.JsonObject;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CountingInputStream;

import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.AmazonS3Client;
import com.google.gson.Gson;

import net.cnri.cordra.GsonUtility;
import net.cnri.cordra.api.ConflictCordraException;
import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.api.CordraObject;
import net.cnri.cordra.api.NotFoundCordraException;
import net.cnri.cordra.api.Payload;
import net.cnri.cordra.api.SearchResults;
import net.cnri.cordra.api.UncheckedCordraException;
import net.cnri.cordra.collections.AbstractSearchResults;
import net.cnri.cordra.storage.CordraStorage;
import net.cnri.cordra.api.InternalErrorCordraException;

public class S3Storage implements CordraStorage {
    // for testing
    static Integer MAX_KEYS;

    private final static Gson gson = GsonUtility.getGson();
    private AmazonS3Client client;
    private String bucketName;
    private String objectPrefix;
    private String payloadPrefix;

    public S3Storage(AmazonS3Client client, String bucketName, String s3KeyPrefix) {
        initialize(client, bucketName, s3KeyPrefix);
    }

    public S3Storage(JsonObject options) {
        String accessKey = getAsStringOrNull(options, "accessKey");
        String secretKey = getAsStringOrNull(options,"secretKey");
        String bucketNameOption = getAsStringOrNull(options,"bucketName");
        String s3KeyPrefix = getAsStringOrNull(options,"s3KeyPrefix");
        String region = getAsStringOrNull(options,"region");

        AWSCredentials credentials = new BasicAWSCredentials(accessKey, secretKey);
        AmazonS3Client s3Client = (AmazonS3Client) AmazonS3ClientBuilder
                .standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withRegion(region)
                .build();

        initialize(s3Client, bucketNameOption, s3KeyPrefix);
    }

    @SuppressWarnings("hiding")
    private void initialize(AmazonS3Client client, String bucketName, String s3KeyPrefix) {
        this.client = client;
        this.bucketName = bucketName;
        this.objectPrefix = getS3Prefix(s3KeyPrefix, "objects");
        this.payloadPrefix = getS3Prefix(s3KeyPrefix, "payloads");
        createBucketIfMissing();
    }

    private static String getAsStringOrNull(JsonObject options, String propertyName) {
        if (options.has(propertyName)) {
            String result = options.get(propertyName).getAsString();
            return result;
        } else {
            return null;
        }
    }

    private static String getS3Prefix(String s3KeyPrefix, String segment) {
        if (s3KeyPrefix == null || s3KeyPrefix.isEmpty()) return segment + "/";
        if (s3KeyPrefix.endsWith("/")) return s3KeyPrefix + segment + "/";
        return s3KeyPrefix + "/" + segment + "/";
    }

    private void createBucketIfMissing() {
        if (!client.doesBucketExist(bucketName)) {
            client.createBucket(bucketName);
        }
    }

    @Override
    public CordraObject get(String id) throws CordraException {
        String s3Id = getS3IdFor(id);
        return getCordraObjectFromS3Id(s3Id);
    }

    private CordraObject getCordraObjectFromS3Id(String s3Id) throws CordraException {
        if (!doesS3ObjectExist(s3Id)) {
            return null;
        }
        try (S3Object s3Object = client.getObject(bucketName, s3Id)) {
            String json = IOUtils.toString(s3Object.getObjectContent(), "UTF-8");
            CordraObject result = gson.fromJson(json, CordraObject.class);
            return result;
        } catch (IOException | SdkClientException e) {
            throw new InternalErrorCordraException(e);
        }
    }

    private boolean doesS3ObjectExist(String s3Id) throws CordraException {
        try {
            return client.doesObjectExist(bucketName, s3Id);
        } catch (SdkClientException e) {
            throw new InternalErrorCordraException(e);
        }
    }

    @Override
    @SuppressWarnings("resource")
    public InputStream getPayload(String id, String payloadName) throws CordraException {
        String payloadId = getS3PayloadIdFor(id, payloadName);
        if (!doesS3ObjectExist(payloadId)) return null;
        try {
            S3Object s3Object = client.getObject(bucketName, payloadId);
            return s3Object.getObjectContent();
        } catch (SdkClientException e) {
            throw new InternalErrorCordraException(e);
        }
    }

    @Override
    @SuppressWarnings({ "resource", "null" })
    public InputStream getPartialPayload(String id, String payloadName, Long start, Long end) throws CordraException {
        String payloadId = getS3PayloadIdFor(id, payloadName);
        if (!doesS3ObjectExist(payloadId)) return null;
        GetObjectRequest getRequest = new GetObjectRequest(bucketName, payloadId);
        if (start != null || end != null) {
            if (start == null) {
                long size = getObjectSize(payloadId);
                start = size - end;
                if (start > 0) {
                    getRequest.setRange(start);
                }
            } else if (end == null) {
                getRequest.setRange(start);
            } else {
                getRequest.setRange(start, end);
            }
        }
        try {
            S3Object s3Object = client.getObject(getRequest);
            return s3Object.getObjectContent();
        } catch (SdkClientException e) {
            throw new InternalErrorCordraException(e);
        }
    }

    private long getObjectSize(String payloadId) {
        ObjectListing res = client.listObjects(bucketName, payloadId);
        S3ObjectSummary summary = res.getObjectSummaries().get(0);
        return summary.getSize();
    }

    @Override
    public CordraObject create(CordraObject d) throws CordraException {
        String s3Id = getS3IdFor(d.id);
        if (doesS3ObjectExist(s3Id)) {
            throw new ConflictCordraException("Object already exists: " + d.id);
        }
        if (d.payloads != null) {
            for (Payload p : d.payloads) {
                try (InputStream in = p.getInputStream();) {
                    long length = writeInputStreamToS3(in, d.id, p.name);
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
        String json = gson.toJson(d);
        client.putObject(bucketName, s3Id, json);
        return d;
    }

    private long writeInputStreamToS3(InputStream inputStream, String id, String payloadName) {
        String payloadId = getS3PayloadIdFor(id, payloadName);
        CountingInputStream  countingInputStream = new CountingInputStream(inputStream) {
            int stashedByteCount;

            @Override
            public synchronized void mark(int readlimit) {
                stashedByteCount = getCount();
                super.mark(readlimit);
            }

            @Override
            public synchronized void reset() throws IOException {
                super.reset();
                resetCount();
                afterRead(stashedByteCount);
            }
        };
        client.putObject(bucketName, payloadId, countingInputStream, null);
        long length = countingInputStream.getByteCount();
        return length;
    }

    private String getHashPrefixFor(String id) {
        MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
        byte[] bytes = id.getBytes(StandardCharsets.UTF_8);
        byte[] hash = md5.digest(bytes);
        String hexString = Hex.encodeHexString(hash);
        String result = hexString.substring(0, 4);
        return result;
    }

    private String getS3IdFor(String id) {
        String hash = getHashPrefixFor(id);
        return objectPrefix + hash + "-" + id;
    }

    private String getHandleFor(String s3Id) {
        return s3Id.substring(s3Id.indexOf("-") + 1);
    }

    private String getS3PayloadIdFor(String id, String payloadName) {
        String hash = getHashPrefixFor(id);
        // include the length of the id to get ironclad guarantee there is no
        // overlap between the payloads of distinct objects
        return payloadPrefix + hash + "-" + Integer.toHexString(id.length()) + "-" + id + "/" + payloadName;
    }

    @Override
    public CordraObject update(CordraObject d) throws CordraException {
        String s3Id = getS3IdFor(d.id);
        if (!doesS3ObjectExist(s3Id)) {
            throw new NotFoundCordraException("Object does not exist: " + d.id);
        }
        List<String> payloadsToDelete = d.getPayloadsToDelete();
        String id = d.id;
        if (payloadsToDelete != null) {
            for (String payloadName : payloadsToDelete) {
                String payloadId = getS3PayloadIdFor(id, payloadName);
                client.deleteObject(bucketName, payloadId);
            }
        }
        d.clearPayloadsToDelete();
        if (d.payloads != null) {
            for (Payload p : d.payloads) {
                if (p.getInputStream() == null) continue;
                try (InputStream in = p.getInputStream();) {
                    long length = writeInputStreamToS3(in, d.id, p.name);
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
        String json = gson.toJson(d);
        client.putObject(bucketName, s3Id, json);
        return d;
    }

    @Override
    public void delete(String id) throws CordraException {
        String s3Id = getS3IdFor(id);
        if (!doesS3ObjectExist(s3Id)) {
            throw new NotFoundCordraException("Object does not exist: " + id);
        }
        CordraObject d = this.get(id);
        client.deleteObject(bucketName, s3Id);
        if (d.payloads != null) {
            for (Payload p : d.payloads) {
                String s3PayloadId = this.getS3PayloadIdFor(id, p.name);
                client.deleteObject(bucketName, s3PayloadId);
            }
        }
    }

    @Override
    public SearchResults<CordraObject> list() throws CordraException {
        return new S3ListSearchResults();
    }

    @Override
    public SearchResults<String> listHandles() throws CordraException {
        return new S3ListHandlesSearchResults();
    }

    @Override
    public void close() {
        client.shutdown();
    }

    private class S3ListSearchResults extends AbstractSearchResults<CordraObject> {
        private ObjectListing objectListing;
        private Iterator<S3ObjectSummary> iter;

        public S3ListSearchResults() {
            ListObjectsRequest request = new ListObjectsRequest(bucketName, objectPrefix, null, null, MAX_KEYS);
            objectListing = client.listObjects(request);
            iter = objectListing.getObjectSummaries().iterator();
        }

        @Override
        public int size() {
            return -1;
        }

        @Override
        protected CordraObject computeNext() {
            while (true) {
                if (iter.hasNext()) {
                    S3ObjectSummary objectSummary = iter.next();
                    String s3Id = objectSummary.getKey();
                    try {
                        return getCordraObjectFromS3Id(s3Id);
                    } catch (CordraException e) {
                        throw new UncheckedCordraException(e);
                    }
                } else {
                    if (!objectListing.isTruncated()) {
                        return null;
                    }
                    objectListing = client.listNextBatchOfObjects(objectListing);
                    iter = objectListing.getObjectSummaries().iterator();
                }
            }
        }
    }

    private class S3ListHandlesSearchResults extends AbstractSearchResults<String> {
        private ObjectListing objectListing;
        private Iterator<S3ObjectSummary> iter;

        public S3ListHandlesSearchResults() {
            ListObjectsRequest request = new ListObjectsRequest(bucketName, objectPrefix, null, null, MAX_KEYS);
            objectListing = client.listObjects(request);
            iter = objectListing.getObjectSummaries().iterator();
        }

        @Override
        public int size() {
            return -1;
        }

        @Override
        protected String computeNext() {
            while (true) {
                if (iter.hasNext()) {
                    S3ObjectSummary objectSummary = iter.next();
                    String s3Id = objectSummary.getKey();
                    return getHandleFor(s3Id);
                } else {
                    if (!objectListing.isTruncated()) {
                        return null;
                    }
                    objectListing = client.listNextBatchOfObjects(objectListing);
                    iter = objectListing.getObjectSummaries().iterator();
                }
            }
        }
    }

}
