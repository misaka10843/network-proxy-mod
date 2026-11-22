package com.misaka10843.networkproxy.mixin.client;

import com.misaka10843.networkproxy.ProxyRoute;
import net.minecraft.client.resources.server.DownloadedPackSource;
import net.minecraft.client.resources.server.ServerPackManager;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DownloadedPackSource.class)
public class DownloadedPackSourceMixin {
    @Inject(method = "configureForServerControl", at = @At("HEAD"))
    private void inheritServerRoute(Connection connection, ServerPackManager.PackPromptStatus status, CallbackInfo ci) {
        ProxyRoute route = ProxyRoute.attachedTo(connection);
        ProxyRoute.attach(this, route != null && route.enforced() ? route : null);
    }

    @Inject(method = {"configureForLocalWorld", "cleanupAfterDisconnect"}, at = @At("TAIL"))
    private void clearServerRoute(CallbackInfo ci) {
        ProxyRoute.attach(this, null);
    }
}
