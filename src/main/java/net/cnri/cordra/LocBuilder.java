package net.cnri.cordra;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import net.cnri.cordra.model.HandleMintingConfig;
import net.cnri.cordra.model.LinkConfig;
import net.cnri.cordra.api.CordraObject;
import net.cnri.cordra.api.Payload;
import net.cnri.util.StringUtils;

public class LocBuilder {

    private static final String MISSING_BASE_URI = "https://localhost:8443/";

    public static String createLocFor(HandleMintingConfig config, CordraObject co, String type, JsonNode dataNode) {
        String baseUri;
        if (config.baseUri != null) baseUri = config.baseUri;
        else baseUri = MISSING_BASE_URI;
        String id = co.id;
        StringBuilder sb = new StringBuilder();
        sb.append("<locations>\n");
        List<LinkConfig> links = getConfigForObjectType(config, type);
        for (LinkConfig link : links) {
            String href = "";
            String weight = "0";
            if (link.primary) {
                weight = "1";
            }
            String view = "";
            if ("json".equals(link.type)) {
                href = baseUri + "objects/" + StringUtils.encodeURLPath(id);
                view = "json";
                String line = "<location href=\""+href+"\" weight=\""+weight+"\" view=\""+view+"\" />\n";
                sb.append(line);
            } else if ("ui".equals(link.type)) {
                href = baseUri + "#objects/" + StringUtils.encodeURLPath(id);
                view = "ui";
                String line = "<location href=\""+href+"\" weight=\""+weight+"\" view=\""+view+"\" />\n";
                sb.append(line);
            } else if ("payload".equals(link.type)) {
                if (link.all != null && link.all == true) {
                    if (co.payloads != null) {
                        for (Payload payload : co.payloads) {
                            String line = getLocationForPayload(payload, baseUri, weight, id);
                            sb.append(line);
                        }
                    }
                } else if (link.specific != null) {
                    Payload payload = CordraService.getCordraObjectPayloadByName(co, link.specific);
                    if (payload == null) continue;
                    String line = getLocationForPayload(payload, baseUri, weight, id);
                    sb.append(line);
                }
            } else if ("url".equals(link.type)) {
                if (link.specific != null) {
                    JsonNode urlNode = JsonUtil.getJsonAtPointer(link.specific, dataNode);
                    String url = urlNode.asText();
                    view = link.specific;
                    String line = "<location href=\""+url+"\" weight=\""+weight+"\" view=\""+view+"\" />\n";
                    sb.append(line);
                }
            } else {
                continue;
            }
        }
        sb.append("</locations>");
        return sb.toString();
    }

    public static String getLocationForPayload(Payload payload, String baseUri, String weight, String id) {
        String payloadName = payload.name;
        String href = baseUri + "objects/" + StringUtils.encodeURLPath(id) + "?payload=" + StringUtils.encodeURLPath(payloadName);
        String view = payloadName;
        String line = "<location href=\""+href+"\" weight=\""+weight+"\" view=\""+view+"\" />\n";
        return line;
    }

    public static List<LinkConfig> getConfigForObjectType(HandleMintingConfig config, String type) {
        if (config.schemaSpecificLinks == null) {
            return config.defaultLinks;
        } else {
            List<LinkConfig> result = config.schemaSpecificLinks.get(type);
            if (result == null) {
                return config.defaultLinks;
            } else {
                return result;
            }
        }
    }
}
