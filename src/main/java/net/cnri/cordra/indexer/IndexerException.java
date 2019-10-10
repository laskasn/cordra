package net.cnri.cordra.indexer;

import net.cnri.cordra.api.InternalErrorCordraException;

public class IndexerException extends InternalErrorCordraException {

    public IndexerException(String message, Throwable cause) {
        super(message, cause);
    }

    public IndexerException(String message) {
        super(message);
    }

    public IndexerException(Throwable cause) {
        super(cause);
    }

}
