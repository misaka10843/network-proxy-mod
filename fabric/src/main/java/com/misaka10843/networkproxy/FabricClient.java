package com.misaka10843.networkproxy;

import net.fabricmc.api.ClientModInitializer;

public class FabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        NetworkProxyClient.initialize();
    }
}
