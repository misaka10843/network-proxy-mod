package com.misaka10843.networkproxy;

import com.google.gson.Gson;
import com.misaka10843.networkproxy.config.ProxyConfig;
import com.misaka10843.networkproxy.config.ProxySettings;
import com.misaka10843.networkproxy.config.ServerProxySettings;
import io.netty.handler.codec.http.HttpUtil;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;

public final class ProxyHostnameResolutionTest {
    private ProxyHostnameResolutionTest() {
    }

    public static void main(String[] args) throws Exception {
        checkMigration();
        checkProxyEndpoint();
        checkDestinations();
        checkEnforcementSelection();
        System.out.println("Proxy hostname resolution regression test passed");
    }

    private static void checkMigration() {
        Gson gson = new Gson();
        var config = gson.fromJson("""
                {
                  "type": "SOCKS5",
                  "host": "proxy.example",
                  "port": 7890,
                  "socks5RemoteDns": true,
                  "serverAddresses": [
                    {"address": "socks.example", "type": "SOCKS5", "socks5RemoteDns": true},
                    {"address": "http.example", "type": "HTTP", "socks5RemoteDns": true},
                    {"address": "remote.example", "type": "HTTP", "resolveHostnamesThroughProxy": true},
                    {"address": "local.example", "type": "SOCKS5", "socks5RemoteDns": true, "resolveHostnamesThroughProxy": false}
                  ]
                }
                """, ProxyConfig.class);
        config.validatePostLoad();
        require(config.host.equals("proxy.example") && config.port == 7890, "Keep saved proxy endpoints");
        require(config.usesRemoteDns(), "The previous SOCKS5 default should retain remote DNS");
        require(config.forServer("socks.example", 25565).usesRemoteDns(), "Preserve legacy SOCKS5 profiles");
        require(!config.forServer("http.example", 25565).usesRemoteDns(), "An inactive legacy HTTP flag must stay off");
        require(config.forServer("remote.example", 25565).usesRemoteDns(), "The new setting applies to HTTP");
        require(!config.forServer("local.example", 25565).usesRemoteDns(), "The new setting takes precedence");

        String saved = gson.toJson(config);
        require(saved.contains("\"resolveHostnamesThroughProxy\"") && !saved.contains("\"socks5RemoteDns\""),
                "Save only the unified hostname resolution setting");
    }

    private static void checkProxyEndpoint() {
        var settings = new ProxySettings();
        require(settings.host.isEmpty() && settings.port == 0, "New settings have no proxy endpoint");
        try {
            settings.proxyAddress();
            throw new AssertionError("An empty proxy endpoint must fail before connecting");
        } catch (IllegalStateException expected) {
            require(expected.getMessage().contains("proxy host and port"), "Explain the missing proxy endpoint");
        }
        settings.host = "127.0.0.1";
        settings.port = 4447;
        require(settings.proxyAddress().getPort() == 4447, "Use the configured endpoint");
    }

    private static void checkDestinations() throws Exception {
        var settings = new ProxySettings();
        int port = 25566;
        String hostname = "server.example";
        var resolved = new InetSocketAddress(InetAddress.getByAddress(hostname, new byte[] {127, 0, 0, 1}), port);
        var unresolved = InetSocketAddress.createUnresolved(hostname, port);

        for (var type : ProxyConfig.ProxyType.values()) {
            settings.type = type;
            var remote = ProxyConnection.proxyDestination(unresolved, settings, true);
            require(remote.isUnresolved() && remote.getHostString().equals(hostname) && remote.getPort() == port,
                    type + " must send the original hostname and port when remote DNS is on");

            var local = ProxyConnection.proxyDestination(resolved, settings, false);
            require(local.getHostString().equals(type == ProxyConfig.ProxyType.SOCKS5 ? hostname : "127.0.0.1")
                            && local.getPort() == port,
                    type + " must preserve the chosen IP and port when remote DNS is off");
            if (type == ProxyConfig.ProxyType.SOCKS5) {
                require(!local.isUnresolved() && local.getAddress().equals(resolved.getAddress()),
                        "SOCKS5 must receive a resolved address to encode the IP");
            }
        }

        settings.type = ProxyConfig.ProxyType.HTTP;
        var ipv6 = new InetSocketAddress(InetAddress.ofLiteral("2001:db8::1"), port);
        var destination = ProxyConnection.proxyDestination(ipv6, settings, false);
        require(HttpUtil.formatHostnameForHttp(destination).equals("[" + ipv6.getAddress().getHostAddress() + "]"),
                "HTTP CONNECT must bracket a locally resolved IPv6 address");
    }

    private static void checkEnforcementSelection() {
        var existing = new Gson().fromJson("{\"enforceProxy\":true}", ProxyConfig.class);
        require(existing.enforceProxy && !existing.enforceProxyForProfile,
                "Keep the saved global enforcement setting separate from the default proxy profile");

        var config = new ProxyConfig();
        config.enforceProxyForProfile = true;

        var protectedProfile = new ServerProxySettings();
        protectedProfile.address = "game.example:25566";
        protectedProfile.enforceProxyForProfile = true;
        var otherPorts = new ServerProxySettings();
        otherPorts.address = "game.example";
        config.serverAddresses = List.of(otherPorts, protectedProfile);

        var protectedRoute = ProxyRoute.select(config, "game.example", 25566, false);
        require(protectedRoute.enforced(), "A port-specific profile can enforce its own proxy");
        var otherRoute = ProxyRoute.select(config, "game.example", 25565, false);
        require(otherRoute.settings() != null && !otherRoute.enforced(),
                "Another profile can use a proxy without inheriting enforcement");
        var unmatched = ProxyRoute.select(config, "other.example", 25565, false);
        require(unmatched.settings() == null && !unmatched.enforced(),
                "The disabled default must leave unmatched addresses direct");

        config.enabled = true;
        require(ProxyRoute.select(config, "other.example", 25565, false).enforced(),
                "Enforcement on the default applies when the default proxy is selected");
        protectedProfile.enabled = false;
        var directProfile = ProxyRoute.select(config, "game.example", 25566, false);
        require(directProfile.settings() == null && !directProfile.enforced(),
                "An explicitly disabled profile is a direct route for independent connections");
        var transfer = ProxyRoute.select(config, "game.example", 25566, protectedRoute.enforced());
        require(transfer.settings() == null && transfer.enforced(),
                "An enforced transfer must not downgrade to the disabled route");
        var proxiedTransfer = ProxyRoute.select(config, "game.example", 25565, protectedRoute.enforced());
        require(proxiedTransfer.settings() != null && proxiedTransfer.enforced(),
                "A proxied transfer inherits enforcement from its original session");

        config.enabled = false;
        config.enforceProxy = true;
        var globallyBlocked = ProxyRoute.select(config, "other.example", 25565, false);
        require(globallyBlocked.settings() == null && globallyBlocked.enforced(),
                "Global enforcement must block addresses without a proxy route");
        var globallyProtected = ProxyRoute.select(config, "game.example", 25565, false);
        require(globallyProtected.settings() != null && globallyProtected.enforced(),
                "Global enforcement also applies to enabled profiles without their own flag");
        var globallyBlockedDirect = ProxyRoute.select(config, "game.example", 25566, false);
        require(globallyBlockedDirect.settings() == null && globallyBlockedDirect.enforced(),
                "Global enforcement must block explicitly disabled profiles");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
