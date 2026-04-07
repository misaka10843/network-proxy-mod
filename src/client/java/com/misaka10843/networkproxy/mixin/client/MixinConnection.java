package com.misaka10843.networkproxy.mixin.client;

import com.misaka10843.networkproxy.NetworkProxyClient;
import com.misaka10843.networkproxy.config.ProxyConfig;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

@Mixin(Connection.class)
public class MixinConnection {
    @Inject(method = "configurePacketHandler", at = @At("HEAD"))
    private void injectSmartProxyRouter(ChannelPipeline pipeline, CallbackInfo ci) {

        pipeline.addFirst("smart_proxy_router", new ChannelOutboundHandlerAdapter() {
            @Override
            public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) throws Exception {
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
                super.connect(ctx, remoteAddress, localAddress, promise);
            }
        });
    }
}