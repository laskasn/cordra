/*************************************************************************\
 Copyright (c) 2019 Corporation for National Research Initiatives;
 All rights reserved.
\*************************************************************************/

package net.cnri.cordra.startup;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.cnri.cordra.migration.ConfigMigratorV1toV2;
import net.cnri.servletcontainer.EmbeddedJetty;
import net.cnri.servletcontainer.EmbeddedJettyConfig;
import net.cnri.servletcontainer.EmbeddedJettyConfig.ConnectorConfig;
import net.cnri.servletcontainer.X509CertificateGenerator;
import net.cnri.util.SimpleCommandLine;
import net.handle.hdllib.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CordraStartup {

    private static Logger logger = null;

    public static final String PRIVATE_KEY_FILE = "privatekey";
    public static final String PUBLIC_KEY_FILE = "publickey";
    public static final String HTTPS_PRIVATE_KEY_FILE = "httpsPrivateKey.pem";
    public static final String HTTPS_CERTIFICATE_FILE = "httpsCertificate.pem";
    private static EmbeddedJetty embeddedJetty = null;
    private static volatile boolean shutdown;

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        int httpPort = 8080;
        int httpsPort = 0;
        int httpToHttpsRedirectPort = 0;
        InetAddress listenAddress = InetAddress.getLoopbackAddress();
        PrivateKey privateKey = null;
        PublicKey publicKey = null;
        String dataDirPathName = "data/";
        String cordraBaseUri = "http://localhost:8080/";
        String webAppsPriorityPath = null;
        String webAppsPath = null;
        String webAppsStoragePath = null;
        String webAppsTempPath = null;
        String jettyXmlPath = null;
        String httpsId = "anonymous";
        JsonObject config = null;

        SimpleCommandLine cl = new SimpleCommandLine("data", "webapps-priority", "webapps", "webapps-storage", "webapps-temp", "jetty.xml");
        cl.parse(args);
        if (cl.hasOption("data")) {
            dataDirPathName = cl.getOptionArgument("data");
        }
        if (cl.hasOption("webapps-priority")) {
            webAppsPriorityPath = cl.getOptionArgument("webapps-priority");
        }
        if (cl.hasOption("webapps")) {
            webAppsPath = cl.getOptionArgument("webapps");
        }
        if (cl.hasOption("webapps-storage")) {
            webAppsStoragePath = cl.getOptionArgument("webapps-storage");
        }
        if (cl.hasOption("webapps-temp")) {
            webAppsTempPath = cl.getOptionArgument("webapps-temp");
        }
        if (cl.hasOption("jetty.xml")) {
            jettyXmlPath = cl.getOptionArgument("jetty.xml");
        }

        Path dataDirPath = Paths.get(dataDirPathName).normalize();

        if (!Files.exists(dataDirPath)) {
            System.out.println("Error: data directory does not exist.");
            return;
        }
        String dataFullPath = dataDirPath.toString();
        System.out.println("data dir: " + dataFullPath);
        System.setProperty("cordra.data", dataFullPath); //This is to tell Cordra where the data dir is.
        configureLogging(dataDirPath);

        ConfigMigratorV1toV2 dataDirMigrator = new ConfigMigratorV1toV2(dataDirPath);
        if (dataDirMigrator.needsToMigrate()) {
            System.out.println("Migrating data directory.");
            dataDirMigrator.migrate();
            System.out.println("Migration complete.");
            System.out.println("A directory called " + ConfigMigratorV1toV2.DELETE_ME_DIR + " has been created and contains items\nthat can be deleted once you are satisfied that the migration was successful.");
        }

        JsonParser parser = new JsonParser();
        Path configJsonPath = dataDirPath.resolve("config.json");
        if (Files.exists(configJsonPath)) {
            try (Reader reader = Files.newBufferedReader(configJsonPath, Charset.forName("UTF-8"))) {
                config = parser.parse(reader).getAsJsonObject();
            }
            if (config.get("httpPort") != null) {
                httpPort = config.get("httpPort").getAsInt();
            }
            if (config.get("httpsPort") != null) {
                httpsPort = config.get("httpsPort").getAsInt();
            }
            if (config.get("httpToHttpsRedirectPort") != null) {
                httpToHttpsRedirectPort = config.get("httpToHttpsRedirectPort").getAsInt();
            }
            if (config.get("serverId") != null) {
                httpsId = config.get("serverId").getAsString();
            }
            if (config.get("httpsId") != null) {
                httpsId = config.get("httpsId").getAsString();
            }
            if (config.get("listenAddress") != null) {
                listenAddress = InetAddress.getByName(config.get("listenAddress").getAsString());
            }
            if (config.get("cordraBaseUri") != null) {
                cordraBaseUri = config.get("cordraBaseUri").getAsString();
            }
        }

        File privKeyFile = new File(dataDirPathName, PRIVATE_KEY_FILE);
        if (privKeyFile.exists()) {
            privateKey = getPrivateKeyAfterAskingForPassphraseIfNeeded(privKeyFile);
        }
        File pubKeyFile = new File(dataDirPathName, PUBLIC_KEY_FILE);
        if (pubKeyFile.exists()) {
            publicKey = Util.getPublicKeyFromFile(pubKeyFile.getAbsolutePath());
        }

        EmbeddedJettyConfig jettyConfig = new EmbeddedJettyConfig();
        int internalListenerPort = getInternalListenerPort();
        String password = UUID.randomUUID().toString();
        jettyConfig.addContextAttribute("net.cnri.cordra.startup.internalListenerPort", internalListenerPort);
        jettyConfig.addContextAttribute("net.cnri.cordra.startup.internalPassword", password);

        if (listenAddress != null) jettyConfig.addContextAttribute("net.cnri.cordra.startup.listenAddress", listenAddress.getHostAddress());
        if (privateKey != null) jettyConfig.addContextAttribute("net.cnri.cordra.startup.privatekey", privateKey);
        if (publicKey != null) jettyConfig.addContextAttribute("net.cnri.cordra.startup.publickey", publicKey);
        jettyConfig.addContextAttribute("net.cnri.cordra.startup.cordraBaseUri", cordraBaseUri);

        //jettyConfig.addSystemClass("net.handle.hdllib.");
        //jettyConfig.addSystemClass("com.google.gson.");
        //jettyConfig.addServerClass("org.slf4j.impl."); // Prevent warning if webapp has its own logging implementation
        // Take over logging completely
        jettyConfig.addSystemClass("org.slf4j.");
        jettyConfig.addSystemClass("org.apache.log4j.");
        jettyConfig.addSystemClass("org.apache.logging.");
        jettyConfig.addSystemClass("org.apache.commons.logging.");
        jettyConfig.setBaseDir(new File(dataDirPathName));
        jettyConfig.setWebAppsPriorityPath(webAppsPriorityPath);
        jettyConfig.setWebAppsPath(webAppsPath);
        jettyConfig.setWebAppsStoragePath(webAppsStoragePath);
        jettyConfig.setWebAppsTempPath(webAppsTempPath);
        jettyConfig.setJettyXmlPath(jettyXmlPath);

        if (httpPort <= 0 && httpsPort <= 0) {
            System.out.println("Error: config.json disables httpPort and httpsPort.");
            return;
        }
        ConnectorConfig httpConnectorConfig = null;
        if (httpPort > 0) {
            System.out.println("Initializing HTTP interface on port " + httpPort);
            httpConnectorConfig = new ConnectorConfig();
            httpConnectorConfig.setHttps(false);
            httpConnectorConfig.setListenAddress(listenAddress);
            httpConnectorConfig.setPort(httpPort);
            httpConnectorConfig.setHttpOnly(true);
            if (httpToHttpsRedirectPort > 0) {
                httpConnectorConfig.setRedirectPort(httpToHttpsRedirectPort);
            } else if (httpsPort > 0) {
                httpConnectorConfig.setRedirectPort(httpsPort);
            }
        }
        ConnectorConfig internalConnectorConfig = null;
        if (internalListenerPort > 0) {
            internalConnectorConfig = new ConnectorConfig();
            internalConnectorConfig.setHttps(false);
            internalConnectorConfig.setListenAddress(InetAddress.getLoopbackAddress());
            internalConnectorConfig.setPort(internalListenerPort);
            internalConnectorConfig.setHttpOnly(true);
        }
        ConnectorConfig httpsConnectorConfig = null;
        if (httpsPort > 0) {
            System.out.println("Initializing HTTPS interface on port " + httpsPort);
            httpsConnectorConfig = new ConnectorConfig();
            httpsConnectorConfig.setHttps(true);
            httpsConnectorConfig.setListenAddress(listenAddress);
            httpsConnectorConfig.setPort(httpsPort);
            boolean useDefaultHttpsKeys = shouldUseDefaultHttpsKeys(config);
            if (useDefaultHttpsKeys) {
                generateHttpsKeys(dataDirPathName, httpsId, privateKey);
                httpsConnectorConfig.setHttpsPrivKeyFile(new File(dataDirPathName, HTTPS_PRIVATE_KEY_FILE).getAbsolutePath());
                httpsConnectorConfig.setHttpsCertificateChainFile(new File(dataDirPathName, HTTPS_CERTIFICATE_FILE).getAbsolutePath());
            } else if (config != null) {
                if (!config.has("httpsKeyStoreFile") && !(config.has("httpsPrivKeyFile") && config.has("httpsCertificateChainFile"))) {
                    System.out.println("config.json issue: either httpsKeyStoreFile, or httpsPrivKeyFile and httpsCertificateChainFile, required");
                    System.exit(1);
                    return;
                }
                if (config.has("httpsKeyStoreFile")) {
                    if (!config.has("httpsKeyStorePassword") && !config.has("httpsKeyPassword")) {
                        System.out.println("config.json issue: one of httpsKeyStorePassword, httpsKeyPassword required");
                        System.exit(1);
                        return;
                    }
                    if (!config.has("httpsAlias")) {
                        System.out.println("config.json issue: httpsAlias required");
                        System.exit(1);
                        return;
                    }
                    if (config.has("httpsKeyStorePassword")) {
                        httpsConnectorConfig.setHttpsKeyStorePassword(config.get("httpsKeyStorePassword").getAsString());
                    } else {
                        httpsConnectorConfig.setHttpsKeyStorePassword(config.get("httpsKeyPassword").getAsString());
                    }
                    if (config.has("httpsKeyPassword")) {
                        httpsConnectorConfig.setHttpsKeyPassword(config.get("httpsKeyPassword").getAsString());
                    } else {
                        httpsConnectorConfig.setHttpsKeyPassword(config.get("httpsKeyStorePassword").getAsString());
                    }
                    httpsConnectorConfig.setHttpsKeyStoreFile(config.get("httpsKeyStoreFile").getAsString());
                    httpsConnectorConfig.setHttpsAlias(config.get("httpsAlias").getAsString());
                } else {
                    if (config.has("httpsKeyPassword")) {
                        httpsConnectorConfig.setHttpsKeyPassword(config.get("httpsKeyPassword").getAsString());
                    }
                    httpsConnectorConfig.setHttpsPrivKeyFile(config.get("httpsPrivKeyFile").getAsString());
                    httpsConnectorConfig.setHttpsCertificateChainFile(config.get("httpsCertificateChainFile").getAsString());
                }
            }
            if (config != null && config.get("httpsClientAuth") != null) {
                httpsConnectorConfig.setHttpsClientAuth(config.get("httpsClientAuth").getAsString());
            } else {
                httpsConnectorConfig.setHttpsClientAuth("false");
            }
        }
        if (httpConnectorConfig != null) {
            jettyConfig.addConnector(httpConnectorConfig);
        }
        if (httpsConnectorConfig != null) {
            jettyConfig.addConnector(httpsConnectorConfig);
        }
        if (internalConnectorConfig != null) {
            jettyConfig.addConnector(internalConnectorConfig);
        }

        Path dataLibPath = dataDirPath.resolve("lib");
        if (Files.isDirectory(dataLibPath)) {
            try (Stream<Path> files = Files.walk(dataLibPath)) {
                String extraClasspath =
                    files
                        .filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                        .map(path -> path.toAbsolutePath().toString())
                        .collect(Collectors.joining(";")); // note: this is a Jetty convention, not the Java path separator
                jettyConfig.setExtraClasspath(extraClasspath);
            }
        }

        embeddedJetty = new EmbeddedJetty(jettyConfig);
        try {
            embeddedJetty.setUpHttpServer();
            embeddedJetty.startPriorityDeploymentManager();
            embeddedJetty.startHttpServer();
        } catch (Exception e) {
            logger.error("Error starting Jetty servlet container", e);
            e.printStackTrace(System.out);
            System.exit(1);
        }

        System.out.println("Startup complete.");

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                cleanUp();
            }
        });

        new ServerMonitor(dataDirPath).start();
    }

    private static int getInternalListenerPort() throws IOException {
        try (
            ServerSocket socket = new ServerSocket(0);
        ) {
          return socket.getLocalPort();
        }
    }

    private static void generateHttpsKeys(String dataDirPathName, String httpsId, PrivateKey privateKey) throws Exception {
        Path privKeyFile = Paths.get(dataDirPathName, HTTPS_PRIVATE_KEY_FILE);
        Path certFile = Paths.get(dataDirPathName, HTTPS_CERTIFICATE_FILE);
        if (Files.exists(privKeyFile) || Files.exists(certFile)) {
            // Don't overwrite existing keys
            System.out.println("Using existing keypair for HTTPS.");
            return;
        }
        Path hdlCertFile = Paths.get(dataDirPathName, "serverCertificate.pem");
        if (privateKey != null && Files.exists(hdlCertFile)) {
            System.out.println("Converting existing keypair for use with HTTPS.");
            Files.move(hdlCertFile, certFile);
            writePrivateKeyToFile(privKeyFile, privateKey);
            return;
        }
        System.out.println("Creating keypair for HTTPS.");
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair keys = kpg.generateKeyPair();
        X509Certificate cert = X509CertificateGenerator.generate(httpsId, keys.getPublic(), keys.getPrivate());
        writePrivateKeyToFile(privKeyFile, keys.getPrivate());
        try (OutputStream fout = Files.newOutputStream(certFile)) {
            StringBuilder sb = new StringBuilder();
            sb.append("-----BEGIN CERTIFICATE-----\r\n");
            sb.append(Base64.getMimeEncoder().encodeToString(cert.getEncoded()));
            sb.append("\r\n");
            sb.append("-----END CERTIFICATE-----\r\n");
            fout.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void writePrivateKeyToFile(Path privKeyFile, PrivateKey key) throws IOException {
        try (OutputStream fout = Files.newOutputStream(privKeyFile)) {
            StringBuilder sb = new StringBuilder();
            sb.append("-----BEGIN PRIVATE KEY-----\r\n");
            sb.append(Base64.getMimeEncoder().encodeToString(key.getEncoded()));
            sb.append("\r\n");
            sb.append("-----END PRIVATE KEY-----\r\n");
            fout.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    private static boolean shouldUseDefaultHttpsKeys(JsonObject config) {
        if (config == null) {
            return true;
        } else if (config.get("httpsUseSelfSignedCert") != null) {
            return config.get("httpsUseSelfSignedCert").getAsBoolean();
        } else if (config.has("httpsKeyStoreFile")) {
            return false;
        } else if (config.has("httpsPrivKeyFile") || config.has("httpsCertificateChainFile")) {
            return false;
        } else {
            return true;
        }
    }

    private static void configureLogging(Path dataDirPath) {
        if (System.getProperty("log4j.configurationFile") != null) {
            logger = LoggerFactory.getLogger(CordraStartup.class);
            return;
        }
        String[] extensions = {"properties", "yaml", "yml", "json", "jsn", "xml"};
        for (String extension : extensions) {
            Path path = dataDirPath.resolve("log4j2." + extension);
            if (Files.exists(path)) {
                System.setProperty("log4j.configurationFile", path.toAbsolutePath().toString());
                break;
            }
        }
        logger = LoggerFactory.getLogger(CordraStartup.class);
        System.clearProperty("log4j.configurationFile");
    }

    private static void cleanUp() {
        if (!shutdown) {
            shutdown = true;
            logger.debug("Shutting down server at " + OffsetDateTime.now());
            if (embeddedJetty != null) {
                embeddedJetty.stopHttpServer();
            }
        }
    }

    private static PrivateKey getPrivateKeyAfterAskingForPassphraseIfNeeded(File privKeyFile) throws Exception {
        if (!privKeyFile.exists() || !privKeyFile.canRead()) {
            logger.error("Error:  cannot read private key file: " + privKeyFile.getAbsolutePath());
            System.exit(1);
        }

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        FileInputStream fin = new FileInputStream(privKeyFile);

        byte buf[] = new byte[1024];
        int r;
        while ((r = fin.read(buf)) >= 0) {
            bout.write(buf, 0, r);
        }
        fin.close();
        buf = bout.toByteArray();
        byte secKey[] = null;
        if (Util.requiresSecretKey(buf)) {
            secKey = Util.getPassphrase("Enter the passphrase to decrypt the private key in " + privKeyFile.getAbsolutePath());
        }
        buf = Util.decrypt(buf, secKey);
        PrivateKey svrKey = Util.getPrivateKeyFromBytes(buf, 0);
        return svrKey;
    }

    private static class ServerMonitor extends Thread {
        Path keepRunningFile;

        ServerMonitor(Path baseDir) throws Exception {
            setDaemon(true);
            setName("Server Monitor");

            keepRunningFile = baseDir.resolve("delete_this_to_stop_server");
            try {
                Files.createFile(keepRunningFile);
            } catch (FileAlreadyExistsException e) {
                // ignore
            }
        }

        @Override
        public void run() {
            while (!shutdown) {
                try {
                    if (!Files.exists(keepRunningFile)) {
                        cleanUp();
                        System.exit(0);
                    }

                    Thread.sleep(1000);
                } catch (Throwable t) {
                }
            }
            try {
                Files.delete(keepRunningFile);
            } catch (NoSuchFileException e) {
                // ignore
            } catch (IOException e) {
                logger.error("Shutdown warning: ", e);
            }
        }
    }

}
