package net.cnri.cordra;

import java.security.PublicKey;
import java.util.List;

import net.cnri.cordra.auth.AuthConfig;
import net.cnri.cordra.doip.DoipServerConfigWithEnabledFlag;
import net.cnri.cordra.model.HandleMintingConfig;
import net.cnri.cordra.model.HandleServerConfig;
import net.cnri.cordra.model.UiConfig;

public class Design {
    public List<String> ids;
    public Boolean useLegacyContentOnlyJavaScriptHooks;
    public Boolean useLegacySearchPageSizeZeroReturnsAll;
    public Boolean useLegacySessionsApi;
    public Boolean allowInsecureAuthentication;
    public Boolean disableAuthenticationBackOff;
    public UiConfig uiConfig;
    public AuthConfig authConfig;
    public HandleMintingConfig handleMintingConfig;
    public HandleServerConfig handleServerConfig;
    public PublicKey adminPublicKey;
    public Boolean enableVersionEdits;
    public CookiesConfig cookies;
    public Boolean includePayloadsInReplicationMessages;
    public DoipServerConfigWithEnabledFlag doip;

    public String javascript;

    public Design() { }

    public Design(Design design) {
        merge(design);
    }

    public void merge(Design design) {
        if (design.ids != null) this.ids = design.ids;
        if (design.useLegacyContentOnlyJavaScriptHooks != null) this.useLegacyContentOnlyJavaScriptHooks = design.useLegacyContentOnlyJavaScriptHooks;
        if (design.useLegacySearchPageSizeZeroReturnsAll != null) this.useLegacySearchPageSizeZeroReturnsAll = design.useLegacySearchPageSizeZeroReturnsAll;
        if (design.useLegacySessionsApi != null) this.useLegacySessionsApi = design.useLegacySessionsApi;
        if (design.allowInsecureAuthentication != null) this.allowInsecureAuthentication = design.allowInsecureAuthentication;
        if (design.uiConfig != null) this.uiConfig = design.uiConfig;
        if (design.authConfig != null) this.authConfig = design.authConfig;
        if (design.handleMintingConfig != null) this.handleMintingConfig = design.handleMintingConfig;
        if (design.handleServerConfig != null) this.handleServerConfig = design.handleServerConfig;
        if (design.doip != null) this.doip = design.doip;
        if (design.adminPublicKey != null) this.adminPublicKey = design.adminPublicKey;
        if (design.disableAuthenticationBackOff != null) this.disableAuthenticationBackOff = design.disableAuthenticationBackOff;
        if (design.enableVersionEdits != null) this.enableVersionEdits = design.enableVersionEdits;
        if (design.cookies != null) this.cookies = design.cookies;
        if (design.includePayloadsInReplicationMessages != null) this.includePayloadsInReplicationMessages = design.includePayloadsInReplicationMessages;
        if (design.javascript != null) this.javascript = design.javascript;
    }

    public static class CookieConfig {
        public String path;
        public Boolean httpOnly;
        public Boolean secure;
    }

    public static class CookiesConfig {
        public CookieConfig csrfToken;
        public CookieConfig jsessionid;
    }
}
