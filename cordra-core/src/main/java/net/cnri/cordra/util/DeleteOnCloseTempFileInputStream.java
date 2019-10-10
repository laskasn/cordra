package net.cnri.cordra.util;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class DeleteOnCloseTempFileInputStream extends FilterInputStream {

    private Path tempFile;

    /**
     * Given the InputStream and the path to the temp file that it comes from, this
     * class will delete the temp file when the InputStream is closed;
     */
    public DeleteOnCloseTempFileInputStream(InputStream in, Path tempFile) {
        super(in);
        this.tempFile = tempFile;
    }

    @Override
    public void close() throws IOException {
        super.close();
        Files.deleteIfExists(tempFile);
    }
}
