package com.misaka10843.networkproxy.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.misaka10843.networkproxy.NetworkProxyClient;
import com.misaka10843.networkproxy.ProxyConnection;
import com.misaka10843.networkproxy.config.ProxyConfig;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.util.NetUtil;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.InetAddress;
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

    @WrapOperation(
            method = "connect",
            at = @At(value = "INVOKE", target = "Lio/netty/bootstrap/Bootstrap;connect(Ljava/net/InetAddress;I)Lio/netty/channel/ChannelFuture;")
    )
    private static ChannelFuture connectWithDeferredDns(Bootstrap bootstrap, InetAddress address, int port,
                                                         Operation<ChannelFuture> original,
                                                         @Local(argsOnly = true) InetSocketAddress target) {
        if (!ProxyConnection.hasDeferredDns()) {
            return original.call(bootstrap, address, port);
        }
        ProxyConfig config = NetworkProxyClient.getConfig();
        if (!config.shouldUseSocks5RemoteDns(target.getHostString())) {
            throw new IllegalStateException("SOCKS5 remote DNS settings changed while connecting");
        }
        return ProxyConnection.connect(bootstrap, target, config);
    }

    @Inject(method = "configurePacketHandler", at = @At("HEAD"))
    private void injectSmartProxyRouter(ChannelPipeline pipeline, CallbackInfo ci) {
        if (pipeline.get(ProxyConnection.REMOTE_DNS_HANDLER) != null) {
            return;
        }
        pipeline.addFirst("smart_proxy_router", new ChannelOutboundHandlerAdapter() {
            @Override
            public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) throws Exception {
                SocketAddress destination = remoteAddress;
                if (remoteAddress instanceof InetSocketAddress targetAddr) {
                    ProxyConfig config = NetworkProxyClient.getConfig();
                    if (config.shouldProxy(targetAddr.getHostString())) {
                        NetworkProxyClient.LOGGER.info("Routing through proxy to: {}", targetAddr.getHostString());
                        String host = targetAddr.getHostString();
                        // SOCKS5 otherwise serializes already-resolved addresses as IPs.
                        if (!NetUtil.isValidIpV4Address(host) && !NetUtil.isValidIpV6Address(host)) {
                            destination = InetSocketAddress.createUnresolved(host, targetAddr.getPort());
                        }
                        InetSocketAddress proxyAddr = new InetSocketAddress(config.host, config.port);
                        ctx.pipeline().addFirst("proxy", ProxyConnection.createHandler(proxyAddr, config, config.type));
                    }
                }

                ctx.pipeline().remove(this);
                super.connect(ctx, destination, localAddress, promise);
            }
        });
    }
}
