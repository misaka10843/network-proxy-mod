package com.misaka10843.networkproxy.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.misaka10843.networkproxy.NetworkProxyClient;
import com.misaka10843.networkproxy.ProxyRoute;
import net.minecraft.network.Connection;
import net.minecraft.server.network.EventLoopGroupHolder;
import net.minecraft.util.debugchart.LocalSampleLogger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.net.InetSocketAddress;

@Mixin(targets = "net.minecraft.realms.RealmsConnect$1")
public class RealmsConnectMixin {
    @WrapOperation(method = "run", at = @At(value = "NEW",
            target = "(Ljava/lang/String;I)Ljava/net/InetSocketAddress;"))
    private InetSocketAddress resolveRealmsAddress(String host, int port, Operation<InetSocketAddress> original,
                                                  @Share("realmsRoute") LocalRef<ProxyRoute> attempt) {
        ProxyRoute route = ProxyRoute.selectRealms(NetworkProxyClient.getConfig().realms);
        attempt.set(route);
        if (route != null && route.enforced()) {
            route.enforcedEndpoint();
            if (route.settings().usesRemoteDns()) {
                return InetSocketAddress.createUnresolved(host, port);
            }
        }
        return original.call(host, port);
    }

    @WrapOperation(method = "run", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/Connection;connectToServer(Ljava/net/InetSocketAddress;Lnet/minecraft/server/network/EventLoopGroupHolder;Lnet/minecraft/util/debugchart/LocalSampleLogger;)Lnet/minecraft/network/Connection;"))
    private Connection connectRealms(InetSocketAddress target, EventLoopGroupHolder eventLoops, LocalSampleLogger logger,
                                     Operation<Connection> original, @Share("realmsRoute") LocalRef<ProxyRoute> attempt) {
        ProxyRoute route = attempt.get();
        if (route == null) {
            return original.call(target, eventLoops, logger);
        }
        return route.call(() -> original.call(target, eventLoops, logger));
    }
}
