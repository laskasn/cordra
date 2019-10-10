package net.cnri.cordra;

import com.google.gson.JsonObject;

public class RequestContext {
    private JsonObject requestContext;
    private boolean isAuthCall;
    private boolean isSystemCall = true; // will be set to false when instantiated by a user request

    public JsonObject getRequestContext() {
        return requestContext;
    }

    public void setRequestContext(JsonObject userContext) {
        this.requestContext = userContext;
    }

    public boolean isAuthCall() {
        return isAuthCall;
    }

    public void setAuthCall(boolean isAuthCall) {
        this.isAuthCall = isAuthCall;
    }

    public boolean isSystemCall() {
        return isSystemCall;
    }

    public void setSystemCall(boolean isSystemCall) {
        this.isSystemCall = isSystemCall;
    }
}
