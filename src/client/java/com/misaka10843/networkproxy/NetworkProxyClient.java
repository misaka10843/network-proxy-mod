package com.misaka10843.networkproxy;

import com.misaka10843.networkproxy.config.ProxyConfig;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.*;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

public class NetworkProxyClient implements ClientModInitializer {
    public static final String MOD_ID = "networkproxy";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final ConcurrentHashMap<URI, Integer> proxiedUrls = new ConcurrentHashMap<>();

    public static ProxyConfig getConfig() {
        return AutoConfig.getConfigHolder(ProxyConfig.class).getConfig();
    }

    @Override
    public void onInitializeClient() {
        AutoConfig.register(ProxyConfig.class, GsonConfigSerializer::new);
        Authenticator.setDefault(new AuthenticatorWrapper(Authenticator.getDefault()));

        LOGGER.info("NetworkProxy Client Initialized!");
    }

    public static boolean shouldProxyHost(String hostName) {
        ProxyConfig config = getConfig();
        if (!config.enabled) {
            return false;
        }
        if (!config.useFilter) {
            return true;
        }
        if (hostName == null || config.proxyDomains == null) {
            return false;
        }

        String lowerHost = hostName.toLowerCase(Locale.ROOT);
        for (String domain : config.proxyDomains) {
            if (domain != null && !domain.isBlank() && lowerHost.contains(domain.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    public static class AuthenticatorWrapper extends Authenticator {
        private final Authenticator original;

        public AuthenticatorWrapper(Authenticator authenticator) {
            this.original = authenticator;
        }

        @Override
        public PasswordAuthentication requestPasswordAuthenticationInstance(
                String host,
                InetAddress addr,
                int port,
                String protocol,
                String prompt,
                String scheme,
                URL url,
                RequestorType reqType
        ) {
            try {
                ProxyConfig config = NetworkProxyClient.getConfig();
                boolean isProxiedDownload = url != null
                        && proxiedUrls.getOrDefault(url.toURI(), 0) > 0
                        && config.enabled
                        && config.useForDownloads
                        && reqType == RequestorType.PROXY;

                if (isProxiedDownload) {
                    return super.requestPasswordAuthenticationInstance(host, addr, port, protocol, prompt, scheme, url, reqType);
                }
            } catch (URISyntaxException ignored) {
                // Fall back to the original authenticator below.
            }

            if (original != null) {
                return original.requestPasswordAuthenticationInstance(host, addr, port, protocol, prompt, scheme, url, reqType);
            }
            return null;
        }

        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            ProxyConfig config = NetworkProxyClient.getConfig();
            if (config.username == null || config.username.isEmpty()) {
                return null;
            }
            return new PasswordAuthentication(config.username, config.password == null ? new char[0] : config.password.toCharArray());
        }
    }
}
