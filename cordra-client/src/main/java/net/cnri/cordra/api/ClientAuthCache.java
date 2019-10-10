package net.cnri.cordra.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ClientAuthCache {
    private static final long REFRESH_MS = 600_000;

    private final SecureRandom random = new SecureRandom();
    private final ConcurrentMap<String, UserInfo> map = new ConcurrentHashMap<>();

    private byte[] generateSalt() {
        byte[] res = new byte[16];
        random.nextBytes(res);
        return res;
    }

    private UserInfo generateUserInfo(String passwordString, String token) {
        UserInfo res = new UserInfo();
        if (passwordString != null) {
            byte[] password = passwordString.getBytes(StandardCharsets.UTF_8);
            byte[] salt = generateSalt();
            MessageDigest hasher = getMessageDigest();
            hasher.update(password);
            hasher.update(salt);
            byte[] hash = hasher.digest();
            res.salt = salt;
            res.hash = hash;
        }
        res.token = token;
        res.lastUsed = System.currentTimeMillis();
        return res;
    }

    private String checkCachedToken(String passwordString, UserInfo cachedValue) {
        byte[] password = passwordString.getBytes(StandardCharsets.UTF_8);
        long now = System.currentTimeMillis();
        if (now - cachedValue.lastUsed > REFRESH_MS) {
            return null;
        }
        if (cachedValue.hash == null || cachedValue.salt == null) {
            return null;
        }
        byte[] salt = cachedValue.salt;
        MessageDigest hasher = getMessageDigest();
        hasher.update(password);
        hasher.update(salt);
        byte[] hash = hasher.digest();
        if (Arrays.equals(hash, cachedValue.hash)) {
            cachedValue.lastUsed = now;
            return cachedValue.token;
        } else {
            return null;
        }
    }

    public String getCachedToken(String username, String password) {
        UserInfo cachedValue = map.get(username);
        if (cachedValue == null) return null;
        if (password != null) {
            String token = checkCachedToken(password, cachedValue);
            if (token == null) map.remove(username);
            return token;
        } else {
            if (cachedValue.hash != null) return null;
            else return cachedValue.token;
        }
    }

    public void storeToken(String username, String password, String token) {
        UserInfo cachedValue = generateUserInfo(password, token);
        map.put(username, cachedValue);
    }

    private MessageDigest getMessageDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    private static class UserInfo {
        byte[] salt;
        byte[] hash;
        String token;
        long lastUsed;
    }
}
