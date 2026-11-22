package com.misaka10843.networkproxy.config;

import java.net.InetSocketAddress;

public class ProxySettings {
    public ProxyConfig.ProxyType type = ProxyConfig.ProxyType.SOCKS5;
    public boolean enforceProxyForProfile;
    public Boolean resolveHostnamesThroughProxy;
    private Boolean socks5RemoteDns;
    public String host = "";
    public int port;
    public String username = "";
    public String password = "";

    public boolean usesRemoteDns() {
        return Boolean.TRUE.equals(resolveHostnamesThroughProxy);
    }

    public InetSocketAddress proxyAddress() {
        if (host == null || ServerProxySettings.normalizeHost(host.strip()).isEmpty() || port < 1 || port > 65535) {
            throw new IllegalStateException("Enter a valid proxy host and port in Network Proxy Settings");
        }
        return new InetSocketAddress(host.strip(), port);
    }

    void fillMissingValues() {
        if (type == null) {
            type = ProxyConfig.ProxyType.SOCKS5;
        }
        if (resolveHostnamesThroughProxy == null) {
            resolveHostnamesThroughProxy = type == ProxyConfig.ProxyType.SOCKS5 && Boolean.TRUE.equals(socks5RemoteDns);
        }
        socks5RemoteDns = null;
        if (host == null) {
            host = "";
        }
        if (username == null) {
            username = "";
        }
        if (password == null) {
            password = "";
        }
    }

    public void copyFrom(ProxySettings source) {
        type = source.type;
        enforceProxyForProfile = source.enforceProxyForProfile;
        resolveHostnamesThroughProxy = source.usesRemoteDns();
        host = source.host;
        port = source.port;
        username = source.username;
        password = source.password;
    }
}
