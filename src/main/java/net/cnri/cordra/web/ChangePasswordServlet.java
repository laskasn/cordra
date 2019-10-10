package net.cnri.cordra.web;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.cnri.cordra.CordraService;
import net.cnri.cordra.CordraServiceFactory;
import net.cnri.cordra.GsonUtility;
import net.cnri.cordra.InvalidException;
import net.cnri.cordra.api.BadRequestCordraException;
import net.cnri.cordra.auth.Authenticator;
import net.cnri.cordra.model.CordraErrorResponse;
import net.cnri.util.StreamUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

@WebServlet(ChangePasswordServlet.SERVLET_PATH)
public class ChangePasswordServlet extends HttpServlet {
    private static Logger logger = LoggerFactory.getLogger(ChangePasswordServlet.class);

    public static final String SERVLET_PATH = "/users/this/password";

    private static Gson gson = GsonUtility.getGson();
    private static CordraService cordra;

    @Override
    public void init() throws ServletException {
        super.init();
        try {
            cordra = CordraServiceFactory.getCordraService();
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        // we require an actual Authorization: header, which is sure to have clobbered any other session
        String authHeader = req.getHeader("Authorization");
        if (authHeader == null || Authenticator.isBearerTokenForSession(authHeader)) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            CordraErrorResponse errorResponse = new CordraErrorResponse("Authorization: header required, either Basic or Bearer with JWT for key-based authentication");
            gson.toJson(errorResponse, resp.getWriter());
            return;
        }
        String userId = (String)req.getAttribute("userId");
        if (userId == null) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            CordraErrorResponse errorResponse = new CordraErrorResponse("No user");
            gson.toJson(errorResponse, resp.getWriter());
            return;
        }
        try {
            String password = StreamUtil.readFully(req.getReader());
            cordra.updatePasswordForUser(password, userId);
            gson.toJson(new UpdateResponse(true), resp.getWriter());
        } catch (BadRequestCordraException | InvalidException invalidException) {
            ServletErrorUtil.badRequest(resp, invalidException.getMessage());
        } catch (Exception e) {
            logger.error("Error changing password", e);
            ServletErrorUtil.internalServerError(resp);
        }
    }

    private static class UpdateResponse {
        @SuppressWarnings("unused")
        boolean success = false;

        public UpdateResponse(boolean success) {
            this.success = success;
        }
    }
}
