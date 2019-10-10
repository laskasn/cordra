package net.cnri.cordra.web;

import java.io.IOException;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

import net.cnri.cordra.CordraService;
import net.cnri.cordra.CordraServiceFactory;
import net.cnri.cordra.GsonUtility;
import net.cnri.cordra.api.NotFoundCordraException;

@WebServlet({"/listMethods/*"})
public class ListMethodsServlet extends HttpServlet { 
     
        private static Logger logger = LoggerFactory.getLogger(DesignServlet.class);

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
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");
            try {
                String objectId = req.getParameter("objectId");
                String type = req.getParameter("type");
                boolean isStatic = false; 
                if (objectId == null) isStatic = ServletUtil.getBooleanParameter(req, "static");
                List<String> result = cordra.listMethods(type, objectId, isStatic);
                Gson gson = GsonUtility.getGson();
                String resultJson = gson.toJson(result);
                resp.getWriter().write(resultJson);
            } catch (NotFoundCordraException e) {
                logger.error("Unexpected error calling method", e);
                ServletErrorUtil.notFound(resp, e.getMessage());    
            } catch (Exception e) {
                logger.error("Unexpected error calling method", e);
                ServletErrorUtil.internalServerError(resp);
            }
        }
    }