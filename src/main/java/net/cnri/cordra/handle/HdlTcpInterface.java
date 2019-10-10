package net.cnri.cordra.handle;

import net.cnri.cordra.model.HandleServerConfig;
import net.cnri.util.GrowBeforeTransferQueueThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;

public class HdlTcpInterface implements Runnable {
    private static Logger logger = LoggerFactory.getLogger(HdlTcpInterface.class);

    private InetAddress bindAddress;
    private int bindPort = 2641;
    private boolean logAccesses = false;
    private final int maxHandlers = 200;
    private final int numThreads = 10;
    private final int maxIdleTime = 5 * 60 * 1000;

    private ServerSocket socket = null;
    private volatile boolean keepServing = true;
    private LightWeightHandleServer server;
    private volatile boolean keepRunning = true;
    private ExecutorService handlerPool = null;

    public HdlTcpInterface(LightWeightHandleServer server, HandleServerConfig config) throws Exception {
        super();
        this.server = server;
        init(config);
    }

    @Override
    public final void run() {
        while (keepRunning) {
            try {
                serveRequests();
            } catch (Throwable t) {
                if (!keepRunning) return;
                logger.error("Error establishing interface", t);
            }
            ExecutorService pool = handlerPool;
            if (pool != null) pool.shutdown();
            stopService();
            try {
                // sleep for about 5 minutes then try to bind again
                if (keepRunning) {
                    Thread.sleep(300000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ThreadDeath e) {
                // ignore
            } catch (Throwable t) {
                logger.error("Error sleeping!: " + t);
            }
        }
    }

    public void stopRunning() {
        keepRunning = false;
        stopService();
        ExecutorService pool = handlerPool;
        if (pool != null) {
            pool.shutdown();
            boolean terminated = false;
            try {
                terminated = pool.awaitTermination(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (!terminated) {
                pool.shutdownNow();
                try {
                    terminated = pool.awaitTermination(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        logger.info("Handle TCP Request Listener stopped");
    }

    private void stopService() {
        keepServing = false;
        try {
            socket.close();
        } catch (Exception e) {
        }
    }

    public void serveRequests() {
        keepServing = true;
        try {
            if (bindAddress == null) {
                socket = new ServerSocket(bindPort, -1);
            } else {
                socket = new ServerSocket(bindPort, -1, bindAddress);
            }
        } catch (Exception e) {
            logger.error("Error setting up server socket:", e);
            return;
        }
        System.out.println("Initializing Handle TCP interface on port " + bindPort);
        logger.info("Initializing Handle TCP interface on port " + bindPort);
        handlerPool = new GrowBeforeTransferQueueThreadPoolExecutor(numThreads, maxHandlers, 1, TimeUnit.MINUTES, new LinkedTransferQueue<>());
        try {
            System.out.flush();
        } catch (Exception e) {
        }
        long recvTime = 0;
        while (keepServing) {
            try {
                @SuppressWarnings("resource")
                Socket newsock = socket.accept();
                newsock.setSoTimeout(maxIdleTime);
                recvTime = System.currentTimeMillis();
                handlerPool.execute(new HdlTcpRequestHandler(server, logAccesses, newsock, recvTime));
            } catch (Exception e) {
                if (keepServing) {
                    logger.error("Error handling request", e);
                }
            }
        }
        try {
            socket.close();
        } catch (Exception e) {
        }
    }

    private void init(HandleServerConfig config) throws Exception {
        Object bindAddressStr = config.listenAddress; // get the specific IP address to bind to, if any.
        if (bindAddressStr == null) {
            bindAddress = null;
        } else {
            bindAddress = InetAddress.getByName(String.valueOf(bindAddressStr));
        }
        bindPort = config.tcpPort; // get the port to listen on...
        if (config.logAccesses != null) {
            logAccesses = config.logAccesses;
        } else {
            logAccesses = false;
        }
    }
}
