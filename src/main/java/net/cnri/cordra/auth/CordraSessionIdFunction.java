package net.cnri.cordra.auth;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.util.function.Function;

public class CordraSessionIdFunction implements Function<HttpServletRequest, String> {

    @Override
    public String apply(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null) return getJSessionId(request);
        String[] parts = header.trim().split(" +");
        if (parts.length != 2) return getJSessionId(request);
        if (!"Bearer".equalsIgnoreCase(parts[0])) return getJSessionId(request);
        if (parts[1].contains(".")) return getJSessionId(request);
        return parts[1];
    }

    public String getJSessionId(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if ("JSESSIONID".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
