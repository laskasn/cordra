package net.cnri.cordra.indexer;

public class CordraTransaction {
    public enum OP { UPDATE, DELETE }

    public long txnId;
    public long timestamp;
    public String objectId;
    public OP op;
    public boolean isNeedToReplicate;

    public CordraTransaction(long txnId, long timestamp, String objectId, OP op, boolean isNeedToReplicate) {
        this.txnId = txnId;
        this.timestamp = timestamp;
        this.objectId = objectId;
        this.op = op;
        this.isNeedToReplicate = isNeedToReplicate;
    }
}
