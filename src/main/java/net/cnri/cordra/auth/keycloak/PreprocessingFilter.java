package net.cnri.cordra.auth.keycloak;

import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

import org.keycloak.adapters.servlet.KeycloakOIDCFilter;


public class PreprocessingFilter extends KeycloakOIDCFilter {

	public static String IS_KEYCLOAK = "IS_KEYCLOAK";
	
	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		
		HttpServletRequest req = (HttpServletRequest) request;
		req.removeAttribute(IS_KEYCLOAK); //to prevent any malicious client-side use
		
		String authHeader = req.getHeader("Authorization");
		if(authHeader == null || !isBearer(authHeader) || !isBearerWithJWT(authHeader)) {
			chain.doFilter(request, response); //go for the normal authentication
			return;
		}
		else {
			req.setAttribute(IS_KEYCLOAK, "anythingwilldo");
			super.doFilter(req, response, chain);
			return;
		}
	}
	
	private static boolean isBearer(String authHeader) {
		return authHeader.substring(0, 10).trim().toLowerCase().startsWith("bearer");
	}
	
	private static boolean isBearerWithJWT(String authHeader) {
		return authHeader.substring(6).trim().split("\\.").length == 3;
	}

}
