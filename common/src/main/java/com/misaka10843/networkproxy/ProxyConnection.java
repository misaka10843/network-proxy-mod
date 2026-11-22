package com.misaka10843.networkproxy;

import com.misaka10843.networkproxy.config.ProxyConfig;
import com.misaka10843.networkproxy.config.ProxySettings;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPromise;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import io.netty.resolver.NoopAddressResolverGroup;
import io.netty.util.AttributeKey;
import io.netty.util.NetUtil;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.function.Supplier;

public final class ProxyConnection {
    public static final String BOOTSTRAP_PROXY_HANDLER = "networkproxy_proxy";
    private static final AttributeKey<Optional<ProxySettings>> SERVER_SETTINGS =
            AttributeKey.valueOf("networkproxy_server_settings");
    private static final ScopedValue<Optional<ProxySettings>> MULTIPLAYER_SETTINGS = ScopedValue.newInstance();

    private ProxyConnection() {
    }

    public static <T> T withServerSettings(ProxySettings settings, Supplier<T> connect) {
        return ScopedValue.where(MULTIPLAYER_SETTINGS, Optional.ofNullable(settings).map(selected -> {
            ProxySettings snapshot = new ProxySettings();
            snapshot.copyFrom(selected);
            return snapshot;
        })).call(connect::get);
    }

    public static boolean hasServerSettings() {
        return MULTIPLAYER_SETTINGS.isBound();
    }

    public static ProxySettings serverSettings() {
        return MULTIPLAYER_SETTINGS.get().orElse(null);
    }

    public static void setServerSettings(Bootstrap bootstrap) {
        bootstrap.attr(SERVER_SETTINGS, MULTIPLAYER_SETTINGS.get());
    }

    public static ProxySettings selectSettings(Channel channel, String host) {
        Optional<ProxySettings> settings = channel.attr(SERVER_SETTINGS).get();
        if (settings != null) {
            return settings.orElse(null);
        }
        ProxyConfig config = NetworkProxyClient.getConfig();
        return config.shouldProxy(host) ? config : null;
    }

    public static boolean hasSelectedSettings(Channel channel) {
        return channel.hasAttr(SERVER_SETTINGS);
    }

    public static InetSocketAddress proxyDestination(InetSocketAddress target, ProxySettings settings,
                                                     boolean sendHostname) {
        String host = target.getHostString();
        int port = target.getPort();
        if (target.isUnresolved()) {
            InetAddress literal = NetUtil.createInetAddressFromIpAddressString(host);
            return literal == null ? target : new InetSocketAddress(literal, port);
        }
        if (sendHostname) {
            return NetUtil.isValidIpV4Address(host) || NetUtil.isValidIpV6Address(host)
                    ? target : InetSocketAddress.createUnresolved(host, port);
        }
        if (settings.type == ProxyConfig.ProxyType.HTTP) {
            // HTTP CONNECT uses getHostString() even for resolved addresses.
            return InetSocketAddress.createUnresolved(target.getAddress().getHostAddress(), port);
        }
        return target;
    }

    public static ChannelFuture connectThroughProxy(Bootstrap bootstrap, InetSocketAddress target,
                                                    ProxySettings config) {
        NetworkProxyClient.LOGGER.info("Routing through proxy to: {}", target.getHostString());
        ProxyHandler proxy = createHandler(config.proxyAddress(), config);
        ChannelHandler originalHandler = bootstrap.config().handler();
        bootstrap.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel channel) {
                channel.pipeline().addFirst(BOOTSTRAP_PROXY_HANDLER, proxy);
                channel.pipeline().addLast(originalHandler);
            }
        });

        // The proxy handler needs the chosen destination exactly as supplied.
        return bootstrap.resolver(NoopAddressResolverGroup.INSTANCE)
                .connect(proxyDestination(target, config, config.usesRemoteDns()));
    }

    public static ChannelFuture awaitTunnel(ChannelFuture transport, String handlerName) {
        ChannelPromise tunnel = transport.channel().newPromise();
        tunnel.addListener(result -> {
            if (result.isCancelled()) {
                transport.channel().close();
            }
        });
        transport.addListener(result -> {
            if (!result.isSuccess()) {
                tunnel.tryFailure(result.cause());
                return;
            }
            if (!(transport.channel().pipeline().get(handlerName) instanceof ProxyHandler proxy)) {
                tunnel.tryFailure(new IllegalStateException("Proxy handler was not installed"));
                transport.channel().close();
                return;
            }
            // The TCP connection can finish before the proxy tunnel is ready for Minecraft packets.
            proxy.connectFuture().addListener(connected -> {
                if (connected.isSuccess()) {
                    tunnel.trySuccess();
                } else {
                    tunnel.tryFailure(connected.cause());
                }
            });
        });
        return tunnel;
    }

    public static ProxyHandler createHandler(InetSocketAddress address, ProxySettings config) {
        String username = config.username;
        boolean authenticated = username != null && !username.isEmpty();
        if (config.type == ProxyConfig.ProxyType.HTTP) {
            return authenticated
                    ? new HttpProxyHandler(address, username, config.password)
                    : new HttpProxyHandler(address);
        }
        return authenticated
                ? new Socks5ProxyHandler(address, username, config.password)
                : new Socks5ProxyHandler(address);
    }
}
