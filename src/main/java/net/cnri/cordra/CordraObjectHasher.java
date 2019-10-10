/*************************************************************************\
    Copyright (c) 2019 Corporation for National Research Initiatives;
                        All rights reserved.
\*************************************************************************/

package net.cnri.cordra;

import com.google.common.hash.Hashing;
import com.google.common.hash.HashingInputStream;
import com.google.common.io.BaseEncoding;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.api.CordraObject;
import net.cnri.cordra.api.Payload;
import net.cnri.cordra.storage.CordraStorage;
import net.cnri.cordra.util.DeleteOnCloseTempFileInputStream;
import net.cnri.cordra.util.JsonCanonicalizer;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class CordraObjectHasher {

    private static final String TEMP_FILE_PREFIX = "payload-temp-files";

    public void generateAllHashesAndSetThemOnTheCordraObject(CordraObject co, CordraStorage storage) throws CordraException {
        JsonObject existingPayloadHashes = new JsonObject();
        if (co.metadata.hashes != null) {
            if (co.metadata.hashes.has("payloads")) {
                existingPayloadHashes = co.metadata.hashes.get("payloads").getAsJsonObject();
            }
        }
        addHashesForStoredPayloadsThatAreMissingHashes(co, existingPayloadHashes, storage);
        removePayloadHashesForPayloadsThatDontExist(co, existingPayloadHashes);
        JsonObject hashes = generateContentAndUserMetataHashes(co);
        if (!existingPayloadHashes.entrySet().isEmpty()) {
            hashes.add("payloads", existingPayloadHashes);
        } else {
            hashes.remove("payloads");
        }
        co.metadata.hashes = hashes;
        if (co.payloads != null && co.payloads.size() > 0) {
            try {
                JsonObject newPayloadHashes = copyIncomingPayloadsToTempFilesAndGenerateHashes(co);
                if (!co.metadata.hashes.has("payloads")) {
                    co.metadata.hashes.add("payloads", newPayloadHashes);
                } else {
                    for (String payloadName : newPayloadHashes.keySet()) {
                        existingPayloadHashes.add(payloadName, newPayloadHashes.get(payloadName));
                    }
                    co.metadata.hashes.add("payloads", existingPayloadHashes);
                }
            } catch (IOException e) {
                throw new CordraException(e);
            }
        }
        String fullHash = generateFullHashFromExistingHashes(co);
        co.metadata.hashes.addProperty("full", fullHash);
    }

    private JsonObject generateContentAndUserMetataHashes(CordraObject co) {
        JsonObject result = new JsonObject();
        result.addProperty("alg", "SHA-256");
        String contentHash = generateContentHash(co);
        result.addProperty("content", contentHash);
        if (co.userMetadata != null) {
            String userMetadataHash = hashJson(co.userMetadata);
            result.addProperty("userMetadata", userMetadataHash);
        }
        return result;
    }

    private String generateContentHash(CordraObject co) {
        return hashJson(co.content);
    }

    /*
     * Expects all specific hashes and payload hashes to be present in metadata.hashes
     */
    private String generateFullHashFromExistingHashes(CordraObject co) {
        JsonObject coJson = GsonUtility.getGson().toJsonTree(co).getAsJsonObject();
        return generateFullHashFromExistingHashes(coJson);
    }

    private String generateFullHashFromExistingHashes(JsonObject coJson) {
        if (coJson.has("metadata")) {
            JsonObject metadata = coJson.get("metadata").getAsJsonObject();
            JsonObject limitedMetadata = new JsonObject();
            if (metadata.has("hashes")) {
                JsonObject hashes = metadata.get("hashes").getAsJsonObject();
                if (hashes.has("payloads")) {
                    JsonObject limitedHashes = new JsonObject();
                    limitedHashes.add("payloads", hashes.get("payloads"));
                    limitedMetadata.add("hashes", limitedHashes);
                }
            }
            coJson.add("metadata", limitedMetadata);
        }
        String fullHash = hashJson(coJson);
        return fullHash;
    }

    public static String hashJson(JsonElement element) {
        String canonicalJson = JsonCanonicalizer.canonicalize(element);
        String hash = hashString(canonicalJson);
        return hash;
    }

    private static String hashString(String s) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
        md.update(s.getBytes(StandardCharsets.UTF_8));
        byte[] hashBytes = md.digest();
        String hash = BaseEncoding.base16().lowerCase().encode(hashBytes);
        return hash;
    }

    /*
     * Copies the incoming payload InputSteams to temp files.
     * Hashes the bytes as they are copied.
     * Replaces the InputStreams on the payloads with ones that point at the temp files.
     */
    private JsonObject copyIncomingPayloadsToTempFilesAndGenerateHashes(CordraObject co) throws IOException {
        if (co.payloads == null) {
            return null;
        }
        JsonObject payloadHashes = new JsonObject();
        for (Payload p : co.payloads) {
            try (InputStream in = p.getInputStream()) {
                if (in != null) {
                    Path tempPath = Files.createTempFile(TEMP_FILE_PREFIX, null);
                    byte[] hashBytes;
                    try (
                        HashingInputStream hin = new HashingInputStream(Hashing.sha256(), in);
                        OutputStream tempFileOut = Files.newOutputStream(tempPath);
                    ) {
                        IOUtils.copy(hin, tempFileOut);
                        hashBytes = hin.hash().asBytes();
                    }
                    String hash = BaseEncoding.base16().lowerCase().encode(hashBytes);
                    payloadHashes.addProperty(p.name, hash);
                    @SuppressWarnings("resource")
                    InputStream tempFileIn = Files.newInputStream(tempPath);
                    @SuppressWarnings("resource")
                    DeleteOnCloseTempFileInputStream selfDeletingTempFileIn = new DeleteOnCloseTempFileInputStream(tempFileIn, tempPath);
                    p.setInputStream(selfDeletingTempFileIn);
                    p.size = Files.size(tempPath);
                }
            }
        }
        return payloadHashes;
    }

    private void removePayloadHashesForPayloadsThatDontExist(CordraObject co, JsonObject existingPayloadHashes) {
        if (existingPayloadHashes != null) {
            List<String> existingPayloadHashNames = new ArrayList<>();
            for (String name : existingPayloadHashes.keySet()) {
                existingPayloadHashNames.add(name);
            }
            for (String name : existingPayloadHashNames) {
                if (!hasPayloadCalled(name, co)) {
                    existingPayloadHashes.remove(name);
                }
            }
        }
    }

    private boolean hasPayloadCalled(String name, CordraObject co) {
        if (co.payloads == null) return false;
        for (Payload p : co.payloads) {
            if (p.name.equals(name)) return true;
        }
        return false;
    }

    private void addHashesForStoredPayloadsThatAreMissingHashes(CordraObject co, JsonObject existingPayloadHashes, CordraStorage storage) throws CordraException {
        if (co.payloads == null) return;
        for (Payload p : co.payloads) {
            if (p.getInputStream() == null) {
                if (existingPayloadHashes == null) {
                    existingPayloadHashes = new JsonObject();
                }
                if (!existingPayloadHashes.has(p.name)) {
                    String payloadHash = generateHashFromStoredPayload(p.name, co, storage);
                    existingPayloadHashes.addProperty(p.name, payloadHash);
                }
            }
        }
    }

    private String generateHashFromStoredPayload(String name, CordraObject co, CordraStorage storage) throws CordraException {
        try (
            InputStream in = storage.getPayload(co.id, name);
            HashingInputStream hin = new HashingInputStream(Hashing.sha256(), in);
        ) {
            byte[] data = new byte[1024];
            int result = 0;
            while (result != -1) {
                result = hin.read(data);
            }
            byte[] hashBytes = hin.hash().asBytes();
            String hash = BaseEncoding.base16().lowerCase().encode(hashBytes);
            return hash;
        } catch (IOException e) {
            throw new CordraException("Could not generate hash from stored payload", e);
        }
    }

    public VerificationReport verify(CordraObject co, CordraStorage storage) throws CordraException {
        VerificationReport report = new VerificationReport();
        JsonObject existingHashes = co.metadata.hashes;
        if (existingHashes == null) {
            return report;
        }
        JsonObject hashes = generateContentAndUserMetataHashes(co);
        JsonObject allPayloadHashes = generateAllHashesFromStoredPayloads(co, storage);
        if (allPayloadHashes != null) {
            hashes.add("payloads", allPayloadHashes);
        }
        JsonObject coJson = GsonUtility.getGson().toJsonTree(co).getAsJsonObject();
        JsonObject metadata = coJson.getAsJsonObject("metadata");
        metadata.add("hashes", hashes);
        String fullHash = generateFullHashFromExistingHashes(coJson);
        hashes.addProperty("full", fullHash);
        report = verificationReportFor(existingHashes, hashes);
        return report;
    }

    private VerificationReport verificationReportFor(JsonObject existingHashes, JsonObject hashes) {
        VerificationReport report = new VerificationReport();
        if (existingHashes.get("full").getAsString().equals(hashes.get("full").getAsString())) {
            report.full = true;
        } else {
            report.full = false;
        }
        if (existingHashes.get("content").getAsString().equals(hashes.get("content").getAsString())) {
            report.content = true;
        } else {
            report.content = false;
        }
        if (existingHashes.has("userMetadata")) {
            if (existingHashes.get("userMetadata").getAsString().equals(hashes.get("userMetadata").getAsString())) {
                report.userMetadata = true;
            } else {
                report.userMetadata = false;
            }
        }
        if (hashes.has("payloads")) {
            Map<String, Boolean> payloadsReport = new HashMap<>();
            JsonObject payloadHashes = hashes.getAsJsonObject("payloads");
            JsonObject existingPayloadHashes = new JsonObject();
            if (existingHashes.has("payloads")) {
                existingPayloadHashes = existingHashes.getAsJsonObject("payloads");
            }
            for (String name : payloadHashes.keySet()) {
                String payloadHash = payloadHashes.get(name).getAsString();
                if (existingPayloadHashes.has(name)) {
                    String existingPayloadHash = existingPayloadHashes.get(name).getAsString();
                    if (payloadHash.equals(existingPayloadHash)) {
                        payloadsReport.put(name, true);
                    } else {
                        payloadsReport.put(name, false);
                    }
                } else {
                    payloadsReport.put(name, false);
                }
            }
            report.payloads = payloadsReport;
        }
        return report;
    }

    private JsonObject generateAllHashesFromStoredPayloads(CordraObject co, CordraStorage storage) throws CordraException {
        JsonObject hashes = new JsonObject();
        if (co.payloads == null) {
            return null;
        }
        for (Payload p : co.payloads) {
            String hash = generateHashFromStoredPayload(p.name, co, storage);
            hashes.addProperty(p.name, hash);
        }
        return hashes;
    }

    public static class VerificationReport {
        public Boolean content;
        public Boolean userMetadata;
        public Boolean full;
        public Map<String, Boolean> payloads;
    }
}
