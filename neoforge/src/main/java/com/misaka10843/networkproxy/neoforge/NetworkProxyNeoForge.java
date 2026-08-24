package com.misaka10843.networkproxy.neoforge;

import com.misaka10843.networkproxy.NetworkProxy;
import com.misaka10843.networkproxy.ui.ProxyConfigScreen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(NetworkProxy.MOD_ID)
public class NetworkProxyNeoForge {

    public NetworkProxyNeoForge(IEventBus modBus, ModContainer container) {
        modBus.addListener(this::onClientSetup);

        // Native "Config" button in the mods list; the multiplayer-screen button
        // (mixin) is the other, loader-independent entry point.
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (modContainer, screen) -> new ProxyConfigScreen(screen));
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        NetworkProxy.init();
    }
}
