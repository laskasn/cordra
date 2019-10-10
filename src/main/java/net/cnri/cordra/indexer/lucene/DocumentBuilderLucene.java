package net.cnri.cordra.indexer.lucene;

import net.cnri.cordra.indexer.DocumentBuilder;
import net.cnri.cordra.storage.CordraStorage;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.util.BytesRef;

public class DocumentBuilderLucene extends DocumentBuilder<Document> {

    public DocumentBuilderLucene(boolean isStoreFields, CordraStorage storage) {
        super(isStoreFields, storage);
        //doc = new Document();
    }

    @Override
    protected Document create() {
        return new Document();
    }
    
    @Override
    protected void addStringFieldToDocument(Document doc, String fieldName, String fieldValue, boolean isStoreFields) {
        doc.add(new StringField(fieldName, fieldValue, storeFields(isStoreFields)));
    }

    @Override
    protected void addTextFieldToDocument(Document doc, String fieldName, String fieldValue, boolean isStoreFields) {
        doc.add(new TextField(fieldName, fieldValue, storeFields(isStoreFields)));
    }

    @Override
    protected void addNumericFieldToDocument(Document doc, String fieldName, long fieldValue, boolean isStoreFields) {
        doc.add(new LongPoint(fieldName, fieldValue));
        doc.add(new NumericDocValuesField(getSortFieldName(fieldName), fieldValue));
        if (isStoreFields) {
            doc.add(new StoredField(fieldName, fieldValue));
        }
    }

    @Override
    protected void addSortFieldToDocument(Document doc, String fieldName, String fieldValue) {
        doc.add(new SortedDocValuesField(fieldName, bytesRefForSorting(fieldValue)));
    }
    
    @Override
    public String getSortFieldName(String field) {
// allow sort on first value of arrays
//        if (field.startsWith("/") && (field.endsWith("/_") || field.contains("/_/"))) return null;
        if ("score".equals(field)) return "score";
        return "sort_" + field;
    }

    private BytesRef bytesRefForSorting(String s) {
        if (s.length() < 1024) return new BytesRef(s);
        else return new BytesRef(s.substring(0, 1024));
    }

    private Field.Store storeFields(boolean isStoreFields) {
        if (isStoreFields) {
            return Field.Store.YES;
        } else {
            return Field.Store.NO;
        }
    }
    
}
