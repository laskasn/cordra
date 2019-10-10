/*************************************************************************\
    Copyright (c) 2019 Corporation for National Research Initiatives;
                        All rights reserved.
\*************************************************************************/

package net.cnri.cordra.auth;

import net.cnri.cordra.CordraService;
import net.cnri.cordra.CordraServiceFactory;
import net.cnri.cordra.GsonUtility;
import net.cnri.cordra.InvalidException;
import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.auth.AclEnforcer.Permission;
import net.cnri.cordra.auth.Authenticator.AuthenticationResponse;
import net.cnri.cordra.web.ChangePasswordServlet;
import net.cnri.cordra.web.ServletErrorUtil;
import net.cnri.cordra.web.ServletUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AuthorizationFilter implements Filter {
    private static Logger logger = LoggerFactory.getLogger(AuthorizationFilter.class);

    private CordraService cordra;
    private AclEnforcer aclEnforcer;
    private Authenticator authenticator;
    // async dispatch passes to a container thread and returns immediately; 1 thread might actually be enough
    private ScheduledExecutorService exec = Executors.newScheduledThreadPool(16);

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) throws IOException, ServletException {
        if(req instanceof HttpServletRequest && resp instanceof HttpServletResponse) {
            if (isStartupStatusRequest((HttpServletRequest)req)) {
                chain.doFilter(req, resp);
            } else if (cordra == null) {
                respondWithTheSystemIsDown((HttpServletResponse)resp);
            } else if (req.getDispatcherType() == DispatcherType.ASYNC) {
                //We have already been parked for the necessary backOff so allow the request through
                doHttpFilter((HttpServletRequest)req, (HttpServletResponse)resp, chain);
            } else {
                long backOff = authenticator.calculateBackOff((HttpServletRequest) req);
                if (backOff != 0) {
                    AsyncContext async = req.startAsync(req, resp);
                    exec.schedule(() -> async.dispatch(), backOff, TimeUnit.MILLISECONDS);
                } else {
                    doHttpFilter((HttpServletRequest)req, (HttpServletResponse)resp, chain);
                }
            }
        } else {
            chain.doFilter(req, resp);
        }
    }

    private void respondWithTheSystemIsDown(HttpServletResponse resp) throws IOException {
        logger.error("Unexpected error checking authorization, the System is down.");
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        ServletErrorUtil.theSystemIsDown(resp);
    }

    private void doHttpFilter(HttpServletRequest req, HttpServletResponse resp, FilterChain chain) throws IOException, ServletException {
        try {
            if (isDisallowedAuthenticatingOverHttp(req)) {
                resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                resp.setContentType("application/json");
                resp.setCharacterEncoding("UTF-8");
                if (isAuthEndpoint(req)) {
                    AuthTokenServlet.ErrorResponse errorResponse = new AuthTokenServlet.ErrorResponse();
                    errorResponse.error = "invalid_request";
                    errorResponse.error_description = "Authentication requires HTTPS";
                    GsonUtility.getGson().toJson(errorResponse, resp.getWriter());
                } else {
                    ServletErrorUtil.forbidden(resp, "Authentication requires HTTPS");
                }
                return;
            }
            if (isAuthEndpoint(req)) {
                chain.doFilter(req, resp);
                return;
            }
            AuthenticationResponse authenticationResponse;
            if (isLegacySessions(req)) {
                authenticationResponse = AuthenticationResponse.FAILED_LEGACY_SESSIONS;
            } else {
                authenticationResponse = authenticator.authenticate(req, resp);
            }
            if (authenticationResponse == AuthenticationResponse.FAILED_LEGACY_SESSIONS) {
                resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                resp.setHeader("WWW-Authenticate", "Bearer realm=\"cordra\"");
                resp.setContentType("application/json");
                resp.setCharacterEncoding("UTF-8");
                ServletErrorUtil.unauthorized(resp, "Legacy sessions disabled on this Cordra");
                return;
            }
            if (isAuthorized(req, resp, authenticationResponse)) {
                chain.doFilter(req, resp);
            } else {
                if (authenticationResponse == Authenticator.AuthenticationResponse.SUCCESS) {
                    resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                } else {
                    resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
// No longer sending WWW-Authenticate: Basic which could allow a CSRF vulnerability
//                    if (req.getParameter("basic") != null)
//                        resp.setHeader("WWW-Authenticate", "Basic realm=\"admin\"");
                    if (authenticationResponse == Authenticator.AuthenticationResponse.FAILED_INVALID_TOKEN) {
                        resp.setHeader("WWW-Authenticate", "Bearer realm=\"cordra\", error=\"invalid_token\"");
                    } else {
                        resp.setHeader("WWW-Authenticate", "Bearer realm=\"cordra\"");
                        if (authenticationResponse == Authenticator.AuthenticationResponse.PASSWORD_CHANGE_REQUIRED) {
                            resp.setContentType("application/json");
                            resp.setCharacterEncoding("UTF-8");
                            ServletErrorUtil.passwordChangeRequired(resp);
                        } else if (authenticationResponse == AuthenticationResponse.ACCOUNT_INACTIVE) {
                            resp.setContentType("application/json");
                            resp.setCharacterEncoding("UTF-8");
                            ServletErrorUtil.unauthorized(resp, "Account is inactive");
                        }
                    }
                }
            }
        } catch (CordraException | InvalidException e) {
            logger.error("Unexpected error checking authorization", e);
            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");
            ServletErrorUtil.internalServerError(resp);
        }
    }

    private boolean isDisallowedAuthenticatingOverHttp(HttpServletRequest req) {
        if (cordra.getDesign().allowInsecureAuthentication == Boolean.TRUE) {
            return false;
        }
        if (req.isSecure()) {
            return false;
        }
        if (Authenticator.isInternalCall(req)) {
            return false;
        }
        if (req.getHeader("Authorization") != null) {
            return true;
        }
        if (isAuthEndpoint(req)) {
            return true;
        }
        if (cordra.getDesign().useLegacySessionsApi != Boolean.TRUE) {
            return false;
        }
        if (isSessionsEndpoint(req) && !isGetOrHead(req)) {
            return true;
        }
        if (req.getRequestedSessionId() != null) {
            return true;
        }
        return false;
    }

    private boolean isGetOrHead(HttpServletRequest req) {
        return "GET".equals(req.getMethod()) || "HEAD".equals(req.getMethod());
    }

    private boolean isLegacySessions(HttpServletRequest req) {
        if (cordra.getDesign().useLegacySessionsApi == Boolean.TRUE) {
            return false;
        }
        return isSessionsEndpoint(req);
    }

    private boolean isSessionsEndpoint(HttpServletRequest req) {
        return req.getServletPath().startsWith("/sessions");
    }

    public static boolean isAuthEndpoint(HttpServletRequest req) {
        String servletPath = req.getServletPath();
        if (servletPath.startsWith("/auth/")) {
            return true;
        }
        return false;
    }

    public static boolean isAuthTokenPost(HttpServletRequest req) {
        String servletPath = req.getServletPath();
        if (servletPath.startsWith("/auth/token")) {
            if ("POST".equals(req.getMethod())) {
                return true;
            }
            return false;
        }
        return false;
    }

    private boolean isStartupStatusRequest(HttpServletRequest req) {
        String servletPath = req.getServletPath();
        if (servletPath.startsWith("/startupStatus")) {
            return true;
        }
        return false;
    }

    private static final String getInitialPathSegment(String servletPath) {
        if (servletPath == null) return null;
        if (servletPath.isEmpty()) return "";
        int slash = servletPath.indexOf('/', 1);
        if (slash < 0) return servletPath.substring(1);
        else return servletPath.substring(1, slash);
    }

    private final List<String> publicPaths = java.util.Arrays.asList("startupStatus", "schemas", "design", "initData",
            "css", "img", "js", "js-test", "lib", "schemaTemplates");

    private boolean isAuthorized(HttpServletRequest req, HttpServletResponse resp, AuthenticationResponse authenticationResponse) throws CordraException {
        if (authenticationResponse == AuthenticationResponse.FAILED
            || authenticationResponse == AuthenticationResponse.FAILED_INVALID_TOKEN
            || authenticationResponse == AuthenticationResponse.FAILED_LEGACY_SESSIONS) {
            // tried to authenticate and failed; always give 401 Unauth
            return false;
        }
        String servletPath = req.getServletPath();
        if (authenticationResponse == AuthenticationResponse.ACCOUNT_INACTIVE) {
            if (servletPath.startsWith("/sessions")) {
                return isGetOrHead(req) || "DELETE".equals(req.getMethod());
            }
            return false;
        }
        if (req.getMethod().equalsIgnoreCase("POST") && servletPath.equals("/operation")) {
            // dispatched call will filter again
            return true;
        }
        String userId = (String) req.getAttribute("userId");
        boolean hasUserObject = ServletUtil.getBooleanAttribute(req, "hasUserObject");
        if (isAdministrativeRequest(req)) {
            // require CSRF
            if (authenticationResponse != AuthenticationResponse.SUCCESS) return false;
            return "admin".equals(userId);
        }
        String initialPathSegment = getInitialPathSegment(servletPath);
        if (isGetOrHead(req)) {
            if (initialPathSegment == null || initialPathSegment.isEmpty() ||
                    publicPaths.contains(initialPathSegment) ||initialPathSegment.endsWith(".html")) {
                return true;
            }
        } else {
            if (authenticationResponse == AuthenticationResponse.NO_CSRF) {
                return false;
            }
        }
        if (authenticationResponse == AuthenticationResponse.PASSWORD_CHANGE_REQUIRED) {
            return false;
        }
        if (authenticationResponse == AuthenticationResponse.SUCCESS_PASSWORD_CHANGE) {
            return true;
        }
        // at this point authenticationResponse is:
        // SUCCESS
        // ANONYMOUS (and userId is null)
        // NO_CSRF but only if GET or HEAD
        if (servletPath.startsWith("/check-credentials")) {
            return true;
        }
        if (servletPath.startsWith("/sessions")) {
            if (isGetOrHead(req)) {
                return true;
            }
            return authenticationResponse == AuthenticationResponse.SUCCESS;
        }
        if (servletPath.startsWith("/schemas")) {
            if ("admin".equals(userId)) return true;
            // GET/HEAD dealt with earlier by publicPaths
            String type = getObjectId(req, userId);
            String objectId = null;
            if (type != null && !type.isEmpty()) {
                objectId = cordra.idFromTypeNoSearch(type);
            }
            cordra.ensureIndexUpToDateWhenAuthChange();
            if (objectId == null) {
                return aclEnforcer.isPermittedToCreate(userId, hasUserObject, "Schema");
            } else {
                return aclEnforcer.permittedOperations(userId, hasUserObject, objectId) == Permission.WRITE;
            }
        }
        if (servletPath.startsWith("/objects") || servletPath.startsWith("/call") || servletPath.startsWith("/listMethods")|| servletPath.startsWith("/acls") || servletPath.startsWith("/relationships") || servletPath.startsWith("/versions")
            || servletPath.equals(ChangePasswordServlet.SERVLET_PATH)) {
            String objectId = getObjectId(req, userId);
            if (objectId != null) {
                cordra.ensureIndexUpToDateWhenAuthChange();
                if (servletPath.startsWith("/call")) {
                    return isPermittedToCall(userId, hasUserObject, req);
                } else {
                    Permission perm = aclEnforcer.permittedOperations(userId, hasUserObject, objectId);
                    resp.addHeader("X-Permission", perm.toString());
                    Permission requiredPermission;
                    if (requestRequiresOnlyReadPermission(req, servletPath)) {
                        requiredPermission = Permission.READ;
                    } else {
                        requiredPermission = Permission.WRITE;
                    }
                    return AclEnforcer.doesPermissionAllowOperation(perm, requiredPermission);
                }
            } else {
                if (ChangePasswordServlet.SERVLET_PATH.equals(servletPath)) {
                    return authenticationResponse == AuthenticationResponse.SUCCESS;
                }
                if (isCreate(req, servletPath)) {
                    cordra.ensureIndexUpToDateWhenAuthChange();
                    return isPermittedToCreate(userId, hasUserObject, req);
                }
                // anyone can query; queries are filtered
                return true;
            }
        }
        return false;
    }

    private boolean isPermittedToCall(String userId, boolean hasUserObject, HttpServletRequest req) throws CordraException {
        String objectId = getObjectId(req, userId);
        String type = req.getParameter("type");
        String method = req.getParameter("method");
        return aclEnforcer.isPermittedToCall(userId, hasUserObject, objectId, method, type);
    }

    private boolean isPermittedToCreate(String userId, boolean hasUserObject, HttpServletRequest req) throws CordraException {
        String objectType = req.getParameter("type");
        if (objectType == null) return true;
        return aclEnforcer.isPermittedToCreate(userId, hasUserObject, objectType);
    }

    private boolean isCreate(HttpServletRequest req, String servletPath) {
        return servletPath.startsWith("/objects") && "POST".equals(req.getMethod());
    }

    private boolean requestRequiresOnlyReadPermission(HttpServletRequest req, String servletPath) {
        if (servletPath.startsWith("/objects") && (isGetOrHead(req) || isGetViaPost(req))) return true;
        if (servletPath.startsWith("/acls") && (isGetOrHead(req))) return true;
        if (servletPath.startsWith("/relationships")) return true;
        if (servletPath.startsWith("/versions") && (isGetOrHead(req))) return true;
//        if (servletPath.startsWith("/listMethods")) return true;
        return false;
    }

    private boolean isGetViaPost(HttpServletRequest req) {
        return "POST".equals(req.getMethod()) && "application/x-www-form-urlencoded".equals(req.getContentType());
    }

    private String getObjectId(HttpServletRequest req, String userId) {
        if (ChangePasswordServlet.SERVLET_PATH.equals(req.getServletPath())) {
            return userId;
        }
        if (req.getServletPath().startsWith("/versions")) {
            return req.getParameter("objectId");
        }
        if (req.getServletPath().startsWith("/call") || req.getServletPath().startsWith("/listMethods")) {
            String objectId = req.getParameter("objectId");
            if (objectId != null) return objectId;
            String type = req.getParameter("type");
            String schemaId = cordra.idFromTypeNoSearch(type);
            return schemaId;
        }
        String objectId = ServletUtil.getPath(req);
        if (objectId != null && !objectId.isEmpty() && !"/".equals(objectId)) {
            objectId = objectId.substring(1);
            return objectId;
        }
        return null;
    }

    private boolean isAdministrativeRequest(HttpServletRequest req) {
        String servletPath = req.getServletPath();
        if (servletPath.startsWith("/adminPassword") ||
            servletPath.startsWith("/updateHandles") ||
            servletPath.startsWith("/reindexBatch") ||
            servletPath.startsWith("/uploadObjects")) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        try {
            cordra = CordraServiceFactory.getCordraService();
            if (cordra != null) {
                this.authenticator = cordra.getAuthenticator();
                this.aclEnforcer = cordra.getAclEnforcer();
            }
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    @Override
    public void destroy() {

    }
}
