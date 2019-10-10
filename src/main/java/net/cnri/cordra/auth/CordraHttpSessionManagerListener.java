package net.cnri.cordra.auth;

import net.cnri.cordra.CordraStartupStatus;
import net.cnri.servletcontainer.sessions.HttpSessionManagerListener;
import javax.servlet.*;

public class CordraHttpSessionManagerListener extends HttpSessionManagerListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        if (CordraStartupStatus.getInstance().state == CordraStartupStatus.State.FAILED) return;
        super.contextInitialized(sce);
    }
}
