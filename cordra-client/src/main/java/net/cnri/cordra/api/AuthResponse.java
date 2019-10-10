package net.cnri.cordra.api;

import java.util.List;

public class AuthResponse {
    public boolean active = false;
    @Deprecated
    public boolean isActiveSession = false;
    public String userId;
    public String username;
    public List<String> typesPermittedToCreate;
    public List<String> groupIds;

    public AuthResponse(boolean active, String userId, String username, List<String> typesPermittedToCreate) {
        this(active, userId, username, typesPermittedToCreate, null);
    }

    public AuthResponse(boolean active, String userId, String username, List<String> typesPermittedToCreate, List<String> groupIds) {
        this.active = active;
        this.isActiveSession = active;
        this.userId = userId;
        this.username = username;
        this.typesPermittedToCreate = typesPermittedToCreate;
        this.groupIds = groupIds;
    }
}
