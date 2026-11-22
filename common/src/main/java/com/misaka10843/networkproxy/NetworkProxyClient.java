package com.misaka10843.networkproxy;

import com.misaka10843.networkproxy.config.ProxyConfig;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.util.Optional;
import java.util.function.Function;

public class NetworkProxyClient {
    public static final String MOD_ID = "networkproxy";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final ScopedValue<Optional<DownloadAuthentication>> DOWNLOAD_AUTHENTICATION =
            ScopedValue.newInstance();

    public static ProxyConfig getConfig() {
        return AutoConfig.getConfigHolder(ProxyConfig.class).getConfig();
    }

    public static void initialize() {
        AutoConfig.register(ProxyConfig.class, GsonConfigSerializer::new);

        LOGGER.info("NetworkProxy Client Initialized!");
        Authenticator.setDefault(new AuthenticatorWrapper(Authenticator.getDefault(), null));
    }

    public static <T> T withDownloadProxy(Proxy defaultProxy, Function<Proxy, T> download) {
        ProxyConfig config = getConfig();
        if (!config.enabled || !config.useForDownloads) {
            return ScopedValue.where(DOWNLOAD_AUTHENTICATION, Optional.empty()).call(() -> download.apply(defaultProxy));
        }

        InetSocketAddress endpoint = new InetSocketAddress(config.host, config.port);
        Proxy.Type type = config.type == ProxyConfig.ProxyType.HTTP ? Proxy.Type.HTTP : Proxy.Type.SOCKS;
        Proxy proxy = new Proxy(type, endpoint);
        String username = config.username;
        PasswordAuthentication credentials = username == null || username.isEmpty() ? null
                : new PasswordAuthentication(username, config.password.toCharArray());
        var authentication = new DownloadAuthentication(endpoint, type, credentials);
        return ScopedValue.where(DOWNLOAD_AUTHENTICATION, Optional.of(authentication)).call(() -> download.apply(proxy));
    }

    public static void configureDownloadAuthentication(URLConnection connection) {
        var authentication = DOWNLOAD_AUTHENTICATION.orElse(Optional.empty());
        if (connection instanceof HttpURLConnection http && authentication.isPresent()
                && authentication.get().type() == Proxy.Type.HTTP) {
            // A connection-specific authenticator also isolates the JDK's HTTP authentication cache.
            http.setAuthenticator(new AuthenticatorWrapper(Authenticator.getDefault(), authentication.get()));
        }
    }

    private record DownloadAuthentication(InetSocketAddress endpoint, Proxy.Type type,
                                          PasswordAuthentication credentials) {
        PasswordAuthentication forRequest(String host, InetAddress address, int port, String protocol,
                                          URL url, Authenticator.RequestorType requestor) {
            if (credentials == null || port != endpoint.getPort()) {
                return null;
            }
            if (type == Proxy.Type.SOCKS) {
                // The JDK SOCKS5 handshake uses the authenticator overload without a URL or requestor type.
                if (url != null || !"SOCKS5".equalsIgnoreCase(protocol)) {
                    return null;
                }
            } else if (requestor != Authenticator.RequestorType.PROXY
                    || (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol))) {
                return null;
            }
            return endpoint.getHostString().equalsIgnoreCase(host)
                    || (address != null && address.equals(endpoint.getAddress())) ? credentials : null;
        }
    }

    private static final class AuthenticatorWrapper extends Authenticator {
        private final Authenticator original;
        private final DownloadAuthentication httpAuthentication;

        AuthenticatorWrapper(Authenticator original, DownloadAuthentication httpAuthentication) {
            this.original = original;
            this.httpAuthentication = httpAuthentication;
        }

        @Override
        public PasswordAuthentication requestPasswordAuthenticationInstance(String host, InetAddress address, int port,
                                                                               String protocol, String prompt, String scheme,
                                                                               URL url, RequestorType requestor) {
            var authentication = DOWNLOAD_AUTHENTICATION.orElse(Optional.empty()).orElse(null);
            if (authentication != null && (authentication == httpAuthentication
                    || (httpAuthentication == null && authentication.type() == Proxy.Type.SOCKS))) {
                var credentials = authentication.forRequest(host, address, port, protocol, url, requestor);
                if (credentials != null) {
                    return credentials;
                }
            }
            return original == null ? null
                    : original.requestPasswordAuthenticationInstance(host, address, port, protocol, prompt, scheme, url, requestor);
        }

        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            return requestPasswordAuthenticationInstance(getRequestingHost(), getRequestingSite(), getRequestingPort(),
                    getRequestingProtocol(), getRequestingPrompt(), getRequestingScheme(), getRequestingURL(), getRequestorType());
        }
    }
}
