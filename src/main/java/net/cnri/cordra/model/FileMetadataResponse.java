package net.cnri.cordra.model;

public class FileMetadataResponse {
    String filename;
    String mimetype;

    public FileMetadataResponse(String filename, String mimetype) {
        this.filename = filename;
        this.mimetype = mimetype;
    }
}
