/*************************************************************************\
    Copyright (c) 2019 Corporation for National Research Initiatives;
                        All rights reserved.
\*************************************************************************/

package net.cnri.cordra.web;

import net.cnri.cordra.RequestContext;
import net.cnri.cordra.RequestContextHolder;
import net.cnri.cordra.api.BadRequestCordraException;

import org.slf4j.MDC;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

public class RequestContextFilter implements Filter {
    public static final String REQUEST_CONTEXT_PARAM = "requestContext";

    @Override
    public void doFilter(ServletRequest servletReq, ServletResponse servletResp, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest)servletReq;
        HttpServletResponse resp = (HttpServletResponse)servletResp;
        // Because MDC is thread-specific, this filter needs to be called again after ASYNC dispatch.
        // But because the filter was already called, the request information may already exist, hence the
        // logic for checking the response headers first
        setRequestId(req, resp);
        try {
            try {
                setRequestContext(req);
            } catch (BadRequestCordraException e) {
                ServletErrorUtil.badRequest(resp, e.getMessage());
                return;
            }
            chain.doFilter(req, resp);
        } finally {
            RequestContextHolder.clear();
        }
    }

    private void setRequestId(HttpServletRequest req, HttpServletResponse resp) {
        String requestId = req.getHeader("Request-Id");
        if (requestId == null) {
            requestId = resp.getHeader("Request-Id");
        }
        if (requestId == null) {
            requestId = generateRequestId();
        }
        MDC.put("requestId", requestId);
        resp.setHeader("Request-Id", requestId);
    }

    private void setRequestContext(HttpServletRequest req) throws BadRequestCordraException {
        // get any previously set request attribute...
        RequestContext requestContextAtt = (RequestContext) req.getAttribute(REQUEST_CONTEXT_PARAM);
        if (requestContextAtt != null) {
            RequestContextHolder.set(requestContextAtt);
            return;
        }
        // otherwise get a brand-new requestContext, and set it as a request attribute
        RequestContext requestContext = new RequestContext();
        requestContext.setSystemCall(false);
        RequestContextHolder.set(requestContext);
        req.setAttribute(REQUEST_CONTEXT_PARAM, requestContext);
        // in which case, also populate the user context
        String requestContextJson = req.getParameter(REQUEST_CONTEXT_PARAM);
        if (requestContextJson != null && !requestContextJson.isEmpty()) {
            JsonObject userContext;
            try {
                userContext = new JsonParser().parse(requestContextJson).getAsJsonObject();
            } catch (Exception e) {
                throw new BadRequestCordraException("Unable to parse requestContext", e);
            }
            requestContext.setRequestContext(userContext);
        }
    }

    private static String generateRequestId() {
        String uuid = UUID.randomUUID().toString();
        return uuid.substring(uuid.length()-12);
    }

    @Override
    public void destroy() { }

    @Override
    public void init(FilterConfig config) throws ServletException { }

}
