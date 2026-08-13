package com.misaka10843.networkproxy.fabric;

import com.misaka10843.networkproxy.NetworkProxy;
import net.fabricmc.api.ClientModInitializer;

public class NetworkProxyFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        NetworkProxy.init();
    }
}
