package net.cnri.cordra.sync.local;

import net.cnri.cordra.model.RepoInit;
import net.cnri.cordra.sync.RepoInitProvider;

public class ObjectBasedRepoInitProvider implements RepoInitProvider {

    private final RepoInit repoInit;
    
    public ObjectBasedRepoInitProvider(RepoInit repoInit) {
        this.repoInit = repoInit;
    }
    
    @Override
    public RepoInit getRepoInit() throws Exception {
        return repoInit;
    }

    @Override
    public void cleanup() throws Exception {
        //no=op
    }

}
