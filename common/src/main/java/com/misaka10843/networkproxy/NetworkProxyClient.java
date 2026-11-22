package com.misaka10843.networkproxy;

import com.misaka10843.networkproxy.config.ProxyConfig;
import com.misaka10843.networkproxy.config.ProxySettings;
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
    private static final int ENFORCED_DOWNLOAD_TIMEOUT_MILLIS = 30_000;
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
        ProxyRoute route = ProxyRoute.current();
        if (route != null && route.enforced()) {
            ProxySettings settings = route.settings();
            InetSocketAddress endpoint = route.enforcedEndpoint();
            if (settings.type == ProxyConfig.ProxyType.SOCKS5 && settings.usesRemoteDns()) {
                throw new IllegalStateException("Cannot download a server resource pack through SOCKS5 without local DNS");
            }
            return withDownloadProxy(settings, endpoint, download);
        }
        return withDownloadProxy(getConfig(), defaultProxy, download);
    }

    static <T> T withDownloadProxy(ProxyConfig config, Proxy defaultProxy, Function<Proxy, T> download) {
        if (!config.enabled) {
            return ScopedValue.where(DOWNLOAD_AUTHENTICATION, Optional.empty()).call(() -> download.apply(defaultProxy));
        }

        return withDownloadProxy(config, config.proxyAddress(), download);
    }

    private static <T> T withDownloadProxy(ProxySettings settings, InetSocketAddress endpoint,
                                           Function<Proxy, T> download) {
        Proxy.Type type = settings.type == ProxyConfig.ProxyType.SOCKS5 ? Proxy.Type.SOCKS : Proxy.Type.HTTP;
        Proxy proxy = new Proxy(type, endpoint);
        String username = settings.username;
        PasswordAuthentication credentials = username == null || username.isEmpty() ? null
                : new PasswordAuthentication(username, settings.password.toCharArray());
        var authentication = new DownloadAuthentication(endpoint, settings.type, credentials);
        return ScopedValue.where(DOWNLOAD_AUTHENTICATION, Optional.of(authentication)).call(() -> download.apply(proxy));
    }

    public static void configureDownloadAuthentication(URLConnection connection) {
        var authentication = DOWNLOAD_AUTHENTICATION.orElse(Optional.empty());
        if (connection instanceof HttpURLConnection http && authentication.isPresent()) {
            ProxyRoute route = ProxyRoute.current();
            if (route != null && route.enforced()) {
                http.setConnectTimeout(ENFORCED_DOWNLOAD_TIMEOUT_MILLIS);
                http.setReadTimeout(ENFORCED_DOWNLOAD_TIMEOUT_MILLIS);
            }
            if (authentication.get().type() == ProxyConfig.ProxyType.HTTP) {
                // A connection-specific authenticator also isolates the JDK's HTTP authentication cache.
                http.setAuthenticator(new AuthenticatorWrapper(Authenticator.getDefault(), authentication.get()));
            }
        }
    }

    public static URLConnection openDownloadThroughHttpsProxy(URL url) {
        var authentication = DOWNLOAD_AUTHENTICATION.orElse(Optional.empty()).orElse(null);
        if (authentication == null || authentication.type() != ProxyConfig.ProxyType.HTTPS) {
            return null;
        }
        return new HttpsProxyDownloadConnection(url, authentication.endpoint(), authentication.credentials());
    }

    private record DownloadAuthentication(InetSocketAddress endpoint, ProxyConfig.ProxyType type,
                                          PasswordAuthentication credentials) {
        PasswordAuthentication forRequest(String host, InetAddress address, int port, String protocol,
                                          URL url, Authenticator.RequestorType requestor) {
            if (credentials == null || port != endpoint.getPort()) {
                return null;
            }
            if (type == ProxyConfig.ProxyType.SOCKS5) {
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
                    || (httpAuthentication == null && authentication.type() == ProxyConfig.ProxyType.SOCKS5))) {
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
