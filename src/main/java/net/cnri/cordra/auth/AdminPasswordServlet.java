package net.cnri.cordra.auth;

import com.google.gson.Gson;
import net.cnri.cordra.CordraService;
import net.cnri.cordra.CordraServiceFactory;
import net.cnri.cordra.GsonUtility;
import net.cnri.cordra.ReadOnlyCordraException;
import net.cnri.cordra.api.BadRequestCordraException;
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
import java.io.PrintWriter;

@WebServlet({"/adminPassword/*"})
public class AdminPasswordServlet extends HttpServlet {
    private Logger logger = LoggerFactory.getLogger(new Object() { }.getClass().getEnclosingClass());

    private CordraService cordra;
    private Gson gson;

    @Override
    public void init() throws ServletException {
        super.init();
        try {
            gson = GsonUtility.getGson();
            cordra = CordraServiceFactory.getCordraService();
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        String adminPasswordJson = ServletUtil.streamToString(req.getInputStream(), req.getCharacterEncoding());
        AdminPassword adminPassword = gson.fromJson(adminPasswordJson, AdminPassword.class);
        String password = adminPassword.password;
        if (password == null) {
            ServletErrorUtil.badRequest(resp, "Password missing.");
        } else {
            try {
                cordra.setAdminPassword(password);
                PrintWriter w = resp.getWriter();
                w.write("{\"success\" : true}");
                w.close();
            } catch (BadRequestCordraException e) {
                ServletErrorUtil.badRequest(resp, e.getMessage());
            } catch (ReadOnlyCordraException e) {
                ServletErrorUtil.badRequest(resp, "Cordra is read-only");
            } catch (Exception e) {
                logger.error("Exception in PUT /adminPassword", e);
                ServletErrorUtil.internalServerError(resp);
            }
        }
    }

    public static class AdminPassword {
        public String password;
    }

}
