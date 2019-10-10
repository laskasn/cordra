package net.cnri.cordra.storage.hds;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.EnvironmentFailureException;

public class JeUpgradeTool {
    static final Logger logger = LoggerFactory.getLogger(JeUpgradeTool.class);

    public static Environment openEnvironment(File envHome, EnvironmentConfig configuration) {
        try {
            return new Environment(envHome, configuration);
        } catch (EnvironmentFailureException e) {
            if (e.getMessage() != null && e.getMessage().contains("DbPreUpgrade_4_1")) {
                try {
                    upgrade(envHome);
                } catch (Exception ex) {
                    System.out.println("Storage JE version upgrade failed!: " + ex);
                    logger.error("Storage JE version upgrade failed!", ex);
                    e.addSuppressed(ex);
                    throw e;
                }
                return new Environment(envHome, configuration);
            } else {
                throw e;
            }
        }
    }

    private static void upgrade(File envHome) throws Exception {
        System.out.println("Storage requires JE version upgrade.  Performing now.");
        logger.info("Storage requires JE version upgrade.  Performing now.");
        File file = new File(JeUpgradeTool.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        while (!"lib".equals(file.getName()) && !"classes".equals(file.getName())) {
            file = file.getParentFile();
        }
        URL jeJar = new File(file.getParentFile(), "tools/je-4.1.27.jar").toURI().toURL();
        URL jtaJar = new File(file.getParentFile(), "tools/jta-1.1.jar").toURI().toURL();
        try (URLClassLoader classLoader = new URLClassLoader(new URL[] { jeJar, jtaJar }, null)) {
            Class<?> klass = classLoader.loadClass("com.sleepycat.je.util.DbPreUpgrade_4_1");
            Constructor<?> constructor = klass.getConstructor(File.class);
            Object upgrader = constructor.newInstance(envHome);
            Method method = klass.getMethod("preUpgrade");
            method.invoke(upgrader);
            System.out.println("Storage JE version upgrade succeeded.");
            logger.info("Storage JE version upgrade succeeded.");
        }
    }
}
