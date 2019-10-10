package net.cnri.cordra.model;

import java.util.Objects;

public class HandleServerConfig {
    public Boolean enabled = false;
    public String listenAddress = null;
    public String externalAddress = null;
    public Integer tcpPort = null;
    public Integer externalTcpPort = null;
    public Boolean logAccesses = false;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HandleServerConfig that = (HandleServerConfig) o;
        return Objects.equals(enabled, that.enabled) &&
                Objects.equals(listenAddress, that.listenAddress) &&
                Objects.equals(externalAddress, that.externalAddress) &&
                Objects.equals(tcpPort, that.tcpPort) &&
                Objects.equals(externalTcpPort, that.externalTcpPort) &&
                Objects.equals(logAccesses, that.logAccesses);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, listenAddress, externalAddress, tcpPort, externalTcpPort, logAccesses);
    }

    public static HandleServerConfig getDefaultNewCordraConfig() {
        HandleServerConfig res = new HandleServerConfig();
        res.enabled = true;
        res.listenAddress = null;
        res.tcpPort = 2641;
        res.externalAddress = "127.0.0.1";
        res.externalTcpPort = 2641;
        return res;
    }
}
