package com.misaka10843.networkproxy.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misaka10843.networkproxy.ProxyConnection;
import io.netty.channel.ChannelFuture;
import net.minecraft.network.Connection;
import net.minecraft.server.network.EventLoopGroupHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.net.InetSocketAddress;

@Mixin(targets = "net.minecraft.client.gui.screens.ConnectScreen$1")
public class ConnectScreenMixin {
    @WrapOperation(method = "run", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/network/Connection;connect(Ljava/net/InetSocketAddress;Lnet/minecraft/server/network/EventLoopGroupHolder;Lnet/minecraft/network/Connection;)Lio/netty/channel/ChannelFuture;"))
    private ChannelFuture connectWithDeferredDns(InetSocketAddress target, EventLoopGroupHolder eventLoops,
                                                  Connection connection, Operation<ChannelFuture> original) {
        if (target.isUnresolved()) {
            return ProxyConnection.withDeferredDns(() -> original.call(target, eventLoops, connection));
        }
        return original.call(target, eventLoops, connection);
    }
}
