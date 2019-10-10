package net.cnri.cordra.auth;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import net.handle.hdllib.Util;

public class Credentials {
    private String username;
    private String password;

    public Credentials(String authHeader) {
        String encodedUsernameAndPassWord = getEncodedUserNameAndPassword(authHeader);
        String decodedAuthHeader = new String(Base64.getDecoder().decode(encodedUsernameAndPassWord), StandardCharsets.UTF_8);
        username = decodedAuthHeader.substring(0, decodedAuthHeader.indexOf(":"));
        password = decodedAuthHeader.substring(decodedAuthHeader.indexOf(":") + 1);
    }

    public Credentials(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getAuthHeader() {
        byte[] usernamePasswordBytes = Util.encodeString(username + ":" + password);
        String usernamePasswordBase64 = Base64.getEncoder().encodeToString(usernamePasswordBytes);
        return "Basic " + usernamePasswordBase64;
    }

    private String getEncodedUserNameAndPassword(String authHeader) {
        return authHeader.substring(authHeader.indexOf(" ") + 1);
    }

    public String getPassword() {
        return password;
    }

    public String getUsername() {
        return username;
    }
}
