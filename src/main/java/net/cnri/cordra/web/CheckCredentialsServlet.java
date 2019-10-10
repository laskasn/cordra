package net.cnri.cordra.web;

import com.google.gson.Gson;
import net.cnri.cordra.CordraService;
import net.cnri.cordra.CordraServiceFactory;
import net.cnri.cordra.GsonUtility;
import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.web.ServletErrorUtil;
import net.cnri.cordra.web.ServletUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

@WebServlet({"/check-credentials/*"})
public class CheckCredentialsServlet extends HttpServlet {
    private static Logger logger = LoggerFactory.getLogger(CheckCredentialsServlet.class);

    private static CordraService cordra;
    private Gson gson;

    @Override
    public void init() throws ServletException {
        super.init();
        try {
            gson = GsonUtility.getGson();
            cordra = CordraServiceFactory.getCordraService();
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");
            boolean full = ServletUtil.getBooleanParameter(req, "full");
            AuthResponse sessionResponse = null;
            boolean active = false;
            String userId = null;
            boolean hasUserObject = false;
            String username = null;
            List<String> typesPermittedToCreate = null;
            List<String> groupIds = null;
            userId = (String) req.getAttribute("userId");
            active = userId != null;
            hasUserObject = ServletUtil.getBooleanAttribute(req, "hasUserObject");
            username = (String) req.getAttribute("username");
            if (full) {
                typesPermittedToCreate = cordra.getTypesPermittedToCreate(userId, hasUserObject);
                groupIds = cordra.getAclEnforcer().getGroupsForUser(userId);
            }
            sessionResponse = new AuthResponse(active, userId, username, typesPermittedToCreate, groupIds);
            String responseJson = gson.toJson(sessionResponse);
            PrintWriter w = resp.getWriter();
            w.write(responseJson);
            w.close();
        } catch (CordraException e) {
            logger.error("Exception in GET /check-credentials", e);
            ServletErrorUtil.internalServerError(resp);
        }
    }

    @SuppressWarnings("unused")
    private static class AuthResponse {
        boolean active = false;
        String userId;
        String username;
        List<String> typesPermittedToCreate;
        List<String> groupIds;

        public AuthResponse(boolean isActiveSession, String userId, String username, List<String> typesPermittedToCreate, List<String> groupIds) {
            this.active = isActiveSession;
            this.userId = userId;
            this.username = username;
            this.typesPermittedToCreate = typesPermittedToCreate;
            this.groupIds = groupIds;
        }
    }
}
