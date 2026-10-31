package com.misaka10843.networkproxy;

import com.misaka10843.networkproxy.config.ProxyConfigScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = "networkproxy", dist = Dist.CLIENT)
public class NeoForgeClient {
    public NeoForgeClient(ModContainer container) {
        NetworkProxyClient.initialize();
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> ProxyConfigScreen.create(parent));
    }
}
