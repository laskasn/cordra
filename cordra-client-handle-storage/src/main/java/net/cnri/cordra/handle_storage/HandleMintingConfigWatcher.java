package net.cnri.cordra.handle_storage;

import com.google.gson.Gson;
import net.cnri.cordra.api.CordraClient;
import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.api.CordraObject;
import net.handle.hdllib.GsonUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HandleMintingConfigWatcher {

    private static Logger logger = LoggerFactory.getLogger(HandleMintingConfigWatcher.class);

    private final Gson gson = GsonUtility.getGson();
    private HandleValuesGenerator handleValueGenerator;
    private ScheduledExecutorService designExecServ = null;
    private CordraClient cordra;

    public HandleMintingConfigWatcher(CordraClient cordra, HandleValuesGenerator handleValueGenerator) {
        this.cordra = cordra;
        this.handleValueGenerator = handleValueGenerator;
    }

    public void start() {
        designExecServ = Executors.newSingleThreadScheduledExecutor();
        designExecServ.scheduleAtFixedRate(this::loadMintingConfig, 0, 1, TimeUnit.MINUTES);
    }

    private void loadMintingConfig() {
        try {
            CordraObject designObject = cordra.get("design");
            HandleMintingConfig newConfig = gson.fromJson(designObject.content.getAsJsonObject().get("handleMintingConfig"), HandleMintingConfig.class);
            handleValueGenerator.setConfig(newConfig);
        } catch (CordraException e) {
            logger.info("Error loading HandleMintingConfig from design. Config not updated: ", e);
            if (handleValueGenerator.getConfig() == null) handleValueGenerator.setConfig(new HandleMintingConfig());
        }
    }

    public void shutdown() {
        designExecServ.shutdown();
        try {
            designExecServ.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
