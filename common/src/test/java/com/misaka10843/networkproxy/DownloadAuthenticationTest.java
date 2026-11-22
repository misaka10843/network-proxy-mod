package com.misaka10843.networkproxy;

import com.misaka10843.networkproxy.config.ProxyConfig;
import com.misaka10843.networkproxy.config.ServerProxySettings;

import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

public final class DownloadAuthenticationTest {
    private static final String PROXY_HOST = "127.0.0.1";
    private static final int PROXY_PORT = 4444;
    private static final String TEST_USERNAME = "dummy-user";
    private static final String TEST_PASSWORD = "dummy-password";

    private DownloadAuthenticationTest() {
    }

    public static void main(String[] args) throws Exception {
        var config = new ProxyConfig();
        config.enabled = true;
        config.type = ProxyConfig.ProxyType.HTTP;
        config.host = PROXY_HOST;
        config.port = PROXY_PORT;
        config.username = TEST_USERNAME;
        config.password = TEST_PASSWORD;

        var connection = new DummyConnection(URI.create("http://origin.invalid/resource-pack.zip").toURL());
        var originalRoute = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(PROXY_HOST, PROXY_PORT + 1));
        NetworkProxyClient.withDownloadProxy(new ProxyConfig(), originalRoute, proxy -> {
            require(proxy == originalRoute, "Disabling the default proxy preserves Minecraft's download route");
            return null;
        });
        NetworkProxyClient.withDownloadProxy(config, Proxy.NO_PROXY, proxy -> {
            require(proxy.type() == Proxy.Type.HTTP && proxy.address().equals(config.proxyAddress()),
                    "Enabling the default proxy also routes web downloads through that proxy");
            NetworkProxyClient.configureDownloadAuthentication(connection);
            var proxyCredentials = connection.authenticate(PROXY_HOST, PROXY_PORT, Authenticator.RequestorType.PROXY);
            require(proxyCredentials != null && TEST_USERNAME.equals(proxyCredentials.getUserName())
                            && Arrays.equals(TEST_PASSWORD.toCharArray(), proxyCredentials.getPassword()),
                    "The configured proxy should receive its test credentials");

            require(connection.authenticate("origin.invalid", 80, Authenticator.RequestorType.SERVER) == null,
                    "The origin server must not receive proxy credentials");
            require(connection.authenticate(PROXY_HOST, PROXY_PORT, Authenticator.RequestorType.SERVER) == null,
                    "An origin request must not receive credentials even if it names the proxy endpoint");
            require(connection.authenticate(PROXY_HOST, PROXY_PORT + 1, Authenticator.RequestorType.PROXY) == null,
                    "A different proxy endpoint must not receive credentials");
            return null;
        });

        require(connection.authenticate(PROXY_HOST, PROXY_PORT, Authenticator.RequestorType.PROXY) == null,
                "The connection authenticator must stop supplying credentials when the download scope ends");
        checkEnforcedDownloads(config);
        System.out.println("Download authentication regression test passed");
    }

    private static void checkEnforcedDownloads(ProxyConfig config) throws Exception {
        config.enabled = false;
        var profile = new ServerProxySettings();
        profile.copyFrom(config);
        profile.address = "game.invalid";
        config.serverAddresses = List.of(profile);
        var route = ProxyRoute.select(config, "game.invalid", 25565, false);
        profile.enabled = false;

        route.run(() -> NetworkProxyClient.withDownloadProxy(Proxy.NO_PROXY, proxy -> {
            require(proxy.type() == Proxy.Type.HTTP && proxy.address().equals(route.enforcedEndpoint()),
                    "Server downloads must retain the per-address route even when the profile is later disabled");
            return null;
        }));

        var transfer = ProxyRoute.select(config, "transfer.invalid", 25565, route.enforced());
        require(transfer.enforced(), "A server-directed transfer must inherit enforcement");
        try {
            transfer.enforcedEndpoint();
            throw new AssertionError("A missing transfer route must fail");
        } catch (IllegalStateException expected) {
            require(expected.getMessage().contains("requires an enabled proxy"), "Explain the blocked transfer");
        }

        config.enabled = true;
        config.enforceProxy = true;
        config.type = ProxyConfig.ProxyType.SOCKS5;
        config.resolveHostnamesThroughProxy = true;
        var socks = ProxyRoute.select(config, "socks.invalid", 25565, false);
        var pack = URI.create("http://pack.invalid/resource-pack.zip").toURL();
        socks.run(() -> NetworkProxyClient.withDownloadProxy(Proxy.NO_PROXY, proxy -> {
            require(proxy.type() == Proxy.Type.SOCKS, "Enforced SOCKS downloads retain the selected proxy type");
            require(NetworkProxyClient.openProxiedDownload(pack) instanceof ProxiedDownloadConnection,
                    "Enforced SOCKS downloads with remote DNS must use the unresolved destination transport");
            return null;
        }));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class DummyConnection extends HttpURLConnection {
        private Authenticator authenticator;

        DummyConnection(URL url) {
            super(url);
        }

        @Override
        public void setAuthenticator(Authenticator authenticator) {
            this.authenticator = authenticator;
        }

        PasswordAuthentication authenticate(String host, int port, Authenticator.RequestorType type) {
            require(authenticator != null, "The download should install a connection-specific authenticator");
            InetAddress address = PROXY_HOST.equals(host) ? InetAddress.ofLiteral(PROXY_HOST) : null;
            return authenticator.requestPasswordAuthenticationInstance(host, address, port, "http",
                    "Test authentication", "Basic", getURL(), type);
        }

        @Override
        public void connect() {
            throw new AssertionError("The dummy connection must not open a network socket");
        }

        @Override
        public void disconnect() {
        }

        @Override
        public boolean usingProxy() {
            return true;
        }
    }
}
