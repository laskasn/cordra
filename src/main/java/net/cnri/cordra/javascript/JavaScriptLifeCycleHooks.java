package net.cnri.cordra.javascript;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import jdk.nashorn.api.scripting.JSObject;
import jdk.nashorn.api.scripting.NashornException;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import net.cnri.cordra.*;
import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.api.CordraObject;
import net.cnri.cordra.api.InternalErrorCordraException;
import net.cnri.cordra.api.NotFoundCordraException;
import net.cnri.cordra.model.ObjectDelta;
import net.cnri.util.javascript.JavaScriptEnvironment;
import net.cnri.util.javascript.JavaScriptRunner;
import net.handle.hdllib.HandleValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import javax.script.ScriptException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Supplier;

public class JavaScriptLifeCycleHooks {

    private static Logger logger = LoggerFactory.getLogger(JavaScriptLifeCycleHooks.class);
    static final Gson gson = GsonUtility.getPrettyGson();

    public static final String ON_OBJECT_RESOLUTION = "onObjectResolution";
    public static final String BEFORE_DELETE = "beforeDelete";
    public static final String BEFORE_SCHEMA_VALIDATION = "beforeSchemaValidation";
    public static final String OBJECT_FOR_INDEXING = "objectForIndexing";
    public static final String GENERATE_ID = "generateId";
    public static final String IS_GENERATE_ID_LOOPABLE = "isGenerateIdLoopable";
    public static final String CREATE_HANDLE_VALUES = "createHandleValues";

    private final DateTimeFormatter dateTimeFormatter = CordraService.dateTimeFormatter;

    private final JavaScriptEnvironment javaScriptEnvironment;
    private final boolean traceRequests;
    private final CordraRequireLookup cordraRequireLookup;
    private final Supplier<Design> designSupplier;

    public JavaScriptLifeCycleHooks(JavaScriptEnvironment javaScriptEnvironment, boolean traceRequests, CordraRequireLookup cordraRequireLookup, Supplier<Design> designSupplier) {
        this.javaScriptEnvironment = javaScriptEnvironment;
        this.traceRequests = traceRequests;
        this.cordraRequireLookup = cordraRequireLookup;
        this.designSupplier = designSupplier;
    }

    public JavaScriptRunner getJavascriptRunner() {
        JavaScriptRunner runner = javaScriptEnvironment.getRunner(null, logger);
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        RequestContext requestContext = RequestContextHolder.get();
        runner.getEventLoop().submit(() -> {
            contextMap.forEach(MDC::put);
            RequestContextHolder.set(requestContext);
        });
        return runner;
    }

    public void recycleJavascriptRunner(JavaScriptRunner runner) {
        runner.getEventLoop().submit(() -> {
            MDC.clear();
            RequestContextHolder.clear();
        });
        javaScriptEnvironment.recycle(runner);
    }

    public String generateIdFromJavaScript(JavaScriptRunner runner, CordraObject co, Map<String, Object> context) throws CordraException, ScriptException, InterruptedException, InvalidException {
        long start = System.currentTimeMillis();
        try {
            JSObject methodJSObject = findJavaScriptFunctionWithRunner(GENERATE_ID, CordraRequireLookup.DESIGN_MODULE_ID, false, false, runner);
            if (methodJSObject != null) {
                String input = gson.toJson(co);
                String output = runJavaScriptFunctionWithRunner(methodJSObject, input, context, false, runner);
                if (output == null) return null;
                JsonElement outputJsonElement = new JsonParser().parse(output);
                if (outputJsonElement.isJsonNull()) return null;
                if (outputJsonElement.isJsonPrimitive()) return outputJsonElement.getAsString();
                throw new InternalErrorCordraException(GENERATE_ID + " returned object or array");
            }
            return null;
        } finally {
            if (traceRequests) {
                long end = System.currentTimeMillis();
                long delta = end - start;
                String startTime = dateTimeFormatter.format(Instant.ofEpochMilli(start));
                logger.trace(GENERATE_ID + " generateIdFromJavaScript: start " + startTime + ", " + delta + "ms");
            }
        }
    }

    public List<HandleValue> generateHandleValuesFromJavaScript(CordraObject co) throws ScriptException, InterruptedException {
        String inputString = gson.toJson(co);
        JavaScriptRunner runner = getJavascriptRunner();
        try {
            Object moduleExports = runner.requireById(CordraRequireLookup.HANDLE_MINTING_CONFIG_MODULE_ID);
            if (!(moduleExports instanceof JSObject)) return Collections.emptyList();
            Object method = ((JSObject)moduleExports).getMember(CREATE_HANDLE_VALUES);
            if (!(method instanceof JSObject)) return Collections.emptyList();
            JSObject obj = runner.jsonParse(inputString);
            JSObject res = (JSObject)runner.submitAndGet(() -> ((JSObject)method).call(null, obj));
            String outputString = runner.jsonStringify(res);
            return gson.fromJson(outputString, new TypeToken<List<HandleValue>>() {}.getType());
        } finally {
            recycleJavascriptRunner(runner);
        }
    }

    public GenerateIdJavaScriptStatus hasJavaScriptGenerateIdFunction(JavaScriptRunner runner) throws InterruptedException, ScriptException, InvalidException {
        if (!cordraRequireLookup.exists(CordraRequireLookup.DESIGN_MODULE_ID)) {
            return new GenerateIdJavaScriptStatus(false, false);
        }
        JSObject methodJSObject = findJavaScriptFunctionWithRunner(GENERATE_ID, CordraRequireLookup.DESIGN_MODULE_ID, false, false, runner);
        Object isLoopableJSObject = findJavaScriptMemberWithRunner(IS_GENERATE_ID_LOOPABLE, CordraRequireLookup.DESIGN_MODULE_ID, false, false, runner);
        if (methodJSObject == null) {
            return new GenerateIdJavaScriptStatus(false, false);
        } else if (isLoopableJSObject instanceof Boolean) {
            boolean isLoopable = (Boolean)isLoopableJSObject;
            return new GenerateIdJavaScriptStatus(true, isLoopable);
        } else {
            return new GenerateIdJavaScriptStatus(true, false);
        }
    }

    public static class GenerateIdJavaScriptStatus {
        public final boolean hasFunction;
        public final boolean isLoopable;

        public GenerateIdJavaScriptStatus(boolean hasFunction, boolean isLoopable) {
            this.hasFunction = hasFunction;
            this.isLoopable = isLoopable;
        }
    }

    public void beforeDelete(CordraObject co, Map<String, Object> context) throws CordraException, ScriptException, InterruptedException, InvalidException {
        runJavaScriptFunction(co, BEFORE_DELETE, context);
    }

    public ObjectDelta beforeSchemaValidation(String type, CordraObject originalObject, ObjectDelta objectDelta, Map<String, Object> context) throws CordraException, ScriptException, InterruptedException, InvalidException {
        long start = System.currentTimeMillis();
        String moduleId = CordraRequireLookup.moduleIdForSchemaType(type);
        if (!cordraRequireLookup.exists(moduleId)) {
            return objectDelta; // unchanged
        }
        boolean isUpdate = originalObject != null;
        JavaScriptRunner runner = getJavascriptRunner();
        try {
            JSObject methodJSObject = findJavaScriptFunctionWithRunner(BEFORE_SCHEMA_VALIDATION, moduleId, false, false, runner);
            if (methodJSObject != null) {
                Design design = designSupplier.get();
                if (!Boolean.TRUE.equals(design.useLegacyContentOnlyJavaScriptHooks)) {
                    return beforeSchemaValidationFull(methodJSObject, originalObject, objectDelta, context, isUpdate, runner);
                } else {
                    context.put("useLegacyContentOnlyJavaScriptHooks", Boolean.TRUE);
                    return beforeSchemaValidationLegacy(methodJSObject, objectDelta, context, runner);
                }
            }
            return objectDelta; // unchanged;
        } finally {
            if (traceRequests) {
                long end = System.currentTimeMillis();
                long delta = end - start;
                String startTime = dateTimeFormatter.format(Instant.ofEpochMilli(start));
                logger.trace("beforeSchemaValidation runJavaScriptFunction: start " + startTime + ", " + delta + "ms");
            }
            recycleJavascriptRunner(runner);
        }
    }

    private ObjectDelta beforeSchemaValidationLegacy(JSObject methodJSObject, ObjectDelta objectDelta, Map<String, Object> context, JavaScriptRunner runner) throws CordraException, ScriptException, InterruptedException, InvalidException {
        String input = objectDelta.jsonData;
        String output = runJavaScriptFunctionWithRunnerDefaultReturnInput(methodJSObject, input, context, false, runner);
        return new ObjectDelta(objectDelta.id, objectDelta.type, output, objectDelta.acl, objectDelta.userMetadata, objectDelta.payloads, objectDelta.payloadsToDelete);
    }

    private ObjectDelta beforeSchemaValidationFull(JSObject methodJSObject, CordraObject originalObject, ObjectDelta objectDelta, Map<String, Object> context, boolean isUpdate, JavaScriptRunner runner) throws CordraException, ScriptException, InterruptedException, InvalidException {
        CordraObject inputObject;
        if (isUpdate) {
            inputObject = objectDelta.asCordraObjectForUpdate(originalObject);
        } else {
            inputObject = objectDelta.asCordraObjectForCreate();
        }
        String input = gson.toJson(inputObject);
        String output = runJavaScriptFunctionWithRunnerDefaultReturnInput(methodJSObject, input, context, false, runner);
        if (isUpdate) {
            return ObjectDelta.fromStringifiedCordraObjectForUpdate(originalObject, output, objectDelta.payloads);
        } else {
            return ObjectDelta.fromStringifiedCordraObjectForCreate(output, objectDelta.payloads);
        }
    }

    private Object findJavaScriptMemberWithRunner(String memberName, String moduleId, boolean isStatic, boolean isMethod, JavaScriptRunner runner) throws ScriptException, InterruptedException, InvalidException {
        try {
            Object moduleExports = runner.requireById(moduleId);
            if (!(moduleExports instanceof JSObject)) return null;
            Object member;
            if (isMethod) {
                String methodsMemberName = isStatic ? "staticMethods" : "methods";
                Object methods = ((JSObject) moduleExports).getMember(methodsMemberName);
                if (!(methods instanceof JSObject)) return null;
                member = ((JSObject) methods).getMember(memberName);
            } else {
                member = ((JSObject) moduleExports).getMember(memberName);
            }
            return member;
        } catch (ScriptException e) {
            String invalidMessage = extractInvalidMessage(e);
            if (invalidMessage == null) {
                logUnexpectedScriptException(e);
                throw e;
            } else throw new InvalidException(invalidMessage, e);
        }
    }

    private JSObject findJavaScriptFunctionWithRunner(String functionName, String moduleId, boolean isStatic, boolean isMethod, JavaScriptRunner runner) throws ScriptException, InterruptedException, InvalidException {
        Object method = findJavaScriptMemberWithRunner(functionName, moduleId, isStatic, isMethod, runner);
        if (!(method instanceof JSObject)) return null;
        return (JSObject) method;
    }

    private String runJavaScriptFunctionWithRunner(JSObject method, String input, Map<String, Object> context, boolean isStatic, JavaScriptRunner runner) throws CordraException, ScriptException, InterruptedException, InvalidException {
        JSObject obj = parseJsonForJavaScript(runner, input);
        JSObject contextJSObject = jsonObjectifyContext(runner, context);
        String output = runJavaScriptFunctionWithRunner(method, obj, contextJSObject, isStatic, runner);
        return output;
    }

    private String runJavaScriptFunctionWithRunnerDefaultReturnInput(JSObject method, String input, Map<String, Object> context, boolean isStatic, JavaScriptRunner runner) throws CordraException, ScriptException, InterruptedException, InvalidException {
        JSObject obj = parseJsonForJavaScript(runner, input);
        JSObject contextJSObject = jsonObjectifyContext(runner, context);
        String output = runJavaScriptFunctionWithRunner(method, obj, contextJSObject, isStatic, runner);
        if (output == null) output = runner.jsonStringify(obj);
        return output;
    }

    private String runJavaScriptFunctionWithRunner(JSObject method, JSObject obj, JSObject context, boolean isStatic, JavaScriptRunner runner) throws ScriptException, InterruptedException, InvalidException {
        try {
            Object[] params;
            if (isStatic) {
                // static method call
                params = new Object[] { context };
            } else {
                params = new Object[] { obj, context };
            }
            Object resObj = runner.submitAndGet(() -> method.call(null, params));
            if (ScriptObjectMirror.isUndefined(resObj)) {
                return null;
            } else {
                String result;
                if (resObj == null) {
                    result = "null";
                } else if (resObj instanceof JSObject) {
                    JSObject res = (JSObject) resObj;
                    result = runner.jsonStringify(res);
                } else {
                    result = gson.toJson(resObj);
                }
                return result;
            }
        } catch (ScriptException e) {
            String invalidMessage = extractInvalidMessage(e);
            if (invalidMessage == null) {
                logUnexpectedScriptException(e);
                throw e;
            } else throw new InvalidException(invalidMessage, e);
        }
    }

    public static JSObject parseJsonForJavaScript(JavaScriptRunner runner, String params) throws InvalidException {
        if (params == null) return null;
        try {
            return runner.jsonParse(params);
        } catch (ScriptException e) {
            if (!(e.getCause() instanceof NashornException)) {
                throw new InvalidException("Invalid JSON: " + e.getMessage(), e);
            } else {
                throw new InvalidException(((NashornException) e.getCause()).getEcmaError().toString(), e);
            }
        }
    }

    private String extractInvalidMessage(ScriptException e) {
        if (!(e.getCause() instanceof NashornException)) return null;
        try {
            Object errorObj = ((NashornException) e.getCause()).getEcmaError();
            if (errorObj instanceof String) return (String) errorObj;
            // Nashorn uses a type ConsString for concatenated strings
            if (errorObj instanceof CharSequence) return errorObj.toString();
            if (errorObj instanceof JSObject) {
                Object nameObj = ((JSObject) errorObj).getMember("name");
                if ("InvalidException".equals(nameObj)) {
                    Object messageObj = ((JSObject) errorObj).getMember("message");
                    if (messageObj instanceof String) return (String) messageObj;
                }
            }
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    public CordraObject runJavaScriptFunction(CordraObject co, String functionName, Map<String, Object> context) throws CordraException, ScriptException, InterruptedException, InvalidException {
        String moduleId = CordraRequireLookup.moduleIdForSchemaType(co.type);
        return runJavaScriptFunction(co, functionName, moduleId, context);
    }

    private CordraObject runJavaScriptFunction(CordraObject co, String functionName, String moduleId, Map<String, Object> context ) throws CordraException, ScriptException, InterruptedException, InvalidException {
        long start = System.currentTimeMillis();
        if (!cordraRequireLookup.exists(moduleId)) {
            return co; // unchanged
        }
        JavaScriptRunner runner = getJavascriptRunner();
        try {
            JSObject methodJSObject = findJavaScriptFunctionWithRunner(functionName, moduleId, false, false, runner);
            if (methodJSObject != null) {
                Design design = designSupplier.get();
                if (!Boolean.TRUE.equals(design.useLegacyContentOnlyJavaScriptHooks)) {
                    String input = gson.toJson(co);
                    String output = runJavaScriptFunctionWithRunnerDefaultReturnInput(methodJSObject, input, context, false, runner);
                    return gson.fromJson(output, CordraObject.class);
                } else {
                    context.put("useLegacyContentOnlyJavaScriptHooks", Boolean.TRUE);
                    String input = co.getContentAsString();
                    String output = runJavaScriptFunctionWithRunnerDefaultReturnInput(methodJSObject, input, context, false, runner);
                    co.content = new JsonParser().parse(output);
                    return co;
                }
            }
            return co; // unchanged;
        } finally {
            if (traceRequests) {
                long end = System.currentTimeMillis();
                long delta = end - start;
                String startTime = dateTimeFormatter.format(Instant.ofEpochMilli(start));
                logger.trace(functionName + " runJavaScriptFunction: start " + startTime + ", " + delta + "ms");
            }
            recycleJavascriptRunner(runner);
        }
    }

    public static class CallResult {
        public final String result;
        public final String before;
        public final String after;

        public CallResult(String result, String before, String after) {
            this.result = result;
            this.before = before;
            this.after = after;
        }
    }

    public CallResult call(String method, String moduleId, boolean isStatic, String coJson, String paramsJson, Map<String, Object> context) throws InvalidException, ScriptException, InterruptedException, CordraException {
        String result;
        String before = null;
        String after = null;
        JavaScriptRunner runner = getJavascriptRunner();
        try {
            JSObject paramsJSObject = parseJsonForJavaScript(runner, paramsJson);
            if (paramsJSObject != null) {
                context.put("params", paramsJSObject);
            }
            boolean isMethod = true;
            JSObject methodJSObject = findJavaScriptFunctionWithRunner(method, moduleId, isStatic, isMethod, runner);
            if (methodJSObject == null) {
                throw new NotFoundCordraException("Schema does not have a " + (isStatic ? "static" : "") + " method called " + method);
            }
            JSObject obj = null;
            if (!isStatic) {
                obj = parseJsonForJavaScript(runner, coJson);
                before = runner.jsonStringify(obj);
            }
            JSObject contextJSObject = jsonObjectifyContext(runner, context);
            result = runJavaScriptFunctionWithRunner(methodJSObject, obj, contextJSObject, isStatic, runner);
            if (!isStatic) {
                after = runner.jsonStringify(obj);
            }
        } finally {
            recycleJavascriptRunner(runner);
        }
        CallResult callResult = new CallResult(result, before, after);
        return callResult;
    }

    private void addRequestContextToContext(Map<String, Object> context) {
        RequestContext requestContextObj = RequestContextHolder.get();
        if (requestContextObj != null) {
            JsonObject requestContext = requestContextObj.getRequestContext();
            if (requestContext != null) {
                context.put("requestContext", requestContext);
            }
        }
    }

    public List<String> listMethods(boolean isStatic, String moduleId) throws InterruptedException, ScriptException {
        JavaScriptRunner runner = getJavascriptRunner();
        try {
            Object moduleExports = runner.requireById(moduleId);
            if (!(moduleExports instanceof JSObject)) {
                return Collections.emptyList();
            }
            String methodsMemberName = isStatic ? "staticMethods" : "methods";
            Object methods = ((JSObject) moduleExports).getMember(methodsMemberName);
            if (!(methods instanceof JSObject)) {
                return Collections.emptyList();
            }
            List<String> result = new ArrayList<>();
            for (String key : ((JSObject) methods).keySet()) {
                Object value = ((JSObject) methods).getMember(key);
                if (value instanceof JSObject && ((JSObject) value).isFunction()) {
                    result.add(key);
                }
            }
            return result;
        } finally {
            recycleJavascriptRunner(runner);
        }
    }

    public CordraObject objectForIndexing(CordraObject co) throws CordraException, ScriptException, InterruptedException, InvalidException {
        long start = System.currentTimeMillis();
        String moduleId = CordraRequireLookup.moduleIdForSchemaType(co.type);
        if (!cordraRequireLookup.exists(moduleId)) {
            return co; // unchanged
        }
        JavaScriptRunner runner = getJavascriptRunner();
        try {
            JSObject methodJSObject = findJavaScriptFunctionWithRunner(OBJECT_FOR_INDEXING, moduleId, false, false, runner);
            if (methodJSObject != null) {
                CordraObject inputObject = co; //do we need to remove payloads?
                String input = gson.toJson(inputObject);
                Map<String, Object> context = new HashMap<>();
                context.put("objectId", co.id);
                String outputJson = runJavaScriptFunctionWithRunnerDefaultReturnInput(methodJSObject, input, context, false, runner);
                CordraObject coResult;
                try {
                    coResult = GsonUtility.getGson().fromJson(outputJson, CordraObject.class);
                    coResult.id = inputObject.id; //Ensure the JS cannot change the digital object id
                    return ObjectDelta.applyPayloadsToCordraObject(coResult, co.payloads);
                } catch (JsonParseException e) {
                    throw new InternalErrorCordraException("Couldn't parse json into CordraObject", e);
                }
            }
            return co; //unchanged
        } finally {
            if (traceRequests) {
                long end = System.currentTimeMillis();
                long delta = end - start;
                String startTime = dateTimeFormatter.format(Instant.ofEpochMilli(start));
                logger.trace("objectForIndexing runJavaScriptFunction: start " + startTime + ", " + delta + "ms");
            }
            recycleJavascriptRunner(runner);
        }
    }

    private void logUnexpectedScriptException(ScriptException e) {
        if (e.getCause() instanceof NashornException) {
            NashornException ne = (NashornException) e.getCause();
            String message = ne.getMessage();
            if (message == null) message = "";
            if (ne.getEcmaError() instanceof JSObject) {
                JSObject jsError = (JSObject) ne.getEcmaError();
                if (jsError.hasMember("name") && !message.contains(jsError.getMember("name").toString())) message += ": " + jsError.getMember("name");
                if (jsError.hasMember("message") && !message.contains(jsError.getMember("message").toString())) message += ": " + jsError.getMember("message");
            }
            logger.error("Unexpected script exception: " + message + "\n" + NashornException.getScriptStackString(ne));
        }
    }

    private JSObject jsonObjectifyContext(JavaScriptRunner runner, Map<String, Object> context) throws CordraException {
        addRequestContextToContext(context);
        Map<String, Object> noJSObject = new HashMap<>();
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof JSObject || ScriptObjectMirror.isUndefined(value)) continue;
            noJSObject.put(entry.getKey(), value);
        }
        JSObject res;
        try {
            res = runner.jsonParse(gson.toJson(noJSObject));
        } catch (ScriptException | JsonParseException e) {
            throw new InternalErrorCordraException("Error in enrichment", e);
        }
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof JSObject || ScriptObjectMirror.isUndefined(value)) {
                res.setMember(entry.getKey(), value);
            }
        }
        return res;
    }

    //This could be improved with caching
    public boolean typeHasJavaScriptFunction(String type, String functionName) throws InterruptedException, ScriptException, InvalidException {
        String moduleId = CordraRequireLookup.moduleIdForSchemaType(type);
        return moduleHasJavaScriptFunction(functionName, moduleId);
    }

    private boolean moduleHasJavaScriptFunction(String functionName, String moduleId) throws ScriptException, InterruptedException, InvalidException {
        if (!cordraRequireLookup.exists(moduleId)) {
            return false;
        }
        JavaScriptRunner runner = getJavascriptRunner();
        try {
            JSObject methodJSObject = findJavaScriptFunctionWithRunner(functionName, moduleId, false, false, runner);
            if (methodJSObject == null) {
                return false;
            } else {
                return true;
            }
        } finally {
            recycleJavascriptRunner(runner);
        }
    }
}
