package net.cnri.cordra.handle;

import net.handle.hdllib.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Calendar;

public class HdlTcpRequestHandler implements Runnable, ResponseMessageCallback {
    private static Logger logger = LoggerFactory.getLogger(HdlTcpRequestHandler.class);
    private static Logger accessLogger = LoggerFactory.getLogger("handleAccessLogger");

    private static final int DEFAULT_MAX_MESSAGE_LENGTH = 1024;

    private Socket socket = null;
    private final LightWeightHandleServer server;

    private boolean logAccesses = true;
    private final MessageEnvelope envelope = new MessageEnvelope();
    private final byte envelopeBuf[] = new byte[Common.MESSAGE_ENVELOPE_SIZE];
    private byte messageBuf[] = new byte[DEFAULT_MAX_MESSAGE_LENGTH];

    public static final String ACCESS_TYPE = "TCP:HDL";
    public static final byte MSG_INVALID_MSG_SIZE[] = Util.encodeString("Invalid message length");
    public static final byte MSG_READ_TIMED_OUT[] = Util.encodeString("Read timed out");

    private long recvTime = 0; // time current request was received
    private AbstractRequest currentRequest;

    public HdlTcpRequestHandler(LightWeightHandleServer server, boolean logAccesses, Socket socket, long recvTime) {
        this.server = server;
        this.logAccesses = logAccesses;
        this.recvTime = recvTime;
        this.socket = socket;
    }

    @Override
    public void run() {
        InputStream in = null;
        try {
            in = socket.getInputStream();
            int r, n = 0;
            // receive and parse the message envelope
            while (n < Common.MESSAGE_ENVELOPE_SIZE && (r = in.read(envelopeBuf, n, Common.MESSAGE_ENVELOPE_SIZE - n)) > 0) {
                n += r;
            }
            Encoder.decodeEnvelope(envelopeBuf, envelope);
            if (envelope.messageLength > Common.MAX_MESSAGE_LENGTH || envelope.messageLength < 0) {
                handleResponse(new ErrorResponse(AbstractMessage.OC_RESERVED, AbstractMessage.RC_PROTOCOL_ERROR, MSG_INVALID_MSG_SIZE));
                return;
            }
            if (messageBuf.length < envelope.messageLength) { // increase the messageBuf size if necessary
                messageBuf = new byte[envelope.messageLength];
            }
            // receive the rest of the message
            r = n = 0;
            while (n < envelope.messageLength && (r = in.read(messageBuf, n, envelope.messageLength - n)) > 0) {
                n += r;
            }
            if (n < envelope.messageLength) { // we didn't receive the whole message...
                String errMsg = "Expecting " + envelope.messageLength + " bytes, " + "only received " + n;
                handleResponse(new ErrorResponse(AbstractMessage.OC_RESERVED, AbstractMessage.RC_PROTOCOL_ERROR, Util.encodeString(errMsg)));
                return;
            }

            if (envelope.encrypted) { //decrypt incoming request if it says so ..
                if (envelope.sessionId > 0) {
                    logger.error("Session manager not available. Unable to decrypt request message.");
                    handleResponse(new ErrorResponse(AbstractMessage.OC_RESERVED, AbstractMessage.RC_SESSION_FAILED, Util.encodeString("Session manager not available. Unable to decrypt request message.")));
                    return;
                } else {
                    logger.error("Invalid session id. Request message not decrypted.");
                    handleResponse(new ErrorResponse(AbstractMessage.OC_RESERVED, AbstractMessage.RC_SESSION_FAILED, Util.encodeString("Invalid session id. Unable to decrypt request message.")));
                    return;
                }
            }

            if (envelope.messageLength < 24) {
                handleResponse(new ErrorResponse(AbstractMessage.OC_RESERVED, AbstractMessage.RC_PROTOCOL_ERROR, MSG_INVALID_MSG_SIZE));
                return;
            }

            int opCode = Encoder.readOpCode(messageBuf, 0);
            if (opCode == 0) {
                handleResponse(new ErrorResponse(AbstractMessage.OC_RESERVED, AbstractMessage.RC_PROTOCOL_ERROR, Util.encodeString("Unknown opCode in message: " + opCode)));
                return;
            }

            currentRequest = (AbstractRequest) Encoder.decodeMessage(messageBuf, 0, envelope);
            server.processRequest(currentRequest, this);
        } catch (SocketTimeoutException e) {
            handleResponse(new ErrorResponse(AbstractMessage.OC_RESERVED, AbstractMessage.RC_PROTOCOL_ERROR, MSG_READ_TIMED_OUT));
        } catch (Throwable e) {
            handleResponse(new ErrorResponse(AbstractMessage.OC_RESERVED, AbstractMessage.RC_ERROR, Util.encodeString("Server error processing request, see server logs")));
            logger.error("Exception processing request", e);
        } finally {
            if (in != null) {
                try { in.close(); } catch (Throwable e) { }
            }
            if (socket != null) {
                try { socket.close(); } catch (Exception e){ }
                socket = null;
            }
        }
    }

    @Override
    @SuppressWarnings("resource") // we may keep the socket (and thus the output stream) open
    public void handleResponse(AbstractResponse response) {
        OutputStream out = null;
        boolean keepSocketOpen = response.continuous;
        boolean errorWriting = false;
        try {
            byte msg[] = response.getEncodedMessage();
            envelope.encrypted = false;
            envelope.messageLength = msg.length;
            envelope.messageId = 0;
            envelope.requestId = response.requestId;
            envelope.sessionId = response.sessionId;
            envelope.protocolMajorVersion = response.majorProtocolVersion;
            envelope.protocolMinorVersion = response.minorProtocolVersion;
            envelope.suggestMajorProtocolVersion = response.suggestMajorProtocolVersion;
            envelope.suggestMinorProtocolVersion = response.suggestMinorProtocolVersion;
            Encoder.encodeEnvelope(envelope, envelopeBuf);

            try {
                out = socket.getOutputStream();
                out.write(Util.concat(envelopeBuf, msg));
                out.flush();
            } catch (Exception e) {
                errorWriting = true;
                throw e;
            }

            long respTime = System.currentTimeMillis() - recvTime;
            if (logAccesses) {
                if (currentRequest != null) {
                    logAccess(ACCESS_TYPE + "(" + currentRequest.suggestMajorProtocolVersion + "." + currentRequest.suggestMinorProtocolVersion + ")", socket.getInetAddress(), currentRequest.opCode, response.responseCode,
                        Util.getAccessLogString(currentRequest, response), respTime);
                }
            }
            if (response.streaming) {
                throw new UnsupportedOperationException("streaming responses not supported");
            }
        } catch (Exception e) {
            String clientString = "";
            try {
                clientString = " to " + Util.rfcIpRepr(socket.getInetAddress());
            } catch (Exception ex) {
                // ignore
            }
            if (errorWriting && keepSocketOpen) {
                keepSocketOpen = false;
                throw new RuntimeException(new HandleException(HandleException.INTERNAL_ERROR, "Error writing continuous handle response" + clientString, e));
            }
            if (e instanceof java.net.SocketTimeoutException || e.getCause() instanceof java.net.SocketTimeoutException) {
                // no stack trace
                logger.error("Exception sending response" + clientString);
            } else {
                logger.error("Exception sending response" + clientString, e);
            }
        } finally {
            if (out != null && !keepSocketOpen) {
                try {
                    out.close();
                } catch (Exception e) {
                    logger.error("Exception sending response", e);
                }
            }
        }
    }

    public void logAccess(String accessType, InetAddress clientAddr, int opCode, int rsCode, String logString, long time) {
        String msg = ((clientAddr == null) ? "" : Util.rfcIpRepr(clientAddr)) + " " + accessType + " \"" + getAccessLogDate() + "\" " + opCode + " " + rsCode + " " + time + "ms " + removeNewlines(logString);
        accessLogger.info(msg);
    }

    private final Calendar accessCal = Calendar.getInstance();

    private String getAccessLogDate() {
        StringBuffer sb = new StringBuffer(40);
        int tmpInt;
        synchronized (accessCal) {
            accessCal.setTimeInMillis(System.currentTimeMillis());
            sb.append(accessCal.get(Calendar.YEAR));

            sb.append('-');
            tmpInt = accessCal.get(Calendar.MONTH) + 1;
            if (tmpInt < 10) sb.append('0');
            sb.append(tmpInt);

            sb.append('-');
            tmpInt = accessCal.get(Calendar.DATE);
            if (tmpInt < 10) sb.append('0');
            sb.append(tmpInt);

            sb.append(' ');
            tmpInt = accessCal.get(Calendar.HOUR_OF_DAY);
            if (tmpInt < 10) sb.append('0');
            sb.append(tmpInt);

            sb.append(':');
            tmpInt = accessCal.get(Calendar.MINUTE);
            if (tmpInt < 10) sb.append('0');
            sb.append(tmpInt);

            sb.append(':');
            tmpInt = accessCal.get(Calendar.SECOND);
            if (tmpInt < 10) sb.append('0');
            sb.append(tmpInt);

            sb.append('.');
            tmpInt = accessCal.get(Calendar.MILLISECOND);
            if (tmpInt < 10) sb.append('0').append('0');
            else if (tmpInt < 100) sb.append('0');
            sb.append(tmpInt);

            tmpInt = (accessCal.get(Calendar.ZONE_OFFSET) + accessCal.get(Calendar.DST_OFFSET)) / 60000; // offset in minutes from UTC
            if (tmpInt < 0) {
                tmpInt *= -1;
                sb.append('-');
                int tzHours = tmpInt / 60;
                tmpInt = tmpInt % 60;
                if (tzHours < 10) sb.append('0');
                sb.append(tzHours);
                if (tmpInt < 10) sb.append(tmpInt);
                sb.append(tmpInt);
            } else if (tmpInt > 0) {
                sb.append('+');
                int tzHours = tmpInt / 60;
                tmpInt = tmpInt % 60;
                if (tzHours < 10) sb.append('0');
                sb.append(tzHours);
                if (tmpInt < 10) sb.append(tmpInt);
                sb.append(tmpInt);
            } else {
                sb.append('Z');
            }
        }
        return sb.toString();
    }

    private static String removeNewlines(String s) {
        return s.replace("\n", "\\n").replace("\r", "\\r");
    }
}
