package com.misaka10843.networkproxy.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.misaka10843.networkproxy.NetworkProxyClient;
import com.misaka10843.networkproxy.ProxyRoute;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "net.minecraft.client.gui.screens.ConnectScreen$1")
public class ConnectScreenMixin {
    @Shadow @Final private ServerAddress val$hostAndPort;
    @Shadow @Final private TransferState val$transferState;

    @WrapMethod(method = "run")
    private void selectProxyRoute(Operation<Void> original) {
        ProxyRoute parent = ProxyRoute.attachedTo(val$transferState);
        ProxyRoute route = ProxyRoute.select(NetworkProxyClient.getConfig(), val$hostAndPort.getHost(),
                val$hostAndPort.getPort(), parent != null && parent.enforced());
        route.run(() -> original.call());
    }
}
