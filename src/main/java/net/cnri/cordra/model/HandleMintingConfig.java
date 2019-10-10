package net.cnri.cordra.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnore;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class HandleMintingConfig {

    public String prefix;
    public String baseUri;
    public Map<String, List<LinkConfig>> schemaSpecificLinks;
    public List<LinkConfig> defaultLinks;
    public boolean omitDoipServiceHandleValue;
    public String handleAdminIdentity;
    public String javascript;
    public boolean ignoreHandleErrors; //If true creates can continue even if the change could not be sent to the handle server.

    public static HandleMintingConfig getDefaultConfig() {
        HandleMintingConfig result = new HandleMintingConfig();
        result.prefix = "test";
        result.defaultLinks = new ArrayList<>();
        LinkConfig jsonLink = new LinkConfig();
        jsonLink.type = "json";
        result.defaultLinks.add(jsonLink);

        LinkConfig uiLink = new LinkConfig();
        uiLink.type = "ui";
        uiLink.primary = true;
        result.defaultLinks.add(uiLink);
        result.ignoreHandleErrors = false;
        return result;
    }

    @JsonIgnore
    public boolean isMintHandles() {
        if (baseUri != null && !baseUri.isEmpty()) return true;
        if (javascript != null && !javascript.isEmpty()) return true;
        return false;
    }
}
