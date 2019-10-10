package net.cnri.cordra.web.admin;

import com.google.gson.*;
import net.cnri.cordra.*;
import net.cnri.cordra.api.*;
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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@WebServlet({"/uploadObjects", "/uploadObjects/"})
public class ObjectsUploadServlet extends HttpServlet {
    private static Logger logger = LoggerFactory.getLogger(ObjectsUploadServlet.class);

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
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try {
            String userId = (String) req.getAttribute("userId");
            boolean hasUserObject = ServletUtil.getBooleanAttribute(req, "hasUserObject");
            // get input before parameters in case someone uses the wrong Content-Type and servlet wants to process the body
            String json;
            try (InputStream inputStream = req.getInputStream()) {
                boolean deleteCurrentObjects = ServletUtil.getBooleanParameter(req, "deleteCurrentObjects");
                if (deleteCurrentObjects) {
                    try {
                        doDeleteCurrentObjects(userId);
                        cordra.ensureIndexUpToDate();
                    } catch (CordraException e) {
                        ServletErrorUtil.internalServerError(resp);
                        logger.error("Error deleting current schemas", e);
                        return;
                    } catch (InvalidException e) {
                        ServletErrorUtil.badRequest(resp, "InvalidException");
                        logger.info("Error loading schema", e);
                        return;
                    } catch (ReadOnlyCordraException e) {
                        ServletErrorUtil.badRequest(resp, "Cordra is read-only");
                        return;
                    }
                }

                json = ServletUtil.streamToString(inputStream, req.getCharacterEncoding());
            }
            List<JsonObject> input = parseInput(json);
            sortSchemasFirst(input);
            boolean schemasUploadFinished = false;
            for (JsonObject obj : input) {
                try {
                    String type = obj.get("type").getAsString();
                    String id = obj.get("id").getAsString();
                    List<Payload> payloads = new ArrayList<>();
                    CordraObject.AccessControlList acl = null;
                    if (obj.has("acl")) {
                        JsonElement aclEl = obj.get("acl");
                        acl = gson.fromJson(aclEl, CordraObject.AccessControlList.class);
                    }
                    JsonObject userMetadata = null;
                    if (obj.has("userMetadata")) {
                        userMetadata = obj.get("userMetadata").getAsJsonObject();
                    }
                    JsonElement content = obj.get("content");
                    String jsonData = content.toString();
                    if ("CordraDesign".equals(type)) {
                        cordra.writeJsonAndPayloadsIntoCordraObjectIfValidAsUpdate(id, type, jsonData, acl, userMetadata, payloads, userId, hasUserObject, null, false);
                    } else {
                        if (!schemasUploadFinished && !"Schema".equals(type)) {
                            // We've finished uploading the schemas. Make sure index is up to date before continuing.
                            // This may not be necessary. Saw problems without, but could not duplicate.
                            cordra.ensureIndexUpToDate();
                            schemasUploadFinished = true;
                        }
                        cordra.writeJsonAndPayloadsIntoCordraObjectIfValid(type, jsonData, acl, userMetadata, payloads, id, userId, false);
                    }
                } catch (ReadOnlyCordraException e) {
                    ServletErrorUtil.badRequest(resp, "Cordra is read-only");
                    return;
                } catch (CordraException e) {
                    ServletErrorUtil.internalServerError(resp);
                    logger.error("Error loading objects", e);
                    return;
                } catch (InvalidException e) {
                    ServletErrorUtil.badRequest(resp, e.getMessage());
                    logger.info("Error loading objects", e);
                    return;
                }
            }
            resp.getWriter().println("{\"message\": \"success\"}");
        } catch(Exception e) {
            logger.error("Error loading objects", e);
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }

    private void sortSchemasFirst(List<JsonObject> input) {
        input.sort(new Comparator<JsonObject>() {
            @Override
            public int compare(JsonObject a, JsonObject b) {
                String aType = a.get("type").getAsString();
                String bType = b.get("type").getAsString();
                if ("CordraDesign".equals(aType)) {
                    return -1;
                }
                if ("CordraDesign".equals(bType)) {
                    return 1;
                }
                if ("Schema".equals(aType) && !"Schema".equals(bType)) {
                    return -1;
                }
                if ("Schema".equals(bType) && !"Schema".equals(aType)) {
                    return 1;
                }
                return 0;
            }
        });
    }

    private void doDeleteCurrentObjects(String userId) throws CordraException, InvalidException, ReadOnlyCordraException {
        deleteByQuery("*:* -type:Schema", userId);
        deleteByQuery("*:*", userId);
        cordra.ensureIndexUpToDate();
    }

    private static void deleteByQuery(String query, String userId) throws CordraException, ReadOnlyCordraException, InvalidException {
        List<String> ids;
        cordra.ensureIndexUpToDate();
        try (SearchResults<String> results = cordra.searchRepoHandles(query)) {
            ids = resultsToList(results);
        }
        deleteAll(ids, userId);
    }

    private static List<String> resultsToList(SearchResults<String> results) {
        List<String> ids = new ArrayList<>();
        for (String id : results) {
            ids.add(id);
        }
        return ids;
    }

    private static void deleteAll(List<String> ids, String userId) throws CordraException, ReadOnlyCordraException, InvalidException {
        for (String id : ids) {
            if ("design".equals(id)) {
                continue;
            }
            try {
                cordra.delete(id, userId);
            } catch (NotFoundCordraException e) {
                // no-op
            }
        }
    }

    private static List<JsonObject> parseInput(String json) {
        try {
            JsonArray results = new JsonParser().parse(json).getAsJsonObject().get("results").getAsJsonArray();
            List<JsonObject> result = new ArrayList<>();
            for (JsonElement obj : results) {
                result.add(obj.getAsJsonObject());
            }
            return result;
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

}
