package net.cnri.cordra.auth;


import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import net.cnri.cordra.GsonUtility;
import net.cnri.servletcontainer.sessions.HttpSessionManager;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

@WebServlet("/auth/revoke")
public class AuthRevokeServlet extends HttpServlet {

    private HttpSessionManager sessionManager;
    private static Gson gson = GsonUtility.getGson();

    @Override
    public void init() throws ServletException {
        sessionManager = (HttpSessionManager) getServletContext().getAttribute(HttpSessionManager.class.getName());
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        AuthTokenServlet.TokenRequest tokenRequest = AuthTokenServlet.getTokenRequest(req);
        if (tokenRequest == null || tokenRequest.token == null) {
            error(resp, "invalid_request", null);
            return;
        }
        HttpSession session = sessionManager.getSession(req, tokenRequest.token, false);
        if (session != null) {
            session.invalidate();
        }
        success(resp);
    }

    private void success(HttpServletResponse resp) throws IOException {
        resp.setStatus(HttpServletResponse.SC_OK);
        SuccessResponse response = new SuccessResponse();
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
        public boolean active = false;
    }

    @SuppressWarnings("unused")
    private static class ErrorResponse {
        public String error;
        public String error_description;
    }
}
