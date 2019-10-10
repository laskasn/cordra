package net.cnri.cordra;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import net.handle.hdllib.Util;

public class HandleMinter {
    private volatile String prefix;
    private final AtomicInteger counter = new AtomicInteger();

    public HandleMinter(String prefix) {
        this.prefix = ensureNoSlash(prefix);
    }

    public void setPrefix(String prefix) {
        this.prefix = ensureNoSlash(prefix);
    }

    private static String ensureNoSlash(String prefix) {
        if (prefix == null) return prefix;
        if (!prefix.endsWith("/")) return prefix;
        return prefix.substring(0, prefix.length() - 1);
    }

    public String mint(String data) {
        return prefix + "/" + digest(data);
    }

    public String mintByTimestamp() {
        return prefix + "/" + digest("" + System.currentTimeMillis() + pad(counter.getAndIncrement()));
    }

    public String mintWithSuffix(String suffix) {
        return prefix + "/" + suffix;
    }

    String pad(int n) {
        n = n % 1000;
        if (n < 0) n = (n + 1000) % 1000;
        if (n < 10) return "00" + n;
        if (n < 100) return "0" + n;
        return "" + n;
    }

    String digest(String data) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-1").digest(Util.encodeString(data));
            bytes = Util.substring(bytes, 0, 10);
            return Util.decodeHexString(bytes, false).toLowerCase(Locale.ENGLISH);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

}
