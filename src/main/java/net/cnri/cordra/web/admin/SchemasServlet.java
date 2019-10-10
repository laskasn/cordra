package net.cnri.cordra.web.admin;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import net.cnri.cordra.*;
import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.api.Payload;
import net.cnri.cordra.model.SchemaInstance;
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
import java.util.ArrayList;
import java.util.List;

@WebServlet({"/schemas/*"})
public class SchemasServlet extends HttpServlet {
    private static Logger logger = LoggerFactory.getLogger(SchemasServlet.class);

    private static CordraService cordra;
    private static Gson gson = GsonUtility.getGson();

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
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        String pathInfo = ServletUtil.getPath(req);
        String type = getTypeFromPathInfo(pathInfo);
        if (type == null || type.isEmpty()) {
            String schemasJson;
            try {
                schemasJson = cordra.getAllSchemasAsJsonString();
                resp.getWriter().println(schemasJson);
            } catch (CordraException e) {
                ServletErrorUtil.internalServerError(resp);
                logger.error("Erroring getting local schemas", e);
            }
        } else {
            try {
                String schemaJson = cordra.getSchemaAsJsonString(type);
                if (schemaJson == null) {
                    resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                }
                resp.getWriter().println(schemaJson);
            } catch (CordraException e) {
                ServletErrorUtil.internalServerError(resp);
                logger.error("Erroring getting local schemas", e);
            }
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        String userId = (String) req.getAttribute("userId");
        boolean hasUserObject = ServletUtil.getBooleanAttribute(req, "hasUserObject");
        String pathInfo = ServletUtil.getPath(req);
        String type = getTypeFromPathInfo(pathInfo);
        if ("Schema".equals(type)) {
            ServletErrorUtil.badRequest(resp, "You may not change the Schema schema.");
            return;
        }
        String schemaJson = ServletUtil.streamToString(req.getInputStream(), req.getCharacterEncoding());
        try {
            String objectId = cordra.idFromType(type);
            List<Payload> payloads = new ArrayList<>();
            SchemaInstance schemaInstance = new SchemaInstance();
            schemaInstance.identifier = "";
            schemaInstance.name = type;
            schemaInstance.schema = new JsonParser().parse(schemaJson);

            if (objectId == null) {
                String json = gson.toJson(schemaInstance);
                cordra.writeJsonAndPayloadsIntoCordraObjectIfValid("Schema", json, null, null, payloads, null, userId, false);
            } else {
                String existingJson = cordra.getObjectJson(objectId);
                SchemaInstance existingInstance = gson.fromJson(existingJson, SchemaInstance.class);
                schemaInstance.javascript = existingInstance.javascript;
                String json = gson.toJson(schemaInstance);
                cordra.writeJsonAndPayloadsIntoCordraObjectIfValidAsUpdate(objectId, null, json, null, null, payloads, userId, hasUserObject, null, false);
            }
            resp.getWriter().println("{\"msg\": \"success\"}");
        } catch (ReadOnlyCordraException e) {
            ServletErrorUtil.badRequest(resp, "Cordra is read-only");
        } catch (CordraException e) {
            ServletErrorUtil.internalServerError(resp);
            logger.error("Error updating schema", e);
        } catch (InvalidException e) {
            ServletErrorUtil.badRequest(resp, "InvalidException");
            logger.info("Error updating schema", e);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        String pathInfo = ServletUtil.getPath(req);
        String type = getTypeFromPathInfo(pathInfo);
        String userId = (String) req.getAttribute("userId");
        if ("Schema".equals(type)) {
            ServletErrorUtil.badRequest(resp, "You may not delete the Schema schema. It would be bad.");
            return;
        }
        try {
            String id = cordra.idFromType(type);
            if (id != null) {
                cordra.delete(id, userId);
                resp.getWriter().println("{\"msg\": \"success\"}");
            } else {
                ServletErrorUtil.badRequest(resp, "Schema " + type + " does not exist.");
            }
        } catch (ReadOnlyCordraException e) {
            ServletErrorUtil.badRequest(resp, "Cordra is read-only");
        } catch (CordraException e) {
            ServletErrorUtil.internalServerError(resp);
            logger.error("Error deleting schema", e);
        } catch (InvalidException e) {
            ServletErrorUtil.badRequest(resp, e.getMessage());
        }
    }

    private String getTypeFromPathInfo(String pathInfo) {
        if (pathInfo == null) return null;
        if (pathInfo.startsWith("/")) return pathInfo.substring(1);
        return pathInfo;
    }

}
