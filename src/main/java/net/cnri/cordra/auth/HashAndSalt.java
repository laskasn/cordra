/*************************************************************************\
    Copyright (c) 2019 Corporation for National Research Initiatives;
                        All rights reserved.
\*************************************************************************/

package net.cnri.cordra.auth;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Random;

public class HashAndSalt {

    public static final String PBKDF2WithHmacSHA1 = "PBKDF2WithHmacSHA1";
    public static final String DEFAULT_ALGORITHM = PBKDF2WithHmacSHA1;
    public static final Integer LEGACY_HASH_ITERATION_COUNT_2048 = 2048;
    public static final Integer NIST_2017_HASH_ITERATION_COUNT_10K = 10000; //NIST recommended as of 2017
    public static final Integer DEFAULT_HASH_ITERATION_COUNT = NIST_2017_HASH_ITERATION_COUNT_10K;
    private static final Integer HASH_KEY_LENGTH = 256;

    private byte[] hash;
    private byte[] salt;
    private Integer iterations;
    private String algorithm;

    public HashAndSalt(String textToHash, Integer iterationsParam, String algorithmParam) {
        if (iterationsParam != null) {
            iterations = iterationsParam;
        } else {
            iterations = DEFAULT_HASH_ITERATION_COUNT;
        }
        if (algorithmParam != null) {
            algorithm = algorithmParam;
        } else {
            algorithm = DEFAULT_ALGORITHM;
        }
        salt = new byte[16];
        Random random = new SecureRandom();
        random.nextBytes(salt);
        KeySpec spec = new PBEKeySpec(textToHash.toCharArray(), salt, iterations, HASH_KEY_LENGTH);
        SecretKeyFactory f;
        try {
            f = SecretKeyFactory.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
        hash = null;
        try {
            hash = f.generateSecret(spec).getEncoded();
        } catch (InvalidKeySpecException e) {
            throw new AssertionError(e);
        }
    }

    public HashAndSalt(String hashHex, String saltHex, Integer iterationsParam, String algorithmParam) {
        if (iterationsParam != null) {
            iterations = iterationsParam;
        } else {
            iterations = DEFAULT_HASH_ITERATION_COUNT;
        }
        if (algorithmParam != null) {
            algorithm = algorithmParam;
        } else {
            algorithm = DEFAULT_ALGORITHM;
        }
        hash = hexStringToByteArray(hashHex);
        salt = hexStringToByteArray(saltHex);
    }

    public boolean verifySecret(String secret) {
        byte[] testHash;

        KeySpec spec = new PBEKeySpec(secret.toCharArray(), salt, iterations, HASH_KEY_LENGTH);
        SecretKeyFactory f;
        try {
            f = SecretKeyFactory.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
        try {
            testHash = f.generateSecret(spec).getEncoded();
        } catch (InvalidKeySpecException e) {
            throw new AssertionError(e);
        }
        if (Arrays.equals(hash, testHash)) {
            return true;
        } else {
            return false;
        }
    }

    private static String toHexString(byte[] bytes) {
        char[] hexArray = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                'a', 'b', 'c', 'd', 'e', 'f' };
        char[] hexChars = new char[bytes.length * 2];
        int v;
        for (int j = 0; j < bytes.length; j++) {
            v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v / 16];
            hexChars[j * 2 + 1] = hexArray[v % 16];
        }
        return new String(hexChars);
    }

    private static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                 + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    public String getHashString() {
        return toHexString(hash);
    }

    public String getSaltString() {
        return toHexString(salt);
    }
    
    public Integer getIterations() {
        return iterations;
    }
    
    public String getAlgorithm() {
        return algorithm;
    }
}
