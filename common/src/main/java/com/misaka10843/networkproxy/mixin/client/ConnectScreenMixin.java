package com.misaka10843.networkproxy.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misaka10843.networkproxy.NetworkProxyClient;
import com.misaka10843.networkproxy.ProxyConnection;
import com.misaka10843.networkproxy.config.ProxySettings;
import io.netty.channel.ChannelFuture;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.Connection;
import net.minecraft.server.network.EventLoopGroupHolder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.net.InetSocketAddress;

@Mixin(targets = "net.minecraft.client.gui.screens.ConnectScreen$1")
public class ConnectScreenMixin {
    @Shadow @Final private ServerAddress val$hostAndPort;

    @WrapOperation(method = "run", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/network/Connection;connect(Ljava/net/InetSocketAddress;Lnet/minecraft/server/network/EventLoopGroupHolder;Lnet/minecraft/network/Connection;)Lio/netty/channel/ChannelFuture;"))
    private ChannelFuture connectWithDeferredDns(InetSocketAddress target, EventLoopGroupHolder eventLoops,
                                                  Connection connection, Operation<ChannelFuture> original) {
        ProxySettings settings = NetworkProxyClient.getConfig().forServer(val$hostAndPort.getHost(), val$hostAndPort.getPort());
        return ProxyConnection.withServerSettings(settings, () -> original.call(target, eventLoops, connection));
    }
}
