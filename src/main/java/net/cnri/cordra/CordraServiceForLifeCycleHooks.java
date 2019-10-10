package net.cnri.cordra;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

import javax.script.ScriptException;

import net.cnri.cordra.api.*;
import net.cnri.cordra.auth.AuthConfig;
import net.cnri.cordra.auth.QueryRestrictor;

public class CordraServiceForLifeCycleHooks {

    private CordraService cordraService;

    public CordraServiceForLifeCycleHooks() {
    }

    public void init(@SuppressWarnings("hiding") CordraService cordraService) {
        this.cordraService = cordraService;
    }

    public String get(String id, Boolean full) throws CordraException {
        try {
            if (full == null) {
                Boolean legacy = cordraService.getDesign().useLegacyContentOnlyJavaScriptHooks;
                if (legacy != null && legacy.booleanValue()) {
                    full = false;
                }
            }
            if (full == null || full.booleanValue() == true) {
                return cordraService.getFullObjectJson(id);
            } else {
                return cordraService.getObjectJson(id);
            }
        } catch (NotFoundCordraException e) {
            return null;
        }
    }

//    public String get(String id) throws CordraException {
//        return cordraService.getObjectJson(id);
//    }

    public String search(String query) throws CordraException, IOException {
        return search(query, 0, -1, null, null);
    }

    public String search(String query, int pageNum, int pageSize) throws CordraException, IOException {
        return search(query, pageNum,  pageSize, null, null);
    }

    public String search(String query, int pageNum, int pageSize, String sortFieldsString) throws CordraException, IOException {
        return search(query, pageNum,  pageSize, sortFieldsString, null);
    }

    public String search(String query, int pageNum, int pageSize, String sortFieldsString, String userId) throws CordraException, IOException {
        StringWriter stringWriter = new StringWriter();
        boolean isPostProcess = false;
        try {
            cordraService.ensureIndexUpToDate();
            String restrictedQuery = "(" + query + ") -isVersion:true -objatt_isVersion:true -id:design";
            cordraService.search(restrictedQuery, pageNum,  pageSize, sortFieldsString, stringWriter, isPostProcess, userId);
        } catch (ScriptException | InterruptedException e) {
            throw new InternalErrorCordraException(e);
        }
        return stringWriter.toString();
    }

    public String searchAsUser(String query, int pageNum, int pageSize, String sortFieldsString, String userId, List<String> groupIds, boolean hasUserObject) throws CordraException, IOException {
        StringWriter stringWriter = new StringWriter();
        boolean isPostProcess = false;
        try {
            cordraService.ensureIndexUpToDate();
            AuthConfig authConfig = cordraService.design.authConfig;
            String restrictedQuery = QueryRestrictor.restrict(query, userId, hasUserObject, groupIds, authConfig, true);
            cordraService.search(restrictedQuery, pageNum,  pageSize, sortFieldsString, stringWriter, isPostProcess, userId);
        } catch (ScriptException | InterruptedException e) {
            throw new InternalErrorCordraException(e);
        }
        return stringWriter.toString();
    }


}
