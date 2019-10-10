/*************************************************************************\
    Copyright (c) 2019 Corporation for National Research Initiatives;
                        All rights reserved.
\*************************************************************************/

package net.cnri.cordra.api;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import net.cnri.cordra.util.DelegatedCloseableInputStream;
import net.cnri.util.StringUtils;
import net.handle.hdllib.trust.JsonWebSignature;
import net.handle.hdllib.trust.JsonWebSignatureFactory;
import org.apache.commons.codec.binary.Hex;
import org.apache.http.Consts;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.*;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.EntityUtils;
import org.slf4j.MDC;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

public class HttpCordraClient implements CordraClient {
    protected final CloseableHttpClient httpClient;
    private Gson gson;
    private final String baseUri;
    protected final String username;
    protected final String password;

    private static final String AS_USER_HEADER = "As-User";
    private static final String REQUEST_ID_HEADER = "Request-Id";
    private static final String REQUEST_ID_MDC_KEY = "requestId";

    private final SecureRandom random;

    public HttpCordraClient(String baseUri, String username, String password) throws CordraException {
        this.httpClient = getNewHttpClient();
        this.gson = new Gson();
        if (!baseUri.endsWith("/")) baseUri += "/";
        this.baseUri = baseUri;
        this.username = username;
        this.password = password;
        this.random = new SecureRandom();
    }

    public CloseableHttpClient getHttpClient() {
        return httpClient;
    }

    @Override
    public Gson getGson() {
        return gson;
    }

    @Override
    public void setGson(Gson gson) {
        this.gson = gson;
    }

    public String getBaseUri() { return baseUri; }

    @Override
    public void close() throws IOException {
        httpClient.close();
    }

    private static void closeQuietly(HttpEntity entity, CloseableHttpResponse response) {
        if (entity!=null) EntityUtils.consumeQuietly(entity);
        if (response != null) try { response.close(); } catch (IOException ex) { }
    }

    protected HttpUriRequest buildAuthenticateRequest() {
        String uri = baseUri + "check-credentials";
        return new HttpGet(uri);
    }

    @Override
    @SuppressWarnings({ "resource", "deprecation" })
    public AuthResponse authenticateAndGetResponse(Options options) throws CordraException {
        CloseableHttpResponse response = null;
        HttpEntity entity = null;
        try {
            response = sendHttpRequestWithCredentials(this::buildAuthenticateRequest, options);
            entity = response.getEntity();
            int statusCode = response.getStatusLine().getStatusCode();
// This code path seems impossible
//            if (statusCode == 403) {
//                return new AuthResponse(false, options.userId, options.userId == null ? options.username : null, null);
//            }
            String responseString = EntityUtils.toString(entity);
            if (statusCode != 200) {
                throw CordraException.fromStatusCode(statusCode, responseString);
            }
            AuthResponse resp = gson.fromJson(responseString, AuthResponse.class);
            // allow either parameter in the JSON response
            if (resp.active || resp.isActiveSession) {
                resp.active = true;
                resp.isActiveSession = true;
            }
            return resp;
        } catch (IOException e) {
            throw new InternalErrorCordraException(e);
        } finally {
            closeQuietly(entity, response);
        }
    }

    @Override
    @SuppressWarnings("resource")
    public CordraObject get(String id, Options options) throws CordraException {
        CloseableHttpResponse response = null;
        HttpEntity entity = null;
        try {
            response = sendHttpRequestWithCredentials(() -> buildGetRequest(id, options.requestContext), options);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 404) {
                return null;
            }
            entity = response.getEntity();
            String responseString = EntityUtils.toString(entity);
            if (statusCode != 200) {
                throw CordraException.fromStatusCode(statusCode, responseString);
            }
            return gson.fromJson(responseString, CordraObject.class);
        } catch (IOException e) {
            throw new InternalErrorCordraException(e);
        } finally {
            closeQuietly(entity, response);
        }
    }

    protected HttpUriRequest buildGetRequest(String id, JsonObject requestContext) {
        String uri = baseUri + "objects/" + StringUtils.encodeURLPath(id) + "?full";
        if (requestContext != null) {
            uri += "&requestContext=" + StringUtils.encodeURLComponent(gson.toJson(requestContext));
        }
        HttpUriRequest request = new HttpGet(uri);
        addRequestIdHeader(request);
        return request;
    }

    protected void addBasicAuthHeader(HttpRequest request, String usernameParam, String passwordParam) {
        if (usernameParam == null) return;
        UsernamePasswordCredentials creds = new UsernamePasswordCredentials(usernameParam, passwordParam);
        try {
            request.addHeader(new BasicScheme().authenticate(creds, request, null));
        } catch (AuthenticationException e) {
            throw new AssertionError(e);
        }
    }

    private void addBearerTokenHeader(HttpRequest request, String iss, PrivateKey privateKey) throws CordraException {
        JsonWebSignature jwt = generateJwt(iss, privateKey);
        addBearerTokenHeader(request, jwt.serialize());
    }

    protected JsonWebSignature generateJwt(String iss, PrivateKey privateKey) throws InternalErrorCordraException {
        long nowSeconds = System.currentTimeMillis() / 1000L;
        JsonObject claims = new JsonObject();
        claims.addProperty("iss", iss);
        claims.addProperty("sub", iss);
        claims.addProperty("jti", generateJti());
        claims.addProperty("iat", nowSeconds);
        claims.addProperty("exp", nowSeconds + 600);
        String claimsJson = claims.toString();
        JsonWebSignature jwt;
        try {
            jwt = JsonWebSignatureFactory.getInstance().create(claimsJson, privateKey);
        } catch (Exception e) {
            throw new InternalErrorCordraException(e);
        }
        return jwt;
    }

    private void addBearerTokenHeader(HttpRequest request, String token) {
        String authHeader = "Bearer " + token;
        request.addHeader("Authorization", authHeader);
    }

    private String generateJti() {
        byte[] bytes = new byte[10];
        random.nextBytes(bytes);
        return Hex.encodeHexString(bytes);
    }

    protected void addAsUserHeader(HttpRequest request, String asUser) {
        if (asUser != null) {
            request.addHeader(AS_USER_HEADER, asUser);
        }
    }

    protected void addRequestIdHeader(HttpRequest request) {
        String requestId = MDC.get(REQUEST_ID_MDC_KEY);
        if (requestId != null) {
            request.addHeader(REQUEST_ID_HEADER, requestId);
        }
    }

    protected void addCredentials(HttpRequest request, Options options) throws CordraException {
        if (options.useDefaultCredentials) {
            addBasicAuthHeader(request, username, password);
        } else if (options.token != null) {
            addBearerTokenHeader(request, options.token);
        } else if (options.privateKey != null) {
            if (options.userId != null) {
                addBearerTokenHeader(request, options.userId, options.privateKey);
            } else if (options.username != null) {
                addBearerTokenHeader(request, options.username, options.privateKey);
            }
        } else if (options.password != null) {
            if (options.userId != null) {
                addBasicAuthHeader(request, options.userId, options.password);
            } else if (options.username != null) {
                addBasicAuthHeader(request, options.username, options.password);
            }
        }
    }

    private static final RequestConfig IGNORE_COOKIES = RequestConfig.custom().setCookieSpec(CookieSpecs.IGNORE_COOKIES).build();

    private CloseableHttpResponse sendHttpRequestWithAuthHeader(Supplier<HttpUriRequest> requestSupplier, Options options) throws IOException, ClientProtocolException, CordraException {
        HttpUriRequest request = requestSupplier.get();
        addCredentials(request, options);
        if (options.asUserId != null) addAsUserHeader(request, options.asUserId);
        HttpClientContext context = HttpClientContext.create();
        context.setRequestConfig(IGNORE_COOKIES);
        return httpClient.execute(request, context);
    }

    protected CloseableHttpResponse sendHttpRequestWithCredentials(Supplier<HttpUriRequest> requestSupplier, Options options) throws IOException, ClientProtocolException, CordraException {
        return sendHttpRequestWithAuthHeader(requestSupplier, options);
    }

    @Override
    @SuppressWarnings("resource")
    public InputStream getPayload(String id, String payloadName, Options options) throws CordraException {
        CloseableHttpResponse response = null;
        HttpEntity entity = null;
        try {
            response = sendHttpRequestWithCredentials(() -> buildPayloadRequest(id, payloadName, options.requestContext), options);
            int statusCode = response.getStatusLine().getStatusCode();
            entity = response.getEntity();
            if (statusCode == 404) {
                closeQuietly(entity, response);
                return null;
            }
            if (statusCode != 200) {
                String responseString = EntityUtils.toString(entity);
                throw CordraException.fromStatusCode(statusCode, responseString);
            }
            return getPayloadInputStreamWithCorrectClose(entity, response);
        } catch (CordraException e) {
            closeQuietly(entity, response);
            throw e;
        } catch (RuntimeException | Error | IOException e) {
            closeQuietly(entity, response);
            throw new InternalErrorCordraException(e);
        }
    }

    protected HttpUriRequest buildPayloadRequest(String id, String payloadName, JsonObject requestContext) {
        String uri = baseUri + "objects/" + StringUtils.encodeURLPath(id) + "?payload=" + StringUtils.encodeURLComponent(payloadName);
        if (requestContext != null) {
            uri += "&requestContext=" + StringUtils.encodeURLComponent(gson.toJson(requestContext));
        }
        HttpUriRequest request = new HttpGet(uri);
        addRequestIdHeader(request);
        return request;
    }

    private static InputStream getPayloadInputStreamWithCorrectClose(HttpEntity entity, CloseableHttpResponse response) throws IOException {
        return new DelegatedCloseableInputStream(entity.getContent(), () -> closeQuietly(entity, response));
    }

    @Override
    @SuppressWarnings("resource")
    public InputStream getPartialPayload(String id, String payloadName, Long start, Long end, Options options) throws CordraException {
        CloseableHttpResponse response = null;
        HttpEntity entity = null;
        try {
            response = sendHttpRequestWithCredentials(() -> buildPartialPayloadRequest(id, payloadName, start, end, options.requestContext), options);
            int statusCode = response.getStatusLine().getStatusCode();
            entity = response.getEntity();
            if (statusCode == 404) {
                closeQuietly(entity, response);
                return null;
            }
            if (statusCode == 416) {
                throw new BadRequestCordraException("Range is not satisfiable");
            }
            if (statusCode != 200 && statusCode != 206) {
                String responseString = EntityUtils.toString(entity);
                throw CordraException.fromStatusCode(statusCode, responseString);
            }
            return getPayloadInputStreamWithCorrectClose(entity, response);
        } catch (CordraException e) {
            closeQuietly(entity, response);
            throw e;
        } catch (RuntimeException | Error | IOException e) {
            closeQuietly(entity, response);
            throw new InternalErrorCordraException(e);
        }
    }

    protected HttpUriRequest buildPartialPayloadRequest(String id, String payloadName, Long start, Long end, JsonObject requestContext) {
        HttpUriRequest request = buildPayloadRequest(id, payloadName, requestContext);
        addRequestIdHeader(request);
        if (start != null || end != null) {
            String rangeHeader = createRangeHeader(start, end);
            request.addHeader("Range", rangeHeader);
        }
        return request;
    }

    public static String createRangeHeader(Long start, Long end) {
        if (start != null && end != null) {
            return "bytes=" + start + "-" + end;
        } else if (start != null && end == null) {
            return "bytes=" + start + "-";
        } else if (start == null && end != null) {
            return "bytes=" + "-" + end;
        } else {
            return null;
        }
    }

    @Override
    public CordraObject create(CordraObject d, Options options) throws CordraException {
        return createOrUpdate(d, true, options);
    }

    @Override
    public CordraObject update(CordraObject d, Options options) throws CordraException {
        return createOrUpdate(d, false, options);
    }

    private static final Type LIST_STRING_TYPE = new TypeToken<List<String>>(){}.getType();

    @Override
    @SuppressWarnings("resource")
    public List<String> listMethods(String objectId, Options options) throws CordraException {
        CloseableHttpResponse response = null;
        HttpEntity entity = null;
        try {
            response = sendHttpRequestWithCredentials(() -> buildListMethodsRequest(objectId, null, false), options);
            entity = response.getEntity();
            String responseString = EntityUtils.toString(entity);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                throw CordraException.fromStatusCode(statusCode, responseString);
            }
            return gson.fromJson(responseString, LIST_STRING_TYPE);
        } catch (IOException e) {
            throw new InternalErrorCordraException(e);
        } finally {
            closeQuietly(entity, response);
        }
    }

    @Override
    @SuppressWarnings("resource")
    public List<String> listMethodsForType(String type, boolean isStatic, Options options) throws CordraException {
        CloseableHttpResponse response = null;
        HttpEntity entity = null;
        try {
            response = sendHttpRequestWithCredentials(() -> buildListMethodsRequest(null, type, isStatic), options);
            entity = response.getEntity();
            String responseString = EntityUtils.toString(entity);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                throw CordraException.fromStatusCode(statusCode, responseString);
            }
            return gson.fromJson(responseString, LIST_STRING_TYPE);
        } catch (IOException e) {
            throw new InternalErrorCordraException(e);
        } finally {
            closeQuietly(entity, response);
        }
    }

    @Override
    @SuppressWarnings("resource")
    public JsonElement call(String objectId, String methodName, JsonElement params, Options options) throws CordraException {
        CloseableHttpResponse response = null;
        HttpEntity entity = null;
        try {
            response = sendHttpRequestWithCredentials(() -> buildCallRequest(objectId, null, methodName, params), options);
            entity = response.getEntity();
            String responseString = EntityUtils.toString(entity);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                throw CordraException.fromStatusCode(statusCode, responseString);
            }
            if (responseString.isEmpty()) return null;
            JsonParser parser = new JsonParser();
            return parser.parse(responseString);
        } catch (IOException e) {
            throw new InternalErrorCordraException(e);
        } finally {
            closeQuietly(entity, response);
        }
    }

    @Override
    @SuppressWarnings("resource")
    public JsonElement callForType(String type, String methodName, JsonElement params, Options options) throws CordraException {
        CloseableHttpResponse response = null;
        HttpEntity entity = null;
        try {
            response = sendHttpRequestWithCredentials(() -> buildCallRequest(null, type, methodName, params), options);
            entity = response.getEntity();
            String responseString = EntityUtils.toString(entity);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                throw CordraException.fromStatusCode(statusCode, responseString);
            }
            if (responseString.isEmpty()) return null;
            JsonParser parser = new JsonParser();
            return parser.parse(responseString);
        } catch (IOException e) {
            throw new InternalErrorCordraException(e);
        } finally {
            closeQuietly(entity, response);
        }
    }

    @Override
    @SuppressWarnings("resource")
    public VersionInfo publishVersion(String objectId, String versionId, boolean clonePayloads, Options options) throws CordraException {
        CloseableHttpResponse response = null;
        HttpEntity entity = null;
        try {
            response = sendHttpRequestWithCredentials(() -> buildPublishVersionRequest(objectId, versionId, clonePayloads, options.requestContext), options);
            entity = response.getEntity();
            String responseString = EntityUtils.toString(entity);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                throw CordraException.fromStatusCode(statusCode, responseString);
            }
            if (responseString.isEmpty()) return null;
            return gson.fromJson(responseString, VersionInfo.class);
        } catch (IOException e) {
            throw new InternalErrorCordraException(e);
        } finally {
            closeQuietly(entity, response);
        }
    }

    @Override
    @SuppressWarnings("resource")
    public List<VersionInfo> getVersionsFor(String objectId, Options options) throws CordraException {
        CloseableHttpResponse response = null;
        HttpEntity entity = null;
        try {
            response = sendHttpRequestWithCredentials(() -> buildGetVersionsForRequest(objectId, options.requestContext), options);
            entity = response.getEntity();
            String responseString = EntityUtils.toString(entity);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                throw CordraException.fromStatusCode(statusCode, responseString);
            }
            if (responseString.isEmpty()) return null;
            return gson.fromJson(responseString, new TypeToken<List<VersionInfo>>() { }.getType());
        } catch (IOException e) {
            throw new InternalErrorCordraException(e);
        } finally {
            closeQuietly(entity, response);
        }
    }

    protected HttpEntityEnclosingRequestBase buildPublishVersionRequest(String objectId, String versionId, boolean clonePayloads, JsonObject requestContext) {
        HttpEntityEnclosingRequestBase request;
        String uri = baseUri + "versions/";
        uri += "?objectId=" + StringUtils.encodeURLComponent(objectId);
        if (versionId != null) {
            uri += "&versionId=" + StringUtils.encodeURLComponent(versionId);
        }
        uri += "&clonePayloads=" + clonePayloads;
        if (requestContext != null) {
            uri += "&requestContext=" + StringUtils.encodeURLComponent(gson.toJson(requestContext));
        }
        request = new HttpPost(uri);
        addRequestIdHeader(request);
        return request;
    }

    protected HttpUriRequest buildGetVersionsForRequest(String objectId, JsonObject requestContext) {
        HttpUriRequest request;
        String uri = baseUri + "versions/?objectId=" + StringUtils.encodeURLComponent(objectId);
        if (requestContext != null) {
            uri += "&requestContext=" + StringUtils.encodeURLComponent(gson.toJson(requestContext));
        }
        request = new HttpGet(uri);
        addRequestIdHeader(request);
        return request;
    }

    protected HttpUriRequest buildListMethodsRequest(String objectId, String type, boolean isStatic) {
        String uri = baseUri + "listMethods";
        if (type != null) {
            uri += "?type=" + StringUtils.encodeURLComponent(type);
            if (isStatic) uri += "&static";
        } else {
            uri += "?objectId=" + StringUtils.encodeURLComponent(objectId);
        }
        HttpUriRequest request = new HttpGet(uri);
        addRequestIdHeader(request);
        return request;
    }

    protected HttpEntityEnclosingRequestBase buildCallRequest(String objectId, String type, String methodName, JsonElement params) {
        HttpEntityEnclosingRequestBase request;
        String uri = baseUri + "call";
        if (type != null) {
            uri += "?type=" + StringUtils.encodeURLComponent(type);
        } else {
            uri += "?objectId=" + StringUtils.encodeURLComponent(objectId);
        }
        uri += "&method=" + StringUtils.encodeURLComponent(methodName);
        request = new HttpPost(uri);
        if (params != null) {
            String paramsJson = params.toString();
            StringEntity body = new StringEntity(paramsJson, StandardCharsets.UTF_8);
            body.setContentType("application/json");
            request.setEntity(body);
        }
        addRequestIdHeader(request);
        return request;
    }

    @SuppressWarnings("resource")
    private CordraObject createOrUpdate(CordraObject d, boolean isCreate, Options options) throws CordraException {
        CloseableHttpResponse response = null;
        HttpEntity entity = null;
        try {
            response = sendHttpRequestWithCredentials(createOrUpdateRequestSupplier(d, options.isDryRun, isCreate, options.requestContext), options);
            entity = response.getEntity();
            String responseString = EntityUtils.toString(entity);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                throw CordraException.fromStatusCode(statusCode, responseString);
            }
            return gson.fromJson(responseString, CordraObject.class);
        } catch (IOException e) {
            throw new InternalErrorCordraException(e);
        } finally {
            if (d.payloads != null) {
                for (Payload payload : d.payloads) {
                    try {
                        InputStream in = payload.getInputStream();
                        if (in != null) {
                            in.close();
                        }
                    } catch (IOException e) { }
                }
            }
            closeQuietly(entity, response);
        }
    }

    // Can only make request once if it includes payloads
    private Supplier<HttpUriRequest> createOrUpdateRequestSupplier(CordraObject d, boolean isDryRun, boolean isCreate, JsonObject requestContext) {
        return new Supplier<HttpUriRequest>() {
            int count = 0;
            @Override
            public HttpUriRequest get() {
                if (d.payloads == null || d.payloads.isEmpty()) return buildCreateOrUpdateRequest(d, isDryRun, isCreate, requestContext);
                count++;
                if (count == 1) return buildCreateOrUpdateRequest(d, isDryRun, isCreate, requestContext);
                return null;
            }
        };
    }

    private HttpEntityEnclosingRequestBase buildCreateOrUpdateRequest(CordraObject d, boolean isDryRun, boolean isCreate, JsonObject requestContext) {
        String id  = d.id;
        String type = d.type;
        HttpEntityEnclosingRequestBase request;
        if (isCreate) {
            request = buildCreateRequest(id, type, isDryRun, requestContext);
        } else {
            request = buildUpdateRequest(id, type, isDryRun, requestContext);
        }
        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.addTextBody("json", gson.toJson(d.content), ContentType.APPLICATION_JSON);
        if (d.acl != null) {
            builder.addTextBody("acl", gson.toJson(d.acl), ContentType.APPLICATION_JSON);
        }
        if (d.userMetadata != null) {
            builder.addTextBody("userMetadata", gson.toJson(d.userMetadata), ContentType.APPLICATION_JSON);
        }
        List<String> payloadsToDelete = d.getPayloadsToDelete();
        for (String payloadToDelete : payloadsToDelete) {
            builder.addTextBody("payloadToDelete", payloadToDelete);
        }
        if (d.payloads != null) {
            for (Payload payload : d.payloads) {
                @SuppressWarnings("resource")
                InputStream in = payload.getInputStream();
                if (in != null) {
                    String mediaType = payload.mediaType;
                    if (mediaType == null) mediaType = "application/octet-stream";
                    String filename = payload.filename;
                    if (filename == null) filename = "";
                    builder.addBinaryBody(payload.name, in, ContentType.parse(mediaType), filename);
                }
            }
        }
        request.setEntity(builder.build());
        return request;
    }

    protected HttpEntityEnclosingRequestBase buildCreateRequest(String id, String type, boolean isDryRun, JsonObject requestContext) {
        HttpEntityEnclosingRequestBase request;
        String uri = baseUri + "objects/";
        uri += "?full&type=" + StringUtils.encodeURLComponent(type);
        if (id != null) uri += "&handle=" + StringUtils.encodeURLComponent(id);
        if (isDryRun) uri += "&dryRun";
        if (requestContext != null) {
            uri += "&requestContext=" + StringUtils.encodeURLComponent(gson.toJson(requestContext));
        }
        request = new HttpPost(uri);
        addRequestIdHeader(request);
        return request;
    }

    protected HttpEntityEnclosingRequestBase buildUpdateRequest(String id, String type, boolean isDryRun, JsonObject requestContext) {
        HttpEntityEnclosingRequestBase request;
        String uri = baseUri + "objects/";
        uri += StringUtils.encodeURLPath(id);
        uri += "?full";
        if (type != null) uri += "&type=" + StringUtils.encodeURLComponent(type);
        if (isDryRun) uri += "&dryRun";
        if (requestContext != null) {
            uri += "&requestContext=" + StringUtils.encodeURLComponent(gson.toJson(requestContext));
        }
        request = new HttpPut(uri);
        addRequestIdHeader(request);
        return request;
    }

    @Override
    @SuppressWarnings("resource")
    public void delete(String id, Options options) throws CordraException {
        CloseableHttpResponse response = null;
        HttpEntity entity = null;
        try {
            response = sendHttpRequestWithCredentials(() -> buildDeleteRequest(id, options.requestContext), options);
            int statusCode = response.getStatusLine().getStatusCode();
            entity = response.getEntity();
            String responseString = EntityUtils.toString(entity);
            if (statusCode != 200 && statusCode != 201) {
                throw CordraException.fromStatusCode(statusCode, responseString);
            }
        } catch (IOException e) {
            throw new InternalErrorCordraException(e);
        } finally {
            closeQuietly(entity, response);
        }
    }

    protected HttpUriRequest buildDeleteRequest(String id, JsonObject requestContext) {
        String uri = baseUri + "objects/" + StringUtils.encodeURLPath(id);
        if (requestContext != null) {
            uri += "?requestContext=" + StringUtils.encodeURLComponent(gson.toJson(requestContext));
        }
        HttpUriRequest request = new HttpDelete(uri);
        addRequestIdHeader(request);
        return request;
    }

    @Override
    @SuppressWarnings("resource")
    public SearchResults<CordraObject> search(String query, QueryParams paramsParam, Options options) throws CordraException {
        QueryParams params;
        if (paramsParam == null) params = QueryParams.DEFAULT;
        else params = paramsParam;
        CloseableHttpResponse response = null;
        HttpEntity entity = null;
        try {
            response = sendHttpRequestWithCredentials(() -> buildSearchRequest(query, params, false, options.requestContext), options);
            int statusCode = response.getStatusLine().getStatusCode();
            entity = response.getEntity();
            if (statusCode != 200) {
                String responseString = EntityUtils.toString(entity);
                throw CordraException.fromStatusCode(statusCode, responseString);
            }
            return new HttpCordraObjectSearchResults(response, entity);
        } catch (CordraException e) {
            closeQuietly(entity, response);
            throw e;
        } catch (RuntimeException | Error | IOException e) {
            closeQuietly(entity, response);
            throw new InternalErrorCordraException(e);
        }
    }

    @Override
    @SuppressWarnings("resource")
    public SearchResults<String> searchHandles(String query, QueryParams paramsParam, Options options) throws CordraException {
        QueryParams params;
        if (paramsParam == null) params = QueryParams.DEFAULT;
        else params = paramsParam;
        CloseableHttpResponse response = null;
        HttpEntity entity = null;
        try {
            response = sendHttpRequestWithCredentials(() -> buildSearchRequest(query, params, true, options.requestContext), options);
            int statusCode = response.getStatusLine().getStatusCode();
            entity = response.getEntity();
            if (statusCode != 200) {
                String responseString = EntityUtils.toString(entity);
                throw CordraException.fromStatusCode(statusCode, responseString);
            }
            return new HttpHandlesSearchResults(response, entity);
        } catch (CordraException e) {
            closeQuietly(entity, response);
            throw e;
        } catch (RuntimeException | Error | IOException e) {
            closeQuietly(entity, response);
            throw new InternalErrorCordraException(e);
        }
    }

    protected HttpUriRequest buildSearchRequest(String query, QueryParams params, boolean handles, JsonObject requestContext) {
        HttpPost request = new HttpPost(baseUri + "objects/");
        request.setHeader("Content-Type", "application/x-www-form-urlencoded");
        String uriQueryParams = "query=" + StringUtils.encodeURLComponent(query) + "&" + encodeParams(params);
        if (handles) {
            uriQueryParams += "&ids";
        }
        if (requestContext != null) {
            uriQueryParams += "&requestContext=" + StringUtils.encodeURLComponent(gson.toJson(requestContext));
        }
        StringEntity body = new StringEntity(uriQueryParams, StandardCharsets.UTF_8);
        request.setEntity(body);
        addRequestIdHeader(request);
        return request;
    }

    static String encodeParams(QueryParams params) {
        String result = "pageNum=" + params.getPageNumber() + "&pageSize=" + params.getPageSize();
        List<SortField> sortFields = params.getSortFields();
        if (sortFields != null && !sortFields.isEmpty()) {
            List<String> sortFieldsForTransport = new ArrayList<>(sortFields.size());
            for(SortField sortField : sortFields) {
                if(sortField.isReverse()) sortFieldsForTransport.add(sortField.getName() + " DESC");
                else sortFieldsForTransport.add(sortField.getName());
            }
            if (!sortFieldsForTransport.isEmpty()) {
                result += "&sortFields=";
                result += StringUtils.encodeURLComponent(listOfStringsToString(sortFieldsForTransport, ","));
            }
        }
        return result;
    }

    private static String listOfStringsToString(List<String> strings, String delim) {
        Iterator<String> iterator = strings.iterator();
        if (iterator == null) {
            return null;
        }
        if (!iterator.hasNext()) {
            return "";
        }
        String first = iterator.next();
        if (!iterator.hasNext()) {
            return first;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(first);
        while (iterator.hasNext()) {
            sb.append(delim);
            String next = iterator.next();
            sb.append(next);
        }
        return sb.toString();
    }

    @SuppressWarnings("resource")
    @Override
    public void changePassword(String usernameParam, String passwordParam, String newPassword) throws CordraException {
        CloseableHttpResponse response = null;
        HttpEntity entity = null;
        try {
            response = sendHttpRequestWithAuthHeader(() -> buildChangePasswordRequest(newPassword), new Options().setUsername(usernameParam).setPassword(passwordParam));
            int statusCode = response.getStatusLine().getStatusCode();
            entity = response.getEntity();
            String responseString = EntityUtils.toString(entity);
            if (statusCode != 200 && statusCode != 201) {
                throw CordraException.fromStatusCode(statusCode, responseString);
            }
        } catch (IOException e) {
            throw new InternalErrorCordraException(e);
        } finally {
            closeQuietly(entity, response);
        }
    }

    @Override
    @SuppressWarnings("resource")
    public void reindexBatch(List<String> batchIds, Options options) throws CordraException {
        CloseableHttpResponse response = null;
        HttpEntity entity = null;
        try {
            response = sendHttpRequestWithCredentials(() -> buildReindexBatchRequest(batchIds, options.reindexBatchLockObjects), options);
            entity = response.getEntity();
            String responseString = EntityUtils.toString(entity);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                throw CordraException.fromStatusCode(statusCode, responseString);
            }
            if (responseString.isEmpty()) return;
        } catch (IOException e) {
            throw new InternalErrorCordraException(e);
        } finally {
            closeQuietly(entity, response);
        }
    }

    protected HttpEntityEnclosingRequestBase buildReindexBatchRequest(List<String> batchIds, boolean reindexBatchLockObjects) {
        HttpEntityEnclosingRequestBase request;
        String uri = baseUri + "reindexBatch/?lockObjects=" + reindexBatchLockObjects;
        request = new HttpPost(uri);
        String batchJson = gson.toJson(batchIds);
        StringEntity body = new StringEntity(batchJson, StandardCharsets.UTF_8);
        body.setContentType("application/json");
        request.setEntity(body);
        addRequestIdHeader(request);
        return request;
    }

    protected HttpUriRequest buildChangePasswordRequest(String newPassword) {
        HttpEntityEnclosingRequestBase request = new HttpPut(baseUri + "users/this/password");
        request.setEntity(new StringEntity(newPassword, StandardCharsets.UTF_8));
        addRequestIdHeader(request);
        return request;
    }

    @SuppressWarnings("resource")
    static CloseableHttpClient getNewHttpClient() throws CordraException {
        try {
            TrustStrategy trustStrategy = new TrustStrategy() {
                @Override
                public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    return true;
                }
            };
            SSLContext sslContext = SSLContexts.custom().loadTrustMaterial(null, trustStrategy).build();
            SSLConnectionSocketFactory factory = new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);

            Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                    .register("http", PlainConnectionSocketFactory.INSTANCE)
                    .register("https", factory)
                    .build();
            ConnectionConfig connectionConfig = ConnectionConfig.custom()
                    .setCharset(Consts.UTF_8)
                    .build();
//            SocketConfig socketConfig = SocketConfig.custom()
//                    .setSoTimeout(90000)
//                    .build();
            PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
            connManager.setDefaultMaxPerRoute(20);
            connManager.setMaxTotal(20);
            connManager.setDefaultConnectionConfig(connectionConfig);
//            connManager.setDefaultSocketConfig(socketConfig);

//            RequestConfig requestConfig = RequestConfig.custom()
////                    .setConnectTimeout(30000)
////                    .setSocketTimeout(90000)
//                    .setCookieSpec(CookieSpecs.IGNORE_COOKIES)
//                    .build();

            return HttpClients.custom()
                    .setConnectionManager(connManager)
//                    .setDefaultRequestConfig(requestConfig)
                    .build();
        } catch (Exception e) {
            throw new InternalErrorCordraException(e);
        }
    }

    @Override
    public void changePassword(String newPassword) throws CordraException {
        changePassword(username, password, newPassword);
    }

//    @Override
//    public SearchResults<CordraObject> get(Collection<String> ids) throws CordraException {
//        throw new UnsupportedOperationException();
//    }
}
