package net.cnri.cordra.auth;

import net.cnri.cordra.CordraService;
import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.api.CordraObject;
import net.cnri.cordra.indexer.CordraIndexer;
import net.cnri.cordra.indexer.IndexerException;
import net.cnri.cordra.storage.CordraStorage;

import java.util.*;
import java.util.stream.Collectors;

public class AclEnforcer {

    private final static List<String> LEGACY_CALL_PERMISSIONS = Collections.singletonList("writers");

    private final CordraService cordra;
    private final CordraStorage storage;
    private final CordraIndexer indexer;
    private final AuthCache authCache;
    private volatile AuthConfig authConfig;

    public enum Permission {
        NONE,
        READ,
        WRITE
    }

    public AclEnforcer(CordraService cordra, CordraStorage storage, CordraIndexer indexer, AuthCache authCache) {
        this.cordra = cordra;
        this.storage = storage;
        this.indexer = indexer;
        this.authCache = authCache;
    }

    public Permission permittedOperations(String userId, boolean hasUserObject, String objectId) throws CordraException {
        if ("admin".equals(userId)) return Permission.WRITE;
        CordraObject co = storage.get(objectId);
        return permittedOperations(userId, hasUserObject, co);
    }

    private Permission permittedOperationsForUser(String userId, boolean hasUserObject, CordraObject co) {
        boolean canWrite = canWrite(co, userId, hasUserObject);
        boolean canRead = canWrite || canRead(co, userId, hasUserObject);
        if (canWrite) return Permission.WRITE;
        if (canRead) return Permission.READ;
        return Permission.NONE;
    }

    public Permission permittedOperations(String userId, boolean hasUserObject, CordraObject co) throws CordraException {
        if ("admin".equals(userId)) return Permission.WRITE;
        Permission res = permittedOperationsForUser(userId, hasUserObject, co);
        if (Permission.WRITE == res) return res;
        List<String> groups = getGroupsForUser(userId);
        for (String group : groups) {
            Permission groupPerm = permittedOperationsForUser(group, hasUserObject, co);
            if (doesPermissionAllowOperation(groupPerm, res)) res = groupPerm;
            if (Permission.WRITE == res) return res;
        }
        return res;
    }

    public boolean canRead(String userId, boolean hasUserObject, CordraObject co) throws CordraException {
        Permission perm = permittedOperations(userId, hasUserObject, co);
        return perm != Permission.NONE;
    }

    public static boolean doesPermissionAllowOperation(Permission permission, Permission requiredPermission) {
        if (requiredPermission == Permission.NONE) {
            return true;
        } else if (requiredPermission == Permission.READ) {
            return permission == Permission.READ || permission == Permission.WRITE;
        } else if (requiredPermission == Permission.WRITE) {
            return permission == Permission.WRITE;
        } else {
            throw new AssertionError();
        }
    }

    public void setAuthConfig(AuthConfig authConfig) {
        this.authConfig = authConfig;
    }

    private boolean isPermittedToCreateForUser(String userId, boolean hasUserObject, String objectType) {
        if ("admin".equals(userId)) return true;
        DefaultAcls acls = authConfig.getAclForObjectType(objectType);
        for (String permittedId : acls.aclCreate) {
            if ("public".equals(permittedId)) return true;
            if (userId != null && hasUserObject && "authenticated".equals(permittedId)) return true;
            if (permittedId.equalsIgnoreCase(userId)) return true;
        }
        return false;
    }

    private boolean isPermittedToCallForUser(String userId, boolean hasUserObject, String objectId, String method, String type, boolean isStatic, CordraObject co) {
        if ("admin".equals(userId)) return true;
        String creator = null;
        if (co != null) {
            if (co.metadata != null) creator = co.metadata.createdBy;
        }
        List<String> callPermissionsForMethod = getCallPermissionsForMethod(type, isStatic, method);
        if (callPermissionsForMethod == null) {
            return false;
        }
        for (String permittedId : callPermissionsForMethod) {
            if ("public".equals(permittedId)) return true;
            if (userId != null && hasUserObject && "authenticated".equals(permittedId)) return true;
            if (permittedId.equalsIgnoreCase(userId)) return true;
            if (objectId != null && objectId.equalsIgnoreCase(userId) && "self".equals(permittedId)) return true;
            if ("creator".equals(permittedId) && userId != null && userId.equals(creator)) return true;
            if ("writers".equals(permittedId) && canWrite(co, userId, hasUserObject)) return true;
            if ("readers".equals(permittedId) && canRead(co, userId, hasUserObject)) return true;
        }
        return false;
    }

    private List<String> getCallPermissionsForMethod(String type, boolean isStatic, String method) {
        DefaultAcls acls = authConfig.getAclForObjectType(type);
        Map<String, Map<String, List<String>>> aclMethods = acls.aclMethods;
        if (aclMethods == null) {
            return LEGACY_CALL_PERMISSIONS;
        }
        Map<String, List<String>> explicitCallPermissionsForObject = null;
        if (isStatic) {
            explicitCallPermissionsForObject = aclMethods.get("static");
        } else {
            explicitCallPermissionsForObject = aclMethods.get("instance");
        }
        if (explicitCallPermissionsForObject != null) {
            List<String> callPermissionsForMethod = explicitCallPermissionsForObject.get(method);
            if (callPermissionsForMethod != null) {
                return callPermissionsForMethod;
            } else {
                return getDefaultCallPermissionsFor(acls, isStatic);
            }
        } else {
            return getDefaultCallPermissionsFor(acls, isStatic);
        }
    }

    public List<String> getDefaultCallPermissionsFor(DefaultAcls acls, boolean isStatic) {
        Map<String, Map<String, List<String>>> call = acls.aclMethods;
        if (call == null) {
            return null;
        }
        Map<String, List<String>> defaultCallPermissionsForObject = call.get("default");
        if (defaultCallPermissionsForObject == null) {
            return null;
        } else {
            if (isStatic) {
                return defaultCallPermissionsForObject.get("static");
            } else {
                return defaultCallPermissionsForObject.get("instance");
            }
        }
    }


    public boolean isPermittedToCall(String userId, boolean hasUserObject, String objectId, String method, String type) throws CordraException {
        boolean isStatic = type != null;
        CordraObject co = null;
        if (!isStatic) {
            co = storage.get(objectId);
            if (co != null) type = co.type;
        } else {
            String schemaId = cordra.idFromTypeNoSearch(type);
            co = storage.get(schemaId);
        }
        if (isPermittedToCallForUser(userId, hasUserObject, objectId, method, type, isStatic, co)) return true;
        List<String> groups = getGroupsForUser(userId);
        for (String group : groups) {
            if (isPermittedToCallForUser(group, hasUserObject, objectId, method, type, isStatic, co)) return true;
        }
        return false;
    }

    public boolean isPermittedToCreate(String userId, boolean hasUserObject, String objectType) throws CordraException {
        if (isPermittedToCreateForUser(userId, hasUserObject, objectType)) return true;
        List<String> groups = getGroupsForUser(userId);
        for (String group : groups) {
            if (isPermittedToCreateForUser(group, hasUserObject, objectType)) return true;
        }
        return false;
    }

    public boolean isPermittedToCreate(String userId, boolean hasUserObject, List<String> groups, String objectType) {
        if (isPermittedToCreateForUser(userId, hasUserObject, objectType)) return true;
        for (String group : groups) {
            if (isPermittedToCreateForUser(group, hasUserObject, objectType)) return true;
        }
        return false;
    }

    public List<String> filterTypesPermittedToCreate(String userId, boolean hasUserObject, List<String> allTypes) throws CordraException {
        List<String> result = new ArrayList<>();
        List<String> groups = getGroupsForUser(userId);
        for (String objectType : allTypes) {
            if (isPermittedToCreate(userId, hasUserObject, groups, objectType)) {
                result.add(objectType);
            }
        }
        return result;
    }

    public List<String> getGroupsForUser(String userId) throws CordraException {
        if (userId == null || "admin".equals(userId)) return Collections.emptyList();
        Collection<String> cachedGroups = authCache.getGroupsForUser(userId);
        if (cachedGroups != null) {
            return new ArrayList<>(cachedGroups);
        }
        //String query = "users:\"" + userId + "\"";
        //List<String> res = indexer.searchHandles(query).stream().collect(Collectors.toList());
        Set<String> seen = new HashSet<>();
        Set<String> groupsForUser = getGroupsForUserRecursiveSearch(userId, seen);
        authCache.setGroupsForUser(userId, groupsForUser);
        return new ArrayList<>(groupsForUser);
    }

    public Set<String> getGroupsForUserRecursiveSearch(String memberId, Set<String> seen) throws IndexerException {
        Set<String> accumulation = new HashSet<>();
        Collection<String> groupsFromCache = authCache.getGroupsForUser(memberId);
        if (groupsFromCache != null) {
            accumulation.addAll(groupsFromCache);
            return accumulation;
        }
        String query = "users:\"" + memberId + "\"";
        List<String> groupsFromSearch = indexer.searchHandles(query).stream().collect(Collectors.toList());
        //we dont directly cache the groupsFromSearch as it doesn't contain the parents
        if (groupsFromSearch.isEmpty()) {
            authCache.setGroupsForUser(memberId, Collections.emptyList());
        } else {
            for (String groupId : groupsFromSearch) {
                if (!seen.contains(groupId)) {
                    seen.add(groupId);
                    Set<String> groupResult = getGroupsForUserRecursiveSearch(groupId, seen);
                    //authCache.setGroupsForUser(groupId, groupResult);
                    accumulation.add(groupId);
                    accumulation.addAll(groupResult);
                }
            }
        }
        return accumulation;
    }

    private boolean canRead(CordraObject co, String userId, boolean hasUserObject) {
        return canPermission(co, userId, hasUserObject, false);
    }

    private boolean canWrite(CordraObject co, String userId, boolean hasUserObject) {
        return canPermission(co, userId, hasUserObject, true);
    }

    public CordraObject.AccessControlList getEffectiveAcl(CordraObject co) {
        CordraObject.AccessControlList acl = new CordraObject.AccessControlList();
        if (co.acl != null) {
            acl.readers = co.acl.readers;
            acl.writers = co.acl.writers;
        }
        if (acl.readers == null || acl.writers == null) {
            DefaultAcls defaultAcls = getDefaultAcls(co.type);
            if (acl.readers == null) acl.readers = defaultAcls.defaultAclRead;
            if (acl.writers == null) acl.writers = defaultAcls.defaultAclWrite;
        }
        acl.readers = Collections.unmodifiableList(acl.readers);
        acl.writers = Collections.unmodifiableList(acl.writers);
        return acl;
    }

    private boolean canPermission(CordraObject co, String userId, boolean hasUserObject, boolean isWrite) {
        List<String> acl = null;
        String handle = null;
        String creator = null;
        if (co != null) {
            if (co.acl != null) {
                if (isWrite) {
                    acl = co.acl.writers;
                } else {
                    acl = co.acl.readers;
                }
            }
            handle = co.id;
            if (co.metadata != null) creator = co.metadata.createdBy;
        }
        if (acl == null && authConfig != null) {
            DefaultAcls defaultAcls = getDefaultAcls(co == null ? null : co.type);
            return isPermittedByDefaultAcls(defaultAcls, handle, creator, userId, hasUserObject, isWrite);
        }
        if (acl == null) {
            acl = Collections.emptyList();
        }
        return isPermittedByAcl(acl, handle, creator, userId, hasUserObject);
    }

    public DefaultAcls getDefaultAcls(String objectType) {
        DefaultAcls defaultAcls;
        if (objectType == null) {
            defaultAcls = authConfig.defaultAcls;
        } else {
            defaultAcls = authConfig.schemaAcls.get(objectType);
            if (defaultAcls == null) {
                defaultAcls = authConfig.defaultAcls;
            }
        }
        return defaultAcls;
    }

    private static boolean isPermittedByDefaultAcls(DefaultAcls defaultAcls, String objectId, String creatorId, String userId, boolean hasUserObject, boolean isWrite) {
        if (!isWrite) {
            return isPermittedByAcl(defaultAcls.defaultAclRead, objectId, creatorId, userId, hasUserObject);
        } else {
            return isPermittedByAcl(defaultAcls.defaultAclWrite, objectId, creatorId, userId, hasUserObject);
        }
    }

    private static boolean isPermittedByAcl(List<String> acl, String objectId, String creatorId, String userId, boolean hasUserObject) {
        if ("admin".equals(userId)) {
            return true;
        }
        for (String permittedId : acl) {
            if ("public".equals(permittedId)) return true;
            if (userId != null && hasUserObject && "authenticated".equals(permittedId)) return true;
            if (objectId != null && objectId.equalsIgnoreCase(userId) && "self".equals(permittedId)) return true;
            if (creatorId != null && creatorId.equalsIgnoreCase(userId) && "creator".equals(permittedId)) return true;
            if (permittedId.equalsIgnoreCase(userId)) return true;
        }
        return false;
    }
}
