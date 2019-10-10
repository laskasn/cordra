package net.cnri.cordra.sync.curator;

import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.KeeperException;

public class CuratorUtil {
    public static void ensurePath(CuratorFramework client, String path) throws Exception {
        try {
            client.create().creatingParentsIfNeeded().forPath(path, new byte[0]);
        } catch (KeeperException.NodeExistsException e) {
            // ignore
        }
    }
}
