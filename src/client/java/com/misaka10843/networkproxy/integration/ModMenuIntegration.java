package com.misaka10843.networkproxy.integration;

import com.misaka10843.networkproxy.NetworkProxyClient;
import com.misaka10843.networkproxy.config.ProxyConfig;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.text.Text;

import java.util.List;

public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            ProxyConfig config = NetworkProxyClient.getConfig();

            ConfigBuilder builder = ConfigBuilder.create()
                    .setParentScreen(parent)
                    .setTitle(Text.translatable("config.networkproxy.title"));

            builder.setSavingRunnable(() -> {
                AutoConfig.getConfigHolder(ProxyConfig.class).save();
            });

            ConfigEntryBuilder entryBuilder = builder.entryBuilder();

            ConfigCategory general = builder.getOrCreateCategory(Text.translatable("config.networkproxy.category.general"));

            general.addEntry(entryBuilder.startBooleanToggle(Text.translatable("config.networkproxy.enabled"), config.enabled)
                    .setDefaultValue(false)
                    .setSaveConsumer(newValue -> config.enabled = newValue)
                    .build());

            general.addEntry(entryBuilder.startBooleanToggle(Text.translatable("config.networkproxy.use_filter"), config.useFilter)
                    .setDefaultValue(false)
                    .setTooltip(Text.translatable("config.networkproxy.use_filter.tooltip"))
                    .setSaveConsumer(newValue -> config.useFilter = newValue)
                    .build());

            general.addEntry(entryBuilder.startStrList(Text.translatable("config.networkproxy.domain_list"), config.proxyDomains)
                    .setDefaultValue(List.of("hypixel.net"))
                    .setTooltip(Text.translatable("config.networkproxy.domain_list.tooltip"))
                    .setSaveConsumer(newValue -> config.proxyDomains = newValue)
                    .build());

            general.addEntry(entryBuilder.startEnumSelector(Text.translatable("config.networkproxy.type"), ProxyConfig.ProxyType.class, config.type)
                    .setDefaultValue(ProxyConfig.ProxyType.SOCKS5)
                    .setEnumNameProvider(e -> Text.literal(e.name()))
                    .setSaveConsumer(newValue -> config.type = newValue)
                    .build());

            general.addEntry(entryBuilder.startStrField(Text.translatable("config.networkproxy.host"), config.host)
                    .setDefaultValue("127.0.0.1")
                    .setTooltip(Text.translatable("config.networkproxy.host.tooltip"))
                    .setSaveConsumer(newValue -> config.host = newValue)
                    .build());

            general.addEntry(entryBuilder.startIntField(Text.translatable("config.networkproxy.port"), config.port)
                    .setDefaultValue(7890)
                    .setMin(0).setMax(65535)
                    .setSaveConsumer(newValue -> config.port = newValue)
                    .build());

            general.addEntry(entryBuilder.startStrField(Text.translatable("config.networkproxy.username"), config.username)
                    .setDefaultValue("")
                    .setSaveConsumer(newValue -> config.username = newValue)
                    .build());

            general.addEntry(entryBuilder.startStrField(Text.translatable("config.networkproxy.password"), config.password)
                    .setDefaultValue("")
                    .setSaveConsumer(newValue -> config.password = newValue)
                    .build());

            return builder.build();
        };
    }
}