package net.cnri.cordra.util;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class DelegatedCloseableInputStream extends FilterInputStream {
    private final Runnable closeFunction;

    public DelegatedCloseableInputStream(InputStream in, Runnable closeFunction) {
        super(in);
        this.closeFunction = closeFunction;
    }

    @Override
    public void close() throws IOException {
        super.close();
        this.closeFunction.run();
    }
}
