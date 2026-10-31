package com.misaka10843.networkproxy.mixin.client;

import com.misaka10843.networkproxy.NetworkProxyClient;
import com.misaka10843.networkproxy.config.ProxyConfig;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import io.netty.util.NetUtil;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

@Mixin(Connection.class)
public class MixinConnection {
    @Shadow private Channel channel;

    @Inject(method = "exceptionCaught", at = @At("HEAD"), cancellable = true)
    private void logEarlyConnectionFailure(ChannelHandlerContext ctx, Throwable cause, CallbackInfo ci) {
        if (channel == null) {
            NetworkProxyClient.LOGGER.error("Connection failed before the channel became active", cause);
            ctx.close();
            ci.cancel();
        }
    }

    @Inject(method = "configurePacketHandler", at = @At("HEAD"))
    private void injectSmartProxyRouter(ChannelPipeline pipeline, CallbackInfo ci) {

        pipeline.addFirst("smart_proxy_router", new ChannelOutboundHandlerAdapter() {
            @Override
            public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) throws Exception {
                SocketAddress destination = remoteAddress;
                if (remoteAddress instanceof InetSocketAddress targetAddr) {
                    ProxyConfig config = NetworkProxyClient.getConfig();
                    boolean shouldProxy = false;

                    if (config.enabled) {
                        if (!config.useFilter) {
                            shouldProxy = true;
                        } else if (config.proxyDomains != null) {
                            String hostName = targetAddr.getHostString().toLowerCase();
                            for (String domain : config.proxyDomains) {
                                if (hostName.contains(domain.toLowerCase())) {
                                    shouldProxy = true;
                                    break;
                                }
                            }
                        }
                    }

                    if (shouldProxy) {
                        NetworkProxyClient.LOGGER.info("Routing through proxy to: {}", targetAddr.getHostString());
                        String host = targetAddr.getHostString();
                        // SOCKS5 otherwise serializes already-resolved addresses as IPs.
                        if (!NetUtil.isValidIpV4Address(host) && !NetUtil.isValidIpV6Address(host)) {
                            destination = InetSocketAddress.createUnresolved(host, targetAddr.getPort());
                        }
                        InetSocketAddress proxyAddr = new InetSocketAddress(config.host, config.port);
                        String user = config.username;
                        String pass = config.password;

                        if (config.type == ProxyConfig.ProxyType.HTTP) {
                            if (user != null && !user.isEmpty()) {
                                ctx.pipeline().addFirst("proxy", new HttpProxyHandler(proxyAddr, user, pass));
                            } else {
                                ctx.pipeline().addFirst("proxy", new HttpProxyHandler(proxyAddr));
                            }
                        } else {
                            if (user != null && !user.isEmpty()) {
                                ctx.pipeline().addFirst("proxy", new Socks5ProxyHandler(proxyAddr, user, pass));
                            } else {
                                ctx.pipeline().addFirst("proxy", new Socks5ProxyHandler(proxyAddr));
                            }
                        }
                    }
                }

                ctx.pipeline().remove(this);
                super.connect(ctx, destination, localAddress, promise);
            }
        });
    }
}
