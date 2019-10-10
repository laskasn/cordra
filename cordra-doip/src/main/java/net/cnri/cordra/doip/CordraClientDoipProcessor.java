/*************************************************************************\
    Copyright (c) 2019 Corporation for National Research Initiatives;
                        All rights reserved.
\*************************************************************************/

package net.cnri.cordra.doip;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.cnri.cordra.api.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonWriter;

import net.dona.doip.DoipConstants;
import net.dona.doip.InDoipMessage;
import net.dona.doip.InDoipSegment;
import net.dona.doip.client.DigitalObject;
import net.dona.doip.client.Element;
import net.dona.doip.server.DoipProcessor;
import net.dona.doip.server.DoipServerRequest;
import net.dona.doip.server.DoipServerResponse;
import net.dona.doip.util.GsonUtility;
import net.dona.doip.util.InDoipMessageUtil;
import net.handle.hdllib.Common;
import net.handle.hdllib.HandleException;
import net.handle.hdllib.HandleResolver;
import net.handle.hdllib.HandleValue;
import net.handle.hdllib.Util;

public class CordraClientDoipProcessor implements DoipProcessor {
    private static final Logger logger = LoggerFactory.getLogger(CordraClientDoipProcessor.class);

    private String serviceId;
    private String address;
    private int port;
    private PublicKey publicKey;
    private HandleResolver resolver = new HandleResolver();

    private CordraClient cordraClient;

    @Override
    public void init(JsonObject config) {
        serviceId = config.get("serviceId").getAsString();
        address = config.has("address") ? config.get("address").getAsString() : null;
        port = config.has("port") ? config.get("port").getAsInt() : -1;
        publicKey = config.has("publicKey") ? GsonUtility.getGson().fromJson(config.get("publicKey"), PublicKey.class) : null;
        String baseUri = config.get("baseUri").getAsString();
        String username = config.get("username").getAsString();
        String password = config.get("password").getAsString();
        try {
            cordraClient = new TokenUsingHttpCordraClient(baseUri, username, password);
        } catch (Exception e) {
            logger.error("Startup error", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void shutdown() {
        try {
            cordraClient.close();
        } catch (Exception e) {
            logger.warn("Error closing", e);
        }
    }

    @Override
    public void process(DoipServerRequest req, DoipServerResponse resp) throws IOException {
        try {
            if (serviceId.equals(req.getTargetId())) {
                processService(req, resp);
            } else {
                processObj(req, resp);
            }
        } catch (ConflictCordraException e) {
            resp.setStatus(DoipConstants.STATUS_CONFLICT);
            resp.setAttribute(DoipConstants.MESSAGE_ATT, e.getMessage());
        } catch (BadRequestCordraException e) {
            resp.setStatus(DoipConstants.STATUS_BAD_REQUEST);
            resp.setAttribute(DoipConstants.MESSAGE_ATT, e.getMessage());
        } catch (NotFoundCordraException e) {
            resp.setStatus(DoipConstants.STATUS_NOT_FOUND);
            resp.setAttribute(DoipConstants.MESSAGE_ATT, e.getMessage());
        } catch (UnauthorizedCordraException e) {
            resp.setStatus(DoipConstants.STATUS_UNAUTHENTICATED);
            resp.setAttribute(DoipConstants.MESSAGE_ATT, e.getMessage());
        } catch (ForbiddenCordraException e) {
            resp.setStatus(DoipConstants.STATUS_FORBIDDEN);
            resp.setAttribute(DoipConstants.MESSAGE_ATT, e.getMessage());
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected exception", e);
            resp.setStatus(DoipConstants.STATUS_ERROR);
            resp.setAttribute(DoipConstants.MESSAGE_ATT, "An unexpected server error occurred");
        }
    }

    private void processService(DoipServerRequest req, DoipServerResponse resp) throws CordraException, IOException {
        String operationId = req.getOperationId();
        if (DoipConstants.OP_HELLO.equals(operationId)) {
            serviceHello(req, resp);
        } else if (DoipConstants.OP_LIST_OPERATIONS.equals(operationId)) {
            listOperationsForService(req, resp);
        } else if (DoipConstants.OP_CREATE.equals(operationId)) {
            create(req, resp);
        } else if (DoipConstants.OP_SEARCH.equals(operationId)) {
            search(req, resp);
        } else {
            resp.setStatus(DoipConstants.STATUS_DECLINED);
            resp.setAttribute(DoipConstants.MESSAGE_ATT, "Operation not supported");
        }
    }

    private void processObj(DoipServerRequest req, DoipServerResponse resp) throws CordraException, IOException {
        String operationId = req.getOperationId();
        String targetId = req.getTargetId();
        if (DoipConstants.OP_RETRIEVE.equals(operationId)) {
            retrieve(req, resp);
        } else if (DoipConstants.OP_UPDATE.equals(operationId)) {
            update(req, resp);
        } else if (DoipConstants.OP_DELETE.equals(operationId)) {
            delete(req, resp);
        } else if (DoipConstants.OP_LIST_OPERATIONS.equals(operationId)) {
            listOperationsForObject(targetId, req, resp);
        } else {
            try {
                call(req, resp);
            } catch (NotFoundCordraException e) {
                resp.setStatus(DoipConstants.STATUS_DECLINED);
                resp.setAttribute(DoipConstants.MESSAGE_ATT, "Operation not supported");
            }
        }
    }

    private void create(DoipServerRequest req, DoipServerResponse resp) throws CordraException, IOException {
        Options options = authenticate(req);
        CordraObject co = cordraObjectFromSegments(req.getInput());
        co = cordraClient.create(co, options);
        DigitalObject dobj = DoipUtil.ofCordraObject(co);
        JsonElement dobjJson = GsonUtility.getGson().toJsonTree(dobj);
        resp.writeCompactOutput(dobjJson);
    }

    private void retrieve(DoipServerRequest req, DoipServerResponse resp) throws CordraException, IOException {
        if (!InDoipMessageUtil.isEmpty(req.getInput())) {
            throw new BadRequestCordraException("Unexpected input");
        }
        Options options = authenticate(req);
        String targetId = req.getTargetId();
        String element = req.getAttributeAsString("element");
        boolean includeElementData = element == null && getBooleanAttribute(req, "includeElementData");
        CordraObject co = cordraClient.get(targetId, options);
        if (co == null) throw new NotFoundCordraException(targetId);
        if (element == null) {
            DigitalObject dobj = DoipUtil.ofCordraObject(co);
            JsonElement dobjJson = GsonUtility.getGson().toJsonTree(dobj);
            if (!includeElementData) {
                resp.writeCompactOutput(dobjJson);
            } else {
                resp.getOutput().writeJson(dobjJson);
                for (Element el : dobj.elements) {
                    JsonObject header = new JsonObject();
                    header.addProperty("id", el.id);
                    resp.getOutput().writeJson(header);
                    resp.getOutput().writeBytes(cordraClient.getPayload(targetId, el.id, options));
                }
            }
        } else { // element != null
            JsonElement rangeElement = req.getAttribute("range");

            if (rangeElement == null) {
                resp.getOutput().writeBytes(cordraClient.getPayload(targetId, element, options));
            } else {
                JsonObject range = rangeElement.getAsJsonObject();
                Long start = null;
                Long end = null;
                if (range.has("start")) {
                    start = range.get("start").getAsLong();
                }
                if (range.has("end")) {
                    end = range.get("end").getAsLong();
                }
                resp.getOutput().writeBytes(cordraClient.getPartialPayload(targetId, element, start, end, options));
            }
        }
    }

    private boolean getBooleanAttribute(DoipServerRequest req, String att) {
        JsonElement el = req.getAttribute(att);
        if (el == null) return false;
        if (!el.isJsonPrimitive()) return false;
        JsonPrimitive priv = el.getAsJsonPrimitive();
        if (priv.isBoolean()) return priv.getAsBoolean();
        if (priv.isString()) return "true".equals(priv.getAsString());
        return false;
    }

    private void update(DoipServerRequest req, DoipServerResponse resp) throws CordraException, IOException {
        Options options = authenticate(req);
        CordraObject co = cordraObjectFromSegments(req.getInput());
        co = cordraClient.update(co, options);
        DigitalObject dobj = DoipUtil.ofCordraObject(co);
        JsonElement dobjJson = GsonUtility.getGson().toJsonTree(dobj);
        resp.writeCompactOutput(dobjJson);
    }

    public static CordraObject cordraObjectFromSegments(InDoipMessage input) throws CordraException, IOException {
        InDoipSegment firstSegment = InDoipMessageUtil.getFirstSegment(input);
        if (firstSegment == null) {
            throw new BadRequestCordraException("Missing input");
        }
        DigitalObject digitalObject = GsonUtility.getGson().fromJson(firstSegment.getJson(), DigitalObject.class);
        CordraObject co = DoipUtil.toCordraObject(digitalObject);
        if (co.payloads != null) {
            Map<String, Payload> payloads = new HashMap<>();
            for (Payload p : co.payloads) {
                payloads.put(p.name, p);
            }
            Iterator<InDoipSegment> segments = input.iterator();
            while (segments.hasNext()) {
                InDoipSegment headerSegment = segments.next();
                String payloadName;
                try {
                    payloadName = headerSegment.getJson().getAsJsonObject().get("id").getAsString();
                } catch (Exception e) {
                    throw new BadRequestCordraException("Unexpected element header");
                }
                if (!segments.hasNext()) {
                    throw new BadRequestCordraException("Unexpected end of input");
                }
                InDoipSegment elementBytesSegment = segments.next();
                Payload p = payloads.get(payloadName);
                if (p == null) {
                    throw new BadRequestCordraException("No such element " + payloadName);
                }
                p.setInputStream(persistInputStream(elementBytesSegment.getInputStream()));
            }
        } else {
            if (!InDoipMessageUtil.isEmpty(input)) {
                throw new BadRequestCordraException("Unexpected input segments");
            }
        }
        return co;
    }

    private static ByteArrayInputStream persistInputStream(InputStream in) throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int r;
        while ((r = in.read(buf)) > 0) {
            bout.write(buf, 0, r);
        }
        return new ByteArrayInputStream(bout.toByteArray());
    }

    @SuppressWarnings("unused")
    private void delete(DoipServerRequest req, DoipServerResponse resp) throws CordraException, IOException {
        if (!InDoipMessageUtil.isEmpty(req.getInput())) {
            throw new BadRequestCordraException("Unexpected input");
        }
        Options options = authenticate(req);
        String targetId = req.getTargetId();
        cordraClient.delete(targetId, options);
    }

    private void search(DoipServerRequest req, DoipServerResponse resp) throws CordraException, IOException {
        if (!InDoipMessageUtil.isEmpty(req.getInput())) {
            throw new BadRequestCordraException("Unexpected input");
        }
        Options options = authenticate(req);
        JsonObject attributes = req.getAttributes();
        String query = req.getAttributeAsString("query");
        if (query == null) throw new BadRequestCordraException("Missing query");
        String type = req.getAttributeAsString("type");
        if (type == null) type = "full";
        int pageNum = 0;
        if (attributes.has("pageNum")) {
            pageNum = attributes.get("pageNum").getAsInt();
        }
        int pageSize = -1;
        if (attributes.has("pageSize")) {
            pageSize = attributes.get("pageSize").getAsInt();
        }
        List<SortField> sortFields = Collections.emptyList();
        if (attributes.has("sortFields")) {
            String sortFieldsString = attributes.get("sortFields").getAsString();
            sortFields = getSortFieldsFromString(sortFieldsString);
        }
        QueryParams params = new QueryParams(pageNum, pageSize, sortFields);
        Gson gson = GsonUtility.getGson();
        if ("id".equals(type)) {
            try (SearchResults<String> results = cordraClient.searchHandles(query, params, options)) {
                try (JsonWriter writer = new JsonWriter(resp.getOutput().getJsonWriter())) {
                    writeBeginResults(writer, results.size());
                    for (String id : results) {
                        writer.value(id);
                    }
                    writer.endArray();
                    writer.endObject();
                }
            }
        } else {
            try (SearchResults<CordraObject> results = cordraClient.search(query, params, options)) {
                try (JsonWriter writer = new JsonWriter(resp.getOutput().getJsonWriter())) {
                    writeBeginResults(writer, results.size());
                    for (CordraObject co : results) {
                        DigitalObject dobj = DoipUtil.ofCordraObject(co);
                        JsonElement dobjJson = gson.toJsonTree(dobj);
                        gson.toJson(dobjJson, writer);
                    }
                    writer.endArray();
                    writer.endObject();
                }
            }
        }
    }

    private void writeBeginResults(JsonWriter writer, int size) throws IOException {
        writer.setIndent("  ");
        writer.beginObject();
        writer.name("size").value(size);
        writer.name("results").beginArray();
    }

    private List<SortField> getSortFieldsFromString(String sortFields) {
        if (sortFields == null || "".equals(sortFields)) {
            return null;
        } else {
            List<SortField> result = new ArrayList<>();
            List<String> sortFieldStrings = getFieldsFromString(sortFields);
            for (String sortFieldString : sortFieldStrings) {
                result.add(getSortFieldFromString(sortFieldString));
            }
            return result;
        }
    }

    private SortField getSortFieldFromString(String sortFieldString) {
        String[] terms = sortFieldString.split(" ");
        boolean reverse = false;
        if (terms.length > 1) {
            String direction = terms[1];
            if ("DESC".equalsIgnoreCase(direction)) reverse = true;
        }
        String fieldName = terms[0];
        return new SortField(fieldName, reverse);
    }

    private List<String> getFieldsFromString(String s) {
        return Arrays.asList(s.split(","));
    }

    private void serviceHello(DoipServerRequest req, DoipServerResponse resp) throws CordraException, IOException {
        if (!InDoipMessageUtil.isEmpty(req.getInput())) {
            throw new BadRequestCordraException("Unexpected input");
        }
        if (containsAuthInfo(req)) {
            Options options = authenticate(req);
            AuthResponse authResponse = cordraClient.authenticateAndGetResponse(options);
            if (authResponse.active == false) {
                throw new UnauthorizedCordraException("Authentication failed");
            }
        }
        JsonObject res = buildDoipServiceInfo(serviceId, address, port, publicKey);
        resp.writeCompactOutput(res);
    }

    public static JsonObject buildDoipServiceInfo(String serviceId, String address, int port, PublicKey publicKey) {
        JsonObject res = new JsonObject();
        res.addProperty("id", serviceId);
        res.addProperty("type", "0.TYPE/DOIPService");
        JsonObject atts = new JsonObject();
        atts.addProperty("ipAddress", address);
        atts.addProperty("port", port);
        atts.addProperty("protocol", "TCP");
        atts.addProperty("protocolVersion", "2.0");
        if (publicKey != null) {
            atts.add("publicKey", GsonUtility.getGson().toJsonTree(publicKey));
        }
        res.add("attributes", atts);
        return res;
    }

    private void listOperationsForService(DoipServerRequest req, DoipServerResponse resp) throws CordraException, IOException {
        if (!InDoipMessageUtil.isEmpty(req.getInput())) {
            throw new BadRequestCordraException("Unexpected input");
        }
        if (containsAuthInfo(req)) {
            Options options = authenticate(req);
            AuthResponse authResponse = cordraClient.authenticateAndGetResponse(options);
            if (authResponse.active == false) {
                throw new UnauthorizedCordraException("Authentication failed");
            }
        }
        JsonArray res = new JsonArray();
        res.add(DoipConstants.OP_HELLO);
        res.add(DoipConstants.OP_LIST_OPERATIONS);
        res.add(DoipConstants.OP_CREATE);
        res.add(DoipConstants.OP_SEARCH);
        resp.writeCompactOutput(res);
    }

    private void listOperationsForObject(String targetId, DoipServerRequest req, DoipServerResponse resp) throws CordraException, IOException {
        if (!InDoipMessageUtil.isEmpty(req.getInput())) {
            throw new BadRequestCordraException("Unexpected input");
        }
        Options options = null;
        if (containsAuthInfo(req)) {
            options = authenticate(req);
        }
        CordraObject co = cordraClient.get(targetId, options);
        if (co == null) {
            resp.setStatus(DoipConstants.STATUS_NOT_FOUND);
            resp.setAttribute(DoipConstants.MESSAGE_ATT, "No such object " + targetId);
        } else {
            JsonArray res = new JsonArray();
            res.add(DoipConstants.OP_LIST_OPERATIONS);
            res.add(DoipConstants.OP_RETRIEVE);
            res.add(DoipConstants.OP_UPDATE);
            res.add(DoipConstants.OP_DELETE);
            if ("Schema".equals(co.type)) {
                List<String> methods = cordraClient.listMethodsForType(getTypeNameForSchemaObject(co), true);
                methods.forEach(res::add);
            } else {
                List<String> methods = cordraClient.listMethods(co.id, options);
                //List<String> methods = cordraClient.listMethodsForType(co.type, false);
                methods.forEach(res::add);
            }
            resp.writeCompactOutput(res);
        }
    }

    private void call(DoipServerRequest req, DoipServerResponse resp) throws CordraException, IOException {
        Options options = authenticate(req);
        String operationId = req.getOperationId();
        String targetId = req.getTargetId();

        InDoipSegment initialSegment = InDoipMessageUtil.getFirstSegment(req.getInput());
        JsonElement params = null;
        if (initialSegment != null) {
            if (initialSegment.isJson()) {
                params = initialSegment.getJson();
                if (!InDoipMessageUtil.isEmpty(req.getInput())) {
                    resp.setStatus(DoipConstants.STATUS_BAD_REQUEST);
                    resp.setAttribute(DoipConstants.MESSAGE_ATT, "Cordra operation expects at most single JSON segment");
                    return;
                }
            } else {
                resp.setStatus(DoipConstants.STATUS_BAD_REQUEST);
                resp.setAttribute(DoipConstants.MESSAGE_ATT, "Cordra operation expects at most single JSON segment");
                return;
            }
        }
        JsonElement result;
        if ("true".equals(req.getAttributeAsString("isCallForType"))) {
            String type = targetId;
            result = cordraClient.callForType(type, operationId, params, options);
        } else {
            CordraObject co = cordraClient.get(targetId);
            if (co == null) {
                resp.setStatus(DoipConstants.STATUS_NOT_FOUND);
                resp.setAttribute(DoipConstants.MESSAGE_ATT, "No such object " + targetId);
                return;
            }
            if ("Schema".equals(co.type)) {
                result = cordraClient.callForType(this.getTypeNameForSchemaObject(co), operationId, params, options);
            } else {
                result = cordraClient.call(targetId, operationId, params, options);
            }
        }
        if (result != null) {
            if (result.isJsonNull()) {
                // workaround for difference between explicit null result and undefined/missing result
                resp.getOutput().writeJson(result);
            } else {
                resp.writeCompactOutput(result);
            }
        }
    }

    private String getTypeNameForSchemaObject(CordraObject co) {
        return co.content.getAsJsonObject().get("name").getAsString();
    }

    private boolean containsAuthInfo(DoipServerRequest req) {
        if (isNullOrEmpty(req.getAuthentication()) && req.getConnectionClientId() == null) {
            return false;
        } else {
            return true;
        }
    }

    private Options authenticate(DoipServerRequest req) throws CordraException {
        if (req.getAuthentication() != null && !req.getAuthentication().isJsonObject()) {
            throw new UnauthorizedCordraException("Unable to parse authentication (not an object)");
        }
        if (isNullOrEmpty(req.getAuthentication())) {
            if (req.getConnectionClientId() != null) {
                return authenticateViaTls(req);
            }
            if (req.getClientId() == null) {
                // anonymous
                return new Options().setUseDefaultCredentials(false);
            } else {
                throw new UnauthorizedCordraException("No authentication provided for " + req.getClientId());
            }
        }
        JsonObject authentication = req.getAuthentication().getAsJsonObject();
        if (authentication.has("token")) {
            return authenticateViaToken(req, authentication);
        }
        if (authentication.has("password")) {
            return authenticateViaPassword(req, authentication);
        }
        throw new UnauthorizedCordraException("Unable to parse authentication (neither token nor password)");
    }

    private boolean isNullOrEmpty(JsonElement authentication) {
        if (authentication == null) return true;
        if (authentication.getAsJsonObject().keySet().isEmpty()) return true;
        return false;
    }

    private Options authenticateViaTls(DoipServerRequest req) throws UnauthorizedCordraException {
        if (req.getClientId() != null && !req.getClientId().equals(req.getConnectionClientId())) {
            throw new UnauthorizedCordraException("No authentication provided for " + req.getClientId());
        }
        String clientId = req.getConnectionClientId();
        PublicKey clientPublicKey = req.getConnectionPublicKey();
        if (clientPublicKeyChecks(clientId, clientPublicKey)) {
            return new Options().setAsUserId(clientId).setUseDefaultCredentials(true);
        } else {
            throw new UnauthorizedCordraException("Client TLS certificate key does not match handle record of " + clientId);
        }
    }

    private Options authenticateViaPassword(DoipServerRequest req, JsonObject authentication) throws UnauthorizedCordraException {
        String password = authentication.get("password").getAsString();
        String username = null;
        if (authentication.has("username")) username = authentication.get("username").getAsString();
        if (req.getClientId() != null) {
            if (username != null) {
                throw new UnauthorizedCordraException("No support for authenticating with both username and clientId");
            }
            return new Options().setUserId(req.getClientId()).setPassword(password);
        }
        if (username == null) {
            throw new UnauthorizedCordraException("Unable to parse authentication (neither username nor clientId)");
        }
        Options options = new Options();
        options.setUsername(username);
        options.setPassword(password);
        if (authentication.has("asUserId")) {
            options.setAsUserId(authentication.get("asUserId").getAsString());
        }
        return options;
    }

    private Options authenticateViaToken(@SuppressWarnings("unused") DoipServerRequest req, JsonObject authentication) {
        String token = authentication.get("token").getAsString();
        // req.getClientId() != null && !req.getClientId().equals(iss)
        Options options = new Options();
        options.setToken(token);
        if (authentication.has("asUserId")) {
            options.setAsUserId(authentication.get("asUserId").getAsString());
        }
        return options;
    }

    private boolean clientPublicKeyChecks(String clientId, PublicKey clientPublicKey) {
        List<PublicKey> publicKeys = getPublicKeysFor(clientId);
        for (PublicKey foundPublicKey : publicKeys) {
            if (foundPublicKey.equals(clientPublicKey)) {
                return true;
            }
        }
        return false;
    }

    private List<PublicKey> getPublicKeysFor(String iss) {
        List<PublicKey> result = new ArrayList<>();
        if ("admin".equals(iss)) {
            try {
                JsonElement adminPublicKeyElement = cordraClient.get("design").content.getAsJsonObject().get("adminPublicKey");
                if (adminPublicKeyElement != null) {
                    result.add(GsonUtility.getGson().fromJson(adminPublicKeyElement, PublicKey.class));
                }
            } catch (Exception e) {
                logger.warn("Error checking admin public key", e);
            }
            return result;
        }
        try {
            HandleValue[] values = resolver.resolveHandle(Util.encodeString(iss), Common.PUBLIC_KEY_TYPES, null);
            List<PublicKey> pubkeyValues = Util.getPublicKeysFromValues(values);
            result.addAll(pubkeyValues);
        } catch (HandleException e) {
            // error resolving handle
        }
        return result;
    }
}
