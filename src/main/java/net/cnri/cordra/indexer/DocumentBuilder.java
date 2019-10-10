package net.cnri.cordra.indexer;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.cnri.cordra.*;
import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.api.CordraObject;
import net.cnri.cordra.api.Payload;
import net.cnri.cordra.relationships.RelationshipsService;
import net.cnri.cordra.storage.CordraStorage;
import org.apache.lucene.document.DateTools;
import org.apache.tika.Tika;
import org.apache.tika.config.TikaConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Supplier;

public abstract class DocumentBuilder<D> {
    private static Logger logger = LoggerFactory.getLogger(DocumentBuilder.class);

    protected final CordraStorage storage;
    private final boolean isStoreFields;
    private Supplier<Design> designSupplier;
    protected volatile boolean shutdown = false;
    private static final int VERSION = 1;

    private ObjectTransformer objectTransformer;

    //protected D doc;

    public DocumentBuilder(boolean isStoreFields, CordraStorage storage) {
        this.storage = storage;
        this.isStoreFields = isStoreFields;
    }

    public void setDesignSupplier(Supplier<Design> designSupplier) {
        this.designSupplier = designSupplier;
    }

    public void setObjectTransformer(ObjectTransformer objectTransformer) { this.objectTransformer = objectTransformer; }

    public void shutdown() {
        shutdown = true;
    }

    protected abstract D create() throws IOException;
    protected abstract void addStringFieldToDocument(D doc, String fieldName, String fieldValue, boolean isStoreFieldsParam) throws IOException;
    protected abstract void addTextFieldToDocument(D doc, String fieldName, String fieldValue, boolean isStoreFieldsParam) throws IOException;
    protected abstract void addNumericFieldToDocument(D doc, String fieldName, long fieldValue, boolean isStoreFieldsParam) throws IOException;
    protected abstract void addSortFieldToDocument(D doc, String fieldName, String fieldValue) throws IOException;
    public abstract String getSortFieldName(String field);

    public D build(CordraObject coParam, boolean indexPayloads, Map<String, JsonNode> pointerToSchemaMap, Collection<Runnable> cleanupActions) throws Exception {
        CordraObject co = objectTransformer == null ? coParam : objectTransformer.transform(coParam);
        D doc = create();
        if (co.metadata == null) co.metadata = new CordraObject.Metadata();
        if (co.metadata.internalMetadata == null) co.metadata.internalMetadata = new JsonObject();
        //doc.add(new TextField("indexVersion", this.getClass().getName() + " " + VERSION, storeFields));
        addTextFieldToDocument(doc, "indexVersion", this.getClass().getName() + " " + VERSION, isStoreFields);
        //doc.add(new StringField("id", co.id, Field.Store.YES));
        addStringFieldToDocument(doc, "id", co.id, true);
        //doc.add(new SortedDocValuesField(getSortFieldName("id"), bytesRefForSorting(co.id)));
        addSortFieldToDocument(doc, getSortFieldName("id"), co.id);


        // store the digital object attributes in the index
        Map<String, String> attributes = AttributesUtil.getAttributes(co, false, false);
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            //doc.add(new TextField("objatt_"+entry.getKey(), entry.getValue(), storeFields));
            addTextFieldToDocument(doc, "objatt_"+entry.getKey(), entry.getValue(), isStoreFields);
            //doc.add(new SortedDocValuesField(getSortFieldName("objatt_"+entry.getKey()), bytesRefForSorting(entry.getValue())));
            addSortFieldToDocument(doc, getSortFieldName("objatt_"+entry.getKey()), entry.getValue());
        }

        String txnIdString = attributes.get(CordraService.TXN_ID);
        if (txnIdString != null) {
            long txnId = Long.parseLong(txnIdString);
//            doc.add(new LongPoint("txnId", txnId));
//            doc.add(new NumericDocValuesField(getSortFieldName("txnId"), txnId));
//            if(storeFields == Field.Store.YES) {
//                doc.add(new StoredField("txnId", txnId));
//            }
            addNumericFieldToDocument(doc, "txnId", txnId, isStoreFields);
        }

        long longCreatedDate = Long.parseLong(attributes.get("internal.created"));
        addStringFieldWithSort(doc, "objcreated", DateTools.dateToString(new Date(longCreatedDate), DateTools.Resolution.MILLISECOND));

        long longModifiedDate = Long.parseLong(attributes.get("internal.modified"));
        addStringFieldWithSort(doc, "objmodified", DateTools.dateToString(new Date(longModifiedDate), DateTools.Resolution.MILLISECOND));
        if (co.payloads != null) {
            for (Payload payload : co.payloads) {
                addFieldForPayload(doc, payload, co.id);
            }
        }
        addStringFieldWithSort(doc, "dtindexed", DateTools.dateToString(new Date(), DateTools.Resolution.MILLISECOND));
        String type = co.type;
        if (type != null) {
            //doc.add(new TextField("type", type, storeFields));
            addTextFieldToDocument(doc, "type", type, true);
            //doc.add(new SortedDocValuesField(getSortFieldName("type"), new BytesRef(type)));
            addSortFieldToDocument(doc, getSortFieldName("type"), type);
        }
        addFieldsForObjectsOfKnownTypes(doc, co, type, indexPayloads, pointerToSchemaMap, retrieveHandleMintingConfigPrefix(), cleanupActions);
        if (co.userMetadata != null) {
            JsonNode userMetadataJsonNode = JsonUtil.gsonToJackson(co.userMetadata);
            addFieldsForJson(doc, userMetadataJsonNode, "userMetadata", "userMetadata", null, new HashSet<>());
        }
        addMetadataAsJsonPointers(co.metadata, doc);

        String createdBy = co.metadata.createdBy;
        if (createdBy != null) {
            //doc.add(new TextField("createdBy", createdBy, storeFields));
            addTextFieldToDocument(doc, "createdBy", createdBy, isStoreFields);
            //doc.add(new SortedDocValuesField(getSortFieldName("createdBy"), new BytesRef(createdBy)));
            addSortFieldToDocument(doc, getSortFieldName("createdBy"), createdBy);
        }
        JsonElement username = co.metadata.internalMetadata.get("username");
        if (username != null) {
            //doc.add(new TextField("username", username, storeFields));
            addTextFieldToDocument(doc, "username", username.getAsString(), isStoreFields);
        }
        JsonElement users = co.metadata.internalMetadata.get("users");
        if (users != null) {
            for (String user : users.getAsString().split("\n")) {
                if (user.isEmpty()) continue;
                //doc.add(new TextField("users", user, storeFields));
                addTextFieldToDocument(doc, "users", user, isStoreFields);
            }
        }
        JsonElement schemaName = co.metadata.internalMetadata.get("schemaName");
        if (schemaName != null) {
            //doc.add(new TextField("schemaName", schemaName, storeFields));
            addTextFieldToDocument(doc, "schemaName", schemaName.getAsString(), isStoreFields);
        }
        Boolean isVersion = co.metadata.isVersion;
        if (isVersion != null) {
            addStringFieldWithSort(doc, VersionManager.IS_VERSION, String.valueOf(isVersion));
        }
        String versionOf = co.metadata.versionOf;
        if (versionOf != null) {
            addStringFieldWithSort(doc, VersionManager.VERSION_OF, versionOf);
        }
        JsonElement payloadIndexState = co.metadata.internalMetadata.get(CordraIndexer.PAYLOAD_INDEX_STATE);
        if (payloadIndexState != null) {
            addStringFieldWithSort(doc, CordraIndexer.PAYLOAD_INDEX_STATE, payloadIndexState.getAsString());
        }
        JsonElement payloadIndexCordraServiceId = co.metadata.internalMetadata.get(CordraIndexer.PAYLOAD_INDEX_CORDRA_SERVICE_ID);
        if (payloadIndexCordraServiceId != null) {
            addStringFieldWithSort(doc, CordraIndexer.PAYLOAD_INDEX_CORDRA_SERVICE_ID, payloadIndexCordraServiceId.getAsString());
        }
        addAclFields(doc, co);
        return doc;
    }

    private void addMetadataAsJsonPointers(CordraObject.Metadata metadata, D doc) throws IOException {
        Gson gson = GsonUtility.getPrettyGson();
        JsonObject metadataJson = gson.toJsonTree(metadata).getAsJsonObject();
        metadataJson.remove("internalMetadata");
        JsonNode metadataJsonNode = JsonUtil.gsonToJackson(metadataJson);
        addFieldsForJson(doc, metadataJsonNode, "metadata", "metadata", null, new HashSet<>());
    }

    private String retrieveHandleMintingConfigPrefix() {
        if (designSupplier == null) return null;
        Design design = designSupplier.get();
        if (design == null) return null;
        if (design.handleMintingConfig == null) return null;
        return design.handleMintingConfig.prefix;
    }

    private void addStringFieldWithSort(D doc, String name, String value) throws IOException {
        //doc.add(new StringField(name, value, storeFields));
        addStringFieldToDocument(doc, name, value, isStoreFields);
        //doc.add(new SortedDocValuesField(getSortFieldName(name), bytesRefForSorting(value)));
        addSortFieldToDocument(doc, getSortFieldName(name), value);
    }

    private void addFieldForPayload(D doc, Payload payload, String objectId) {
        String payloadName = payload.name;
        try {
            if (payload.filename != null && !payload.filename.isEmpty()) {
                addTextFieldToDocument(doc, "internal.all", payload.filename, false);
            }

            //doc.add(new StringField("elname_" + payloadName, "true", storeFields));
            addStringFieldToDocument(doc, "elname_" + payloadName, "true", isStoreFields);

            final String escapedPayloadName = escapePayloadName(payloadName);
            final boolean escapedSame = payloadName.equals(escapedPayloadName);

            Map<String, String> attributes = AttributesUtil.getAttributes(payload);

            for (Map.Entry<String, String> entry : attributes.entrySet()) {
                //doc.add(new TextField("elatt_"+escapedPayloadName+"_"+entry.getKey(), entry.getValue(), storeFields));
                addTextFieldToDocument(doc, "elatt_"+escapedPayloadName+"_"+entry.getKey(), entry.getValue(), isStoreFields);
                //doc.add(new SortedDocValuesField(getSortFieldName("elatt_"+escapedPayloadName+"_"+entry.getKey()), bytesRefForSorting(entry.getValue())));
                addSortFieldToDocument(doc, getSortFieldName("elatt_"+escapedPayloadName+"_"+entry.getKey()), entry.getValue());
                if(!escapedSame) {
                    //doc.add(new TextField("elatt_"+payloadName+"_"+entry.getKey(), entry.getValue(), storeFields));
                    addTextFieldToDocument(doc, "elatt_"+payloadName+"_"+entry.getKey(), entry.getValue(), isStoreFields);
                    //doc.add(new SortedDocValuesField(getSortFieldName("elatt_"+payloadName+"_"+entry.getKey()), bytesRefForSorting(entry.getValue())));
                    addSortFieldToDocument(doc, getSortFieldName("elatt_"+payloadName+"_"+entry.getKey()), entry.getValue());
                }
            }
        } catch (Exception e) {
            logger.error("error getting payload "+payloadName+" from object "+objectId,e);
        }
    }

    private static String escapePayloadName(String payloadName) {
        return payloadName.replace("%","%25").replace("_","%5F");
    }

    private void addFieldsForObjectsOfKnownTypes(D doc, CordraObject co, String type, boolean indexPayloads, Map<String, JsonNode> pointerToSchemaMap, String handleMintingConfigPrefix, Collection<Runnable> cleanupActions) throws IOException {
        if (co.content != null) {
            try {
                JsonNode jsonNode = JsonUtil.gsonToJackson(co.content);
                addFieldsForJson(doc, jsonNode, pointerToSchemaMap);
                JsonNode rootSchema = null;
                if (pointerToSchemaMap != null) rootSchema = pointerToSchemaMap.get("");
                if (indexPayloads) addPayloads(doc, jsonNode, co, rootSchema, cleanupActions);
                addReferencesField(doc, co, type, jsonNode, pointerToSchemaMap, handleMintingConfigPrefix);
                addJavaScriptModuleNames(doc, co, jsonNode, pointerToSchemaMap);
            } catch (CordraException e) {
                logger.warn("Exception indexing " + co.id, e);
            }
        }
        //doc.add(new StringField("valid", "true", storeFields));
        addStringFieldToDocument(doc, "valid", "true", isStoreFields);
    }

    private void addJavaScriptModuleNames(D doc, CordraObject co, JsonNode jsonNode, Map<String, JsonNode> pointerToSchemaMap) throws IOException {
        if (pointerToSchemaMap == null) return;
        List<String> directoryNames = new ArrayList<>();
        for (Map.Entry<String, JsonNode> entry : pointerToSchemaMap.entrySet()) {
            String jsonPointer = entry.getKey();
            JsonNode subSchema = entry.getValue();
            if (!SchemaUtil.isPathForScriptsInPayloads(subSchema)) continue;
            JsonNode directoryNode = jsonNode.at(jsonPointer);
            if (directoryNode == null || !directoryNode.isTextual()) {
                logger.warn("Unexpected script.directory node " + jsonPointer + " in " + co.id);
            } else {
                directoryNames.add(directoryNode.asText());
            }
        }
        if (directoryNames.isEmpty()) return;
        if (co.payloads == null) return;
        for (Payload payload : co.payloads) {
            if (shutdown) break;
            String payloadName = payload.name;
            for (String directoryName : directoryNames) {
                String moduleName = Paths.get("/" + directoryName).resolve(payloadName).normalize().toString();
                //doc.add(new TextField("javaScriptModuleName", moduleName, storeFields));
                addTextFieldToDocument(doc, "javaScriptModuleName", moduleName, isStoreFields);
            }
        }
    }

    private void addPayloads(D doc, JsonNode jsonNode, CordraObject co, JsonNode rootSchema, @SuppressWarnings("unused") Collection<Runnable> cleanupActions) throws CordraException {
        if (co.payloads == null) return;
        for (Payload payload : co.payloads) {
            if (shutdown) break;
            String payloadName = payload.name;
            // Name of the field is the element name.
            // However, for backward compatibility with json-pointer-payloads, we turn payloads named
            // with json pointers to array elements into wildcards.
            String wildcardPayloadPointer = payloadName;
            if (JsonUtil.isValidJsonPointer(payloadName)) wildcardPayloadPointer = JsonUtil.convertJsonPointerToUseWildCardForArrayIndices(payloadName, jsonNode);
            InputStream inputStream = storage.getPayload(co.id, payloadName);
            if (inputStream != null) {
                try {
                    addPayloadTokensToDocument(doc, inputStream, wildcardPayloadPointer, rootSchema, co.id);
                } catch (Exception e) {
                    logger.error("Exception indexing " + co.id, e);
                } finally {
                    try { inputStream.close(); } catch (Exception e) { logger.warn("Exception closing stream", e); }
                }
            } else {
                logger.warn("Input stream is unexpectedly null for " + co.id + " " + payloadName);
            }
        }
    }

    private void addPayloadTokensToDocument(D doc, InputStream stream, String payloadFieldName, JsonNode rootSchema, String objectId) throws IOException {
        Exception exception = null;
        Reader reader = null;
        StringBuilder sb = new StringBuilder();
        try {
            String configXml = "<properties><service-loader initializableProblemHandler='ignore'/></properties>";
            InputStream configInputStream = new ByteArrayInputStream(configXml.getBytes(StandardCharsets.UTF_8));
            TikaConfig tikaConfig = new TikaConfig(configInputStream);
            Tika tika = new Tika(tikaConfig);
            reader = tika.parse(stream);
            char[] buf = new char[1024];
            int r;
            try {
                while ((r = reader.read(buf)) > 0 && !shutdown) {
                    sb.append(buf, 0, r);
                }
            } catch (Exception e) {
                exception = e;
            }
        } catch (Exception e) {
            exception = e;
        } finally {
            if (reader != null) try { reader.close(); } catch (IOException e) { logger.warn("Exception closing stream", e); }
        }
        String extractedText = sb.toString();
        //doc.add(new TextField(payloadFieldName, extractedText, Field.Store.NO));
        addTextFieldToDocument(doc, payloadFieldName, extractedText, false);
        String altPayloadFieldName = getAltPayloadFieldName(rootSchema, payloadFieldName);
        if (altPayloadFieldName != null) {
            //doc.add(new TextField(altPayloadFieldName, extractedText, Field.Store.NO));
            addTextFieldToDocument(doc, altPayloadFieldName, extractedText, false);
        }
        //doc.add(new TextField("internal.all", extractedText, Field.Store.NO));
        addTextFieldToDocument(doc, "internal.all", extractedText, false);
        if (exception != null) {
            logger.error("Exception indexing payload " + payloadFieldName + " of " + objectId, exception);
            //doc.add(new StringField("payload_indexing_exception", "true", storeFields));
            addStringFieldToDocument(doc, "payload_indexing_exception", "true", isStoreFields);
        }
    }

    private String getAltPayloadFieldName(JsonNode rootSchema, String payloadFieldName) {
        if (rootSchema == null) return null;
        JsonNode altPayloadFieldNamesNode = SchemaUtil.getDeepCordraSchemaProperty(rootSchema, "search", "altPayloadFieldNames");
        if (altPayloadFieldNamesNode == null || !altPayloadFieldNamesNode.isObject()) return null;
        JsonNode node = altPayloadFieldNamesNode.get(payloadFieldName);
        if (node == null) return null;
        return node.asText();
    }

    private void addFieldsForJson(D doc, JsonNode jsonNode, Map<String, JsonNode> pointerToSchemaMap) throws IOException {
        addFieldsForJson(doc, jsonNode, "", "", pointerToSchemaMap, new HashSet<>());
    }

    private void addFieldsForJson(D doc, JsonNode jsonNode, String jsonPointer, String jsonPointerArraysAsUnderscore, Map<String, JsonNode> pointerToSchemaMap, Set<String> sortFieldNames) throws IOException {
        if (jsonNode.isArray()) {
            for (int i = 0; i < jsonNode.size(); i++) {
                JsonNode child = jsonNode.path(i);
                addFieldsForJson(doc, child, jsonPointer + "/" + i, jsonPointerArraysAsUnderscore + "/_", pointerToSchemaMap, sortFieldNames);
            }
        } else if (jsonNode.isObject()) {
            Iterator<String> iter = jsonNode.fieldNames();
            while (iter.hasNext()) {
                String fieldName = iter.next();
                JsonNode child = jsonNode.path(fieldName);
                addFieldsForJson(doc, child, jsonPointer + "/" + JsonUtil.encodeSegment(fieldName), jsonPointerArraysAsUnderscore + "/" + JsonUtil.encodeSegment(fieldName), pointerToSchemaMap, sortFieldNames);
            }
        } else {
            String text = jsonNode.asText(null);
            if (text != null) {
                //doc.add(new TextField(jsonPointerArraysAsUnderscore, text, storeFields));
                addTextFieldToDocument(doc, jsonPointerArraysAsUnderscore, text, isStoreFields);
                String sortFieldName = getSortFieldName(jsonPointerArraysAsUnderscore);
                if (sortFieldName != null && !sortFieldNames.contains(sortFieldName)) {
                    //doc.add(new SortedDocValuesField(sortFieldName, bytesRefForSorting(text)));
                    addSortFieldToDocument(doc, sortFieldName, text);
                    sortFieldNames.add(sortFieldName);
                }
                //doc.add(new TextField("internal.all", text, Field.Store.NO));
                addTextFieldToDocument(doc, "internal.all", text, false);
                JsonNode schema = null;
                if (pointerToSchemaMap != null) schema = pointerToSchemaMap.get(jsonPointer);
                if (schema != null) {
                    JsonNode altField = SchemaUtil.getDeepCordraSchemaProperty(schema, "search", "altFieldName");
                    if (altField != null) {
                        String altFieldName = altField.asText(null);
                        //doc.add(new TextField(altFieldName, text, storeFields));
                        addTextFieldToDocument(doc, altFieldName, text, isStoreFields);
                        String altSortFieldName = getSortFieldName(altFieldName);
                        if (altSortFieldName != null && !sortFieldNames.contains(altSortFieldName)) {
                            //doc.add(new SortedDocValuesField(altSortFieldName, bytesRefForSorting(text)));
                            addSortFieldToDocument(doc, altSortFieldName, text);
                            sortFieldNames.add(sortFieldName);
                        }
                    }
                }
            }
        }
    }

    private void addReferencesField(D doc, CordraObject co, @SuppressWarnings("unused") String objectType, JsonNode jsonNode, Map<String, JsonNode> pointerToSchemaMap, String handleMintingConfigPrefix) throws IOException {
        if (pointerToSchemaMap == null) return;
        List<RelationshipsService.ObjectPointer> pointedAtIds = RelationshipsService.pointedAtIds(co.id, jsonNode, pointerToSchemaMap, handleMintingConfigPrefix);
        for (RelationshipsService.ObjectPointer objectPointer : pointedAtIds) {
            String id = objectPointer.objectId;
            //doc.add(new TextField("internal.pointsAt", id, storeFields));
            addTextFieldToDocument(doc, "internal.pointsAt", id, isStoreFields);
        }
    }

    private void addAclFields(D doc, CordraObject co) throws IOException {
        CordraObject.AccessControlList acl = co.acl;
        if (acl == null || acl.readers == null) {
            //doc.add(new TextField("aclRead", "missing", storeFields));
            addTextFieldToDocument(doc, "aclRead", "missing", isStoreFields);
        } else {
            for (String id : acl.readers) {
                //doc.add(new TextField("aclRead", id, storeFields));
                addTextFieldToDocument(doc, "aclRead", id, isStoreFields);
            }
        }
        if (acl == null || acl.writers == null) {
            //doc.add(new TextField("aclWrite", "missing", storeFields));
            addTextFieldToDocument(doc, "aclWrite", "missing", isStoreFields);
        } else {
            for (String id : acl.writers) {
                //doc.add(new TextField("aclWrite", id, storeFields));
                addTextFieldToDocument(doc, "aclWrite", id, isStoreFields);
            }
        }
    }
}
