package net.cnri.cordra.handle;

import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.api.NotFoundCordraException;
import net.cnri.cordra.model.HandleServerConfig;
import net.handle.hdllib.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.ArrayList;
import java.util.List;

public class LightWeightHandleServer {
    private static Logger logger = LoggerFactory.getLogger(LightWeightHandleServer.class);

    private final HandleServerConfig config;
    private HdlTcpInterface tcpInterface;
    private SiteInfo thisSite;

    private final int serialNumber = 1;
    private final boolean caseSensitive = true; // Cordra is case-sensitive

    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    private static final int NUM_SERVER_SIGNATURES = 50;
    private static final byte SERVER_STATUS_HANDLE[] = Util.encodeString("0.SITE/status");

    private static final byte MSG_INTERNAL_ERROR[] = Util.encodeString("Internal Error");
    private static final byte MSG_RESOLUTION_ONLY[] = Util.encodeString("This server is resolution only");

    private Signature[] serverSignaturesSha1 = null;
    private Signature[] serverSignaturesSha256 = null;
    private int currentSigIndex = 0;

    private final ResolutionOnlyHandleStorage handleStorage;

    public LightWeightHandleServer(PrivateKey privateKey, PublicKey publicKey, HandleServerConfig config, ResolutionOnlyHandleStorage handleStorage) throws Exception {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
        this.config = config;
        this.handleStorage = handleStorage;
        buildSiteInfo();
        initSignatures();
    }

    private void buildSiteInfo() throws HandleException, UnknownHostException {
        thisSite = new SiteInfo();
        thisSite.isPrimary = true;
        thisSite.isRoot = false;
        thisSite.multiPrimary = false;
        ServerInfo serverInfo = new ServerInfo();
        serverInfo.serverId = 0;
        if (publicKey != null) {
            serverInfo.publicKey = Util.getBytesFromPublicKey(publicKey);
        }
        String addressString = config.externalAddress;
        if (addressString == null) addressString = config.listenAddress;
        InetAddress ipAddress = InetAddress.getByName(addressString);
        serverInfo.ipAddress = ipAddress.getAddress();
        Integer port = config.externalTcpPort;
        if (port == null) port = config.tcpPort;
        Interface iface = new Interface(Interface.ST_QUERY, Interface.SP_HDL_TCP, port);
        serverInfo.interfaces = new Interface[] { iface };
        thisSite.servers = new ServerInfo[] { serverInfo };
        thisSite.majorProtocolVersion = Common.MAJOR_VERSION;
        thisSite.minorProtocolVersion = Common.MINOR_VERSION;
        thisSite.attributes = new Attribute[] { new Attribute(Util.encodeString("desc"), Util.encodeString("Cordra")) };
    }

    private void initSignatures() throws Exception {
        try {
            serverSignaturesSha1 = new Signature[NUM_SERVER_SIGNATURES];
            for (int i = 0; i < serverSignaturesSha1.length; i++) {
                serverSignaturesSha1[i] = Signature.getInstance(Util.getSigIdFromHashAlgId(Common.HASH_ALG_SHA1, privateKey.getAlgorithm()));
                serverSignaturesSha1[i].initSign(privateKey);
            }
            serverSignaturesSha256 = new Signature[NUM_SERVER_SIGNATURES];
            for (int i = 0; i < serverSignaturesSha256.length; i++) {
                serverSignaturesSha256[i] = Signature.getInstance(Util.getSigIdFromHashAlgId(Common.HASH_ALG_SHA256, privateKey.getAlgorithm()));
                serverSignaturesSha256[i].initSign(privateKey);
            }
        } catch (Exception e) {
            logger.error("Unable to initialize server signature object", e);
            throw e;
        }
    }

    public void processRequest(AbstractRequest req, ResponseMessageCallback callback) throws HandleException {
        switch (req.opCode) {
            case AbstractMessage.OC_GET_SITE_INFO:
                sendResponse(callback, new GetSiteInfoResponse(req, thisSite));
                break;
            case AbstractMessage.OC_RESOLUTION:
                sendResponse(callback, doResolution((ResolutionRequest) req));
                break;
            default:
                sendResponse(callback, new ErrorResponse(req, AbstractMessage.RC_OPERATION_NOT_SUPPORTED, MSG_RESOLUTION_ONLY));
                break;
        }
    }

    private final AbstractResponse doResolution(ResolutionRequest req) throws HandleException {
        byte[] handle = req.handle;
        if (Util.equalsCI(handle, SERVER_STATUS_HANDLE)) {
            return new ErrorResponse(req, AbstractMessage.RC_HANDLE_NOT_FOUND, null);
        }

        if (!Util.hasSlash(handle)) handle = Util.convertSlashlessHandleToZeroNaHandle(handle);

        byte handleValues[][] = null;
        try {
            handleValues = getRawHandleValues(caseSensitive ? handle : Util.upperCase(handle), req.requestedIndexes, req.requestedTypes);
        } catch (Exception e) {
            logger.error("error getting values", e);
            return new ErrorResponse(req, AbstractMessage.RC_ERROR, MSG_INTERNAL_ERROR);
        }
        if (handleValues == null) {
            return new ErrorResponse(req, AbstractMessage.RC_HANDLE_NOT_FOUND, null);
        } else if (handleValues.length == 0) {
            return new ErrorResponse(req, AbstractMessage.RC_VALUES_NOT_FOUND, null);
        } else {
            byte unrestrictedVals[][] = getUnrestrictedValues(handleValues);
            if (unrestrictedVals.length == 0) {
                return new ErrorResponse(req, AbstractMessage.RC_VALUES_NOT_FOUND, null);
            } else {
                return new ResolutionResponse(req, req.handle, unrestrictedVals);
            }
        }
    }

    private byte[][] getUnrestrictedValues(byte clumps[][]) {
        int numUnrestricted = 0;
        for (byte[] clump : clumps) {
            if ((Encoder.getHandleValuePermissions(clump, 0) & Encoder.PERM_PUBLIC_READ) != 0) numUnrestricted++;
        }

        byte unrestrictedVals[][] = new byte[numUnrestricted][];
        if (numUnrestricted == 0) {
            return unrestrictedVals;
        }
        numUnrestricted--;
        for (int i = clumps.length - 1; i >= 0; i--) {
            if ((Encoder.getHandleValuePermissions(clumps[i], 0) & Encoder.PERM_PUBLIC_READ) != 0) {
                unrestrictedVals[numUnrestricted--] = clumps[i];
            }
        }
        return unrestrictedVals;
    }

    private byte[][] getRawHandleValues(byte[] handleBytes, int[] indexList, byte[][] typeList) throws HandleException {
        String handle = Util.decodeString(handleBytes);
        try {
            List<HandleValue> allValues = handleStorage.getHandleValues(handle);
            if (allValues == null) return null;
            List<HandleValue> filteredValues;
            boolean includeAllValues = (indexList == null || indexList.length == 0) && (typeList == null || typeList.length == 0);
            if (includeAllValues) {
                filteredValues = allValues;
            } else {
                filteredValues = new ArrayList<>();
                for (HandleValue value : allValues) {
                    int index = value.getIndex();
                    byte[] type = value.getType();
                    if (Util.isParentTypeInArray(typeList, type) || Util.isInArray(indexList, index)) {
                        filteredValues.add(value);
                    }
                }
            }
            byte valBytes[][] = Encoder.encodeHandleValues(filteredValues.toArray(new HandleValue[0]));
            return valBytes;
        } catch (NotFoundCordraException nfe) {
            return null;
        } catch (CordraException e) {
            throw new HandleException(HandleException.INTERNAL_ERROR, "Error in getRawHandleValues", e);
        }
    }

    private void sendResponse(ResponseMessageCallback callback, AbstractResponse response) throws HandleException {
        response.authoritative = true;
        response.siteInfoSerial = serialNumber;

        if (response.certify && (response.cacheCertify || response.signature == null)) {
            Signature[] serverSignatures;
            if (supportsSha256Signature(response)) {
                serverSignatures = serverSignaturesSha256;
            } else {
                serverSignatures = serverSignaturesSha1;
            }

            try {
                // rotating through an array of signatures avoids a bottleneck
                // since we have to 'synchronize' on the signature while signing
                int sigIndex = currentSigIndex++;
                if (sigIndex >= serverSignatures.length) sigIndex = currentSigIndex = 0;

                Signature sig = serverSignatures[sigIndex];

                synchronized (sig) {
                    response.signMessage(sig);
                }
            } catch (Exception e) {
                // If we get an error while signing the response, we return
                // the unsigned message anyway.
                logger.error("Exception signing response", e);
            }
        }
        callback.handleResponse(response);
    }

    private boolean supportsSha256Signature(AbstractResponse response) {
        if (response.hasEqualOrGreaterVersion(2, 11)) return true;
        if ("DSA".equals(privateKey.getAlgorithm())) return false;
        if (response.hasEqualOrGreaterVersion(2, 7)) return true;
        return false;
    }

    public synchronized void shutdown() {
        if (tcpInterface != null) {
            tcpInterface.stopRunning();
            tcpInterface = null;
        }
    }

    public synchronized void startInterfaces() throws Exception {
        tcpInterface = new HdlTcpInterface(this, config);
        Thread t = new Thread(tcpInterface);
        t.start();
    }
}
