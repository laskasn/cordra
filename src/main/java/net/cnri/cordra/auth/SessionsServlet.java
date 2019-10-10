package net.cnri.cordra.auth;

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
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

@WebServlet({"/sessions/*"})
public class SessionsServlet extends HttpServlet {
    private static Logger logger = LoggerFactory.getLogger(SessionsServlet.class);

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
            HttpSession session = req.getSession(false);
            boolean full = ServletUtil.getBooleanParameter(req, "full");
            AuthResponse sessionResponse = null;
            boolean isActiveSession = false;
            String userId = null;
            boolean hasUserObject = false;
            String username = null;
            List<String> typesPermittedToCreate = null;
            List<String> groupIds = null;
            if (session != null) {
                isActiveSession = true;
                userId = (String) session.getAttribute("userId");
                hasUserObject = ServletUtil.getBooleanAttribute(session, "hasUserObject");
                username = (String) session.getAttribute("username");
            }
            if (full) {
                typesPermittedToCreate = cordra.getTypesPermittedToCreate(userId, hasUserObject);
                groupIds = cordra.getAclEnforcer().getGroupsForUser(userId);
            }
            sessionResponse = new AuthResponse(isActiveSession, userId, username, typesPermittedToCreate, groupIds);
            String responseJson = gson.toJson(sessionResponse);
            PrintWriter w = resp.getWriter();
            w.write(responseJson);
            w.close();
        } catch (CordraException e) {
            logger.error("Exception in GET /sessions", e);
            ServletErrorUtil.internalServerError(resp);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");
            HttpSession session = req.getSession(false);
            String userId = (String) session.getAttribute("userId");
            if (userId != null && !cordra.isUserAccountActive(userId)) {
                session.invalidate();
                ServletErrorUtil.unauthorized(resp, "Account is inactive.");
                return;
            }
            boolean hasUserObject = ServletUtil.getBooleanAttribute(session, "hasUserObject");
            String username = (String) session.getAttribute("username");
            List<String> typesPermittedToCreate = cordra.getTypesPermittedToCreate(userId, hasUserObject);
            List<String> groupIds = cordra.getAclEnforcer().getGroupsForUser(userId);
            String json = gson.toJson(new AuthResponse(true, userId, username, typesPermittedToCreate, groupIds));
            resp.getWriter().println(json);
        } catch (CordraException e) {
            logger.error("Exception in POST /sessions", e);
            ServletErrorUtil.internalServerError(resp);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");
            String path = req.getPathInfo();
            if (path == null || path.isEmpty()) {
                resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return;
            }

            path = path.substring(1);
            if (!"this".equals(path)) {
                resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return;
            }

            HttpSession session = req.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            PrintWriter w = resp.getWriter();
            List<String> typesPermittedToCreate = cordra.getTypesPermittedToCreate(null, false);
            String json = gson.toJson(new AuthResponse(false, null, null, typesPermittedToCreate, null));
            w.println(json);
            w.close();
        } catch (CordraException e) {
            logger.error("Exception in DELETE /sessions", e);
            ServletErrorUtil.internalServerError(resp);
            return;
        }
    }

    @SuppressWarnings("unused")
    private static class AuthResponse {
        boolean isActiveSession = false;
        String userId;
        String username;
        List<String> typesPermittedToCreate;
        List<String> groupIds;

        public AuthResponse(boolean isActiveSession, String userId, String username, List<String> typesPermittedToCreate, List<String> groupIds) {
            this.isActiveSession = isActiveSession;
            this.userId = userId;
            this.username = username;
            this.typesPermittedToCreate = typesPermittedToCreate;
            this.groupIds = groupIds;
        }
    }
}
