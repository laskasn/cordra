package net.cnri.cordra.web;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.cnri.cordra.CordraService;
import net.cnri.cordra.CordraServiceFactory;
import net.cnri.cordra.InvalidException;
import net.cnri.cordra.api.BadRequestCordraException;
import net.cnri.cordra.api.NotFoundCordraException;
import net.cnri.util.StreamUtil;

@WebServlet({"/call/*"})
public class CallServlet extends HttpServlet {
    private static Logger logger = LoggerFactory.getLogger(CallServlet.class);

    private static CordraService cordra;

    @Override
    public void init() throws ServletException {
        super.init();
        try {
            cordra = CordraServiceFactory.getCordraService();
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            String objectId = req.getParameter("objectId");
            String type = req.getParameter("type");
            String method = req.getParameter("method");
            String userId = (String) req.getAttribute("userId");
            boolean hasUserObject = ServletUtil.getBooleanAttribute(req, "hasUserObject");
            String json =  StreamUtil.readFully(req.getReader());
            if ("".equals(json)) {
                json = null;
            }
            String result = cordra.call(objectId, type, userId, hasUserObject, method, json);
            if (result != null) {
                // don't set content-type for empty response
                resp.setContentType("application/json");
                resp.setCharacterEncoding("UTF-8");
                resp.getWriter().write(result);
            }
        } catch (NotFoundCordraException e) {
            ServletErrorUtil.notFound(resp, e.getMessage());
        } catch (InvalidException e) {
            ServletErrorUtil.badRequest(resp, e.getMessage());
        } catch (BadRequestCordraException e) {
            ServletErrorUtil.badRequest(resp, e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error calling method", e);
            ServletErrorUtil.internalServerError(resp);
        }
    }
}
