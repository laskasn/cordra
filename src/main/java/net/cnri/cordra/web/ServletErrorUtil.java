package net.cnri.cordra.web;

import com.google.gson.Gson;
import net.cnri.cordra.GsonUtility;
import net.cnri.cordra.model.CordraErrorResponse;
import net.cnri.cordra.model.PasswordChangeErrorResponse;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class ServletErrorUtil {
    private static Gson gson = GsonUtility.getGson();

    public static void internalServerError(HttpServletResponse resp) throws IOException {
        resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        CordraErrorResponse errorResponse = new CordraErrorResponse("Something went wrong. Contact your sysadmin.");
        gson.toJson(errorResponse, resp.getWriter());
    }

    public static void theSystemIsDown(HttpServletResponse resp) throws IOException {
        resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        CordraErrorResponse errorResponse = new CordraErrorResponse("The system is down.");
        gson.toJson(errorResponse, resp.getWriter());
    }

    public static void badRequest(HttpServletResponse resp, String message) throws IOException {
        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        CordraErrorResponse errorResponse = new CordraErrorResponse(message);
        gson.toJson(errorResponse, resp.getWriter());
    }

    public static void unauthorized(HttpServletResponse resp, String message) throws IOException {
        resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        CordraErrorResponse errorResponse = new CordraErrorResponse(message);
        gson.toJson(errorResponse, resp.getWriter());
    }

    public static void forbidden(HttpServletResponse resp, String message) throws IOException {
        resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
        CordraErrorResponse errorResponse = new CordraErrorResponse(message);
        gson.toJson(errorResponse, resp.getWriter());
    }

    public static void conflict(HttpServletResponse resp, String message) throws IOException {
        resp.setStatus(HttpServletResponse.SC_CONFLICT);
        CordraErrorResponse errorResponse = new CordraErrorResponse(message);
        gson.toJson(errorResponse, resp.getWriter());
    }
    
    public static void notFound(HttpServletResponse resp, String message) throws IOException {
        resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
        CordraErrorResponse errorResponse = new CordraErrorResponse(message);
        gson.toJson(errorResponse, resp.getWriter());
    }

    public static void passwordChangeRequired(HttpServletResponse resp) throws IOException {
        resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        String message = "Password change required.";
        CordraErrorResponse errorResponse = new PasswordChangeErrorResponse(message);
        gson.toJson(errorResponse, resp.getWriter());
    }
}
