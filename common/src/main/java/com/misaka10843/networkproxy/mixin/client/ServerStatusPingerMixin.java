package com.misaka10843.networkproxy.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.misaka10843.networkproxy.NetworkProxyClient;
import com.misaka10843.networkproxy.ProxyConnection;
import com.misaka10843.networkproxy.config.ProxyConfig;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.network.Connection;
import net.minecraft.server.network.EventLoopGroupHolder;
import net.minecraft.util.debugchart.LocalSampleLogger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.net.InetAddress;
import java.net.InetSocketAddress;

@Mixin(ServerStatusPinger.class)
public class ServerStatusPingerMixin {
    @WrapOperation(method = "pingServer", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/network/Connection;connectToServer(Ljava/net/InetSocketAddress;Lnet/minecraft/server/network/EventLoopGroupHolder;Lnet/minecraft/util/debugchart/LocalSampleLogger;)Lnet/minecraft/network/Connection;"))
    private Connection pingWithDeferredDns(InetSocketAddress target, EventLoopGroupHolder eventLoops,
                                            LocalSampleLogger logger, Operation<Connection> original) {
        if (target.isUnresolved()) {
            return ProxyConnection.withDeferredDns(() -> original.call(target, eventLoops, logger));
        }
        return original.call(target, eventLoops, logger);
    }

    @WrapOperation(
            method = "pingLegacyServer",
            at = @At(value = "INVOKE", target = "Lio/netty/bootstrap/Bootstrap;connect(Ljava/net/InetAddress;I)Lio/netty/channel/ChannelFuture;")
    )
    private ChannelFuture pingThroughSocks5(Bootstrap bootstrap, InetAddress address, int port,
                                           Operation<ChannelFuture> original,
                                           @Local(argsOnly = true) InetSocketAddress target) {
        ProxyConfig config = NetworkProxyClient.getConfig();
        if (target.isUnresolved() && config.shouldUseSocks5RemoteDns(target.getHostString())) {
            return ProxyConnection.connect(bootstrap, target, config);
        }
        // Vanilla discards the future. Skip the fallback if SOCKS5 was disabled during the ping.
        return target.isUnresolved() ? null : original.call(bootstrap, address, port);
    }
}
