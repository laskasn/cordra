package net.cnri.cordra.auth;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import net.cnri.cordra.auth.Authenticator.AuthenticationResponse;

public class AuthenticatorBackOff {

    private final Cache<String, BackOffState> backOffCache;
    private volatile boolean enabled;

    public AuthenticatorBackOff(boolean enabled) {
        backOffCache = CacheBuilder.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();
        this.enabled = enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void reportResult(String userId, AuthenticationResponse result) {
        if (userId == null) return;
        if (!enabled) return;
        if (result == AuthenticationResponse.SUCCESS) {
            backOffCache.invalidate(userId);
        } else if (result == AuthenticationResponse.FAILED)  {
            BackOffState backOffState;
            try {
                backOffState = backOffCache.get(userId, () -> {
                    return new BackOffState();
                });
                backOffState.incrementFails();
            } catch (ExecutionException e) {
                //cannot happen
            }
        }
    }

    public long calculateBackOffFor(String userId) {
        if (!enabled) return 0;
        BackOffState backOffState = backOffCache.getIfPresent(userId);
        if (backOffState == null) {
            return 0;
        }
        long now = System.currentTimeMillis();
        long backOff = calculateBackOff(backOffState.getFailAttempts());
        long timeSinceLastFail = now - backOffState.getLastFail();
        long backOffFromNow = backOff - timeSinceLastFail;
        if (backOffFromNow < 0) {
            backOffFromNow = 0;
        }
        return backOffFromNow;
    }

    private static long calculateBackOff(long failAttempts) {
        long backOff = (failAttempts * failAttempts) * 100;
        if (backOff > 5000) {
            backOff = 5000;
        }
        return backOff;
    }

    public static class BackOffState {
        private long lastFail = System.currentTimeMillis();
        private long failAttempts = 0;

        public synchronized void incrementFails() {
            failAttempts++;
            lastFail = System.currentTimeMillis();
        }

        public long getLastFail() {
            return lastFail;
        }

        public long getFailAttempts() {
            return failAttempts;
        }
    }
}
