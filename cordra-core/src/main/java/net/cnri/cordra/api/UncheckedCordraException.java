package net.cnri.cordra.api;

/** Unchecked wrapper for Cordra exceptions.  This is thrown by iterators which are not allowed to throw checked exceptions.  Example use:
 * <code><pre>
    CloseableIterator&lt;DataElement&gt; iter = dobj.listDataElements();
    try {
        while (iter.hasNext()) {
            DataElement element = iter.next();
            // ...
        }
    } catch (UncheckedCordraException e) {
        e.throwCause();
    }
    finally {
        iter.close();
    }
 * </pre></code>
 * */
public class UncheckedCordraException extends RuntimeException {

    public UncheckedCordraException(CordraException cause) {
        super(cause);
    }

    public void throwCause() throws CordraException {
        throw getCause();
    }

    @Override
    public synchronized CordraException getCause() {
        return ((CordraException)super.getCause());
    }
}
