package net.cnri.cordra.web.admin;

import net.cnri.cordra.CordraStartupStatus;
import net.cnri.cordra.GsonUtility;
import net.cnri.cordra.web.ServletErrorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet({"/startupStatus/*"})
public class StartupStatusServlet extends HttpServlet {

    private static Logger logger = LoggerFactory.getLogger(StartupStatusServlet.class);

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        CordraStartupStatus status = CordraStartupStatus.getInstance();
        try {
            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");
            GsonUtility.getPrettyGson().toJson(status, resp.getWriter());
        } catch (Exception e) {
            logger.error("Exception in GET /startupStatus", e);
            ServletErrorUtil.internalServerError(resp);
            return;
        }
    }
}
