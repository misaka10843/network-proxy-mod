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
import java.util.function.BooleanSupplier;

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
        ProxyConfig edited = new ProxyConfig();
        edited.copyFrom(config);
        edited.enabled = config.enabled;
        edited.enforceProxy = config.enforceProxy;
        edited.useForDownloads = config.useForDownloads;
        edited.configuredAddressesOnly = config.configuredAddressesOnly;
        ConfigBuilder builder = ConfigBuilder.create().setParentScreen(this)
                .setTitle(label("category.general")).setSavingRunnable(() -> {
                    synchronized (config) {
                        config.copyFrom(edited);
                        config.enabled = edited.enabled;
                        config.enforceProxy = edited.enforceProxy;
                        config.useForDownloads = edited.useForDownloads;
                        config.configuredAddressesOnly = edited.configuredAddressesOnly;
                    }
                    saveConfig();
                });
        ConfigCategory general = builder.getOrCreateCategory(label("category.general"));
        ConfigEntryBuilder fields = builder.entryBuilder();
        var enabled = fields.startBooleanToggle(label("default_enabled"), edited.enabled)
                .setDefaultValue(false).setSaveConsumer(value -> edited.enabled = value).build();
        general.addEntry(enabled);
        general.addEntry(fields.startBooleanToggle(label("enforce_proxy_all"), edited.enforceProxy)
                .setDefaultValue(false).setTooltip(label("enforce_proxy_all.tooltip"))
                .setSaveConsumer(value -> edited.enforceProxy = value).build());
        general.addEntry(fields.startBooleanToggle(label("use_for_downloads"), edited.useForDownloads)
                .setDefaultValue(false).setSaveConsumer(value -> edited.useForDownloads = value).build());
        general.addEntry(fields.startBooleanToggle(label("use_filter"), Boolean.TRUE.equals(edited.configuredAddressesOnly))
                .setDefaultValue(false).setTooltip(label("use_filter.tooltip"))
                .setSaveConsumer(value -> edited.configuredAddressesOnly = value).build());
        addProxyFields(general, fields, edited, enabled::getValue);
        return builder.build();
    }

    private Screen addressSettings(int index) {
        ServerProxySettings entry = index < 0 ? new ServerProxySettings() : config.serverAddresses.get(index).copy();
        if (index < 0) {
            entry.type = config.type;
            entry.resolveHostnamesThroughProxy = config.usesRemoteDns();
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
        var enabled = fields.startBooleanToggle(label("address_enabled"), entry.enabled)
                .setDefaultValue(true).setSaveConsumer(value -> entry.enabled = value).build();
        category.addEntry(enabled);
        category.addEntry(fields.startBooleanToggle(label("enforce_proxy"), entry.enforceProxyForProfile)
                .setDefaultValue(false).setTooltip(label("enforce_proxy.tooltip"))
                .setSaveConsumer(value -> entry.enforceProxyForProfile = value).build());
        addProxyFields(category, fields, entry, enabled::getValue);
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

    private static void addProxyFields(ConfigCategory category, ConfigEntryBuilder fields,
                                       ProxySettings settings, BooleanSupplier enabled) {
        category.addEntry(fields.startEnumSelector(label("type"), ProxyConfig.ProxyType.class, settings.type)
                .setDefaultValue(ProxyConfig.ProxyType.SOCKS5).setEnumNameProvider(type -> Component.literal(type.name()))
                .setSaveConsumer(value -> settings.type = value).build());
        category.addEntry(fields.startBooleanToggle(label("resolve_hostnames_through_proxy"), settings.usesRemoteDns())
                .setDefaultValue(false).setTooltip(label("resolve_hostnames_through_proxy.tooltip"))
                .setSaveConsumer(value -> settings.resolveHostnamesThroughProxy = value).build());
        category.addEntry(fields.startStrField(label("host"), settings.host)
                .setDefaultValue("").setTooltip(label("host.tooltip"))
                .setErrorSupplier(value -> {
                    if (value.isBlank()) {
                        return enabled.getAsBoolean() ? Optional.of(label("required_proxy_host")) : Optional.empty();
                    }
                    return ServerProxySettings.normalizeHost(value.strip()).isEmpty()
                            ? Optional.of(label("invalid_proxy_host")) : Optional.empty();
                })
                .setSaveConsumer(value -> settings.host = value.strip()).build());
        category.addEntry(fields.startStrField(label("port"), settings.port == 0 ? "" : Integer.toString(settings.port))
                .setDefaultValue("")
                .setErrorSupplier(value -> {
                    if (value.isBlank()) {
                        return enabled.getAsBoolean() ? Optional.of(label("required_proxy_port")) : Optional.empty();
                    }
                    return parsePort(value) < 1 ? Optional.of(label("invalid_proxy_port")) : Optional.empty();
                })
                .setSaveConsumer(value -> settings.port = parsePort(value)).build());
        category.addEntry(fields.startStrField(label("username"), settings.username)
                .setDefaultValue("").setSaveConsumer(value -> settings.username = value).build());
        category.addEntry(fields.startStrField(label("password"), settings.password)
                .setDefaultValue("").setSaveConsumer(value -> settings.password = value).build());
    }

    private static int parsePort(String value) {
        if (value.isBlank()) {
            return 0;
        }
        try {
            int port = Integer.parseInt(value.strip());
            return port >= 1 && port <= 65535 ? port : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static Component label(String key) {
        return Component.translatable("config.networkproxy." + key);
    }

    private static void saveConfig() {
        AutoConfig.getConfigHolder(ProxyConfig.class).save();
    }
}
