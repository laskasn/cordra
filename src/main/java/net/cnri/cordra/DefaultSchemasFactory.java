package net.cnri.cordra;

import java.io.InputStream;
import java.io.InputStreamReader;

import net.cnri.util.StreamUtil;

public class DefaultSchemasFactory {

    public static String getSchemaSchema() {
        return getResourceAsString("schema.schema.json");
    }
    
    public static String getCordraDesignSchema() {
        InputStream resource = DefaultSchemasFactory.class.getResourceAsStream("schema.cordradesign.json");
        try {
            return StreamUtil.readFully(new InputStreamReader(resource, "UTF-8"));
        } catch (Exception e) {
            throw new AssertionError(e);
        } finally {
            try { resource.close(); } catch (Exception e) { }
        }
    }

    public static String getDefaultUserSchema() {
        return getResourceAsString("schema.user.json");
    }
    
    public static String getDefaultUserJavaScript() {
        return getResourceAsString("schema.user.js");
    }

    public static String getLegacyDefaultUserJavaScript() {
        return getResourceAsString("schema.user.legacy.js");
    }
    
    public static String getLegacyUpdateDefaultUserJavaScript() {
        return getResourceAsString("schema.user.legacy-update.js");
    }

    public static String getDefaultGroupSchema() {
        return getResourceAsString("schema.group.json");
    }

    public static String getDefaultDocumentSchema() {
        return getResourceAsString("schema.document.json");
    }
    
    public static String getResourceAsString(String name) {
        InputStream resource = DefaultSchemasFactory.class.getResourceAsStream(name);
        try {
            return StreamUtil.readFully(new InputStreamReader(resource, "UTF-8"));
        } catch (Exception e) {
            throw new AssertionError(e);
        } finally {
            try { resource.close(); } catch (Exception e) { }
        }
    }
}
