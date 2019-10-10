package net.cnri.cordra.relationships;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

import net.cnri.cordra.GsonUtility;
import net.cnri.cordra.web.ServletErrorUtil;
import net.cnri.cordra.web.ServletUtil;

@WebServlet({"/relationships/*"})
public class RelationshipsServlet extends HttpServlet {

    private static Logger logger = LoggerFactory.getLogger(RelationshipsServlet.class);

    private static RelationshipsService relationshipsService;

    private Gson gson;

    @Override
    public void init() throws ServletException {
        super.init();
        try {
            gson = GsonUtility.getGson();
            relationshipsService = RelationshipsServiceFactory.getRelationshipsService();
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");
            String path = ServletUtil.getPath(req);
            if (path == null || "".equals(path)) {
                ServletErrorUtil.badRequest(resp, "Missing objectId");
            } else {
                String objectId = path.substring(1);
                boolean outboundOnly = ServletUtil.getBooleanParameter(req, "outboundOnly");
                String userId = (String) req.getAttribute("userId");
                boolean hasUserObject = ServletUtil.getBooleanAttribute(req, "hasUserObject");
                Relationships relationships = relationshipsService.getRelationshipsFor(objectId, outboundOnly, userId, hasUserObject);
                gson.toJson(relationships, resp.getWriter());
            }
        } catch (Exception e) {
            logger.error("Unexpected error getting relationships", e);
            ServletErrorUtil.internalServerError(resp);
        }
    }
}
