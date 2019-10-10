package net.cnri.cordra.util.cmdline;

import java.util.ArrayList;
import java.util.List;

public class DefaultAcls {
    public List<String> defaultAclRead;
    public List<String> defaultAclWrite;
    public List<String> aclCreate;

    public DefaultAcls() {
        defaultAclRead = new ArrayList<>();
        defaultAclWrite = new ArrayList<>();
        aclCreate = new ArrayList<>();
    }
}
