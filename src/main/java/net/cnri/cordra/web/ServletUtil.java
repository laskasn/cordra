package net.cnri.cordra.web;

import net.cnri.util.StringUtils;
import net.handle.hdllib.Util;

import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

public class ServletUtil {
    static void setNoCaching(ServletResponse servletResponse) {
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate"); // HTTP 1.1.
        response.setHeader("Pragma", "no-cache"); // HTTP 1.0.
        response.setDateHeader("Expires", 0);
    }

    public static String contentDispositionHeaderFor(String disposition, String filename) {
        if (filename == null) return disposition;
        String latin1Version;
        try {
            latin1Version = new String(filename.getBytes("ISO-8859-1"), "ISO-8859-1");
        } catch(UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
        String latin1VersionEscaped = latin1Version.replace("\n","?").replace("\\","\\\\").replace("\"","\\\"");
        if (filename.equals(latin1Version)) {
            return disposition + ";filename=\"" + latin1VersionEscaped + "\"";
        } else {
            return disposition + ";filename=\"" + latin1VersionEscaped + "\";filename*=UTF-8''" + StringUtils.encodeURLComponent(filename);
        }
    }

    public static boolean getBooleanParameter(HttpServletRequest req, String param) {
        String value = req.getParameter(param);
        if (value == null) return false;
        if (value.isEmpty()) return true;
        return Boolean.parseBoolean(value);
    }
    
    public static boolean getBooleanParameter(HttpServletRequest req, String param, boolean defaultValue) {
        String value = req.getParameter(param);
        if (value == null) return defaultValue;
        if (value.isEmpty()) return true;
        return Boolean.parseBoolean(value);
    }
    
    public static String getPath(HttpServletRequest servletReq) {
        String pathInfo = net.cnri.util.ServletUtil.pathExcluding(servletReq.getRequestURI(), servletReq.getContextPath() + servletReq.getServletPath());
        pathInfo = StringUtils.decodeURLIgnorePlus(pathInfo);
        return pathInfo;
    }
    
    public static boolean getBooleanAttribute(HttpSession session, String att) {
        Boolean attValue = (Boolean) session.getAttribute(att);
        if (attValue == null) return false;
        return attValue.booleanValue();
    }
    
    public static boolean getBooleanAttribute(HttpServletRequest req, String att) {
        Boolean attValue = (Boolean) req.getAttribute(att);
        if (attValue == null) return false;
        return attValue.booleanValue();
    }

    public static String streamToString(InputStream input, String encoding) throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        byte buf[] = new byte[4096];
        int r;
        while ((r = input.read(buf)) >= 0) {
            bout.write(buf, 0, r);
        }
        if (encoding == null) {
            return Util.decodeString(bout.toByteArray());
        }
        else {
            return new String(bout.toByteArray(), encoding);
        }
    }
}
