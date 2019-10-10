package net.cnri.cordra.sync.local;

import java.util.Map;
import java.util.AbstractMap.SimpleEntry;
import java.util.concurrent.TimeUnit;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import net.cnri.cordra.sync.KeyPairAuthJtiChecker;

public class MemoryKeyPairAuthJtiChecker implements KeyPairAuthJtiChecker {

    private final Cache<Map.Entry<String, String>, Long> issJtiExpCache = CacheBuilder.newBuilder()
        .expireAfterWrite(1, TimeUnit.HOURS)
        .build();

    @Override
    public boolean check(String iss, String jti, long exp, long now) {
        Map.Entry<String, String> key = new SimpleEntry<>(iss, jti);
        Long oldExp = issJtiExpCache.asMap().putIfAbsent(key, exp);
        if (oldExp == null) return true;
        if (oldExp.longValue() >= now) return false; // old value not yet expired
        if (issJtiExpCache.asMap().replace(key, oldExp, exp)) return true;
        return false; // another caller used this jti before we did
    }

}
