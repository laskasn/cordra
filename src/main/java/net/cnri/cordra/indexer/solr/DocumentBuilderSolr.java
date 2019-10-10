package net.cnri.cordra.indexer.solr;

import org.apache.solr.common.SolrInputDocument;

import net.cnri.cordra.indexer.DocumentBuilder;
import net.cnri.cordra.storage.CordraStorage;

public class DocumentBuilderSolr extends DocumentBuilder<SolrInputDocument> {

    public DocumentBuilderSolr(boolean isStoreFields, CordraStorage storage) {
        super(isStoreFields, storage);
        //doc = new SolrInputDocument();
    }

    @Override
    protected SolrInputDocument create() {
        return new SolrInputDocument();
    }
    
    @Override
    protected void addStringFieldToDocument(SolrInputDocument doc, String fieldName, String fieldValue, boolean isStoreFieldsParam) {
        doc.addField(fieldName, fieldValue);
    }

    @Override
    protected void addTextFieldToDocument(SolrInputDocument doc, String fieldName, String fieldValue, boolean isStoreFieldsParam) {
        addStringFieldToDocument(doc, fieldName, fieldValue, isStoreFieldsParam);    
    }

    @Override
    protected void addNumericFieldToDocument(SolrInputDocument doc, String fieldName, long fieldValue, boolean isStoreFieldsParam) {
        doc.addField(fieldName, fieldValue);
    }

    @Override
    protected void addSortFieldToDocument(SolrInputDocument doc, String fieldName, String fieldValue) {
        doc.addField(fieldName, truncateForSorting(fieldValue));
    }
    
    private String truncateForSorting(String s) {
        if (s.length() < 1024) return s;
        return s.substring(0, 1024);
    }

    @Override
    public String getSortFieldName(String field) {
     // Allow sort of first element of arrays
//      if (field.startsWith("/") && (field.endsWith("/_") || field.contains("/_/"))) return null;
      if ("score".equals(field)) return "score"; // special pseudo-field for sort
      if ("txnId".equals(field)) return "txnId"; // special long field
      return "sort_" + field;
    }

}
