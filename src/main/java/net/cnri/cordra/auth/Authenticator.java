/*************************************************************************\
    Copyright (c) 2019 Corporation for National Research Initiatives;
                        All rights reserved.
\*************************************************************************/

package net.cnri.cordra.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.cnri.cordra.GsonUtility;
import net.cnri.cordra.*;
import net.cnri.cordra.Design.CookieConfig;
import net.cnri.cordra.api.*;
import net.cnri.cordra.auth.keycloak.PreprocessingFilter;
import net.cnri.cordra.storage.memory.MemoryStorage;
import net.cnri.cordra.sync.KeyPairAuthJtiChecker;
import net.cnri.cordra.sync.local.MemoryKeyPairAuthJtiChecker;
import net.cnri.cordra.web.ChangePasswordServlet;
import net.cnri.cordra.web.ServletUtil;
import net.handle.apps.batch.BatchUtil;
import net.handle.hdllib.*;
import net.handle.hdllib.trust.JsonWebSignature;
import net.handle.hdllib.trust.JsonWebSignatureFactory;

import javax.servlet.ServletContext;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class Authenticator {
    public enum AuthenticationResponse {
        SUCCESS,
        SUCCESS_PASSWORD_CHANGE,
        NO_CSRF,
        FAILED,
        FAILED_LEGACY_SESSIONS,
        FAILED_INVALID_TOKEN,
        ANONYMOUS,
        PASSWORD_CHANGE_REQUIRED,
        ACCOUNT_INACTIVE
    }

    private final AdminPasswordCheckerInterface adminPasswordChecker;
    private final SecureRandom random;
    private final CordraService cordra;
    private final AuthCache authCache;
    private final HandleResolver resolver;
    private final KeyPairAuthJtiChecker keyPairAuthJtiChecker;
    private final AuthenticatorBackOff authenticatorBackOff;

    public Authenticator(AdminPasswordCheckerInterface adminPasswordChecker, CordraService cordra, AuthCache authCache, KeyPairAuthJtiChecker keyPairAuthJtiChecker, Boolean enableAuthenticationBackOff) {
        this.adminPasswordChecker = adminPasswordChecker;
        this.random = new SecureRandom();
        this.resolver = new HandleResolver();
        this.cordra = cordra;
        this.authCache = authCache;
        this.keyPairAuthJtiChecker = keyPairAuthJtiChecker;
        authenticatorBackOff = new AuthenticatorBackOff(enableAuthenticationBackOff);
    }

    // for testing CordraKeyPair (Bearer) authentication
    private Authenticator(byte[] pubKeyBytes) throws Exception {
        this.adminPasswordChecker = null;
        this.random = new SecureRandom();
        this.resolver = new HandleResolver() {
            @Override
            public net.handle.hdllib.AbstractResponse processRequest(net.handle.hdllib.AbstractRequest req) throws HandleException {
                HandleValue[] values = { new HandleValue(300, "HS_PUBKEY", pubKeyBytes) };
                return new ResolutionResponse(req.handle, Encoder.encodeHandleValues(values));
            }
        };
        this.cordra = CordraServiceFactory.getCordraServiceForTesting(new MemoryStorage());
        Design design = new Design();
        design.ids = Arrays.asList("cordra/id");
        this.cordra.updateDesign(design);
        this.authCache = null;
        this.keyPairAuthJtiChecker = new MemoryKeyPairAuthJtiChecker();
        authenticatorBackOff = new AuthenticatorBackOff(false);
    }

    static Authenticator getAuthenticatorForTestingCordraKeyPairAuth(byte[] pubKeyBytes) throws Exception {
        return new Authenticator(pubKeyBytes);
    }

    public void setBackOffEnabled(boolean backOffEnabled) {
        authenticatorBackOff.setEnabled(backOffEnabled);
    }

    public AuthenticationResponse authenticate(HttpServletRequest req, HttpServletResponse resp) throws CordraException, InvalidException {
        boolean wasNoContext = RequestContextHolder.beforeAuthCall();
        try {
            AuthenticationResponse authResponse = doAuthenticate(req, resp);
            if (authResponse == AuthenticationResponse.SUCCESS || authResponse == AuthenticationResponse.NO_CSRF) {
                authResponse = processAsUserHeader(req, authResponse);
            }
            return authResponse;
        } finally {
            RequestContextHolder.afterAuthCall(wasNoContext);
        }
    }

    private AuthenticationResponse processAsUserHeader(HttpServletRequest req, AuthenticationResponse authResponse) throws CordraException {
        String asUser = req.getHeader("As-User");
        if (asUser != null) {
            String userId = getUserId(req);
            if (!"admin".equals(userId)) {
                return AuthenticationResponse.FAILED;
            }
            if (authResponse == AuthenticationResponse.SUCCESS || "GET".equals(req.getMethod()) || "HEAD".equals(req.getMethod())) {
                req.setAttribute("userId", asUser);
                boolean hasUserObject = cordra.doesCordraObjectExist(asUser);
                req.setAttribute("hasUserObject", hasUserObject);
            }
        }
        return authResponse;
    }

    private String getUserId(HttpServletRequest req) {
        String userId = (String) req.getAttribute("userId");
        if (userId != null) return userId;
        HttpSession session = req.getSession(false);
        if (session == null) return null;
        return (String) session.getAttribute("userId");
    }

    private AuthenticationResponse doAuthenticate(HttpServletRequest req, HttpServletResponse resp) throws CordraException, InvalidException {
        String authHeader = req.getHeader("Authorization");
        if (isBearerTokenForSession(authHeader)) {
            HttpSession session = req.getSession(false);
            if (session == null) {
                return AuthenticationResponse.FAILED_INVALID_TOKEN;
            }
            if (session.getAttribute("userId") == null) {
                return AuthenticationResponse.FAILED_INVALID_TOKEN;
            }
            setRequestAttributesFromSession(req);
            return AuthenticationResponse.SUCCESS;
        } else if (authHeader == null) {
            if (req.getRequestedSessionId() == null) {
                return AuthenticationResponse.ANONYMOUS;
            }
            if (cordra.getDesign().useLegacySessionsApi != Boolean.TRUE) {
                // could be FAILED_LEGACY_SESSIONS, but that might cause problems for upgrades and browsers
                return AuthenticationResponse.ANONYMOUS;
            }
            HttpSession session = req.getSession(false);
            if (session == null) {
                return AuthenticationResponse.FAILED_INVALID_TOKEN;
            }
            if (session.getAttribute("userId") == null) {
                return AuthenticationResponse.FAILED_INVALID_TOKEN;
            }
            String csrfTokenFromRequest = getCsrfTokenFromRequest(req);
            if (csrfTokenFromRequest == null) {
                if ("GET".equals(req.getMethod()) || "HEAD".equals(req.getMethod())) {
                    setRequestAttributesFromSession(req);
                }
                return AuthenticationResponse.NO_CSRF;
            }
            String csrfTokenFromSession = (String) session.getAttribute("csrfToken");
            if (!csrfTokenFromRequest.equals(csrfTokenFromSession)) return AuthenticationResponse.FAILED;
            setRequestAttributesFromSession(req);
            return AuthenticationResponse.SUCCESS;
        } else {
            authHeader = authHeader.trim();
            CheckCredentialsResponse credsResponse = null;
            if (isBasicAuth(authHeader)) {
                Credentials c = new Credentials(authHeader);
                String username = c.getUsername();
                String password = c.getPassword();
                credsResponse = checkCredentialsAndReportResult(req, username, password);
            }
            else if (isKeycloakAuth(req)) {
            	//check if the user already has an associated object.
            	credsResponse = initialiseKeycloakUser(req, authHeader);
            	//has already passed the keycloak authentication filter, so whitelist it
            }
            else if (isCordraKeyPairAuth(authHeader)) {
                String jwt = getJwtFromAuthHeader(authHeader);
                credsResponse = checkJwtCredentials(req, jwt);
            }
            if (credsResponse == null) {
                return AuthenticationResponse.FAILED;
            } else {
                if (credsResponse.authResponse == AuthenticationResponse.SUCCESS) {
                    setRequestAttributes(req, credsResponse.username, credsResponse.userId, credsResponse.hasUserObject);
                    if (cordra.getDesign().useLegacySessionsApi == Boolean.TRUE) {
                        setUpSessionForNewAuthentication(req, resp, credsResponse.username, credsResponse.userId, credsResponse.hasUserObject);
                    }
                } else if (credsResponse.authResponse == AuthenticationResponse.SUCCESS_PASSWORD_CHANGE) {
                    // don't set up session... make them log in again
                    setRequestAttributes(req, credsResponse.username, credsResponse.userId, credsResponse.hasUserObject);
                }
                return credsResponse.authResponse;
            }
        }
    }

    private CheckCredentialsResponse initialiseKeycloakUser(HttpServletRequest req, String authHeader) {
    	String jwt = getJwtFromAuthHeader(authHeader);
    	//jwt validity is already checked by Keycloak filter
    	String[] splits = jwt.split("\\.");
    	if(splits.length!=3)
    		return null;
    	String jsonPayloadStr = new String(Base64.getUrlDecoder().decode(splits[1].getBytes()));
    	
    	JSONObject jsonPayload;
    	try {
    		jsonPayload = (JSONObject) new JSONParser().parse(jsonPayloadStr);
		} catch (ParseException e) {
			return null;
		} 
    	
    	String userId = jsonPayload.get("sub").toString();
    	String username = jsonPayload.get("preferred_username").toString();
    	String fullname = jsonPayload.get("name").toString();
    	String name = jsonPayload.get("given_name").toString();
    	String surname = jsonPayload.get("family_name").toString();
    	String email = jsonPayload.get("email").toString();
    
    	String prefixedUserId = cordra.getHandleForSuffix(userId);
    	
    	
    	CordraObject existingUser = null;
    	try {
    		existingUser = cordra.getCordraObjectOrNull(prefixedUserId);
		} catch (CordraException e1) {
			return null;
		}
    	if(existingUser != null) {
    		return new CheckCredentialsResponse(AuthenticationResponse.SUCCESS, username, existingUser.id /*prefixedUserId*/, true);
    	}
    	
    	req.setAttribute("userId", userId);
    	
    	Map<String, Object> userObject = new HashMap<String, Object>();

    	userObject.put("id", prefixedUserId);
    	userObject.put("username", fullname);
    	userObject.put("password", randomPassword(18));
    	
    	String userJson;
		try {
			userJson = new ObjectMapper().writeValueAsString(userObject);
		} catch (JsonProcessingException e) {
			return null;
		}
		
    	CordraObject user = null;
    	try {
			user = cordra.writeJsonAndPayloadsIntoCordraObjectIfValid("User", userJson, null, null, new ArrayList<Payload>(), prefixedUserId, userId, false);
		} catch (CordraException | InvalidException | ReadOnlyCordraException e) {
			return null;
		}
    	
    	if(user == null)
    		return null;
    	
    	
    	return new CheckCredentialsResponse(AuthenticationResponse.SUCCESS, username, user.id /*prefixedUserId*/, true);
    }
    
    private static String randomPassword(int length) {
    	byte [] password = new byte[length];
    	for(int i=0;i<length;i++)
    		password[i] = (byte)Math.floor((double)(Math.random()*93)+33);
    	return new String(password, StandardCharsets.UTF_8);
    }
    
    
    public static boolean isBearerTokenForSession(String authHeader) {
        if (authHeader == null) return false;
        String[] parts = authHeader.trim().split(" +");
        if (parts.length != 2) return false;
        if (!"Bearer".equalsIgnoreCase(parts[0])) return false;
        if (parts[1].contains(".")) return false;
        return true;
    }

    private PublicKey getPublicKeyFromCordraObject(CordraObject user) throws InternalErrorCordraException {
        String type = user.type;
        try {
            JsonNode jsonNode = JsonUtil.gsonToJackson(user.content);
            Map<String, JsonNode> pointerToSchemaMap = cordra.getPointerToSchemaMap(type, jsonNode);
            for (Map.Entry<String, JsonNode> entry : pointerToSchemaMap.entrySet()) {
                String jsonPointer = entry.getKey();
                JsonNode subSchema = entry.getValue();
                JsonNode authNode = SchemaUtil.getDeepCordraSchemaProperty(subSchema, "auth");
                if (authNode != null && authNode.isObject()) {
                    authNode = authNode.get("type");
                }
                if (authNode == null || !"publicKey".equals(authNode.asText())) continue;
                JsonNode publicKeyNode = JsonUtil.getJsonAtPointer(jsonPointer, jsonNode);
                String publicKeyJson = JsonUtil.printJson(publicKeyNode);
                PublicKey publicKey = GsonUtility.getGson().fromJson(publicKeyJson, PublicKey.class);
                return publicKey;
            }
        } catch (InvalidException e) {
            throw new InternalErrorCordraException(e);
        }
        return null;
    }

    private List<PublicKey> getPublicKeysFor(String iss) throws CordraException {
        List<PublicKey> result = new ArrayList<>();
        if ("admin".equals(iss)) {
            PublicKey adminPublickey = cordra.getAdminPublicKey();
            result.add(adminPublickey);
            return result;
        }
        String issAsUsername = iss;
        CordraObject user = getUserObjectByUsername(issAsUsername); // Try with iss as a username
        if (user == null) {
            try {
                user = cordra.getCordraObject(iss); //Try with iss as a cordra object id
            } catch (NotFoundCordraException e) {
                // no such user
            }
        }
        if (user != null) {
            PublicKey keyFromCordraObject = getPublicKeyFromCordraObject(user);
            if (keyFromCordraObject != null) {
                result.add(keyFromCordraObject);
                return result;
            }
        }
        try {
            String handle = iss; //Try with iss as a handle
            HandleValue[] values = BatchUtil.resolveHandle(handle, resolver, null);
            List<HandleValue> pubkeyValues = BatchUtil.getValuesOfType(values, "HS_PUBKEY");
            for (HandleValue value : pubkeyValues) {
                try {
                    PublicKey publicKey = Util.getPublicKeyFromBytes(value.getData(), 0);
                    result.add(publicKey);
                } catch (Exception e) {
                    // unable to parse public key
                }
            }
        } catch (HandleException e) {
            // error resolving handle
        }
        return result;
    }

    public CheckCredentialsResponse checkJwtCredentials(HttpServletRequest req, String jwt) throws CordraException {
        String iss = cordraKeyPairSuccessfulAuthenticationIssuer(jwt);
        if (iss != null) {
            if (!cordra.isUserAccountActive(iss)) {
                return new CheckCredentialsResponse(AuthenticationResponse.ACCOUNT_INACTIVE);
            }
            boolean hasUserObject = cordra.doesCordraObjectExist(iss);
            if (hasUserObject && isPasswordChange(req)) {
                return new CheckCredentialsResponse(AuthenticationResponse.SUCCESS_PASSWORD_CHANGE, null, iss, true);
            } else {
                return new CheckCredentialsResponse(AuthenticationResponse.SUCCESS, null, iss, true);
            }
        } else {
            return new CheckCredentialsResponse(AuthenticationResponse.FAILED);
        }
    }

    String cordraKeyPairSuccessfulAuthenticationIssuer(String jwt) {
        JsonWebSignatureFactory signatureFactory = JsonWebSignatureFactory.getInstance();
        try {
            JsonWebSignature jws = signatureFactory.deserialize(jwt);
            String payloadJson = jws.getPayloadAsString();
            JsonParser parser = new JsonParser();
            JsonObject claims = parser.parse(payloadJson).getAsJsonObject();
            if (!checkClaims(claims)) return null;
            String iss = claims.get("iss").getAsString();
            List<PublicKey> publicKeys = getPublicKeysFor(iss);
            for (PublicKey publicKey : publicKeys) {
                boolean isValid = jws.validates(publicKey);
                if (isValid) {
                    return iss;
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean checkClaims(JsonObject claims) {
        if (!claims.has("iss") || !claims.has("exp")) {
            // need an issuer
            // need an expiration date (prevent footguns)
            return false;
        }
        String iss = claims.get("iss").getAsString();
        if (claims.has("sub")) {
            // if sub is present, must match issuer
            String sub = claims.get("sub").getAsString();
            if (!iss.equals(sub)) {
                return false;
            }
        }
        long exp = claims.get("exp").getAsLong();
        long nowInSeconds = System.currentTimeMillis() / 1000L;
        if (nowInSeconds > exp) {
            return false;
        }
        if (exp > nowInSeconds + 3600) {
            // need an expiration date less than an hour in the future (prevent footguns)
            return false;
        }
        long nbf = 0;
        if (claims.has("nbf")) {
            nbf = claims.get("nbf").getAsLong();
        }
        if (nowInSeconds + 300 < nbf) {
            // 5 minute clock skew for nbf
            return false;
        }
        if (claims.has("aud")) {
            JsonElement audElement = claims.get("aud");
            if (!checkAudience(audElement)) {
                return false;
            }
        }
        if (claims.has("jti")) {
            String jti = claims.get("jti").getAsString();
            if (!keyPairAuthJtiChecker.check(iss, jti, exp, nowInSeconds)) {
                return false;
            }
        }
        return true;
    }

    private boolean checkAudience(JsonElement audElement) {
        List<String> aud;
        if (audElement.isJsonPrimitive()) {
            aud = Collections.singletonList(audElement.getAsString());
        } else if (audElement.isJsonArray()) {
            aud = StreamSupport.stream(audElement.getAsJsonArray().spliterator(), false)
                .map(JsonElement::getAsString)
                .collect(Collectors.toList());
        } else {
            return false;
        }
        List<String> ids = cordra.getIds();
        boolean found = false;
        for (String audId : aud) {
            if (ids.contains(audId)) {
                found = true;
                break;
            }
        }
        return found;
    }

    private String getJwtFromAuthHeader(String authHeader) {
        return authHeader.substring(authHeader.indexOf(" ") + 1);
    }

    public CheckCredentialsResponse checkCredentialsAndReportResult(HttpServletRequest req, String username, String password) throws CordraException, InvalidException {
        CheckCredentialsResponse checkCredentialsResponse = checkCredentials(req, username, password);
        authenticatorBackOff.reportResult(username, checkCredentialsResponse.authResponse);
        return checkCredentialsResponse;
    }

    private CheckCredentialsResponse checkCredentials(HttpServletRequest req, String username, String password) throws CordraException, InvalidException {
        if (isInternalAdminCall(req, username, password)) {
            String userId = "admin";
            username = "admin";
            return new CheckCredentialsResponse(AuthenticationResponse.SUCCESS, username, userId, true);
        } else if ("admin".equalsIgnoreCase(username) && adminPasswordChecker.check(password)) {
            String userId = "admin";
            return new CheckCredentialsResponse(AuthenticationResponse.SUCCESS, username, userId, true);
        } else {
            CordraObject user = getUserObjectByUsername(username);
            if (user == null) {
                // username could be user id
                user = cordra.getCordraObjectOrNull(username);
                username = null;
            }
            if (user == null) {
                return new CheckCredentialsResponse(AuthenticationResponse.FAILED);
            } else if (!cordra.isUserAccountActive(user)) {
                return new CheckCredentialsResponse(AuthenticationResponse.ACCOUNT_INACTIVE);
            } else {
                boolean isPasswordCorrect = cordra.checkUserPassword(user, password);

                if (isPasswordCorrect) {
                    boolean requiresPasswordChange = cordra.isPasswordChangeRequired(user);

                    String userId = user.id;
                    if (isPasswordChange(req)) {
                        return new CheckCredentialsResponse(AuthenticationResponse.SUCCESS_PASSWORD_CHANGE, username, userId, true);
                    } else if (requiresPasswordChange) {
                        return new CheckCredentialsResponse(AuthenticationResponse.PASSWORD_CHANGE_REQUIRED);
                    }
                    return new CheckCredentialsResponse(AuthenticationResponse.SUCCESS, username, userId, true);
                } else {
                    return new CheckCredentialsResponse(AuthenticationResponse.FAILED);
                }
            }
        }
    }

    public static class CheckCredentialsResponse {
        public final AuthenticationResponse authResponse;
        public final String username;
        public final String userId;
        public final boolean hasUserObject;

        public CheckCredentialsResponse(AuthenticationResponse authResponse, String username, String userId, boolean hasUserObject) {
            this.authResponse = authResponse;
            this.userId = userId;
            this.username = username;
            this.hasUserObject = hasUserObject;
        }

        public CheckCredentialsResponse(AuthenticationResponse authResponse) {
            this.authResponse = authResponse;
            this.userId = null;
            this.username = null;
            this.hasUserObject = false;
        }
    }

    public static boolean isInternalCall(HttpServletRequest req) {
        if (!"localhost".equalsIgnoreCase(req.getServerName())) return false;
        ServletContext servletContext = req.getServletContext();
        Integer listenerPort = (Integer) servletContext.getAttribute("net.cnri.cordra.startup.internalListenerPort");
        if (listenerPort == null) return false;
        if (req.getServerPort() != listenerPort.intValue()) return false;
        return true;
    }

    public static boolean isInternalAdminCall(HttpServletRequest req, String username, String password) {
        if (!isInternalCall(req)) return false;
        if (!"admin".equalsIgnoreCase(username)) return false;
        ServletContext servletContext = req.getServletContext();
        String internalPassword = (String) servletContext.getAttribute("net.cnri.cordra.startup.internalPassword");
        if (internalPassword == null) return false;
        if (!internalPassword.equals(password)) return false;
        return true;
    }

    private void setRequestAttributesFromSession(HttpServletRequest req) {
        HttpSession session = req.getSession();
        String username = (String) session.getAttribute("username");
        String userId = (String) session.getAttribute("userId");
        boolean hasUserObject = ServletUtil.getBooleanAttribute(session, "hasUserObject");
        setRequestAttributes(req, username, userId, hasUserObject);
    }

    private void setRequestAttributes(HttpServletRequest req, String username, String userId, boolean hasUserObject) {
        req.setAttribute("userId", userId);
        req.setAttribute("hasUserObject", hasUserObject);
        if (username != null) {
            req.setAttribute("username", username);
        }
    }

    private static boolean isBasicAuth(String authHeader) {
        return authHeader.startsWith("Basic ");
    }

    private static boolean isKeycloakAuth(HttpServletRequest req) {
    	return req.getAttribute(PreprocessingFilter.IS_KEYCLOAK) != null;
    }
    
    private static boolean isCordraKeyPairAuth(String authHeader) {
        // after ruled out access token
        return authHeader.startsWith("Bearer ");
    }

    private boolean isPasswordChange(HttpServletRequest req) {
        return ChangePasswordServlet.SERVLET_PATH.equals(req.getServletPath());
    }

    private String getCsrfTokenFromRequest(HttpServletRequest req) {
        String res = req.getHeader("X-Csrf-Token");
        if (res != null) return res;
        if ("POST".equals(req.getMethod()) && "application/x-www-form-urlencoded".equals(req.getContentType())) {
            return req.getParameter("csrfToken");
        } else {
            return null;
        }
    }

    private void setUpSessionForNewAuthentication(HttpServletRequest req, HttpServletResponse resp, String username, String userId, boolean hasUserObject) {
        HttpSession session = req.getSession(true);
        String csrfToken = (String) session.getAttribute("csrfToken");
        if (csrfToken == null) {
            csrfToken = generateSecureToken();
            session.setAttribute("csrfToken", csrfToken);
            Cookie csrfTokenCookie = new Cookie("Csrf-token", csrfToken);
            if (cordra.getDesign().cookies == null || cordra.getDesign().cookies.csrfToken == null || cordra.getDesign().cookies.csrfToken.path == null) {
                csrfTokenCookie.setPath(getNonEmptyContextPath(req));
            }
            if (cordra.getDesign().cookies != null) {
                setUpCookie(csrfTokenCookie, cordra.getDesign().cookies.csrfToken);
            }
            resp.addCookie(csrfTokenCookie);
        }
        if (req.getServletContext().getEffectiveSessionTrackingModes().isEmpty()) {
            Cookie sessionCookie = new Cookie("JSESSIONID", session.getId());
            if (cordra.getDesign().cookies == null || cordra.getDesign().cookies.jsessionid == null || cordra.getDesign().cookies.jsessionid.path == null) {
                sessionCookie.setPath(getNonEmptyContextPath(req));
            }
            if (cordra.getDesign().cookies == null || cordra.getDesign().cookies.jsessionid == null || cordra.getDesign().cookies.jsessionid.httpOnly == null) {
                sessionCookie.setHttpOnly(true);
            }
            if (cordra.getDesign().cookies == null || cordra.getDesign().cookies.jsessionid == null || cordra.getDesign().cookies.jsessionid.secure == null) {
                if (req.isSecure()) {
                    sessionCookie.setSecure(true);
                }
            }
            if (cordra.getDesign().cookies != null) {
                setUpCookie(sessionCookie, cordra.getDesign().cookies.jsessionid);
            }
            resp.addCookie(sessionCookie);
        }
        if (username != null) session.setAttribute("username", username);
        session.setAttribute("userId", userId);
        session.setAttribute("hasUserObject", hasUserObject);
    }

    private void setUpCookie(Cookie cookie, CookieConfig config) {
        if (config == null) return;
        if (config.path != null) cookie.setPath(config.path);
        if (config.httpOnly != null) cookie.setHttpOnly(config.httpOnly);
        if (config.secure != null) cookie.setSecure(config.secure);
    }

    private String getNonEmptyContextPath(HttpServletRequest req) {
        String contextPath = req.getContextPath();
        if (contextPath.isEmpty()) return "/";
        return contextPath;
    }

    public CordraObject getUserObjectByUsername(String username) throws CordraException {
        if (authCache != null) {
            String cachedUserId = authCache.getUserIdForUsername(username);
            if (cachedUserId != null) {
                return cordra.getCordraObject(cachedUserId);
            }
        }
        CordraObject user = null;
        String q = "username:\"" + username + "\"";
        cordra.ensureIndexUpToDateWhenAuthChange();
        try (SearchResults<CordraObject> results = cordra.searchRepo(q)) {
            for (CordraObject co : results) {
                JsonElement foundUsername = co.metadata.internalMetadata.get("username");
                if (foundUsername != null && username.equalsIgnoreCase(foundUsername.getAsString())) {
                    user = co;
                }
            }
        } catch (UncheckedCordraException e) {
            e.throwCause();
        }
        if (user != null && authCache != null) {
            authCache.setUserIdForUsername(username, user.id);
        }
        return user;
    }

    private String generateSecureToken() {
        return new BigInteger(130, random).toString(32);
    }

    public long calculateBackOff(HttpServletRequest req) throws IOException {
        if (AuthorizationFilter.isAuthTokenPost(req)) {
            AuthTokenServlet.TokenRequest tokenRequest = AuthTokenServlet.getTokenRequest(req);
            if (tokenRequest != null && tokenRequest.username != null) {
                long backOff = authenticatorBackOff.calculateBackOffFor(tokenRequest.username);
                return backOff;
            } else {
                return 0;
            }
        }
        String authHeader = req.getHeader("Authorization");
        if (authHeader == null) {
            return 0;
        } else {
            if (isBasicAuth(authHeader)) {
                Credentials c = new Credentials(authHeader);
                String username = c.getUsername();
                long backOff = authenticatorBackOff.calculateBackOffFor(username);
                return backOff;
            } else {
                return 0;
            }
        }
    }
}
