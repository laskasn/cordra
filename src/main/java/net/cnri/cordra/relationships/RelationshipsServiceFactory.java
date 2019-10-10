package net.cnri.cordra.relationships;

import net.cnri.cordra.CordraServiceFactory;

public class RelationshipsServiceFactory {
//    private static Logger logger = LoggerFactory.getLogger(new Object() { }.getClass().getEnclosingClass());

    private static RelationshipsService relationshipsService = null;

    public synchronized static RelationshipsService getRelationshipsService() throws Exception {
        if (relationshipsService == null) {
            relationshipsService = new RelationshipsService(CordraServiceFactory.getCordraService());
        }
        return relationshipsService;
    }

}
