package com.misaka10843.networkproxy;

import com.misaka10843.networkproxy.config.ConfigManager;
import com.misaka10843.networkproxy.config.ProxyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Authenticator;
import java.net.InetAddress;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loader-agnostic core of the mod.
 *
 * <p>Lives in the shared {@code common} source set so both the Fabric and the
 * NeoForge builds compile the very same classes against the deobfuscated
 * Minecraft 26.x sources (no remapping is needed anymore).</p>
 */
public final class NetworkProxy {
    public static final String MOD_ID = "networkproxy";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** URIs currently being downloaded through our proxy (used by the authenticator). */
    public static final ConcurrentHashMap<URI, Integer> proxiedUrls = new ConcurrentHashMap<>();

    private static ProxyConfig config;
    private static boolean initialized;

    private NetworkProxy() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        Authenticator.setDefault(new AuthenticatorWrapper(Authenticator.getDefault()));
        LOGGER.info("NetworkProxy initialized");
    }

    /** Lazily loaded so we never touch {@code Minecraft.getInstance()} too early. */
    public static ProxyConfig getConfig() {
        if (config == null) {
            config = ConfigManager.load();
        }
        return config;
    }

    public static void saveConfig() {
        ConfigManager.save(getConfig());
    }

    /**
     * Supplies proxy credentials only for requests that we routed through the
     * proxy ourselves, and delegates everything else to the previous default
     * authenticator (e.g. Mojang's for Realms/skins).
     */
    public static class AuthenticatorWrapper extends Authenticator {

        private final Authenticator original;

        public AuthenticatorWrapper(Authenticator original) {
            this.original = original;
        }

        @Override
        public PasswordAuthentication requestPasswordAuthenticationInstance(String host, InetAddress addr, int port,
                                                                            String protocol, String prompt, String scheme,
                                                                            URL url, RequestorType reqType) {
            try {
                ProxyConfig config = NetworkProxy.getConfig();
                if (proxiedUrls.getOrDefault(url.toURI(), 0) > 0 && (config.enabled && config.useForDownloads)) {
                    return super.requestPasswordAuthenticationInstance(host, addr, port, protocol, prompt, scheme, url, reqType);
                } else {
                    return original.requestPasswordAuthenticationInstance(host, addr, port, protocol, prompt, scheme, url, reqType);
                }
            } catch (Exception e) {
                return original.requestPasswordAuthenticationInstance(host, addr, port, protocol, prompt, scheme, url, reqType);
            }
        }

        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            ProxyConfig config = NetworkProxy.getConfig();
            return new PasswordAuthentication(config.username, config.password.toCharArray());
        }
    }
}
