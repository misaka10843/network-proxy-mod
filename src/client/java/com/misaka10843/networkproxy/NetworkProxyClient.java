package com.misaka10843.networkproxy;

import com.misaka10843.networkproxy.config.ProxyConfig;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetworkProxyClient implements ClientModInitializer {
    public static final String MOD_ID = "networkproxy";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static ProxyConfig getConfig() {
        return AutoConfig.getConfigHolder(ProxyConfig.class).getConfig();
    }

    @Override
    public void onInitializeClient() {
        AutoConfig.register(ProxyConfig.class, GsonConfigSerializer::new);

        LOGGER.info("NetworkProxy Client Initialized!");
    }
}