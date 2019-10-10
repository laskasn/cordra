package net.cnri.cordra.handle;

import net.cnri.cordra.api.CordraException;
import net.handle.hdllib.HandleValue;

import java.util.List;

@FunctionalInterface
public interface ResolutionOnlyHandleStorage {
    List<HandleValue> getHandleValues(String handle) throws CordraException;
}
