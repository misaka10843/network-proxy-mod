package com.misaka10843.networkproxy.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;

import java.util.ArrayList;
import java.util.List;

@Config(name = "networkproxy")
public class ProxyConfig implements ConfigData {
    public boolean enabled = false;

    public boolean useFilter = false;

    public List<String> proxyDomains = new ArrayList<>();

    public ProxyType type = ProxyType.SOCKS5;
    public String host = "127.0.0.1";
    public int port = 7890;
    public String username = "";
    public String password = "";

    public enum ProxyType {
        HTTP,
        SOCKS5
    }
}