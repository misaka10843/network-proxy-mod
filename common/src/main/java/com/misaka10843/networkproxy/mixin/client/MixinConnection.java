package com.misaka10843.networkproxy.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.misaka10843.networkproxy.NetworkProxyClient;
import com.misaka10843.networkproxy.ProxyConnection;
import com.misaka10843.networkproxy.ProxyRoute;
import com.misaka10843.networkproxy.config.ProxySettings;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
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
    private static ChannelFuture connectMultiplayer(Bootstrap bootstrap, InetAddress address, int port,
                                                         Operation<ChannelFuture> original,
                                                         @Local(argsOnly = true) InetSocketAddress target,
                                                         @Local(argsOnly = true) Connection connection) {
        ProxyRoute route = ProxyRoute.current();
        if (route == null) {
            return original.call(bootstrap, address, port);
        }

        ProxyRoute.attach(connection, route);
        ProxySettings settings = route.settings();
        if (route.enforced() || target.isUnresolved()) {
            ChannelFuture transport = ProxyConnection.connectThroughRoute(bootstrap, target, route);
            return ProxyConnection.awaitTunnel(transport, ProxyConnection.BOOTSTRAP_PROXY_HANDLER);
        }

        ProxyConnection.setServerSettings(bootstrap, settings);
        ChannelFuture transport = original.call(bootstrap, address, port);
        return settings == null ? transport : ProxyConnection.awaitTunnel(transport, "proxy");
    }

    @Inject(method = "configurePacketHandler", at = @At("HEAD"))
    private void injectSmartProxyRouter(ChannelPipeline pipeline, CallbackInfo ci) {
        if (pipeline.get(ProxyConnection.BOOTSTRAP_PROXY_HANDLER) != null) {
            return;
        }
        pipeline.addFirst("smart_proxy_router", new ChannelOutboundHandlerAdapter() {
            @Override
            public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) throws Exception {
                SocketAddress destination = remoteAddress;
                if (remoteAddress instanceof InetSocketAddress targetAddr) {
                    String host = targetAddr.getHostString();
                    ProxySettings settings = ProxyConnection.selectSettings(ctx.channel(), host);
                    if (settings != null) {
                        NetworkProxyClient.LOGGER.info("Routing through proxy to: {}", host);
                        boolean sendHostname = settings.usesRemoteDns() || !ProxyConnection.hasSelectedSettings(ctx.channel());
                        destination = ProxyConnection.proxyDestination(targetAddr, settings, sendHostname);
                        ProxyConnection.installHandler(ctx.pipeline(), "proxy", settings);
                    }
                }

                ctx.pipeline().remove(this);
                super.connect(ctx, destination, localAddress, promise);
            }
        });
    }
}
