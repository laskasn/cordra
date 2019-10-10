package net.cnri.cordra.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.handle.hdllib.trust.JsonWebSignature;
import org.apache.http.HttpRequest;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class TokenUsingHttpCordraClient extends HttpCordraClient {

    private final ClientAuthCache authCache;

    public TokenUsingHttpCordraClient(String baseUri, String username, String password) throws CordraException {
        super(baseUri, username, password);
        this.authCache = new ClientAuthCache();
    }

    @Override
    @SuppressWarnings("resource")
    protected CloseableHttpResponse sendHttpRequestWithCredentials(Supplier<HttpUriRequest> requestSupplier, Options options) throws IOException, ClientProtocolException, CordraException {
        HttpUriRequest request = requestSupplier.get();
        String userKey = null;
        String passwordForUser = null;
        if (options.useDefaultCredentials) {
            userKey = username;
            passwordForUser = password;
        } else if (options.userId != null) {
            userKey = options.userId;
            if (options.token == null && options.privateKey == null) {
                passwordForUser = options.password;
            }
        } else {
            userKey = options.username;
            if (options.token == null && options.privateKey == null) {
                passwordForUser = options.password;
            }
        }
        if (!options.useDefaultCredentials && options.token != null) {
            addCredentials(request, options);
        } else if (options.privateKey != null) {
            String iss = options.userId;
            if (iss == null) {
                iss = options.username;
            }
            String authHeader = authHeaderFor(iss, options.privateKey);
            request.addHeader("Authorization", authHeader);
        } else {
            addAuthHeaderToRequestIfNeeded(userKey, passwordForUser, request);
        }
        if (options.asUserId != null) addAsUserHeader(request, options.asUserId);
        CloseableHttpResponse resp = httpClient.execute(request);
        return resp;
    }

    private void addAuthHeaderToRequestIfNeeded(String usernameParam, String passwordParam, HttpRequest httpRequest) throws CordraException {
        if (usernameParam != null && passwordParam != null) {
            String authHeader = authHeaderFor(usernameParam, passwordParam);
            httpRequest.addHeader("Authorization", authHeader);
        }
    }

    private String authHeaderFor(String iss, PrivateKey privateKey) throws CordraException {
        String token = authCache.getCachedToken(iss, null);
        if (token == null) {
            token = acquireNewToken(iss, privateKey);
            authCache.storeToken(iss, null, token);
        }
        return "Bearer " + token;
    }

    private String authHeaderFor(String usernameParam, String passwordParam) throws CordraException {
        String token = authCache.getCachedToken(usernameParam, passwordParam);
        if (token == null) {
            token = acquireNewToken(usernameParam, passwordParam);
            authCache.storeToken(usernameParam, passwordParam, token);
        }
        return "Bearer " + token;
    }

    private String acquireNewToken(String iss, PrivateKey privateKey) throws CordraException {
        JsonWebSignature jwt = generateJwt(iss, privateKey);
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer"));
        params.add(new BasicNameValuePair("assertion", jwt.serialize()));
        String token = acquireNewTokenForParams(params);
        return token;
    }

    private String acquireNewToken(String usernameParam, String passwordParam) throws CordraException {
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("grant_type", "password"));
        params.add(new BasicNameValuePair("username", usernameParam));
        params.add(new BasicNameValuePair("password", passwordParam));
        String token = acquireNewTokenForParams(params);
        return token;
    }

    private String acquireNewTokenForParams(List<NameValuePair> params) throws UnauthorizedCordraException, InternalErrorCordraException {
        String baseUri = getBaseUri();
        HttpPost authPost = new HttpPost(baseUri + "auth/token");
        authPost.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
        try (CloseableHttpResponse response = httpClient.execute(authPost)) {
            String respString = EntityUtils.toString(response.getEntity());
            JsonObject resp = new JsonParser().parse(respString).getAsJsonObject();
            if (response.getStatusLine().getStatusCode() == HttpServletResponse.SC_OK) {
                return resp.get("access_token").getAsString();
            } else {
                throw new UnauthorizedCordraException("Authentication failed: " + resp.get("error").getAsString());
            }
        } catch (IOException e) {
            throw new InternalErrorCordraException(e);
        }
    }
}
