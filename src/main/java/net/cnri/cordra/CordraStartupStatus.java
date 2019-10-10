package net.cnri.cordra;

import java.util.*;

public class CordraStartupStatus {

    public static enum State { STARTING, UP, FAILED, NO_ATTEMPT }

    public State state = State.STARTING;
    public Map<String, State> details = new LinkedHashMap<>();

    public CordraStartupStatus() { }

    public void setStartingToFailed() {
        for (String key : details.keySet()) {
            if (details.get(key) == State.STARTING) {
                details.put(key, State.FAILED);
            }
        }
    }

    private static CordraStartupStatus instance = null;

    public static CordraStartupStatus getInstance() {
        if (instance == null) {
            instance = new CordraStartupStatus();
            return instance;
        } else {
            return instance;
        }
    }
}
