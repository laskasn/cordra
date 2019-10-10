package net.cnri.cordra.sync;

import net.cnri.cordra.model.RepoInit;

public interface RepoInitProvider {
    RepoInit getRepoInit() throws Exception;
    void cleanup() throws Exception;
}
