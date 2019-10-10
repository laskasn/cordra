package net.cnri.cordra.auth;

import net.cnri.cordra.CordraConfigSource;
import net.cnri.cordra.model.CordraConfig;
import net.cnri.servletcontainer.sessions.HttpSessionManagerFilter;
import net.cnri.servletcontainer.sessions.memory.InMemoryHttpSessionCoreStore;
import net.cnri.servletcontainer.sessions.tomcat.TomcatSessionCoreStore;

import javax.servlet.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class CordraHttpSessionManagerFilter extends HttpSessionManagerFilter {
    private static final Logger logger = LoggerFactory.getLogger(CordraHttpSessionManagerFilter.class);

    @Override
    public void init(FilterConfig filterConfig) {
        ServletContext servletContext = filterConfig.getServletContext();
        CordraConfig.SessionsConfig config;
        try {
            config = CordraConfigSource.getConfig(servletContext).sessions;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        String sessionCoreStoreClass;
        if ("mongo".equalsIgnoreCase(config.module) || "mongodb".equalsIgnoreCase(config.module)) {
            logger.info("Using mongo sessions");
            sessionCoreStoreClass = CordraMongoHttpSessionCoreStore.class.getName();
        } else if ("tomcat".equalsIgnoreCase(config.module)) {
            logger.info("Using Tomcat sessions");
            sessionCoreStoreClass = TomcatSessionCoreStore.class.getName();
        } else {
            logger.info("Using memory sessions");
            sessionCoreStoreClass = InMemoryHttpSessionCoreStore.class.getName();
        }
        try {
            super.init(servletContext, sessionCoreStoreClass, config.function, config.timeout * 60, null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
