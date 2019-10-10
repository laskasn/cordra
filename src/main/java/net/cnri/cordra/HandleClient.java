/*************************************************************************\
    Copyright (c) 2019 Corporation for National Research Initiatives;
                        All rights reserved.
\*************************************************************************/

package net.cnri.cordra;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.script.ScriptException;

import net.cnri.cordra.javascript.JavaScriptLifeCycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.gson.JsonObject;

import net.cnri.cordra.model.HandleMintingConfig;
import net.dona.doip.util.GsonUtility;
import net.cnri.cordra.api.CordraObject;
import net.cnri.cordra.doip.CordraClientDoipProcessor;
import net.cnri.cordra.doip.DoipServerConfigWithEnabledFlag;
import net.handle.hdllib.AbstractMessage;
import net.handle.hdllib.AbstractResponse;
import net.handle.hdllib.AdminRecord;
import net.handle.hdllib.AuthenticationInfo;
import net.handle.hdllib.Common;
import net.handle.hdllib.CreateHandleRequest;
import net.handle.hdllib.DeleteHandleRequest;
import net.handle.hdllib.Encoder;
import net.handle.hdllib.HandleException;
import net.handle.hdllib.HandleResolver;
import net.handle.hdllib.HandleValue;
import net.handle.hdllib.Util;

public class HandleClient {
    private static final Logger logger = LoggerFactory.getLogger(HandleClient.class);

    public static final byte[] LOCATION_TYPE = Util.encodeString("10320/loc");
    public static final byte[] REGISTRY_TYPE = Util.encodeString("10320/registry");
    public static final byte[] OLD_REPO_LOOKUP_TYPE = Util.encodeString("CNRI.OBJECT_SERVER");

    private final AuthenticationInfo authInfo;

    private final HandleMintingConfig handleMintingConfig;
    private final HandleResolver resolver;
    private final JavaScriptLifeCycleHooks javaScriptLifeCycleHooks;
    private String doipServiceId;

    public HandleClient(AuthenticationInfo authInfo, HandleMintingConfig handleMintingConfig, JavaScriptLifeCycleHooks javaScriptLifeCycleHooks, String doipServiceId) {
        this.authInfo = authInfo;
        this.handleMintingConfig = handleMintingConfig;
        this.resolver = new HandleResolver();
        this.javaScriptLifeCycleHooks = javaScriptLifeCycleHooks;
        this.doipServiceId = doipServiceId;
//        String oldJavaScript = cordraRequireLookup.getHandleJavaScript();
//        if ((handleMintingConfig.javascript == null && oldJavaScript != null) || (handleMintingConfig.javascript != null && !handleMintingConfig.javascript.equals(oldJavaScript))) {
//            cordraRequireLookup.setHandleJavaScript(handleMintingConfig.javascript);
//            javaScriptEnvironment.clearCache();
//        }
    }

    public void setDoipServiceId(String doipServiceId) {
        this.doipServiceId = doipServiceId;
    }

    public void registerHandle(String handle, CordraObject co, String type, JsonNode dataNode) throws HandleException, ScriptException, InterruptedException {
        try {
            HandleValue[] valuesArray = createHandleValuesArray(co, type, dataNode);
            CreateHandleRequest req = new CreateHandleRequest(Util.encodeString(handle), valuesArray, authInfo);
            // req.overwriteWhenExists = true;
            AbstractResponse response = resolver.processRequest(req);
            if (response.responseCode != AbstractMessage.RC_SUCCESS) {
                throw new HandleException(HandleException.INTERNAL_ERROR, "Unexpected response creating " + handle + ": " + response);
            }
        } catch (Exception e) {
            if (handleMintingConfig.ignoreHandleErrors) {
                logger.warn("Exception registering handle " + handle + ", out of sync", e);
            } else {
                throw e;
            }
        }
    }

    public HandleValue[] createHandleValuesArray(CordraObject co, String type, JsonNode dataNode) throws InterruptedException, ScriptException {
        List<HandleValue> valuesList = HandleClient.createHandleValues(co, type, dataNode, handleMintingConfig, javaScriptLifeCycleHooks, authInfo, doipServiceId);
        HandleValue[] valuesArray = valuesList.toArray(new HandleValue[0]);
        return valuesArray;
    }

    public static List<HandleValue> createHandleValues(CordraObject co, String type, JsonNode dataNode, HandleMintingConfig handleMintingConfig, JavaScriptLifeCycleHooks javaScriptLifeCycleHooks, AuthenticationInfo authInfo, String doipServiceId) throws ScriptException, InterruptedException {
        List<HandleValue> valuesList = new ArrayList<>();
        if (handleMintingConfig.javascript != null) {
            List<HandleValue> jsCreatedValues = javaScriptLifeCycleHooks.generateHandleValuesFromJavaScript(co);
            valuesList.addAll(jsCreatedValues);
        } else {
            if (authInfo != null) {
                AdminRecord adminRecord = new AdminRecord(authInfo.getUserIdHandle(),authInfo.getUserIdIndex(),true,true,true,true,true,true,true,true,true,true,true,true);
                HandleValue adminValue = new HandleValue(100,Common.ADMIN_TYPE,Encoder.encodeAdminRecord(adminRecord));
                valuesList.add(adminValue);
            }
            String locXml = LocBuilder.createLocFor(handleMintingConfig, co, type, dataNode);
            HandleValue locationValue = new HandleValue(1, LOCATION_TYPE, Util.encodeString(locXml));
            valuesList.add(locationValue);
        }
        if (doipServiceId != null && !handleMintingConfig.omitDoipServiceHandleValue) {
            if (!valuesList.stream().anyMatch(value -> "0.TYPE/DOIPService".equals(value.getTypeAsString()))) {
                int index = getNextAvailableIndex(valuesList);
                HandleValue doipValue = new HandleValue(index, "0.TYPE/DOIPService", doipServiceId);
                valuesList.add(doipValue);
            }
        }
        return valuesList;
    }

    static int getNextAvailableIndex(List<HandleValue> valuesList) {
        List<Integer> sortedIndexes = valuesList.stream().map(HandleValue::getIndex).sorted().collect(Collectors.toList());
        int index = 1;
        for (Integer used : sortedIndexes) {
            if (index == used) {
                index++;
            }
        }
        return index;
    }

    public void deleteHandle(String handle) throws HandleException {
        try {
            DeleteHandleRequest req = new DeleteHandleRequest(Util.encodeString(handle), authInfo);
            AbstractResponse response = resolver.processRequest(req);
            if (response.responseCode != AbstractMessage.RC_SUCCESS) {
                throw new HandleException(HandleException.INTERNAL_ERROR, "Unexpected response deleting " + handle + ": " + response);
            }
        } catch (Exception e) {
            if (handleMintingConfig.ignoreHandleErrors) {
                logger.warn("Exception deleting handle " + handle + ", out of sync", e);
            } else {
                throw e;
            }
        }
    }

    public void updateHandleFor(String handle, CordraObject co, String type, JsonNode dataNode) throws HandleException, InterruptedException, ScriptException {
        try {
            updateHandleThrowingExceptions(handle, co, type, dataNode);
        } catch (Exception e) {
            if (handleMintingConfig.ignoreHandleErrors) {
                logger.warn("Exception updating handle " + handle + ", out of sync", e);
            } else {
                throw e;
            }
        }
    }

    public void updateHandleThrowingExceptions(String handle, CordraObject co, String type, JsonNode dataNode) throws ScriptException, InterruptedException, HandleException {
        HandleValue[] valuesArray = createHandleValuesArray(co, type, dataNode);
        CreateHandleRequest req = new CreateHandleRequest(Util.encodeString(handle), valuesArray, authInfo);
        req.overwriteWhenExists = true;
        AbstractResponse response = resolver.processRequest(req);
        if (response.responseCode != AbstractMessage.RC_SUCCESS) {
            throw new HandleException(HandleException.INTERNAL_ERROR, "Unexpected response updating " + handle + ": " + response);
        }
    }

    public static List<HandleValue> createDoipServiceHandleValues(DoipServerConfigWithEnabledFlag doipServerConfig) {
        JsonObject config = doipServerConfig.processorConfig;
        String serviceId = config.get("serviceId").getAsString();
        String address = config.has("address") ? config.get("address").getAsString() : null;
        int port = config.has("port") ? config.get("port").getAsInt() : -1;
        PublicKey publicKey = config.has("publicKey") ? GsonUtility.getGson().fromJson(config.get("publicKey"), PublicKey.class) : null;
        JsonObject doipServiceInfo = CordraClientDoipProcessor.buildDoipServiceInfo(serviceId, address, port, publicKey);
        List<HandleValue> valuesList = new ArrayList<>();
        HandleValue doipServiceInfoValue = new HandleValue(1, "0.TYPE/DOIPServiceInfo", doipServiceInfo.toString());
        valuesList.add(doipServiceInfoValue);
//        HandleValue doipValue = new HandleValue(2, "0.TYPE/DOIPService", serviceId);
//        valuesList.add(doipValue);
        return valuesList;
    }

}
