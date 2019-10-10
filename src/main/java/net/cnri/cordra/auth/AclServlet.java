package net.cnri.cordra.auth;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import net.cnri.cordra.CordraService;
import net.cnri.cordra.CordraServiceFactory;
import net.cnri.cordra.GsonUtility;
import net.cnri.cordra.ReadOnlyCordraException;
import net.cnri.cordra.api.CordraObject;
import net.cnri.cordra.api.NotFoundCordraException;
import net.cnri.cordra.web.ServletErrorUtil;
import net.cnri.cordra.web.ServletUtil;
import net.cnri.util.StreamUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet("/acls/*")
public class AclServlet extends HttpServlet {
    private static Logger logger = LoggerFactory.getLogger(AclServlet.class);

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
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        String objectId = ServletUtil.getPath(req);
        if (objectId != null && !objectId.isEmpty()) objectId = objectId.substring(1);
        String userId = (String) req.getAttribute("userId");
        try {
            CordraObject.AccessControlList acl = cordra.getAclFor(objectId, userId);
            if (acl == null) acl = new CordraObject.AccessControlList();
            gson.toJson(acl, resp.getWriter());
        } catch (NotFoundCordraException e) {
            ServletErrorUtil.notFound(resp, "No such object " + objectId);
        } catch (Exception e) {
            logger.error("Exception getting acls", e);
            ServletErrorUtil.internalServerError(resp);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPut(req, resp);
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        String objectId = ServletUtil.getPath(req);
        if (objectId != null && !objectId.isEmpty()) objectId = objectId.substring(1);
        String userId = (String) req.getAttribute("userId");
        boolean hasUserObject = ServletUtil.getBooleanAttribute(req, "hasUserObject");
        try {
            String json = StreamUtil.readFully(req.getReader());
            CordraObject.AccessControlList sAcl = gson.fromJson(json, CordraObject.AccessControlList.class);
            cordra.updateAcls(objectId, sAcl, userId, hasUserObject);
            gson.toJson(sAcl, resp.getWriter());
        } catch (JsonParseException e) {
            ServletErrorUtil.badRequest(resp, "Invalid ACL format");
        } catch (ReadOnlyCordraException e) {
            ServletErrorUtil.badRequest(resp, "Cordra is read-only");
        } catch (NotFoundCordraException e) {
            ServletErrorUtil.notFound(resp, "No such object " + objectId);
        } catch (Exception e) {
            logger.error("Exception getting acls", e);
            ServletErrorUtil.internalServerError(resp);
        }
    }
}
