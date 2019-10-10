package net.cnri.cordra.web.admin;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import net.cnri.cordra.CordraService;
import net.cnri.cordra.CordraServiceFactory;
import net.cnri.cordra.GsonUtility;
import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.web.ServletErrorUtil;
import net.cnri.cordra.web.ServletUtil;
import net.cnri.util.StreamUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

@WebServlet({"/reindexBatch", "/reindexBatch/"})
public class ReindexBatchServlet extends HttpServlet {
    private static Logger logger = LoggerFactory.getLogger(ReindexBatchServlet.class);

    private CordraService cordra;
    private Gson prettyGson;

    @Override
    public void init() throws ServletException {
        super.init();
        try {
            prettyGson = GsonUtility.getPrettyGson();
            cordra = CordraServiceFactory.getCordraService();
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        String json = StreamUtil.readFully(req.getReader());
        List<String> batch = prettyGson.fromJson(json, new TypeToken<List<String>>(){}.getType());
        boolean lockObjects = ServletUtil.getBooleanParameter(req, "lockObjects", true);
        try {
            cordra.reindexBatchIds(batch, lockObjects);
            resp.getWriter().println("{\"msg\": \"success\"}");
        } catch (CordraException e) {
            ServletErrorUtil.internalServerError(resp);
            logger.error("Error reindexing batch", e);
        }
    }
}
