package com.misaka10843.networkproxy.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.misaka10843.networkproxy.ProxyRoute;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "net.minecraft.client.multiplayer.ServerStatusPinger$1")
public class StatusListenerMixin {
    @Shadow @Final private Connection val$connection;

    @WrapMethod(method = "onDisconnect")
    private void preserveLegacyPingRoute(DisconnectionDetails reason, Operation<Void> original) {
        ProxyRoute route = ProxyRoute.attachedTo(val$connection);
        if (route == null) {
            original.call(reason);
        } else {
            route.run(() -> original.call(reason));
        }
    }
}
