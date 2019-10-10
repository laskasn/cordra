package net.cnri.cordra.util.cmdline;

import java.util.HashMap;
import java.util.Map;

public class AuthConfig {
    public Map<String, DefaultAcls> schemaAcls;
    public DefaultAcls defaultAcls;

    public AuthConfig() {
        schemaAcls = new HashMap<>();
        defaultAcls = new DefaultAcls();
    }

    public DefaultAcls getAclForObjectType(String objectType) {
        DefaultAcls res = schemaAcls.get(objectType);
        if (res == null) {
            res = defaultAcls;
        }
        return res;
    }
}
