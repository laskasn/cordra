package net.cnri.cordra;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.servlet.ServletContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.handle.hdllib.Common;
import net.handle.hdllib.Util;

public class DoipSetupProvider {
    private static final Logger logger = LoggerFactory.getLogger(DoipSetupProvider.class);

    public static final String DOIP_PRIVATE_KEY_FILE = "doipPrivateKey";
    public static final String DOIP_PUBLIC_KEY_FILE = "doipPublicKey";
    public static final String DOIP_CERTIFICATE_FILE = "doipCertificate.pem";

    private final ServletContext servletContext;
    private PrivateKey doipPrivateKey;
    private PublicKey doipPublicKey;
    private X509Certificate[] doipCertificateChain;

    public DoipSetupProvider(ServletContext servletContext, PrivateKey privateKey) throws Exception {
        this.servletContext = servletContext;
        initializeKeysAndCertChain(privateKey);
    }

    private void initializeKeysAndCertChain(PrivateKey privateKey) throws Exception {
        doipPrivateKey = privateKey;
        doipPublicKey = (PublicKey) servletContext.getAttribute("net.cnri.cordra.startup.publicKey");
        String cordraDataString = System.getProperty(Constants.CORDRA_DATA);
        if (cordraDataString == null) throw new Exception("cordra.data is null");
        Path cordraDataPath = Paths.get(cordraDataString);
        File doipPrivKeyFile = new File(cordraDataPath.toFile(), DOIP_PRIVATE_KEY_FILE);
        if (doipPrivKeyFile.exists()) {
            doipPrivateKey = Util.getPrivateKeyFromFileWithPassphrase(doipPrivKeyFile, null);
            File doipPubKeyFile = new File(cordraDataPath.toFile(), DOIP_PUBLIC_KEY_FILE);
            if (doipPubKeyFile.exists()) {
                doipPublicKey = Util.getPublicKeyFromFile(doipPubKeyFile.getAbsolutePath());
            }
            File doipCertFile = new File(cordraDataPath.toFile(), DOIP_CERTIFICATE_FILE);
            if (doipCertFile.exists()) {
                doipCertificateChain = readCertChainFromFile(doipCertFile);
            }
        }
    }

    public String getListenAddress() {
        return (String) servletContext.getAttribute("net.cnri.cordra.startup.listenAddress");
    }

    public Integer getInternalListenerPort() {
        return (Integer) servletContext.getAttribute("net.cnri.cordra.startup.internalListenerPort");
    }

    public String getContextPath() {
        return servletContext.getContextPath();
    }

    public String getInternalPassword() {
        return (String) servletContext.getAttribute("net.cnri.cordra.startup.internalPassword");
    }

    public PublicKey getPublicKey() {
        return doipPublicKey;
    }

    public PrivateKey getPrivateKey() {
        return doipPrivateKey;
    }

    public X509Certificate[] getCertChain() {
        return doipCertificateChain;
    }

    private static X509Certificate[] readCertChainFromFile(File certFile) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        try (FileInputStream fis = new FileInputStream(certFile)) {
            return cf.generateCertificates(fis).stream().toArray(X509Certificate[]::new);
        }
    }

    public void createAndSaveKeysIfNecessary() {
        if (doipPrivateKey == null && doipPublicKey == null) {
            logger.info("No handle keys found; minting new keypair");
        } else {
            return;
        }
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            KeyPair keys = kpg.generateKeyPair();
            doipPublicKey = keys.getPublic();
            doipPrivateKey = keys.getPrivate();
            String cordraDataString = System.getProperty(Constants.CORDRA_DATA);
            if (cordraDataString == null) throw new Exception("cordra.data is null");
            Path cordraDataPath = Paths.get(cordraDataString);
            byte[] privateKeyBytes = Util.encrypt(Util.getBytesFromPrivateKey(doipPrivateKey), null, Common.ENCRYPT_NONE);
            Path privateKeyPath = cordraDataPath.resolve(DOIP_PRIVATE_KEY_FILE);
            Files.write(privateKeyPath, privateKeyBytes, StandardOpenOption.CREATE_NEW);
            byte[] publicKeyBytes = Util.getBytesFromPublicKey(doipPublicKey);
            Path publicKeyPath = cordraDataPath.resolve(DOIP_PUBLIC_KEY_FILE);
            Files.write(publicKeyPath, publicKeyBytes, StandardOpenOption.CREATE_NEW);
        } catch (Exception e) {
            logger.error("Unable to store newly-minted DOIP keys", e);
            System.out.println("Unable to store newly-minted DOIP keys (see error.log for details)");
        }
    }
}
