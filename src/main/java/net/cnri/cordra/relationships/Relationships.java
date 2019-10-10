package net.cnri.cordra.relationships;

import java.util.List;
import java.util.Map;

import net.cnri.cordra.model.SearchResult;

public class Relationships {
    List<Node> nodes;
    List<Edge> edges;
    Map<String, SearchResult> results;

    public Relationships() {
    }

    public Relationships(List<Node> nodes, List<Edge> edges, Map<String, SearchResult> results) {
        this.nodes = nodes;
        this.edges = edges;
        this.results = results;
    }

    public List<Node> getNodes() {
        return nodes;
    }

    public void setNodes(List<Node> nodes) {
        this.nodes = nodes;
    }

    public List<Edge> getEdges() {
        return edges;
    }

    public void setEdges(List<Edge> edges) {
        this.edges = edges;
    }


}
