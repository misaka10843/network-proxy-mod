package com.misaka10843.networkproxy.config;

public class ProxySettings {
    public ProxyConfig.ProxyType type = ProxyConfig.ProxyType.SOCKS5;
    public Boolean resolveHostnamesThroughProxy;
    private Boolean socks5RemoteDns;
    public String host = "127.0.0.1";
    public int port = 7890;
    public String username = "";
    public String password = "";

    public boolean usesRemoteDns() {
        return Boolean.TRUE.equals(resolveHostnamesThroughProxy);
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
        resolveHostnamesThroughProxy = source.usesRemoteDns();
        host = source.host;
        port = source.port;
        username = source.username;
        password = source.password;
    }
}
