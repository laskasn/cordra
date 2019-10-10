package net.cnri.cordra.auth;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class MemoryAuthCache implements AuthCache {
    private final ConcurrentMap<String, String> userIdForUsername = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, List<String>> groupsForUser = new ConcurrentHashMap<>();
    
    
    @Override
    public String getUserIdForUsername(String username) {
        return userIdForUsername.get(username);
    }

    @Override
    public void setUserIdForUsername(String username, String userId) {
        userIdForUsername.put(username, userId);
    }

    @Override
    public void clearUserIdForUsername(String username) {
        userIdForUsername.remove(username);
    }

    @Override
    public void clearAllUserIdForUsernameValues() {
        userIdForUsername.clear();
    }

    @Override
    public Collection<String> getGroupsForUser(String userId) {
        return groupsForUser.get(userId);
    }

    @Override
    public void setGroupsForUser(String userId, Collection<String> groupIds) {
        groupsForUser.put(userId, new ArrayList<>(groupIds));
    }
    
    @Override
    public void clearGroupsForUser(String userId) {
        groupsForUser.remove(userId);
    }
    
    @Override
    public void clearAllGroupsForUserValues() {
        groupsForUser.clear();
    }

}
