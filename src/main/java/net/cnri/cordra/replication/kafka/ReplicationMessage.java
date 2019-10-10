package net.cnri.cordra.replication.kafka;

public class ReplicationMessage {

    public enum Type {
        DELETE,
        UPDATE
    }
    
    public String cordraClusterId; //The name of the Cordra cluster that created this transaction
    public Type type;
    public String handle;
    public CordraObjectWithPayloadsAsStrings object;
}
