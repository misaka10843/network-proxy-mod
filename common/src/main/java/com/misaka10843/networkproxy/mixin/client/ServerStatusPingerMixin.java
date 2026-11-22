package com.misaka10843.networkproxy.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.misaka10843.networkproxy.NetworkProxyClient;
import com.misaka10843.networkproxy.ProxyConnection;
import com.misaka10843.networkproxy.config.ProxySettings;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
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
                                            LocalSampleLogger logger, Operation<Connection> original,
                                            @Local ServerAddress serverAddress) {
        ProxySettings settings = NetworkProxyClient.getConfig().forServer(serverAddress.getHost(), serverAddress.getPort());
        return ProxyConnection.withServerSettings(settings, () -> original.call(target, eventLoops, logger));
    }

    @WrapOperation(
            method = "pingLegacyServer",
            at = @At(value = "INVOKE", target = "Lio/netty/bootstrap/Bootstrap;connect(Ljava/net/InetAddress;I)Lio/netty/channel/ChannelFuture;")
    )
    private ChannelFuture routeLegacyPing(Bootstrap bootstrap, InetAddress address, int port,
                                           Operation<ChannelFuture> original,
                                           @Local(argsOnly = true) InetSocketAddress target,
                                           @Local(argsOnly = true) ServerAddress serverAddress) {
        ProxySettings settings = NetworkProxyClient.getConfig().forServer(serverAddress.getHost(), serverAddress.getPort());
        if (settings != null && (!target.isUnresolved() || settings.usesRemoteDns())) {
            return ProxyConnection.connectThroughProxy(bootstrap, target, settings);
        }
        // Vanilla discards the future. Skip the fallback if SOCKS5 was disabled during the ping.
        return target.isUnresolved() ? null : original.call(bootstrap, address, port);
    }
}
