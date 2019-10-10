package net.cnri.cordra.api;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.util.EntityUtils;

@Deprecated
public class SessionUsingHttpCordraClient extends HttpCordraClient {

    private static final long TIMEOUT_MILLIS = 600_000;
    private final ConcurrentMap<String, UserInfo> userInfoMap = new ConcurrentHashMap<>();
    private final boolean disableRetry;

    public SessionUsingHttpCordraClient(String baseUri, String username, String password) throws CordraException {
        super(baseUri, username, password);
        this.disableRetry = false;
    }

    public SessionUsingHttpCordraClient(String baseUri, String username, String password, boolean disableRetry) throws CordraException {
        super(baseUri, username, password);
        this.disableRetry = disableRetry;
    }

    @Override
    @SuppressWarnings("resource")
    protected CloseableHttpResponse sendHttpRequestWithCredentials(Supplier<HttpUriRequest> requestSupplier, Options options) throws IOException, ClientProtocolException, CordraException {
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
        if (userKey == null) {
            CookieStore cookieStore = new BasicCookieStore();
            HttpClientContext context = HttpClientContext.create();
            context.setCookieStore(cookieStore);
            HttpUriRequest request = requestSupplier.get();
            addCredentials(request, options);
            return httpClient.execute(request, context);
        }
        long now = System.currentTimeMillis();
        UserInfo userInfo = userInfoMap.get(userKey);
        String hashedPasswordParam = null;
        boolean isNew;
        if (userInfo == null) {
            isNew = true;
        } else {
            hashedPasswordParam = passwordForUser == null ? null : DigestUtils.sha256Hex(passwordForUser);
            if (!Objects.equals(userInfo.hashedPassword, hashedPasswordParam)) {
                isNew = true;
            } else if (now - userInfo.lastUsed.get() > TIMEOUT_MILLIS) {
                isNew = true;
            } else {
                isNew = false;
            }
        }
        CookieStore cookieStore;
        if (isNew || userInfo == null) {
            cookieStore = new BasicCookieStore();
        } else {
            cookieStore = userInfo.cookieStore;
        }
        HttpClientContext context = HttpClientContext.create();
        context.setCookieStore(cookieStore);
        HttpUriRequest request = requestSupplier.get();
        if (isNew) {
            addCredentials(request, options);
        } else {
            String csrfToken = getCsrfToken(cookieStore);
            if (csrfToken == null) {
                if (disableRetry) {
                    throw new AssertionError("CSRF should not be null.");
                } else {
                    addCredentials(request, options);
                }
            } else {
                request.addHeader("X-Csrf-Token", csrfToken);
            }
        }
        if (options.asUserId != null) addAsUserHeader(request, options.asUserId);
        CloseableHttpResponse resp = httpClient.execute(request, context);
        if (!disableRetry && !isNew && resp.getStatusLine().getStatusCode() == 401) {
            userInfoMap.remove(userKey);
            // re-try if can obtain new request
            request = requestSupplier.get();
            if (request != null) {
                EntityUtils.consumeQuietly(resp.getEntity());
                resp.close();
                isNew = true;
                cookieStore = new BasicCookieStore();
                context = HttpClientContext.create();
                context.setCookieStore(cookieStore);
                addCredentials(request, options);
                if (options.asUserId != null) addAsUserHeader(request, options.asUserId);
                resp = httpClient.execute(request, context);
            }
        }
        if (resp.getStatusLine().getStatusCode() != 401) {
            if (isNew || userInfo == null) {
                if (hashedPasswordParam == null) {
                    hashedPasswordParam = passwordForUser == null ? null : DigestUtils.sha256Hex(passwordForUser);
                }
                UserInfo newUserInfo = new UserInfo();
                newUserInfo.hashedPassword = hashedPasswordParam;
                newUserInfo.cookieStore = cookieStore;
                newUserInfo.lastUsed.set(now);
                userInfoMap.put(userKey, newUserInfo);
            } else {
                userInfo.lastUsed.set(now);
            }
        }
        return resp;
    }

    private String getCsrfToken(CookieStore cookieStore) {
        for (Cookie cookie : cookieStore.getCookies()) {
            if (cookie.getName().equals("Csrf-token")) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static class UserInfo {
        String hashedPassword;
        CookieStore cookieStore;
        AtomicLong lastUsed = new AtomicLong();
    }

}
