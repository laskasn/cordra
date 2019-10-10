package net.cnri.cordra.util.cmdline;

import org.apache.commons.codec.binary.Hex;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public class HashDirectory {

    public static String hashPathFor(String id, int hashLength, int segmentLength) {
        String hex = hexHashFor(id);
        hex = hex.substring(0, hashLength);
        String result = segmentedPathFor(hex, segmentLength);
        return result;
    }

    static String segmentedPathFor(String s, int segmentLength) {
        List<String> segments = toSegments(s, segmentLength);
        StringBuilder result = new StringBuilder();
        for (String segment : segments) {
            result.append(segment).append("/");
        }
        return result.toString();
    }

    static List<String> toSegments(String s, int segmentLength) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < s.length(); i += segmentLength) {
            int end = i + segmentLength;
            if (end > s.length()) {
                end = s.length();
            }
            String segment = s.substring(i, end);
            result.add(segment);
        }
        return result;
    }

    static String hexHashFor(String s) {
        MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        byte[] hash = md5.digest(bytes);
        String hexString = Hex.encodeHexString(hash);
        return hexString;
    }

    public static final String convertToFileName(String s) {
        byte buf[];
        try {
            buf = s.getBytes("UTF8");
        } catch (Exception e) {
            buf = s.getBytes();
        }

        StringBuffer sb = new StringBuffer(buf.length + 10);
        for (int i = 0; i < buf.length; i++) {
            byte b = buf[i];
            if ((b >= 'A' && b <= 'Z') || (b >= 'a' && b <= 'z') || (b >= '0' && b <= '9') || b == '_' || b == '-') {
                sb.append((char) b);
            } else {
                sb.append('.');
                sb.append(getHexForByte(b));
            }
        }
        return sb.toString();
    }

    public static String getHexForByte(byte b) {
        StringBuilder sb = new StringBuilder();
        sb.append(HEX_VALUES[(b & 0xF0) >>> 4]);
        sb.append(HEX_VALUES[(b & 0xF)]);
        return sb.toString();
    }

    private static final char HEX_VALUES[] = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

}
