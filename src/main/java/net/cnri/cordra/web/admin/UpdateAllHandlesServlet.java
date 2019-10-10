package net.cnri.cordra.web.admin;

import com.google.gson.Gson;
import net.cnri.cordra.*;
import net.cnri.cordra.web.ServletErrorUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Writer;

@WebServlet({"/updateHandles/*"})
public class UpdateAllHandlesServlet extends HttpServlet {
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
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        try {
            cordra.updateAllHandleRecords();
            Writer w = response.getWriter();
            w.write("{}");
            w.flush();
            w.close();
        } catch (ReadOnlyCordraException e) {
            ServletErrorUtil.badRequest(response, "Cordra is read-only");
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        AllHandlesUpdater.UpdateStatus status = cordra.getHandleUpdateStatus();
        String json = gson.toJson(status);
        Writer w = response.getWriter();
        w.write(json);
        w.flush();
        w.close();
    }

}
