package net.cnri.cordra.javascript;

import java.util.Collection;

public interface ModuleCordraObjectCache {
    Collection<String> getObjectIdsForModule(String module);
    void setObjectIdsForModule(String module, Collection<String> objectIds);
    void clearObjectIdsForModule(String module);
    void clearAllObjectIdsForModuleValues();
}
