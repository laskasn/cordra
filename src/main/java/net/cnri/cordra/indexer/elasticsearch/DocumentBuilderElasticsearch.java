package net.cnri.cordra.indexer.elasticsearch;

import net.cnri.cordra.indexer.DocumentBuilder;
import net.cnri.cordra.storage.CordraStorage;

import java.util.*;

public class DocumentBuilderElasticsearch extends DocumentBuilder<Map<String, List<Object>>> {

    public DocumentBuilderElasticsearch(boolean isStoreFields, CordraStorage storage) {
        super(isStoreFields, storage);
    }

    @Override
    protected Map<String, List<Object>> create() {
        return new HashMap<>();
    }

    @Override
    protected void addStringFieldToDocument(Map<String, List<Object>> doc, String fieldName, String fieldValue, boolean isStoreFieldsParam) {
        doc.putIfAbsent(fieldName, new ArrayList<>());
        doc.get(fieldName).add(fieldValue);
    }

    @Override
    protected void addTextFieldToDocument(Map<String, List<Object>> doc, String fieldName, String fieldValue, boolean isStoreFieldsParam) {
        doc.putIfAbsent(fieldName, new ArrayList<>());
        doc.get(fieldName).add(fieldValue);
    }

    @Override
    protected void addNumericFieldToDocument(Map<String, List<Object>> doc, String fieldName, long fieldValue, boolean isStoreFieldsParam) {
        doc.putIfAbsent(fieldName, new ArrayList<>());
        doc.get(fieldName).add(fieldValue);
    }

    @Override
    protected void addSortFieldToDocument(Map<String, List<Object>> doc, String fieldName, String fieldValue) {
        String sortFieldName = getSortFieldName(fieldName);
        String sortFieldValue = truncateForSorting(fieldValue);
        doc.putIfAbsent(sortFieldName, new ArrayList<>());
        doc.get(sortFieldName).add(sortFieldValue);

    }

    private String truncateForSorting(String s) {
        if (s.length() < 1024) return s;
        return s.substring(0, 1024);
    }

    @Override
    public String getSortFieldName(String field) {
        // allow sort on first element of arrays
        //        if (field.startsWith("/") && (field.endsWith("/_") || field.contains("/_/"))) return null;
        if ("score".equals(field)) return "_score";
        if (field == null || "undefined".equals(field)) return null;
        if ("txnId".equals(field)) return "txnId";
        if (field.startsWith("sort_")) return field;
        return "sort_" + field;
    }

}
