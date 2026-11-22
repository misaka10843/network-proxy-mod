package com.misaka10843.networkproxy.config;

public class ProxySettings {
    public ProxyConfig.ProxyType type = ProxyConfig.ProxyType.SOCKS5;
    public boolean socks5RemoteDns = false;
    public String host = "127.0.0.1";
    public int port = 7890;
    public String username = "";
    public String password = "";

    public boolean usesRemoteDns() {
        return type == ProxyConfig.ProxyType.SOCKS5 && socks5RemoteDns;
    }

    void fillMissingValues() {
        if (type == null) {
            type = ProxyConfig.ProxyType.SOCKS5;
        }
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
        socks5RemoteDns = source.socks5RemoteDns;
        host = source.host;
        port = source.port;
        username = source.username;
        password = source.password;
    }
}
