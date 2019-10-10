package net.cnri.cordra.auth;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import net.cnri.cordra.CordraService;
import net.cnri.cordra.CordraServiceFactory;
import net.cnri.cordra.GsonUtility;
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
import java.util.List;

@WebServlet("/auth/introspect/*")
public class AuthIntrospectServlet extends HttpServlet {
    private static Logger logger = LoggerFactory.getLogger(AuthIntrospectServlet.class);

    private HttpSessionManager sessionManager;

    private static CordraService cordra;
    private static Gson gson = GsonUtility.getGson();

    @Override
    public void init() throws ServletException {
        super.init();
        try {
            cordra = CordraServiceFactory.getCordraService();
            sessionManager = (HttpSessionManager) getServletContext().getAttribute(HttpSessionManager.class.getName());
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            AuthTokenServlet.TokenRequest tokenRequest = AuthTokenServlet.getTokenRequest(req);
            if (tokenRequest == null || tokenRequest.token == null) {
                error(resp, "invalid_request", null);
                return;
            }
            HttpSession session = sessionManager.getSession(req, tokenRequest.token, false);
            boolean full = ServletUtil.getBooleanParameter(req, "full");
            boolean active = false;
            String userId = null;
            boolean hasUserObject = false;
            String username = null;
            List<String> typesPermittedToCreate = null;
            List<String> groupIds = null;
            if (session != null) {
                active = true;
                userId = (String) session.getAttribute("userId");
                hasUserObject = ServletUtil.getBooleanAttribute(session, "hasUserObject");
                username = (String) session.getAttribute("username");
                if (full) {
                    typesPermittedToCreate = cordra.getTypesPermittedToCreate(userId, hasUserObject);
                    groupIds = cordra.getAclEnforcer().getGroupsForUser(userId);
                }
            } else if (full) {
                typesPermittedToCreate = cordra.getTypesPermittedToCreate(null, false);
            }
            SuccessResponse successResponse = new SuccessResponse(active, username, userId, typesPermittedToCreate, groupIds);
            success(resp, successResponse);
        } catch (Exception e) {
            logger.error("Exception in POST /auth/introspect", e);
            ServletErrorUtil.internalServerError(resp);
        }
    }

    private void success(HttpServletResponse resp, SuccessResponse response) throws IOException {
        resp.setStatus(HttpServletResponse.SC_OK);
        respondAsJson(response, resp);
    }

    private void error(HttpServletResponse resp, String error, String message) throws IOException {
        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        ErrorResponse response = new ErrorResponse();
        response.error = error;
        response.error_description = message;
        respondAsJson(response, resp);
    }

    protected void respondAsJson(Object o, HttpServletResponse resp) throws IOException {
        try {
            gson.toJson(o, resp.getWriter());
        } catch (JsonIOException e) {
            throw new IOException("Unable to write JSON", e);
        }
        resp.getWriter().println();
    }

    @SuppressWarnings("unused")
    private static class SuccessResponse {
        public boolean active;
        public String username;
        public String userId;
        public List<String> typesPermittedToCreate;
        public List<String> groupIds;

        public SuccessResponse(boolean active, String username, String userId, List<String> typesPermittedToCreate, List<String> groupIds) {
            this.active = active;
            this.username = username;
            this.userId = userId;
            this.typesPermittedToCreate = typesPermittedToCreate;
            this.groupIds = groupIds;
        }
    }

    @SuppressWarnings("unused")
    private static class ErrorResponse {
        public String error;
        public String error_description;
    }
}
