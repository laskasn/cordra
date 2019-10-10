package net.cnri.cordra.web;

public class PathParser {

    private String objectType = null;
    private String objectId = null;
    private String path = null;

    /**
     * @param path /<objecttype>/<objectid>
     */
    public PathParser(String path) {
        this.path = path;
        parse();
    }

    private void parse() {
        int indexOfFirstSlash = path.indexOf("/", 1);
        if (indexOfFirstSlash == -1) {
            throw new IllegalArgumentException();
        }
        objectType = path.substring(1, indexOfFirstSlash);
        objectId = path.substring(indexOfFirstSlash + 1);
    }

    public String getObjectType() {
        return objectType;
    }

    public String getObjectId() {
        return objectId;
    }

}
