package net.cnri.cordra.auth;

public class AuthConfigFactory {

    public static AuthConfig getDefaultAuthConfig() {
        AuthConfig result = new AuthConfig();
        DefaultAcls userAcls = new DefaultAcls();
        userAcls.defaultAclRead.add("public");
        userAcls.defaultAclWrite.add("self");
        result.schemaAcls.put("User", userAcls);

        DefaultAcls schemaAcls = new DefaultAcls();
        schemaAcls.defaultAclRead.add("public");
        result.schemaAcls.put("Schema", schemaAcls);

        DefaultAcls cordraDesignAcls = new DefaultAcls();
        cordraDesignAcls.defaultAclRead.add("public");
        result.schemaAcls.put("CordraDesign", cordraDesignAcls);
        
        result.defaultAcls.defaultAclRead.add("public");
        result.defaultAcls.defaultAclWrite.add("creator");
        result.defaultAcls.aclCreate.add("authenticated");
        return result;
    }
}
