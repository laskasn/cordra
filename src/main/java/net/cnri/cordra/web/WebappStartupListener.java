/*************************************************************************\
    Copyright (c) 2019 Corporation for National Research Initiatives;
                        All rights reserved.
\*************************************************************************/

package net.cnri.cordra.web;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import net.cnri.cordra.CordraStartupStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.cnri.cordra.CordraService;
import net.cnri.cordra.CordraServiceFactory;

@WebListener
public class WebappStartupListener implements ServletContextListener {
    private static final Logger logger = LoggerFactory.getLogger(WebappStartupListener.class);

    CordraService cordra;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        try {
            ServletContext context = sce.getServletContext();
            cordra = CordraServiceFactory.getAndInitializeCordraService(context);
            CordraStartupStatus.getInstance().state = CordraStartupStatus.State.UP;
//            String baseUri = getBaseUri(serverMain, context);
//            System.out.println("Go to " + baseUri + " in a web browser to access your repository.");

//            CordraDoipOperations cordraDoipOperations = new CordraDoipOperations(baseUri, serverMain.getServerID(), httpClientManager.getClient());
//            serverMain.setKnowbotMapping(CordraDoipOperations.class.getName(), cordraDoipOperations);
        } catch (Exception e) {
            System.out.println("Something went wrong during Cordra startup.  Please check the error.log.");
            logger.error("Something went wrong during Cordra startup", e);
            CordraStartupStatus.getInstance().state = CordraStartupStatus.State.FAILED;
            CordraStartupStatus.getInstance().setStartingToFailed();
//            throw new RuntimeException(e);
        }
    }

//    private String getBaseUri(net.cnri.apps.doserver.Main serverMain, ServletContext context) {
//        String path = context.getContextPath();
//
//        String baseUri = "";
//        String httpPort = serverMain.getConfigVal("http_port");
//        String httpsPort = serverMain.getConfigVal("https_port");
//        if (httpPort == null) {
//            baseUri = "https://localhost:"+httpsPort + path;
//        } else {
//            baseUri = "http://localhost:"+httpPort + path;
//        }
//        return baseUri;
//    }
//
    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        if (cordra != null) {
            cordra.shutdown();
        }
        logger.info("Cordra shutdown complete");
    }
}
