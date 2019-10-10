package net.cnri.cordra.sync.curator;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.KeeperException;

import com.google.gson.Gson;

import net.cnri.cordra.GsonUtility;
import net.cnri.cordra.model.RepoInit;
import net.cnri.cordra.sync.RepoInitProvider;

public class CuratorRepoInitProvider implements RepoInitProvider {

    private final CuratorFramework client;
    private final String path;
    
    public CuratorRepoInitProvider(CuratorFramework client, String path) {
        this.client = client;
        this.path = path;
    }

    @Override
    public RepoInit getRepoInit() throws Exception {
        try {
            byte[] repoInitBytes = client.getData().forPath(path);
            StringReader reader = new StringReader(new String(repoInitBytes, StandardCharsets.UTF_8));
            Gson gson = GsonUtility.getGson();
            RepoInit repoInit = gson.fromJson(reader, RepoInit.class);
            return repoInit;
        } catch (KeeperException.NoNodeException e) {
            return null;
        }
    }
    
    @Override
    public void cleanup() throws Exception {
        try {
            client.delete().forPath(path);
        } catch (KeeperException.NoNodeException e) {
            // ignore
        }
    }
}
