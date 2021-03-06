package net.cnri.cordra.relationships;

public class Edge {
    String id;
    String label;
    String from;
    String to;
    String style = "arrow";
    String jsonPointer;

    public Edge() { }

    public Edge(String id, String label, String from, String to, String jsonPointer) {
        this.id = id;
        this.label = label;
        this.from = from;
        this.to = to;
        this.jsonPointer = jsonPointer;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getJsonPointer() {
        return jsonPointer;
    }

    public void setJsonPointer(String jsonPointer) {
        this.jsonPointer = jsonPointer;
    }

}
