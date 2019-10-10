package net.cnri.cordra.web;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.cnri.cordra.CordraService;
import net.cnri.cordra.CordraServiceFactory;
import net.cnri.cordra.GsonUtility;
import net.cnri.cordra.ReadOnlyCordraException;
import net.cnri.cordra.VersionException;
import net.cnri.cordra.api.VersionInfo;
import net.cnri.cordra.api.BadRequestCordraException;
import net.cnri.cordra.api.ConflictCordraException;
import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.api.CordraObject;

import com.google.gson.Gson;

@WebServlet({"/versions/*"})
public class VersionsServlet extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(VersionsServlet.class);

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

    /**
     * Lists all versions of the specified object
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        String objectId = req.getParameter("objectId");
        String userId = (String) req.getAttribute("userId");
        boolean hasUserObject = ServletUtil.getBooleanAttribute(req, "hasUserObject");

        try {
            List<CordraObject> versions = cordra.getVersionsFor(objectId, userId, hasUserObject);
            List<VersionInfo> versionInfos = getVersionInfoListFor(versions);
            String json = gson.toJson(versionInfos);
            PrintWriter w = resp.getWriter();
            w.write(json);
            w.close();
        } catch (CordraException e) {
            logger.error("Error in VersionsServlet", e);
            ServletErrorUtil.internalServerError(resp);
        }
    }

    private List<VersionInfo> getVersionInfoListFor(List<CordraObject> versions) {
        List<VersionInfo> result = new ArrayList<>();
        for (CordraObject version : versions) {
            VersionInfo versionInfo = getVersionInfoFor(version);
            result.add(versionInfo);
        }
        return result;
    }

    private VersionInfo getVersionInfoFor(CordraObject co) {
        VersionInfo versionInfo = new VersionInfo();
        versionInfo.id = co.id;
        versionInfo.versionOf = co.metadata.versionOf;
        versionInfo.type = co.type;
        if (versionInfo.versionOf == null) {
            versionInfo.isTip = true;
            versionInfo.modifiedOn = co.metadata.modifiedOn;
        } else {
            versionInfo.publishedBy = co.metadata.publishedBy;
            versionInfo.publishedOn = co.metadata.publishedOn;
        }
        return versionInfo;
    }

    /**
     * Creates a new locked copy of the specified object and returns the new Id.
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        String objectId = req.getParameter("objectId");
        String versionId = req.getParameter("versionId");
        boolean clonePayloads = ServletUtil.getBooleanParameter(req, "clonePayloads", true);
        String userId = (String) req.getAttribute("userId");

        if (objectId != null) {
            try {
                CordraObject versionObject = cordra.publishVersion(objectId, versionId, clonePayloads, userId);
                VersionInfo versionInfo = getVersionInfoFor(versionObject);
                String json = gson.toJson(versionInfo);
                PrintWriter w = resp.getWriter();
                w.write(json);
                w.close();
            } catch (ReadOnlyCordraException e) {
                ServletErrorUtil.badRequest(resp, "Cordra is read-only");
            } catch (ConflictCordraException e) {
                logger.error("Error in VersionsServlet", e);
                ServletErrorUtil.conflict(resp, e.getMessage());
            } catch (BadRequestCordraException e) {
                ServletErrorUtil.badRequest(resp, e.getMessage());
            } catch (CordraException e) {
                logger.error("Error in VersionsServlet", e);
                ServletErrorUtil.internalServerError(resp);
            } catch (VersionException e) {
                logger.error("Error in VersionsServlet", e);
                ServletErrorUtil.badRequest(resp, e.getMessage());
            }
        }
    }
}
