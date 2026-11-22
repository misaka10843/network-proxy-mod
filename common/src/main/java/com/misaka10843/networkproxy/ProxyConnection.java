package com.misaka10843.networkproxy;

import com.misaka10843.networkproxy.config.ProxyConfig;
import com.misaka10843.networkproxy.config.ProxySettings;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
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
import java.net.SocketAddress;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

public final class ProxyConnection {
    public static final String BOOTSTRAP_PROXY_HANDLER = "networkproxy_proxy";
    private static final String PROXY_TLS_HANDLER = "networkproxy_tls";
    private static final AttributeKey<Optional<ProxySettings>> SERVER_SETTINGS =
            AttributeKey.valueOf("networkproxy_server_settings");
    private ProxyConnection() {
    }

    public static void setServerSettings(Bootstrap bootstrap, ProxySettings settings) {
        bootstrap.attr(SERVER_SETTINGS, Optional.ofNullable(settings));
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

    public static ChannelFuture connectThroughRoute(Bootstrap bootstrap, InetSocketAddress target, ProxyRoute route) {
        InetSocketAddress endpoint = route.enforced() ? route.enforcedEndpoint() : null;
        ProxySettings settings = route.settings();
        if (settings == null || (target.isUnresolved() && !settings.usesRemoteDns())
                || (route.enforced() && settings.usesRemoteDns() && !target.isUnresolved())) {
            throw new IllegalStateException("The selected proxy route cannot use this destination address");
        }
        return connectThroughProxy(bootstrap, target, settings, endpoint);
    }

    public static ChannelFuture connectThroughProxy(Bootstrap bootstrap, InetSocketAddress target, ProxySettings config) {
        return connectThroughProxy(bootstrap, target, config, null);
    }

    private static ChannelFuture connectThroughProxy(Bootstrap bootstrap, InetSocketAddress target,
                                                     ProxySettings config, InetSocketAddress enforcedEndpoint) {
        NetworkProxyClient.LOGGER.info("Routing through proxy to: {}", target.getHostString());
        ChannelHandler originalHandler = bootstrap.config().handler();
        bootstrap.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel channel) {
                ChannelPipeline pipeline = channel.pipeline();
                InetSocketAddress endpoint = enforcedEndpoint == null ? config.proxyAddress() : enforcedEndpoint;
                ProxyHandler proxy = createHandler(endpoint, config);
                SslHandler tls = installHandler(pipeline, BOOTSTRAP_PROXY_HANDLER, config, endpoint, proxy);
                pipeline.addLast(originalHandler);
                if (enforcedEndpoint != null) {
                    guardProxySocket(pipeline, endpoint, proxy, tls);
                }
            }
        });

        return bootstrap.resolver(NoopAddressResolverGroup.INSTANCE)
                .connect(proxyDestination(target, config, config.usesRemoteDns()));
    }

    private static void guardProxySocket(ChannelPipeline pipeline, InetSocketAddress endpoint,
                                         ProxyHandler proxy, SslHandler tls) {
        // Outbound connect travels back toward the head after the proxy rewrites its destination.
        pipeline.addFirst("networkproxy_required_peer", new ChannelOutboundHandlerAdapter() {
            @Override
            public void connect(ChannelHandlerContext ctx, SocketAddress remote, SocketAddress local,
                                ChannelPromise promise) {
                var handlers = pipeline.names();
                int proxyIndex = handlers.indexOf(BOOTSTRAP_PROXY_HANDLER);
                int tlsIndex = handlers.indexOf(PROXY_TLS_HANDLER);
                boolean tlsInOrder = tls == null || (pipeline.get(PROXY_TLS_HANDLER) == tls
                        && tlsIndex > 0 && tlsIndex < proxyIndex);
                if (pipeline.first() != this || !endpoint.equals(remote) || pipeline.get(BOOTSTRAP_PROXY_HANDLER) != proxy
                        || !tlsInOrder || pipeline.toMap().values().stream()
                        .anyMatch(handler -> handler instanceof ProxyHandler && handler != proxy)) {
                    promise.tryFailure(new IllegalStateException("Cannot enforce the configured proxy route"));
                    ctx.close();
                    return;
                }
                ctx.connect(remote, local, promise);
            }
        });
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
        installHandler(pipeline, name, config, address, createHandler(address, config));
    }

    private static SslHandler installHandler(ChannelPipeline pipeline, String name, ProxySettings config,
                                             InetSocketAddress address, ProxyHandler proxy) {
        SslHandler tls = config.type == ProxyConfig.ProxyType.HTTPS ? proxyTls(address) : null;
        pipeline.addFirst(name, proxy);
        if (tls != null) {
            // CONNECT and proxy authentication must pass through the TLS handler.
            pipeline.addFirst(PROXY_TLS_HANDLER, tls);
        }
        return tls;
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
