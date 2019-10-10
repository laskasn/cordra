package net.cnri.cordra.web;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

public class AccessTokenFilter implements Filter {

    private static final String AUTHORIZATION = "Authorization";

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // no-op
    }

    @Override
    public void doFilter(ServletRequest servletReq, ServletResponse servletResp, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest)servletReq;
        HttpServletResponse resp = (HttpServletResponse)servletResp;
        boolean usedNonHeaderAccessToken = false;
        if (req.getHeader(AUTHORIZATION) == null && isAppropriateForNonHeaderAuth(req)) {
            String accessToken = req.getParameter("access_token");
            if (accessToken != null && !accessToken.isEmpty()) {
                usedNonHeaderAccessToken = true;
                req = wrapReqForAccessToken(req, accessToken);
            }
        }
        chain.doFilter(req, resp);
        if (usedNonHeaderAccessToken && resp.getHeader("Cache-Control") == null) {
            resp.setHeader("Cache-Control", "private");
        }
    }

    private boolean isAppropriateForNonHeaderAuth(HttpServletRequest req) {
        String method = req.getMethod();
        if ("GET".equals(method) || "HEAD".equals(method)) return true;
        if ("POST".equals(method)) {
            if ("application/x-www-form-urlencoded".equals(req.getContentType())) return true;
        }
        return false;
    }

    private HttpServletRequest wrapReqForAccessToken(HttpServletRequest req, String accessToken) {
        return new HttpServletRequestWrapper(req) {
            @Override
            public long getDateHeader(String name) {
                if (AUTHORIZATION.equals(name)) throw new IllegalArgumentException();
                return super.getDateHeader(name);
            }

            @Override
            public int getIntHeader(String name) {
                if (AUTHORIZATION.equals(name)) throw new NumberFormatException();
                return super.getIntHeader(name);
            }

            @Override
            public String getHeader(String name) {
                if (AUTHORIZATION.equals(name)) return "Bearer " + accessToken;
                return super.getHeader(name);
            }

            @Override
            public Enumeration<String> getHeaders(String name) {
                if (AUTHORIZATION.equals(name)) return Collections.enumeration(Collections.singleton("Bearer " + accessToken));
                return super.getHeaders(name);
            }

            @Override
            public Enumeration<String> getHeaderNames() {
                Enumeration<String> existingHeaders = super.getHeaderNames();
                List<String> headersList = Collections.list(existingHeaders);
                headersList.add(AUTHORIZATION);
                return Collections.enumeration(headersList);
            }
        };
    }

    @Override
    public void destroy() {
        // no-op
    }

}
