package net.cnri.cordra.auth;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DefaultAcls {
    public List<String> defaultAclRead;
    public List<String> defaultAclWrite;
    public List<String> aclCreate;
    public Map<String, Map<String, List<String>>> aclMethods;
    /* example methods json

     "aclMethods" : {
       "static" : {
         "exampleStaticMethod" : [ "public" ]
       },
       "instance" : {
         "exampleInstanceMethod" : [ "authenticated" ]
       }
     }

     */

    public DefaultAcls() {
        defaultAclRead = new ArrayList<>();
        defaultAclWrite = new ArrayList<>();
        aclCreate = new ArrayList<>();
    }
}
