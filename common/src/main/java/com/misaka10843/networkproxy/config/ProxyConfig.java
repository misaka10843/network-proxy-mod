package com.misaka10843.networkproxy.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Plain config POJO, serialized to {@code config/networkproxy.json} with Gson.
 *
 * <p>Field names intentionally match the old AutoConfig layout so existing
 * config files keep working after dropping the Cloth Config dependency.</p>
 */
public class ProxyConfig {
    public boolean enabled = false;
    public boolean useForDownloads = false;
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
