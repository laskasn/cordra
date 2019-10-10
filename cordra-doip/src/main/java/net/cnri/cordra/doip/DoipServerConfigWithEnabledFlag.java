package net.cnri.cordra.doip;

import java.util.Objects;

import net.dona.doip.server.DoipServerConfig;

public class DoipServerConfigWithEnabledFlag extends DoipServerConfig {
    public Boolean enabled;

    public DoipServerConfigWithEnabledFlag() {
        this.numThreads = 20;
    }

    public static DoipServerConfigWithEnabledFlag getDefaultNewCordraConfig() {
        DoipServerConfigWithEnabledFlag res = new DoipServerConfigWithEnabledFlag();
        res.enabled = true;
        res.port = 9000;
        return res;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Objects.hash(enabled);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!super.equals(obj)) return false;
        if (getClass() != obj.getClass()) return false;
        DoipServerConfigWithEnabledFlag other = (DoipServerConfigWithEnabledFlag) obj;
        return Objects.equals(enabled, other.enabled);
    }
}
