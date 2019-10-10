package net.cnri.cordra.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class LinkConfig {

    public String type; // may be one of, json, ui, payload, url
    public String specific; //payload name or jsonPointer for url.
    public Boolean all = null; //only for type payload. Indicates that a link should be created for all payloads.
    public boolean primary = false;
}
