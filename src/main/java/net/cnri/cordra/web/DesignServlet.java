package net.cnri.cordra.web;

import net.cnri.cordra.CordraService;
import net.cnri.cordra.CordraServiceFactory;
import net.cnri.cordra.DesignPlusSchemas;
import net.cnri.cordra.GsonUtility;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet({"/design/*"})
public class DesignServlet extends HttpServlet {

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
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        DesignPlusSchemas design = cordra.getDesign();
        GsonUtility.getPrettyGson().toJson(design, resp.getWriter());
    }
}
