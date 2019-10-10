package net.cnri.cordra.auth;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import net.cnri.cordra.CordraService;
import net.cnri.cordra.CordraServiceFactory;
import net.cnri.cordra.GsonUtility;
import net.cnri.cordra.InvalidException;
import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.web.ServletErrorUtil;
import net.cnri.cordra.web.ServletUtil;
import net.cnri.servletcontainer.sessions.HttpSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet("/auth/token")
public class AuthTokenServlet extends HttpServlet {
    private static Logger logger = LoggerFactory.getLogger(AuthTokenServlet.class);

    private HttpSessionManager sessionManager;

    private static CordraService cordra;
    private static Gson gson = GsonUtility.getGson();
    private Authenticator authenticator;

    @Override
    public void init() throws ServletException {
        super.init();
        try {
            cordra = CordraServiceFactory.getCordraService();
            sessionManager = (HttpSessionManager) getServletContext().getAttribute(HttpSessionManager.class.getName());
            authenticator = cordra.getAuthenticator();
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            TokenRequest tokenRequest = getTokenRequest(req);
            if (tokenRequest == null) {
                error(resp, "invalid_request", null);
                return;
            }
            if ("urn:ietf:params:oauth:grant-type:jwt-bearer".equals(tokenRequest.grant_type)) {
                jwtAuth(req, resp, tokenRequest);
            } else {
                if (!"password".equals(tokenRequest.grant_type)) {
                    // allow omitting grant_type if username and password are present
                    if (tokenRequest.grant_type != null || (tokenRequest.username == null || tokenRequest.password == null)) {
                        error(resp, "unsupported_grant_type", null);
                        return;
                    }
                }
                passwordAuth(req, resp, tokenRequest);
            }
        } catch (Exception e) {
            logger.error("Exception in POST /auth/token", e);
            ServletErrorUtil.internalServerError(resp);
        }
    }

    private void jwtAuth(HttpServletRequest req, HttpServletResponse resp, TokenRequest tokenRequest) throws IOException, CordraException {
        if (tokenRequest.assertion == null) {
            error(resp, "invalid_request", null);
            return;
        }
        String jwt = tokenRequest.assertion;
        Authenticator.CheckCredentialsResponse credsResponse = authenticator.checkJwtCredentials(req, jwt);
        respondAndSetupSessionIfNeeded(credsResponse, req, resp);
    }

    private void passwordAuth(HttpServletRequest req, HttpServletResponse resp, TokenRequest tokenRequest) throws IOException, CordraException, InvalidException {
        Authenticator.CheckCredentialsResponse credsResponse = authenticator.checkCredentialsAndReportResult(req, tokenRequest.username, tokenRequest.password);
        respondAndSetupSessionIfNeeded(credsResponse, req, resp);
    }

    private void respondAndSetupSessionIfNeeded(Authenticator.CheckCredentialsResponse credsResponse, HttpServletRequest req, HttpServletResponse resp) throws CordraException, IOException {
        if (credsResponse.authResponse == Authenticator.AuthenticationResponse.SUCCESS) {
            Map<String, Object> atts = new HashMap<>();
            if (credsResponse.username != null) atts.put("username", credsResponse.username);
            atts.put("userId", credsResponse.userId);
            atts.put("hasUserObject", credsResponse.hasUserObject);
            HttpSession session = sessionManager.getSession(req, null, true, atts);
            String token = session.getId();
            List<String> typesPermittedToCreate = null;
            List<String> groupIds = null;
            boolean full = ServletUtil.getBooleanParameter(req, "full");
            if (full) {
                typesPermittedToCreate = cordra.getTypesPermittedToCreate(credsResponse.userId, credsResponse.hasUserObject);
                groupIds = cordra.getAclEnforcer().getGroupsForUser(credsResponse.userId);
            }
            SuccessResponse successResponse = new SuccessResponse();
            successResponse.access_token = token;
            successResponse.token_type = "Bearer";
            successResponse.active = true;
            successResponse.userId = credsResponse.userId;
            successResponse.username = credsResponse.username;
            successResponse.typesPermittedToCreate = typesPermittedToCreate;
            successResponse.groupIds = groupIds;
            success(resp, successResponse);
        } else {
            Boolean isPasswordChangeRequired = null;
            if (credsResponse.authResponse == Authenticator.AuthenticationResponse.PASSWORD_CHANGE_REQUIRED) {
                isPasswordChangeRequired = true;
            }
            error(resp, "invalid_grant", "Authorization failed", isPasswordChangeRequired);
            return;
        }
    }

    public static TokenRequest getTokenRequest(HttpServletRequest req) throws IOException {
        Object tokenRequestAttribute = req.getAttribute("TokenRequest");
        if (tokenRequestAttribute != null) {
            return (TokenRequest) tokenRequestAttribute;
        }
        String mediaType = req.getContentType();
        if (mediaType == null) {
            return null;
        }
        if (mediaType.contains(";")) {
            mediaType = mediaType.substring(0, mediaType.indexOf(";")).trim();
        }
        if ("application/x-www-form-urlencoded".equals(mediaType)) {
            TokenRequest tokenRequest = new TokenRequest();
            tokenRequest.grant_type = req.getParameter("grant_type");
            tokenRequest.username = req.getParameter("username");
            tokenRequest.password = req.getParameter("password");
            tokenRequest.assertion = req.getParameter("assertion");
            tokenRequest.token = req.getParameter("token");
            req.setAttribute("TokenRequest", tokenRequest);
            return tokenRequest;
        } else if ("application/json".equals(mediaType)) {
            TokenRequest tokenRequest = gson.fromJson(req.getReader(), TokenRequest.class);
            req.setAttribute("TokenRequest", tokenRequest);
            return tokenRequest;
        } else {
            return null;
        }
    }

    private void success(HttpServletResponse resp, SuccessResponse successResponse) throws IOException {
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setHeader("Cache-Control", "no-store");
        resp.setHeader("Pragma", "no-cache");
        respondAsJson(successResponse, resp);
    }

    protected void respondAsJson(Object o, HttpServletResponse resp) throws IOException {
        try {
            gson.toJson(o, resp.getWriter());
        } catch (JsonIOException e) {
            throw new IOException("Unable to write JSON", e);
        }
        resp.getWriter().println();
    }

    private void error(HttpServletResponse resp, String error, String message) throws IOException {
        error(resp, error, message, null);
    }

    private void error(HttpServletResponse resp, String error, String message, Boolean isPasswordChangeRequired) throws IOException {
        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        ErrorResponse response = new ErrorResponse();
        response.error = error;
        response.error_description = message;
        response.passwordChangeRequired = isPasswordChangeRequired;
        respondAsJson(response, resp);
    }

    public static class TokenRequest {
        public String grant_type;
        public String assertion;
        public String username;
        public String password;
        public String token;
    }

    @SuppressWarnings("unused")
    private static class SuccessResponse {
        public String access_token;
        public String token_type;
        public boolean active = false;
        public String userId;
        public String username;
        public List<String> typesPermittedToCreate;
        public List<String> groupIds;
    }

    @SuppressWarnings("unused")
    public static class ErrorResponse {
        public String error;
        public String error_description;
        public Boolean passwordChangeRequired;
    }
}
