package net.cnri.cordra.api;

public class SortField {
    private final String name;
    private final boolean reverse;

    public SortField(String name, boolean reverse) {
        this.name = name;
        this.reverse = reverse;
    }

    public SortField(String name) {
        this(name, false);
    }

    public String getName() {
        return name;
    }

    public boolean isReverse() {
        return reverse;
    }
}
