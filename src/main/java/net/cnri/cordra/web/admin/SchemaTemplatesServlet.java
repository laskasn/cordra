package net.cnri.cordra.web.admin;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.cnri.cordra.GsonUtility;
import net.cnri.cordra.web.ServletUtil;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@WebServlet({"/schemaTemplates/*"})
public class SchemaTemplatesServlet extends HttpServlet {

    private Gson gson;

    TemplatesWithJavaScript templatesWithJavaScript;

//    Map<String, JsonElement> templates;
//    String exampleJavaScript = null;

    @Override
    public void init() throws ServletException {
        super.init();
        templatesWithJavaScript = new TemplatesWithJavaScript();
        try {
            gson = GsonUtility.getGson();
            JsonParser parser = new JsonParser();
            ServletContext context = getServletContext();
            Set<String> schemaTemplateFiles = context.getResourcePaths("/schema-templates");
            for (String schemaTemplateFile : schemaTemplateFiles) {
                if (schemaTemplateFile.endsWith(".json")) {
                    InputStream is = context.getResourceAsStream(schemaTemplateFile);
                    String schemaTemplate = ServletUtil.streamToString(is, "UTF-8");
                    is.close();
                    JsonElement template = parser.parse(schemaTemplate);
                    String name = nameFromPath(schemaTemplateFile);
                    templatesWithJavaScript.templates.put(name, template);
                } else if (schemaTemplateFile.endsWith(".js")) {
                    try (InputStream is = context.getResourceAsStream(schemaTemplateFile)) {
                        templatesWithJavaScript.exampleJavaScript = ServletUtil.streamToString(is, "UTF-8");
                    }
                }
            }
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    private String nameFromPath(String path) {
        File f = new File(path);
        String name = f.getName();
        int lastDot = name.lastIndexOf(".");
        return name.substring(0, lastDot);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        String json = gson.toJson(templatesWithJavaScript);
        PrintWriter w = resp.getWriter();
        w.write(json);
        w.close();
    }

    public static class TemplatesWithJavaScript {
        public Map<String, JsonElement> templates = new HashMap<>();
        public String exampleJavaScript = null;
    }

}
