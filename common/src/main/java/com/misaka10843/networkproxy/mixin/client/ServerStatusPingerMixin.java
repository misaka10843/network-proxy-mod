package com.misaka10843.networkproxy.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.misaka10843.networkproxy.NetworkProxyClient;
import com.misaka10843.networkproxy.ProxyConnection;
import com.misaka10843.networkproxy.ProxyRoute;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.server.network.EventLoopGroupHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.net.InetAddress;
import java.net.InetSocketAddress;

@Mixin(ServerStatusPinger.class)
public class ServerStatusPingerMixin {
    @WrapMethod(method = "pingServer")
    private void selectProxyRoute(ServerData data, Runnable onPersistentDataChange, Runnable onPongResponse,
                                  EventLoopGroupHolder eventLoops, Operation<Void> original) {
        ServerAddress address = ServerAddress.parseString(data.ip);
        ProxyRoute route = ProxyRoute.select(NetworkProxyClient.getConfig(), address.getHost(), address.getPort(), false);
        route.run(() -> original.call(data, onPersistentDataChange, onPongResponse, eventLoops));
    }

    @WrapOperation(method = "pingLegacyServer", at = @At(value = "INVOKE",
            target = "Lio/netty/bootstrap/Bootstrap;connect(Ljava/net/InetAddress;I)Lio/netty/channel/ChannelFuture;"))
    private ChannelFuture routeLegacyPing(Bootstrap bootstrap, InetAddress address, int port,
                                           Operation<ChannelFuture> original,
                                           @Local(argsOnly = true) InetSocketAddress target,
                                           @Local(argsOnly = true) ServerAddress serverAddress) {
        ProxyRoute route = ProxyRoute.current();
        if (route == null) {
            route = ProxyRoute.select(NetworkProxyClient.getConfig(), serverAddress.getHost(), serverAddress.getPort(), false);
        }
        if (route.settings() != null || route.enforced()) {
            return ProxyConnection.connectThroughRoute(bootstrap, target, route);
        }
        return target.isUnresolved() ? null : original.call(bootstrap, address, port);
    }
}
