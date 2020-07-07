package net.cnri.cordra.web;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.io.IOUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.cnri.cordra.CordraService;
import net.cnri.cordra.CordraServiceFactory;
import net.cnri.cordra.DesignPlusSchemas;
import net.cnri.cordra.GsonUtility;
import net.cnri.cordra.api.CordraException;

@WebServlet({"/initData/*"})
public class InitDataServlet extends HttpServlet {
    private static CordraService cordra;
    private static Logger logger = LoggerFactory.getLogger(InitDataServlet.class);

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
        try {
            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");
            HttpSession session = req.getSession(false);
            boolean hasUserObject = false;
            InitDataResponse initDataResponse = new InitDataResponse();
            if (session != null) {
                initDataResponse.isActiveSession = true;
                String username = (String) session.getAttribute("username");
                initDataResponse.username = username;
                initDataResponse.userId = (String) session.getAttribute("userId");
                hasUserObject = ServletUtil.getBooleanAttribute(session, "hasUserObject");
            }
            InputStream is = getServletContext().getResourceAsStream("/WEB-INF/keycloak-js-client.json");
            if(is != null) {
            	try {
					initDataResponse.externalLoginConfig = (JSONObject)new JSONParser().parse(new InputStreamReader(is, Charset.forName("UTF-8")));
				} catch (ParseException e) {
					logger.error("The file keycloak-js-client.json at WEB-INF is not a valid JSON file. External login is disabled on UI");
				}
            }
            List<String> typesPermittedToCreate = cordra.getTypesPermittedToCreate(initDataResponse.userId, hasUserObject);
            initDataResponse.typesPermittedToCreate = typesPermittedToCreate;
            DesignPlusSchemas design = cordra.getDesign();
            initDataResponse.design = design;
            GsonUtility.getPrettyGson().toJson(initDataResponse, resp.getWriter());
        } catch (CordraException e) {
            logger.error("Exception in GET /initData", e);
            ServletErrorUtil.internalServerError(resp);
            return;
        }
    }

    public static class InitDataResponse {
        public DesignPlusSchemas design;
        public boolean isActiveSession = false;
        public String username;
        public String userId;
        public List<String> typesPermittedToCreate;
        public JSONObject externalLoginConfig;
    }

}
