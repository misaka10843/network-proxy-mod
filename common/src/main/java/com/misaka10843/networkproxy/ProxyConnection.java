package com.misaka10843.networkproxy;

import com.misaka10843.networkproxy.config.ProxyConfig;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import io.netty.resolver.NoopAddressResolverGroup;
import io.netty.util.NetUtil;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.function.Supplier;

public final class ProxyConnection {
    public static final String REMOTE_DNS_HANDLER = "networkproxy_remote_dns";
    private static final ScopedValue<Boolean> DEFERRED_DNS = ScopedValue.newInstance();

    private ProxyConnection() {
    }

    public static <T> T withDeferredDns(Supplier<T> connect) {
        return ScopedValue.where(DEFERRED_DNS, true).call(connect::get);
    }

    public static boolean hasDeferredDns() {
        return DEFERRED_DNS.isBound();
    }

    public static ChannelFuture connect(Bootstrap bootstrap, InetSocketAddress target, ProxyConfig config) {
        String host = target.getHostString();
        NetworkProxyClient.LOGGER.info("Routing through proxy to: {}", host);

        InetAddress literal = NetUtil.createInetAddressFromIpAddressString(host);
        InetSocketAddress destination = literal == null
                ? InetSocketAddress.createUnresolved(host, target.getPort())
                : new InetSocketAddress(literal, target.getPort());
        InetSocketAddress proxyAddress = new InetSocketAddress(config.host, config.port);
        ProxyHandler proxy = createHandler(proxyAddress, config, ProxyConfig.ProxyType.SOCKS5);
        ChannelHandler originalHandler = bootstrap.config().handler();
        bootstrap.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel channel) {
                channel.pipeline().addFirst(REMOTE_DNS_HANDLER, proxy);
                channel.pipeline().addLast(originalHandler);
            }
        });

        // Netty must pass the destination hostname to the proxy handler without resolving it.
        return bootstrap.resolver(NoopAddressResolverGroup.INSTANCE).connect(destination);
    }

    public static ProxyHandler createHandler(InetSocketAddress address, ProxyConfig config,
                                             ProxyConfig.ProxyType type) {
        String username = config.username;
        boolean authenticated = username != null && !username.isEmpty();
        if (type == ProxyConfig.ProxyType.HTTP) {
            return authenticated
                    ? new HttpProxyHandler(address, username, config.password)
                    : new HttpProxyHandler(address);
        }
        return authenticated
                ? new Socks5ProxyHandler(address, username, config.password)
                : new Socks5ProxyHandler(address);
    }
}
