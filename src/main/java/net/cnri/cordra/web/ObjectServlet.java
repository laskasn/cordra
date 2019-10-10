/*************************************************************************\
    Copyright (c) 2019 Corporation for National Research Initiatives;
                        All rights reserved.
\*************************************************************************/

package net.cnri.cordra.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import net.cnri.cordra.*;
import net.cnri.cordra.api.*;
import net.cnri.cordra.auth.QueryRestrictor;
import net.cnri.cordra.model.*;
import net.cnri.util.StreamUtil;
import net.cnri.util.StringUtils;
import org.apache.commons.io.IOUtils;
import org.elasticsearch.ElasticsearchStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.*;

@WebServlet({"/objects/*"})
@MultipartConfig
public class ObjectServlet extends HttpServlet {
    private static Logger logger = LoggerFactory.getLogger(ObjectServlet.class);
    private static final String JSON = "json";
    private static final String CONTENT = "content";
    private static final String ACL = "acl";
    private static final String USER_METADATA = "userMetadata";

    private CordraService cordra;
    private Gson gson;
    private Gson prettyGson;

    @Override
    public void init() throws ServletException {
        super.init();
        try {
            gson = GsonUtility.getGson();
            prettyGson = GsonUtility.getPrettyGson();
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
        String userId = (String) req.getAttribute("userId");
        if (objectId != null && !objectId.isEmpty()) objectId = objectId.substring(1);
        if (objectId == null || objectId.isEmpty()) {
            String query = req.getParameter("query");
            if (query == null) {
                ServletErrorUtil.badRequest(resp, "Missing object id in GET");
                return;
            } else {
                doSearch(req, resp, query);
                return;
            }
        }
        String jsonPointer = req.getParameter("jsonPointer");
        String filterJson = req.getParameter("filter");
        String payload = req.getParameter("payload");
        boolean isWantText = ServletUtil.getBooleanParameter(req, "text");
        boolean isFull = ServletUtil.getBooleanParameter(req, "full");
        boolean isPretty = ServletUtil.getBooleanParameter(req, "pretty", false);
        boolean hasResponseContext = ServletUtil.getBooleanParameter(req, "includeResponseContext");

        if (payload != null) {
            doGetPayload(req, resp, objectId, payload);
        } else if (jsonPointer == null && filterJson == null) {
            if (isFull) {
                getObjectPlusMetadata(resp, objectId, userId, isPretty, hasResponseContext);
            } else {
                doGetWholeObject(resp, objectId, userId, isWantText, isPretty);
            }
        } else {
            if (jsonPointer != null) {
                if (JsonUtil.isValidJsonPointer(jsonPointer)) {
                    doGetJsonPointer(resp, objectId, userId, jsonPointer, isWantText, isPretty);
                } else {
                    ServletErrorUtil.badRequest(resp, "Invalid JSON Pointer " + jsonPointer);
                }
            } else {
                doGetFilterByJsonPointers(resp, objectId, userId, filterJson, isPretty, isFull);
            }
        }
    }

    private void getObjectPlusMetadata(HttpServletResponse resp, String objectId, String userId, boolean isPretty, boolean hasResponseContext) throws IOException {
        try {
            CordraObject co = cordra.getContentPlusMetaWithPostProcessing(objectId, userId);
            if (co.content == null || co.type == null) {
                ServletErrorUtil.notFound(resp, "Not a valid cordra object");
                return;
            }
            resp.setHeader("Location", StringUtils.encodeURLPath("/objects/" + co.id));
            resp.setHeader("X-Schema", co.type);
            if (hasResponseContext) {
                String perm = resp.getHeader("X-Permission");
                if (perm != null) {
                    if (co.responseContext == null) co.responseContext = new JsonObject();
                    co.responseContext.addProperty("permission", perm);
                }
            }
            String json;
            if (isPretty) {
                json = prettyGson.toJson(co);
            } else {
                json = gson.toJson(co);
            }
            resp.getWriter().write(json);
        } catch (InvalidException e) {
            ServletErrorUtil.forbidden(resp, e.getMessage());
        } catch (NotFoundCordraException e) {
            ServletErrorUtil.notFound(resp, "Missing object");
        } catch (Exception e) {
            ServletErrorUtil.internalServerError(resp);
            logger.error("Something went wrong getting " + objectId, e);
        }
    }

    private void doGetWholeObject(HttpServletResponse resp, String objectId, String userId, boolean isWantText, boolean isPretty) throws IOException {
        try {
            CordraObject co = cordra.getContentPlusMetaWithPostProcessing(objectId, userId);
            if (co.content == null || co.type == null) {
                ServletErrorUtil.notFound(resp, "Not a valid cordra object");
                return;
            }
            resp.setHeader("Location", StringUtils.encodeURLPath("/objects/" + co.id));
            resp.setHeader("X-Schema", co.type);
            if (isWantText) {
                JsonNode jsonNode = JsonUtil.gsonToJackson(co.content);
                String mediaType = cordra.getMediaType(co.type, jsonNode, "");
                writeText(resp, jsonNode, mediaType);
            } else {
                String jsonData;
                if (isPretty) {
                    jsonData = prettyGson.toJson(co.content);
                } else {
                    jsonData = co.content.toString();
                }
                resp.getWriter().write(jsonData);
            }
        } catch (InvalidException e) {
            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");
            ServletErrorUtil.forbidden(resp, e.getMessage());
        } catch (NotFoundCordraException e) {
            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");
            ServletErrorUtil.notFound(resp, "Missing object");
        } catch (Exception e) {
            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");
            ServletErrorUtil.internalServerError(resp);
            logger.error("Something went wrong getting " + objectId, e);
        }
    }

    private void doGetPayload(HttpServletRequest req, HttpServletResponse resp, String objectId, String payloadName) throws IOException {
        boolean metadata = ServletUtil.getBooleanParameter(req, "metadata");
        PayloadWithRange payload = null;
        try {
            Range range = getRangeFromRequest(req);
            payload = cordra.getPayloadByName(objectId, payloadName, metadata, range.getStart(), range.getEnd());
            if (payload == null) {
                ServletErrorUtil.notFound(resp, "No payload " + payloadName + " in object " + objectId);
                return;
            }
            sendPayloadResponse(req, resp, metadata, payload);
        } catch (NotFoundCordraException e) {
            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");
            ServletErrorUtil.notFound(resp, "Missing object");
        } catch (Exception e) {
            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");
            ServletErrorUtil.internalServerError(resp);
            logger.error("Something went wrong getting " + objectId, e);
        } finally {
            if (payload != null && payload.getInputStream() != null) {
                try { payload.getInputStream().close(); } catch (Exception e) { }
            }
        }
    }

    private void doGetFilterByJsonPointers(HttpServletResponse resp, String objectId, String userId, String filterJson, boolean isPretty, boolean isFull) throws IOException {
        try {
            Set<String> filter = gson.fromJson(filterJson, new TypeToken<Set<String>>(){}.getType());
            JsonElement jsonElement = cordra.getObjectFilterByJsonPointers(objectId, userId, filter, isFull);
            String json;
            if (isPretty) {
                json = GsonUtility.getPrettyGson().toJson(jsonElement);
            } else {
                json = jsonElement.toString();
            }
            resp.getWriter().write(json);
        } catch (InvalidException e) {
            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");
            ServletErrorUtil.forbidden(resp, e.getMessage());
        } catch (NotFoundCordraException e) {
            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");
            ServletErrorUtil.notFound(resp, "Missing object");
        } catch (Exception e) {
            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");
            ServletErrorUtil.internalServerError(resp);
            logger.error("Something went wrong getting " + objectId, e);
        }
    }

    private void doGetJsonPointer(HttpServletResponse resp, String objectId, String userId, String jsonPointer, boolean isWantText, boolean isPretty) throws IOException {
        try {
            JsonNode jsonNode = cordra.getAtJsonPointer(objectId, userId, jsonPointer);

            if (jsonNode == null) {
                ServletErrorUtil.notFound(resp, "Missing data at jsonPointer in object");
            } else {
                if (isWantText) {
                    CordraObject co = cordra.getCordraObject(objectId);
                    String mediaType = cordra.getMediaType(co.type, JsonUtil.gsonToJackson(co.content), jsonPointer);
                    writeText(resp, jsonNode, mediaType);
                } else {
                    String json;
                    if (isPretty) {
                        ObjectMapper mapper = new ObjectMapper();
                        json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode);
                    } else {
                        json = jsonNode.toString();
                    }
                    resp.getWriter().write(json);
                }
            }
        } catch (InvalidException e) {
            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");
            ServletErrorUtil.forbidden(resp, e.getMessage());
        } catch (NotFoundCordraException e) {
            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");
            ServletErrorUtil.notFound(resp, "Missing object");
        } catch (Exception e) {
            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");
            ServletErrorUtil.internalServerError(resp);
            logger.error("Something went wrong getting " + objectId, e);
        }
    }

    Range getRangeFromRequest(HttpServletRequest req) {
        if (req.getHeader("If-Range") != null) return new Range(null, null);
        String rangeHeader = req.getHeader("Range");
        if (rangeHeader == null) return new Range(null, null);
        if (!rangeHeader.startsWith("bytes=")) return new Range(null, null);
        if (rangeHeader.contains(",")) return new Range(null, null);
        String rangePart = rangeHeader.substring(6);
        String[] parts = rangePart.split("-");
        if (parts.length == 1 && rangePart.endsWith("-")) {
            return new Range(Long.parseLong(parts[0]), null);
        }

        if (parts.length != 2) return new Range(null, null);
        try {
            if (parts[0].isEmpty()) {
                return new Range(null, Long.parseLong(parts[1]));
            } else if (parts[1].isEmpty()) {
                return new Range(Long.parseLong(parts[0]), null);
            } else {
                return new Range(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
            }
        } catch (NumberFormatException e) {
            return new Range(null, null);
        }
    }

    private void sendPayloadResponse(HttpServletRequest req, HttpServletResponse resp, boolean metadata, PayloadWithRange payload) throws IOException {
        String mediaType = payload.mediaType;
        String filename = payload.filename;
        if (metadata) {
            FileMetadataResponse metadataResponse = new FileMetadataResponse(filename, mediaType);
            gson.toJson(metadataResponse, resp.getWriter());
        } else {
            try (InputStream in = payload.getInputStream()) {
                if (in == null) {
                    ServletErrorUtil.internalServerError(resp);
                    logger.error("Unexpected null payload stream");
                    return;
                }
                Long start = payload.range.getStart();
                Long end = payload.range.getEnd();
                long size = payload.size;
                if (start != null && end != null && (start >= size || end < start)) {
                    resp.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                    resp.setHeader("Content-Range", "bytes */" + size);
                    return;
                }
                if (mediaType == null) {
                    mediaType = "application/octet-stream";
                }
                String disposition = req.getParameter("disposition");
                if (disposition == null) disposition = "inline";
                resp.setHeader("Content-Disposition", ServletUtil.contentDispositionHeaderFor(disposition, filename));
                resp.setContentType(mediaType);

                int contentLength = (int) size;
                if (start != null && end != null) {
                    resp.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
                    resp.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + size);
                    long contentLengthLong = (end - start) +1;
                    contentLength = (int) contentLengthLong;
                }
                resp.setContentLength(contentLength);
                IOUtils.copy(in, resp.getOutputStream());
            }
        }
    }

    private void writeText(HttpServletResponse resp, JsonNode jsonNode, String mediaType) throws IOException {
        if (jsonNode.isValueNode()) {
            if (mediaType == null) {
                mediaType = "text/plain";
            }
            resp.setContentType(mediaType);
            if (!mediaType.contains(";")) {
                resp.setCharacterEncoding("UTF-8");
            }
            resp.getWriter().write(jsonNode.asText());
        } else {
            ServletErrorUtil.badRequest(resp, "Requested json is not textual");
        }
    }

    private void doSearch(HttpServletRequest req, HttpServletResponse resp, String query) throws IOException {
        try {
            cordra.ensureIndexUpToDate();
            String userId = (String) req.getAttribute("userId");
            boolean hasUserObject = ServletUtil.getBooleanAttribute(req, "hasUserObject");
            List<String> groupIds = cordra.getAclEnforcer().getGroupsForUser(userId);
            boolean excludeVersions = true;
            String restrictedQuery = QueryRestrictor.restrict(query, userId, hasUserObject, groupIds, cordra.getDesign().authConfig, excludeVersions);
            String pageNumString = req.getParameter("pageNum");
            if (pageNumString == null) pageNumString = "0";
            String pageSizeString = req.getParameter("pageSize");
            if (pageSizeString == null) pageSizeString = "-1";
            String sortFieldsString = req.getParameter("sortFields");
            boolean isHandles = ServletUtil.getBooleanParameter(req, "ids");
            int pageNum;
            int pageSize;
            try {
                pageNum = Integer.parseInt(pageNumString);
                pageSize = Integer.parseInt(pageSizeString);
            } catch (NumberFormatException e) {
                ServletErrorUtil.badRequest(resp, e.getMessage());
                return;
            }
            if (pageNum < 0) {
                pageNum = 0;
            }
            boolean isPostProcess = true;
            if (isHandles) {
                cordra.searchHandles(restrictedQuery, pageNum, pageSize, sortFieldsString, resp.getWriter(), isPostProcess, userId);
            } else {
                boolean isFull = ServletUtil.getBooleanParameter(req, "full", true);
                String filterJson = req.getParameter("filter");
                Set<String> filter = null;
                if (filterJson != null) {
                    filter = gson.fromJson(filterJson, new TypeToken<Set<String>>(){}.getType());
                    cordra.search(restrictedQuery, pageNum, pageSize, sortFieldsString, resp.getWriter(), isPostProcess, userId, filter, isFull);
                } else {
                    cordra.search(restrictedQuery, pageNum, pageSize, sortFieldsString, resp.getWriter(), isPostProcess, userId, isFull);
                }
            }
        } catch (CordraException e) {
            if (looksLikeParseFailure(e)) {
                ServletErrorUtil.badRequest(resp, "Query parse failure");
            } else {
                logger.error("Error in doSearch", e);
                ServletErrorUtil.internalServerError(resp);
            }
        } catch (Exception e) {
            logger.error("Error in doSearch", e);
            ServletErrorUtil.internalServerError(resp);
        }
    }

    public static boolean looksLikeParseFailure(CordraException e) {
        if (e.getCause() instanceof ElasticsearchStatusException) {
            for (Throwable t : e.getCause().getSuppressed()) {
                if (t instanceof org.elasticsearch.client.ResponseException) {
                    if (t.getMessage() != null && t.getMessage().contains("Failed to parse query")) {
                        return true;
                    }
                }
            }
        }
        return e.getMessage() != null && (e.getMessage().contains("Parse failure")
                || e.getMessage().contains("Cannot parse")
                || e.getMessage().contains("org.apache.lucene.queryparser.classic.ParseException")
                || e.getMessage().contains("org.apache.solr.search.SyntaxError"));
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        createOrUpdate(req, resp, false);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (isForm(req)) {
            doGet(req, resp);
        } else {
            createOrUpdate(req, resp, true);
        }
    }

    private void createOrUpdate(HttpServletRequest req, HttpServletResponse resp, boolean isCreate) throws IOException {
        // Note: due to longstanding Tomcat behaviour, ensure that getParts() is called before getParameter() for multipart PUT requests
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        //PathParser path = new PathParser(req.getPathInfo());
        String objectId = ServletUtil.getPath(req);
        if (objectId != null && !objectId.isEmpty()) objectId = objectId.substring(1);
        else objectId = null;

        if (isCreate && objectId != null && !objectId.isEmpty()) {
            ServletErrorUtil.badRequest(resp, "Unexpected object id in POST");
            return;
        }
        if (!isCreate && (objectId == null || objectId.isEmpty())) {
            ServletErrorUtil.badRequest(resp, "Missing object id in PUT");
            return;
        }

        String handle = null;
        List<Payload> payloads = null;
        try {
            Request requestData = getJsonAndPayloadsFromRequest(req);
            boolean isDryRun = ServletUtil.getBooleanParameter(req, "dryRun");
            String objectType = req.getParameter("type");
            if (objectType != null && !cordra.isKnownType(objectType)) {
                ServletErrorUtil.badRequest(resp, "Unknown type " + objectType);
                return;
            }
            if (isCreate) {
                if (objectType == null || objectType.isEmpty()) {
                    ServletErrorUtil.badRequest(resp, "Missing object type");
                    return;
                }
            }
            List<String> payloadsToDelete = new ArrayList<>();
            String[] payloadsToDeleteArray = req.getParameterValues("payloadToDelete");
            if (payloadsToDeleteArray != null) {
                for (String payloadName : payloadsToDeleteArray) {
                    payloadsToDelete.add(payloadName);
                }
            }
            handle = req.getParameter("handle");
            if (handle == null) {
                String suffix = req.getParameter("suffix");
                if (suffix != null) handle = cordra.getHandleForSuffix(suffix);
            }
            String jsonData = requestData.json;
            String aclString = requestData.acl;
            String userMetadataString = requestData.userMetadata;
            payloads = requestData.payloads;
            if (payloads != null) {
                for (Payload payload : payloads) {
                    payloadsToDelete.remove(payload.name);
                }
            }
            if (jsonData == null) {
                ServletErrorUtil.badRequest(resp, "Missing JSON");
                return;
            }
            CordraObject.AccessControlList acl = null;
            if (aclString != null) {
                try {
                    acl = gson.fromJson(aclString, CordraObject.AccessControlList.class);
                } catch (JsonParseException e) {
                    ServletErrorUtil.badRequest(resp, "Invalid ACL format");
                    return;
                }
            }
            JsonObject userMetadata = null;
            if (userMetadataString != null) {
                try {
                    JsonParser parser = new JsonParser();
                    userMetadata = parser.parse(userMetadataString).getAsJsonObject();
                } catch (JsonParseException e) {
                    ServletErrorUtil.badRequest(resp, "Invalid userMetadata format");
                    return;
                }
            }
            CordraObject co;
            String userId = (String) req.getAttribute("userId");
            boolean hasUserObject = ServletUtil.getBooleanAttribute(req, "hasUserObject");
            String jsonPointer = req.getParameter("jsonPointer");
            if (isCreate) {
                co = cordra.writeJsonAndPayloadsIntoCordraObjectIfValid(objectType, jsonData, acl, userMetadata, payloads, handle, userId, isDryRun);
            } else {
                if (jsonPointer != null) {
                    co = cordra.modifyObjectAtJsonPointer(objectId, jsonPointer, jsonData, userId, hasUserObject, isDryRun);
                } else {
                    co = cordra.writeJsonAndPayloadsIntoCordraObjectIfValidAsUpdate(objectId, objectType, jsonData, acl, userMetadata, payloads, userId, hasUserObject, payloadsToDelete, isDryRun);
                }
            }

            resp.setStatus(HttpServletResponse.SC_OK);
            resp.setHeader("Location", StringUtils.encodeURLPath("/objects/" + co.id));
            resp.setHeader("X-Schema", co.type);
            boolean isFull = ServletUtil.getBooleanParameter(req, "full");
            if (isFull) {
//                if (co.metadata != null) co.metadata.internalMetadata = null;
                co = CordraService.copyOfCordraObjectRemovingInternalMetadata(co);
                boolean hasResponseContext = ServletUtil.getBooleanParameter(req, "includeResponseContext");
                if (hasResponseContext) {
                    String perm = resp.getHeader("X-Permission");
                    if (perm != null) {
                        if (co.responseContext == null) co.responseContext = new JsonObject();
                        co.responseContext.addProperty("permission", perm);
                    }
                }
                String fullJson = gson.toJson(co);
                resp.getWriter().write(fullJson);
            } else if (!isCreate && jsonPointer != null) {
                JsonNode node = JsonUtil.gsonToJackson(co.content);
                node = JsonUtil.getJsonAtPointer(jsonPointer, node);
                JsonUtil.printJson(resp.getWriter(), node);
            } else {
                resp.getWriter().write(co.getContentAsString());
            }
        } catch (ReadOnlyCordraException e) {
            ServletErrorUtil.badRequest(resp, "Cordra is read-only");
        } catch (NotFoundCordraException e) {
            ServletErrorUtil.notFound(resp, "Missing object");
        } catch (ConflictCordraException e) {
            if (e.getMessage() != null) {
                ServletErrorUtil.conflict(resp, e.getMessage());
//                logger.error("ObjectAlreadyExistsCordraException in doPost", e);
            } else if (handle != null) {
                ServletErrorUtil.conflict(resp, "Object " + handle + " already exists");
//                logger.error("ObjectAlreadyExistsCordraException in doPost", e);
            } else {
                ServletErrorUtil.internalServerError(resp);
                logger.error("Unexpected ObjectAlreadyExistsCordraException in doPost", e);
            }
        } catch (BadRequestCordraException e) {
            ServletErrorUtil.badRequest(resp, e.getMessage());
            logger.error("BadRequestCordraException in doPost", e);
        } catch (InternalErrorCordraException e) {
            ServletErrorUtil.internalServerError(resp);
            logger.error("InternalErrorCordraException in doPost", e);
        } catch (InvalidException invalidException) {
            ServletErrorUtil.badRequest(resp, invalidException.getMessage());
        } catch (Exception e) {
            ServletErrorUtil.internalServerError(resp);
            logger.error("Unexpected exception in doPost", e);
        } finally {
            if (payloads != null) {
                for (Payload payload : payloads) {
                    try { payload.getInputStream().close(); } catch (Exception e) { }
                }
            }
        }
    }

    private static class Request {
        String json;
        String acl;
        String userMetadata;
        List<Payload> payloads;

        public Request(String json) {
            this.json = json;
        }

        public Request(String json, String acl, String userMetadata, List<Payload> payloads) {
            this.json = json;
            this.acl = acl;
            this.userMetadata = userMetadata;
            this.payloads = payloads;
        }
    }

    private Request getJsonAndPayloadsFromRequest(HttpServletRequest req) throws IOException, ServletException {
        // Note: due to longstanding Tomcat behaviour, ensure that getParts() is called before getParameter() for multipart PUT requests
        if (isMultipart(req)) {
            String jsonData = null;
            String acl = null;
            String userMetadata = null;
            List<Payload> payloads = new ArrayList<>();
            Collection<Part> parts = req.getParts();
            for(Part part : parts) {
                String partName = part.getName();
                String filename = getFileName(part.getHeader("Content-Disposition"));
                if ((JSON.equals(partName) || CONTENT.equals(partName)) && filename == null) {
                    jsonData = getPartAsString(part);
                } else if (ACL.equals(partName) && filename == null) {
                    acl = getPartAsString(part);
                } else if (USER_METADATA.equals(partName) && filename == null) {
                    userMetadata = getPartAsString(part);
                } else {
                    String payloadName = partName;
                    if (filename != null) {
                        // form-data without filename treated as parameters, so does not generate a payload
                        Payload payload = new Payload();
                        payload.name = payloadName;
                        payload.setInputStream(part.getInputStream());
                        payload.mediaType = part.getContentType();
                        payload.filename = filename;
                        payloads.add(payload);
                    }
                }
            }
            return new Request(jsonData, acl, userMetadata, payloads);
        } else if (isForm(req) && "POST".equals(req.getMethod())) {
            String content = req.getParameter(CONTENT);
            if (content == null) content = req.getParameter(JSON);
            return new Request(content);
        } else if (isForm(req)) {
            return new Request(getJsonFromFormData(StreamUtil.readFully(req.getReader())));
        } else {
            return new Request(StreamUtil.readFully(req.getReader()));
        }
    }

    static String getJsonFromFormData(String data) {
        String[] strings = data.split("&");
        for (String string : strings) {
            int equals = string.indexOf('=');
            if (equals < 0) {
                String name = StringUtils.decodeURL(string);
                if (JSON.equals(name) || CONTENT.equals(name)) return "";
            } else {
                String name = StringUtils.decodeURL(string.substring(0, equals));
                if (JSON.equals(name) || CONTENT.equals(name)) {
                    String value = StringUtils.decodeURL(string.substring(equals + 1));
                    return value;
                }
            }
        }
        return null;
    }

    private static boolean isMultipart(HttpServletRequest req) {
        String contentType = req.getContentType();
        if (contentType == null) return false;
        contentType = contentType.toLowerCase(Locale.ENGLISH);
        return contentType.startsWith("multipart/form-data");
    }

    private static final String APPLICATION_X_WWW_FORM_URLENCODED = "application/x-www-form-urlencoded";
    private static final int APPLICATION_X_WWW_FORM_URLENCODED_LENGTH = APPLICATION_X_WWW_FORM_URLENCODED.length();

    private static boolean isForm(HttpServletRequest req) {
        String contentType = req.getContentType();
        if (contentType == null) return false;
        contentType = contentType.toLowerCase(Locale.ENGLISH);
        if (contentType.startsWith(APPLICATION_X_WWW_FORM_URLENCODED)) {
            if (contentType.length() == APPLICATION_X_WWW_FORM_URLENCODED_LENGTH) return true;
            char ch = contentType.charAt(APPLICATION_X_WWW_FORM_URLENCODED_LENGTH);
            if (ch == ';' || ch == ' ' || ch == '\t') return true;
        }
        return false;
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        String userId = (String) req.getAttribute("userId");
        String objectId = ServletUtil.getPath(req);
        if (objectId != null && !objectId.isEmpty()) objectId = objectId.substring(1);
        if (objectId == null) objectId = "";
        boolean deleteObjectWithEmptyId = ServletUtil.getBooleanParameter(req, "deleteObjectWithEmptyId", false);
        if (deleteObjectWithEmptyId && !objectId.isEmpty()) {
            ServletErrorUtil.badRequest(resp, "Specified object id to delete but also deleteObjectWithEmptyId");
            return;
        }

        if (objectId.isEmpty() && !deleteObjectWithEmptyId) {
            ServletErrorUtil.badRequest(resp, "Missing object id in DELETE");
            return;
        }
        String jsonPointer = req.getParameter("jsonPointer");
        String payloadName = req.getParameter("payload");
        if (jsonPointer != null && payloadName != null) {
            ServletErrorUtil.badRequest(resp, "DELETE specified both jsonPointer and payload");
            return;
        }
        if ("".equals(jsonPointer)) {
            ServletErrorUtil.badRequest(resp, "Cannot delete empty json pointer");
            return;
        }
        boolean hasUserObject = ServletUtil.getBooleanAttribute(req, "hasUserObject");
        if (jsonPointer == null && payloadName == null) {
            try {
                cordra.delete(objectId, userId);
                resp.getWriter().write("{}");
            } catch (ReadOnlyCordraException e) {
                ServletErrorUtil.badRequest(resp, "Cordra is read-only");
            } catch (NotFoundCordraException e) {
                ServletErrorUtil.notFound(resp, "Missing object");
            } catch (InvalidException e) {
                ServletErrorUtil.forbidden(resp, e.getMessage());
            } catch (Exception e) {
                ServletErrorUtil.internalServerError(resp);
                logger.error("Unexpected exception in doDelete", e);
            }
        } else {
            try {
                if (jsonPointer != null) {
                    cordra.deleteJsonPointer(objectId, jsonPointer, userId, hasUserObject);
                }
                if (payloadName != null) {
                    cordra.deletePayload(objectId, payloadName, userId, hasUserObject);
                }
                resp.getWriter().write("{}");
            } catch (ReadOnlyCordraException e) {
                ServletErrorUtil.badRequest(resp, "Cordra is read-only");
            } catch (NotFoundCordraException e) {
                ServletErrorUtil.notFound(resp, "Missing object");
            } catch (InvalidException e) {
                ServletErrorUtil.badRequest(resp, e.getMessage());
            } catch (Exception e) {
                ServletErrorUtil.internalServerError(resp);
                logger.error("Unexpected exception in doDelete", e);
            }
        }
    }

    private static String getPartAsString(Part part) throws IOException {
        InputStream in = part.getInputStream();
        String result = IOUtils.toString(in, "UTF-8").trim();
        in.close();
        return result;
    }

    static String getFileName(String contentDispositionHeader) {
        if (contentDispositionHeader == null) return null;
        int index = contentDispositionHeader.indexOf(';');
        if (index < 0) return null;
        String firstPart = contentDispositionHeader.substring(0, index);
        if (firstPart.contains("=") || firstPart.contains(",") || firstPart.contains("\"")) return null;
        String filename = null;
        String filenameStar = null;
        while (index >= 0) {
            int nameStart = index + 1;
            int equals = contentDispositionHeader.indexOf('=', nameStart);
            if (equals < 0) return null;
            String name = contentDispositionHeader.substring(nameStart, equals).trim().toLowerCase(Locale.ENGLISH);
            if (name.contains(",") || name.contains(";")) return null;
            int valueStart = skipWhitespace(contentDispositionHeader, equals + 1);
            if (valueStart >= contentDispositionHeader.length()) return null;
            char ch = contentDispositionHeader.charAt(valueStart);
            if (ch == ';') return null;
            boolean quoted = ch == '"';
            if (quoted) valueStart++;
            ch = ';';
            int valueEnd = valueStart;
            while (valueEnd < contentDispositionHeader.length()) {
                ch = contentDispositionHeader.charAt(valueEnd);
                if (quoted && ch == '"') break;
                if (!quoted && ch == ';') break;
                if (!quoted && (ch == ',' || ch == ' ')) return null;
                if (quoted && ch == '\\') {
                    valueEnd++;
                    if (valueEnd >= contentDispositionHeader.length()) break;
                }
                valueEnd++;
            }
            if (quoted && ch != '"') return null;
            if (quoted) {
                if (unexpectedCharactersAfterQuotedString(contentDispositionHeader, valueEnd)) return null;
            }
            if ("filename".equals(name) || "filename*".equals(name)) {
                String value = contentDispositionHeader.substring(valueStart, valueEnd).trim();
                if (quoted) {
                    value = unescapeQuotedStringContents(value);
                }
                if ("filename*".equals(name)) {
                    if (quoted) return null;
                    if (filenameStar != null) continue;
                    String decoding = rfc5987Decode(value);
                    if (decoding != null) {
                        filenameStar = decoding;
                    }
                } else {
                    if (filename != null) return null;
                    filename = value;
                }
            }
            index = contentDispositionHeader.indexOf(';', valueEnd);
        }
        if (filenameStar != null) filename = filenameStar;
        filename = stripPath(filename);
        return filename;
    }

    private static int skipWhitespace(String contentDispositionHeader, int index) {
        while (index < contentDispositionHeader.length()) {
            char ch = contentDispositionHeader.charAt(index);
            if (!Character.isWhitespace(ch)) break;
            index++;
        }
        return index;
    }

    private static boolean unexpectedCharactersAfterQuotedString(String contentDispositionHeader, int index) {
        int semicolon = contentDispositionHeader.indexOf(';', index);
        String remainder;
        if (semicolon < 0) {
            remainder = contentDispositionHeader.substring(index + 1);
        } else {
            remainder = contentDispositionHeader.substring(index + 1, semicolon);
        }
        return !remainder.trim().isEmpty();
    }

    private static String unescapeQuotedStringContents(String value) {
        StringBuilder sb = new StringBuilder(value);
        int escape = sb.indexOf("\\");
        while (escape >= 0) {
            sb.replace(escape, escape + 2, sb.substring(escape + 1, escape + 2));
            escape = sb.indexOf("\\", escape + 1);
        }
        value = sb.toString();
        return value;
    }

    private static String rfc5987Decode(String value) {
        int apos = value.indexOf('\'');
        int apos2 = -1;
        if (apos > 0) apos2 = value.indexOf('\'', apos + 1);
        if (apos2 < 0) return null;
        String enc = value.substring(0, apos);
        value = value.substring(apos2 + 1);
        try {
            return URLDecoder.decode(value.replace("+", "%2B"), enc);
        } catch (UnsupportedEncodingException e) {
            return null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String stripPath(String filename) {
        if (filename != null) {
            int lastSlash = filename.lastIndexOf('/');
            int lastBackslash = filename.lastIndexOf('\\');
            if (lastSlash > lastBackslash) filename = filename.substring(lastSlash + 1);
            else if (lastBackslash > lastSlash) filename = filename.substring(lastBackslash + 1);
        }
        return filename;
    }
}
