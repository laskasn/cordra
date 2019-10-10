package net.cnri.cordra.auth;

import java.util.Collection;

public interface AuthCache {
    String getUserIdForUsername(String username);
    void setUserIdForUsername(String username, String userId);
    void clearUserIdForUsername(String username);
    void clearAllUserIdForUsernameValues();

    Collection<String> getGroupsForUser(String userId);
    void setGroupsForUser(String userId, Collection<String> groupIds);
    void clearGroupsForUser(String userId);
    void clearAllGroupsForUserValues();

}
