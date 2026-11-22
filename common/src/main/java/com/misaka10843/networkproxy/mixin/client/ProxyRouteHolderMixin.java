package com.misaka10843.networkproxy.mixin.client;

import com.misaka10843.networkproxy.ProxyRoute;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.resources.server.DownloadedPackSource;
import net.minecraft.network.Connection;
import net.minecraft.server.packs.DownloadQueue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin({Connection.class, TransferState.class, DownloadedPackSource.class, DownloadQueue.BatchConfig.class})
public abstract class ProxyRouteHolderMixin implements ProxyRoute.Holder {
    @Unique
    private volatile ProxyRoute networkProxy$route;

    @Override
    public ProxyRoute networkProxy$route() {
        return networkProxy$route;
    }

    @Override
    public void networkProxy$setRoute(ProxyRoute route) {
        networkProxy$route = route;
    }
}
