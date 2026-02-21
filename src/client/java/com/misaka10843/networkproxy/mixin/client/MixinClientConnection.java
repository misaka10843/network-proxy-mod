package com.misaka10843.networkproxy.mixin.client;

import com.misaka10843.networkproxy.NetworkProxyClient;
import com.misaka10843.networkproxy.config.ProxyConfig;
import io.netty.bootstrap.AbstractBootstrap;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkingBackend;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;

@Mixin(ClientConnection.class)
public class MixinClientConnection {
    @Unique
    private static final ThreadLocal<InetSocketAddress> CURRENT_TARGET = new ThreadLocal<>();

    @Inject(
            method = "connect(Ljava/net/InetSocketAddress;Lnet/minecraft/network/NetworkingBackend;Lnet/minecraft/network/ClientConnection;)Lio/netty/channel/ChannelFuture;",
            at = @At("HEAD")
    )
    private static void captureAddress(InetSocketAddress address, NetworkingBackend backend, ClientConnection connection, CallbackInfoReturnable<ChannelFuture> cir) {
        CURRENT_TARGET.set(address);
    }

    @Inject(
            method = "connect(Ljava/net/InetSocketAddress;Lnet/minecraft/network/NetworkingBackend;Lnet/minecraft/network/ClientConnection;)Lio/netty/channel/ChannelFuture;",
            at = @At("RETURN")
    )
    private static void cleanupAddress(InetSocketAddress address, NetworkingBackend backend, ClientConnection connection, CallbackInfoReturnable<ChannelFuture> cir) {
        CURRENT_TARGET.remove();
    }

    @Redirect(
            method = "connect(Ljava/net/InetSocketAddress;Lnet/minecraft/network/NetworkingBackend;Lnet/minecraft/network/ClientConnection;)Lio/netty/channel/ChannelFuture;",
            at = @At(value = "INVOKE", target = "Lio/netty/bootstrap/Bootstrap;handler(Lio/netty/channel/ChannelHandler;)Lio/netty/bootstrap/AbstractBootstrap;")
    )
    private static AbstractBootstrap<?, ?> injectProxy(Bootstrap bootstrap, ChannelHandler originalHandler) {
        ProxyConfig config = NetworkProxyClient.getConfig();

        InetSocketAddress targetAddr = CURRENT_TARGET.get();

        boolean shouldProxy = false;

        if (config.enabled) {
            if (!config.useFilter) {
                NetworkProxyClient.LOGGER.info("Proxy All Injected");
                shouldProxy = true;
            } else if (targetAddr != null && config.proxyDomains != null) {
                String hostName = targetAddr.getHostString().toLowerCase();

                for (String domain : config.proxyDomains) {
                    if (hostName.contains(domain.toLowerCase())) {
                        shouldProxy = true;
                        break;
                    }
                }
            }
        }

        if (!shouldProxy) {
            return bootstrap.handler(originalHandler);
        }

        NetworkProxyClient.LOGGER.info("Proxy Injected for {}: {}://{}:{}",
                (targetAddr != null ? targetAddr.getHostString() : "unknown"),
                config.type, config.host, config.port);

        return bootstrap.handler(new ChannelInitializer<>() {
            @Override
            protected void initChannel(Channel ch) {
                InetSocketAddress proxyAddr = new InetSocketAddress(config.host, config.port);
                String user = config.username;
                String pass = config.password;

                if (config.type == ProxyConfig.ProxyType.HTTP) {
                    if (user != null && !user.isEmpty()) {
                        ch.pipeline().addFirst("proxy", new HttpProxyHandler(proxyAddr, user, pass));
                    } else {
                        ch.pipeline().addFirst("proxy", new HttpProxyHandler(proxyAddr));
                    }
                } else {
                    if (user != null && !user.isEmpty()) {
                        ch.pipeline().addFirst("proxy", new Socks5ProxyHandler(proxyAddr, user, pass));
                    } else {
                        ch.pipeline().addFirst("proxy", new Socks5ProxyHandler(proxyAddr));
                    }
                }

                try {
                    Method initMethod = ChannelInitializer.class.getDeclaredMethod("initChannel", Channel.class);
                    initMethod.setAccessible(true);
                    initMethod.invoke(originalHandler, ch);
                } catch (Exception e) {
                    throw new ChannelException("Failed to invoke original initChannel", e);
                }
            }
        });
    }
}