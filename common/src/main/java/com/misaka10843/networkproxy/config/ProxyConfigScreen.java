package com.misaka10843.networkproxy.config;

import com.misaka10843.networkproxy.NetworkProxyClient;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Optional;

public final class ProxyConfigScreen extends Screen {
    private final Screen parent;
    private final ProxyConfig config;
    private int page;

    private ProxyConfigScreen(Screen parent) {
        super(label("title"));
        this.parent = parent;
        this.config = NetworkProxyClient.getConfig();
    }

    public static Screen create(Screen parent) {
        return new ProxyConfigScreen(parent);
    }

    @Override
    protected void init() {
        int contentWidth = Math.min(370, width - 24);
        int left = (width - contentWidth) / 2;
        int half = (contentWidth - 6) / 2;
        int rows = Math.max(1, (height - 100) / 24);
        int count = config.serverAddresses.size();
        page = Math.min(page, Math.max(0, (count - 1) / rows));

        addRenderableOnly(new StringWidget(left, 10, contentWidth, 20, title, font));
        button(label("category.general"), left, 34, half, () -> minecraft.gui.setScreen(generalSettings()));
        button(label("add_address"), left + half + 6, 34, half, () -> minecraft.gui.setScreen(addressSettings(-1)));

        for (int index = page * rows; index < Math.min(count, (page + 1) * rows); index++) {
            int entryIndex = index;
            ServerProxySettings entry = config.serverAddresses.get(index);
            int y = 58 + (index - page * rows) * 24;
            Component address = Component.literal(entry.address);
            button(address, left, y, contentWidth - 76, () -> minecraft.gui.setScreen(addressSettings(entryIndex)))
                    .setTooltip(Tooltip.create(entry.enabled
                            ? Component.translatable("config.networkproxy.address_proxy", entry.type.name())
                            : label("address_direct")));
            button(label("remove_address"), left + contentWidth - 70, y, 70, () -> removeAddress(entryIndex, address));
        }

        if (count == 0) {
            addRenderableOnly(new StringWidget(left, 58, contentWidth, 20, label("no_addresses"), font).setMaxWidth(contentWidth));
        }

        button(Component.literal("<"), left, height - 30, 32, () -> changePage(-1)).active = page > 0;
        button(CommonComponents.GUI_DONE, left + 38, height - 30, contentWidth - 76, this::onClose);
        button(Component.literal(">"), left + contentWidth - 32, height - 30, 32, () -> changePage(1)).active = (page + 1) * rows < count;
    }

    private Button button(Component label, int x, int y, int width, Runnable action) {
        return addRenderableWidget(Button.builder(label, button -> action.run()).bounds(x, y, width, 20).build());
    }

    private void changePage(int offset) {
        page += offset;
        rebuildWidgets();
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    private Screen generalSettings() {
        ConfigBuilder builder = ConfigBuilder.create().setParentScreen(this)
                .setTitle(label("category.general")).setSavingRunnable(ProxyConfigScreen::saveConfig);
        ConfigCategory general = builder.getOrCreateCategory(label("category.general"));
        ConfigEntryBuilder fields = builder.entryBuilder();
        general.addEntry(fields.startBooleanToggle(label("default_enabled"), config.enabled)
                .setDefaultValue(false).setSaveConsumer(value -> config.enabled = value).build());
        general.addEntry(fields.startBooleanToggle(label("use_for_downloads"), config.useForDownloads)
                .setDefaultValue(false).setSaveConsumer(value -> config.useForDownloads = value).build());
        general.addEntry(fields.startBooleanToggle(label("use_filter"), Boolean.TRUE.equals(config.configuredAddressesOnly))
                .setDefaultValue(false).setTooltip(label("use_filter.tooltip"))
                .setSaveConsumer(value -> config.configuredAddressesOnly = value).build());
        addProxyFields(general, fields, config);
        return builder.build();
    }

    private Screen addressSettings(int index) {
        ServerProxySettings entry = index < 0 ? new ServerProxySettings() : config.serverAddresses.get(index).copy();
        if (index < 0) {
            entry.copyFrom(config);
            entry.username = "";
            entry.password = "";
        }
        ConfigBuilder builder = ConfigBuilder.create().setParentScreen(this)
                .setTitle(label(index < 0 ? "add_address" : "edit_address"))
                .setSavingRunnable(() -> {
                    var entries = new ArrayList<>(config.serverAddresses);
                    if (index < 0) {
                        entries.add(entry);
                        page = Integer.MAX_VALUE;
                    } else {
                        entries.set(index, entry);
                    }
                    config.serverAddresses = entries;
                    saveConfig();
                });
        ConfigCategory category = builder.getOrCreateCategory(label("server_address"));
        ConfigEntryBuilder fields = builder.entryBuilder();
        category.addEntry(fields.startStrField(label("server_address"), entry.address)
                .setTooltip(label(entry.legacyDomainMatch ? "server_address.legacy_tooltip" : "server_address.tooltip"))
                .setErrorSupplier(value -> addressError(index, entry, value))
                .setSaveConsumer(value -> {
                    String address = value.strip();
                    if (!entry.address.equals(address)) {
                        entry.legacyDomainMatch = false;
                    }
                    entry.address = address;
                }).build());
        category.addEntry(fields.startBooleanToggle(label("address_enabled"), entry.enabled)
                .setDefaultValue(true).setSaveConsumer(value -> entry.enabled = value).build());
        addProxyFields(category, fields, entry);
        return builder.build();
    }

    private Optional<Component> addressError(int index, ServerProxySettings entry, String value) {
        if (entry.legacyDomainMatch && entry.address.equals(value.strip())) {
            return Optional.empty();
        }
        if (!ServerProxySettings.isValidAddress(value)) {
            return Optional.of(label("invalid_address"));
        }
        for (int other = 0; other < config.serverAddresses.size(); other++) {
            if (other != index && config.serverAddresses.get(other).hasSameAddress(value)) {
                return Optional.of(label("duplicate_address"));
            }
        }
        return Optional.empty();
    }

    private void removeAddress(int index, Component address) {
        minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                var entries = new ArrayList<>(config.serverAddresses);
                entries.remove(index);
                config.serverAddresses = entries;
                saveConfig();
            }
            minecraft.gui.setScreen(this);
        }, label("remove_address"), Component.translatable("config.networkproxy.remove_address.confirm", address)));
    }

    private static void addProxyFields(ConfigCategory category, ConfigEntryBuilder fields, ProxySettings settings) {
        category.addEntry(fields.startEnumSelector(label("type"), ProxyConfig.ProxyType.class, settings.type)
                .setDefaultValue(ProxyConfig.ProxyType.SOCKS5).setEnumNameProvider(type -> Component.literal(type.name()))
                .setSaveConsumer(value -> settings.type = value).build());
        category.addEntry(fields.startBooleanToggle(label("resolve_hostnames_through_proxy"), settings.usesRemoteDns())
                .setDefaultValue(false).setTooltip(label("resolve_hostnames_through_proxy.tooltip"))
                .setSaveConsumer(value -> settings.resolveHostnamesThroughProxy = value).build());
        category.addEntry(fields.startStrField(label("host"), settings.host)
                .setDefaultValue("127.0.0.1").setTooltip(label("host.tooltip"))
                .setErrorSupplier(value -> ServerProxySettings.normalizeHost(value.strip()).isEmpty()
                        ? Optional.of(label("invalid_proxy_host")) : Optional.empty())
                .setSaveConsumer(value -> settings.host = value.strip()).build());
        category.addEntry(fields.startIntField(label("port"), settings.port)
                .setDefaultValue(7890).setMin(1).setMax(65535).setSaveConsumer(value -> settings.port = value).build());
        category.addEntry(fields.startStrField(label("username"), settings.username)
                .setDefaultValue("").setSaveConsumer(value -> settings.username = value).build());
        category.addEntry(fields.startStrField(label("password"), settings.password)
                .setDefaultValue("").setSaveConsumer(value -> settings.password = value).build());
    }

    private static Component label(String key) {
        return Component.translatable("config.networkproxy." + key);
    }

    private static void saveConfig() {
        AutoConfig.getConfigHolder(ProxyConfig.class).save();
    }
}
