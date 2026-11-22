package com.misaka10843.networkproxy;

import com.misaka10843.networkproxy.config.ProxyConfig;
import com.misaka10843.networkproxy.config.ProxySettings;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.resolver.NoopAddressResolverGroup;
import io.netty.util.AttributeKey;
import io.netty.util.NetUtil;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.function.Supplier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

public final class ProxyConnection {
    public static final String BOOTSTRAP_PROXY_HANDLER = "networkproxy_proxy";
    private static final String PROXY_TLS_HANDLER = "networkproxy_tls";
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
        if (settings.type != ProxyConfig.ProxyType.SOCKS5) {
            // HTTP CONNECT uses getHostString() even for resolved addresses.
            return InetSocketAddress.createUnresolved(target.getAddress().getHostAddress(), port);
        }
        return target;
    }

    public static ChannelFuture connectThroughProxy(Bootstrap bootstrap, InetSocketAddress target,
                                                    ProxySettings config) {
        NetworkProxyClient.LOGGER.info("Routing through proxy to: {}", target.getHostString());
        ChannelHandler originalHandler = bootstrap.config().handler();
        bootstrap.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel channel) {
                installHandler(channel.pipeline(), BOOTSTRAP_PROXY_HANDLER, config);
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
            if (transport.channel().pipeline().get(PROXY_TLS_HANDLER) instanceof SslHandler tls) {
                tls.handshakeFuture().addListener(handshake -> {
                    if (handshake.isSuccess()) {
                        awaitProxyHandshake(proxy, tunnel);
                    } else {
                        tunnel.tryFailure(handshake.cause());
                    }
                });
            } else {
                awaitProxyHandshake(proxy, tunnel);
            }
        });
        return tunnel;
    }

    private static void awaitProxyHandshake(ProxyHandler proxy, ChannelPromise tunnel) {
        // TCP and TLS can finish before the proxy tunnel is ready for Minecraft packets.
        proxy.connectFuture().addListener(connected -> {
            if (connected.isSuccess()) {
                tunnel.trySuccess();
            } else {
                tunnel.tryFailure(connected.cause());
            }
        });
    }

    public static void installHandler(ChannelPipeline pipeline, String name, ProxySettings config) {
        InetSocketAddress address = config.proxyAddress();
        SslHandler tls = config.type == ProxyConfig.ProxyType.HTTPS ? proxyTls(address) : null;
        pipeline.addFirst(name, createHandler(address, config));
        if (tls != null) {
            // CONNECT and proxy authentication must pass through the TLS handler.
            pipeline.addFirst(PROXY_TLS_HANDLER, tls);
        }
    }

    private static SslHandler proxyTls(InetSocketAddress address) {
        try {
            SSLEngine engine = SSLContext.getDefault().createSSLEngine(address.getHostString(), address.getPort());
            engine.setUseClientMode(true);
            var parameters = engine.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            engine.setSSLParameters(parameters);
            return new SslHandler(engine);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Cannot initialize TLS for the HTTPS proxy", e);
        }
    }

    private static ProxyHandler createHandler(InetSocketAddress address, ProxySettings config) {
        String username = config.username;
        boolean authenticated = username != null && !username.isEmpty();
        if (config.type != ProxyConfig.ProxyType.SOCKS5) {
            return authenticated
                    ? new HttpProxyHandler(address, username, config.password)
                    : new HttpProxyHandler(address);
        }
        return authenticated
                ? new Socks5ProxyHandler(address, username, config.password)
                : new Socks5ProxyHandler(address);
    }
}
