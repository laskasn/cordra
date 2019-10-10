package net.cnri.cordra.web;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@WebListener
public class LoggingStartupListener implements ServletContextListener {
    private static Logger logger = null; // initialized late to allow configuration

    // Set up cordra.data system property so that logging will always work.
    // Do this as early as possible, before any loggers are statically initialized.
    private static final String CORDRA_DATA = "cordra.data";
    static {
        String cordraDataPath = System.getProperty(CORDRA_DATA);
        if (cordraDataPath == null) {
            cordraDataPath = "data";
            System.setProperty(CORDRA_DATA, "data");
        }
        configureLogging(Paths.get(cordraDataPath));
    }

    private static void configureLogging(Path dataDirPath) {
        if (System.getProperty("log4j.configurationFile") != null) {
            logger = LoggerFactory.getLogger(LoggingStartupListener.class);
            logger.info("Using log4j2 configuration file " + System.getProperty("log4j.configurationFile"));
            return;
        }
        String[] extensions = { "properties", "yaml", "yml", "json", "jsn", "xml" };
        boolean found = false;
        for (String extension : extensions) {
            Path path = dataDirPath.resolve("log4j2." + extension);
            if (Files.exists(path)) {
                found = true;
                System.setProperty("log4j.configurationFile", path.toAbsolutePath().toString());
                break;
            }
        }
        logger = LoggerFactory.getLogger(LoggingStartupListener.class);
        if (found) {
            logger.info("Using log4j2 configuration file " + System.getProperty("log4j.configurationFile"));
        } else {
            logger.info("Using default Cordra log4j2 configuration");
        }
        System.clearProperty("log4j.configurationFile");
    }

    @Override
    public void contextInitialized(ServletContextEvent sce) {

    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {

    }
}
