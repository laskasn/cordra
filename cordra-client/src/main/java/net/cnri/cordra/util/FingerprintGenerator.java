package net.cnri.cordra.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.main.JsonSchema;
import com.google.gson.JsonElement;

import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.api.InternalErrorCordraException;

/**
 * A tool for generating fingerprints of JSON objects.
 */
public class FingerprintGenerator {

    /**
     * Generates a fingerprint for a JSON object, after removing unwanted properties
     * First the object is pruned to the given schema and specified top-level properties are removed (see {@link JsonPruner}).
     * Second the object is put into a canonical string form (see {@link JsonCanonicalizer}).
     * Third the SHA-256 hash of the string is computed.
     * Finally the Base64 encoding of the hash is computed.
     */
    public static String generateFingerprint(JsonElement json, JsonSchema schema, String... propertiesToRemove) throws CordraException {
        JsonElement pruned;
        try {
            pruned = JsonPruner.pruneToMatchSchemaWithoutProperties(json, schema, propertiesToRemove);
        } catch (IOException | ProcessingException e) {
            throw new InternalErrorCordraException(e);
        }
        return generateFingerprint(pruned);
    }

    /**
     * Generates a fingerprint for a JSON object
     * First the object is put into a canonical string form (see {@link JsonCanonicalizer}).
     * Second the SHA-256 hash of the string is computed.
     * Finally the Base64 encoding of the hash is computed.
     */
    public static String generateFingerprint(JsonElement json) {
        String canonicalPrunedJson = JsonCanonicalizer.canonicalize(json);
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
        md.update(canonicalPrunedJson.getBytes(StandardCharsets.UTF_8));
        byte[] digest = md.digest();
        String fingerprint = Base64.getEncoder().encodeToString(digest);
        return fingerprint;
    }
}
