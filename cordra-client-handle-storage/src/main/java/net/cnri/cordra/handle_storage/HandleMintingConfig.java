package net.cnri.cordra.handle_storage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HandleMintingConfig {

    public CordraConfig cordra;
    
    public String prefix;
    public List<String> prefixes;
    public String baseUri;
    public Map<String, List<LinkConfig>> schemaSpecificLinks;
    public List<LinkConfig> defaultLinks = getDefaultDefaultLinks();
    public String javascript;

    private static List<LinkConfig> getDefaultDefaultLinks() {
        List<LinkConfig> defaultLinks = new ArrayList<>();
        LinkConfig jsonLink = new LinkConfig();
        jsonLink.type = "json";
        defaultLinks.add(jsonLink);
        LinkConfig uiLink = new LinkConfig();
        uiLink.type = "ui";
        uiLink.primary = true;
        defaultLinks.add(uiLink);
        return defaultLinks;
    }
    
    public static class CordraConfig {
        public String baseUri;
        public String username;
        public String password;
    }
}
