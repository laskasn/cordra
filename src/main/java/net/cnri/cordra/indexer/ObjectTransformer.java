package net.cnri.cordra.indexer;

import net.cnri.cordra.InvalidException;
import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.api.CordraObject;

import javax.script.ScriptException;

public interface ObjectTransformer {

    CordraObject transform(CordraObject co) throws CordraException, ScriptException, InterruptedException, InvalidException;
}
