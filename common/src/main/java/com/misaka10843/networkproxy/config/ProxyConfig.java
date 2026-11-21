package com.misaka10843.networkproxy.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Config(name = "networkproxy")
public class ProxyConfig implements ConfigData {
    public boolean enabled = false;
    public boolean useForDownloads = false;
    public boolean useFilter = false;

    public List<String> proxyDomains = new ArrayList<>();

    public ProxyType type = ProxyType.SOCKS5;
    public boolean socks5RemoteDns = false;
    public String host = "127.0.0.1";
    public int port = 7890;
    public String username = "";
    public String password = "";

    public boolean shouldUseSocks5RemoteDns(String host) {
        return socks5RemoteDns && type == ProxyType.SOCKS5 && shouldProxy(host);
    }

    public boolean shouldProxy(String host) {
        if (!enabled) {
            return false;
        }
        if (!useFilter) {
            return true;
        }
        if (proxyDomains == null) {
            return false;
        }

        String normalizedHost = host.toLowerCase(Locale.ROOT);
        return proxyDomains.stream().anyMatch(domain -> normalizedHost.contains(domain.toLowerCase(Locale.ROOT)));
    }

    public enum ProxyType {
        HTTP,
        SOCKS5
    }
}
