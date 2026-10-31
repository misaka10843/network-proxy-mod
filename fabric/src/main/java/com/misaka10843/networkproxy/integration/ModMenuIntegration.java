package com.misaka10843.networkproxy.integration;

import com.misaka10843.networkproxy.config.ProxyConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ProxyConfigScreen::create;
    }
}
