package com.misaka10843.networkproxy.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.misaka10843.networkproxy.ProxyRoute;
import net.minecraft.client.resources.server.DownloadedPackSource;
import net.minecraft.server.packs.DownloadQueue;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.client.resources.server.DownloadedPackSource$4")
public class PackDownloaderMixin {
    @Shadow @Final private DownloadedPackSource this$0;

    @ModifyExpressionValue(method = "download", at = @At(value = "NEW",
            target = "net/minecraft/server/packs/DownloadQueue$BatchConfig"))
    private DownloadQueue.BatchConfig retainRouteForWorker(DownloadQueue.BatchConfig config) {
        ProxyRoute.attach(config, ProxyRoute.attachedTo(this$0));
        return config;
    }
}
