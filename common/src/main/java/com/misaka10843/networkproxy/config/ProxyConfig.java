package com.misaka10843.networkproxy.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Config(name = "networkproxy")
public class ProxyConfig extends ProxySettings implements ConfigData {
    public boolean enabled = false;
    public boolean useForDownloads = false;
    public boolean useFilter = false;

    public List<String> proxyDomains = new ArrayList<>();
    public volatile List<ServerProxySettings> serverAddresses;
    public Boolean configuredAddressesOnly;

    @Override
    public void validatePostLoad() {
        fillMissingValues();
        if (configuredAddressesOnly == null) {
            configuredAddressesOnly = useFilter;
        }
        if (serverAddresses != null) {
            var entries = new ArrayList<ServerProxySettings>();
            for (ServerProxySettings entry : serverAddresses) {
                if (entry != null) {
                    entry.fillMissingValues();
                    if (entry.address == null) {
                        entry.address = "";
                        entry.legacyDomainMatch = false;
                    }
                    entries.add(entry);
                }
            }
            serverAddresses = entries;
            return;
        }
        serverAddresses = new ArrayList<>();
        if (useFilter && proxyDomains != null) {
            for (String domain : proxyDomains) {
                if (domain == null) {
                    continue;
                }
                ServerProxySettings settings = new ServerProxySettings();
                settings.copyFrom(this);
                settings.enabled = enabled;
                settings.address = domain;
                settings.legacyDomainMatch = true;
                serverAddresses.add(settings);
            }
        }
    }

    public ProxySettings forServer(String host, int port) {
        ServerProxySettings match = null;
        int bestMatch = 0;
        var entries = serverAddresses;
        if (entries != null) {
            for (ServerProxySettings entry : entries) {
                int score = entry == null ? 0 : entry.match(host, port);
                if (score > bestMatch) {
                    match = entry;
                    bestMatch = score;
                }
            }
        }
        if (match != null) {
            return match.enabled ? match : null;
        }
        return enabled && !Boolean.TRUE.equals(configuredAddressesOnly) ? this : null;
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
        return proxyDomains.stream().anyMatch(domain -> domain != null && normalizedHost.contains(domain.toLowerCase(Locale.ROOT)));
    }

    public enum ProxyType {
        HTTP,
        HTTPS,
        SOCKS5
    }
}
