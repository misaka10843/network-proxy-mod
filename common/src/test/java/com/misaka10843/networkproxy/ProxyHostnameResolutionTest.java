package com.misaka10843.networkproxy;

import com.google.gson.Gson;
import com.misaka10843.networkproxy.config.ProxyConfig;
import com.misaka10843.networkproxy.config.ProxySettings;
import com.misaka10843.networkproxy.config.RealmsProxySettings;
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
        checkProxyDnsDefaults();
        checkProxyEndpoint();
        checkDestinations();
        checkEnforcementSelection();
        checkRealmsSelection();
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
                    {"address": "legacy-local.example", "type": "SOCKS5", "socks5RemoteDns": false},
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
        require(!config.forServer("legacy-local.example", 25565).usesRemoteDns(), "Preserve an explicit legacy SOCKS5 opt-out");
        require(config.forServer("remote.example", 25565).usesRemoteDns(), "The new setting applies to HTTP");
        require(!config.forServer("local.example", 25565).usesRemoteDns(), "The new setting takes precedence");

        String saved = gson.toJson(config);
        require(saved.contains("\"resolveHostnamesThroughProxy\"") && !saved.contains("\"socks5RemoteDns\""),
                "Save only the unified hostname resolution setting");
    }

    private static void checkProxyDnsDefaults() {
        var gson = new Gson();
        var config = gson.fromJson("""
                {
                  "serverAddresses": [
                    {"address": "socks.example"},
                    {"address": "local.example", "resolveHostnamesThroughProxy": false},
                    {"address": "https.example", "type": "HTTPS"}
                  ]
                }
                """, ProxyConfig.class);
        config.validatePostLoad();
        require(config.usesRemoteDns() && config.realms.usesRemoteDns(),
                "Default and custom Realms SOCKS5 profiles must delegate hostname resolution");
        require(config.forServer("socks.example", 25565).usesRemoteDns(), "New SOCKS5 address profiles default to proxy DNS");
        require(!config.forServer("local.example", 25565).usesRemoteDns(), "Keep an explicitly disabled SOCKS5 setting");
        require(!config.forServer("https.example", 25565).usesRemoteDns(), "HTTPS must keep local DNS unless enabled");

        var copy = new ProxySettings();
        copy.copyFrom(config.forServer("socks.example", 25565));
        copy.type = ProxyConfig.ProxyType.HTTP;
        require(!copy.usesRemoteDns(), "An automatic setting follows changes to the proxy type");
        copy.type = ProxyConfig.ProxyType.SOCKS5;
        require(copy.usesRemoteDns(), "Switching back to SOCKS5 restores its DNS default");
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
        require(existing.enforceProxy, "Keep saved global enforcement");

        var config = new ProxyConfig();

        var protectedProfile = new ServerProxySettings();
        protectedProfile.address = "game.example:25566";
        var otherPorts = new ServerProxySettings();
        otherPorts.address = "game.example";
        config.serverAddresses = List.of(otherPorts, protectedProfile);

        var saved = new Gson().toJson(config);
        var loaded = new Gson().fromJson(saved, ProxyConfig.class);
        var protectedRoute = ProxyRoute.select(loaded, "game.example", 25566, false);
        require(protectedRoute.enforced(), "An enabled port-specific profile must require its proxy after reload");
        var otherRoute = ProxyRoute.select(config, "game.example", 25565, false);
        require(otherRoute.settings() != null && otherRoute.enforced(),
                "An enabled hostname-only profile also requires its proxy");
        var unmatched = ProxyRoute.select(config, "other.example", 25565, false);
        require(unmatched.settings() == null && !unmatched.enforced(),
                "The disabled default must leave unmatched addresses direct");

        config.enabled = true;
        var defaultRoute = ProxyRoute.select(config, "other.example", 25565, false);
        require(defaultRoute.settings() != null && !defaultRoute.enforced(),
                "The default proxy only enforces when the global switch is enabled");
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
        config.enabled = true;
        var globallyProtected = ProxyRoute.select(config, "other.example", 25565, false);
        require(globallyProtected.settings() != null && globallyProtected.enforced(),
                "Global enforcement also requires the selected default proxy");
        var globallyBlockedDirect = ProxyRoute.select(config, "game.example", 25566, false);
        require(globallyBlockedDirect.settings() == null && globallyBlockedDirect.enforced(),
                "Global enforcement must block explicitly disabled profiles");
    }

    private static void checkRealmsSelection() {
        var gson = new Gson();
        var config = gson.fromJson("{\"enforceProxy\":true,\"realms\":null}", ProxyConfig.class);
        config.validatePostLoad();
        require(ProxyRoute.selectRealms(config.realms) == null,
                "Older configurations keep the original Realms route independently of multiplayer enforcement");

        config.realms.mode = RealmsProxySettings.Mode.DIRECT;
        var direct = ProxyRoute.selectRealms(config.realms);
        require(direct != null && direct.settings() == null && !direct.enforced(),
                "Direct mode explicitly bypasses proxy selection for the Realms game connection");

        config.realms.mode = RealmsProxySettings.Mode.CUSTOM;
        config.realms.host = "127.0.0.1";
        config.realms.port = 4447;
        config.realms.resolveHostnamesThroughProxy = true;
        var loaded = gson.fromJson(gson.toJson(config), ProxyConfig.class);
        loaded.validatePostLoad();
        var custom = ProxyRoute.selectRealms(loaded.realms);
        loaded.realms.port = 5555;
        require(custom.enforced() && custom.settings().usesRemoteDns() && custom.enforcedEndpoint().getPort() == 4447,
                "A custom Realms route requires its saved proxy and snapshots the remote DNS policy");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
