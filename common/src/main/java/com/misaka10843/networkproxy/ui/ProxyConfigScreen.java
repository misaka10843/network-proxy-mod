package com.misaka10843.networkproxy.ui;

import com.misaka10843.networkproxy.NetworkProxy;
import com.misaka10843.networkproxy.config.ProxyConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Hand-written vanilla config screen. No Cloth Config, no ModMenu, no other
 * config-library: only stock Minecraft widgets ({@link CycleButton} and
 * {@link EditBox}) plus plain Gson persistence.
 */
public class ProxyConfigScreen extends Screen {

    private static final int ROW_HEIGHT = 26;
    private static final int FIELD_HEIGHT = 20;
    private static final int FIELD_WIDTH = 170;
    private static final int START_Y = 40;
    private static final int COLOR_LABEL = 0xFFFFFFFF;

    private final Screen parent;

    private ProxyConfig config;
    private CycleButton<Boolean> enabledButton;
    private CycleButton<Boolean> useForDownloadsButton;
    private CycleButton<Boolean> useFilterButton;
    private CycleButton<ProxyConfig.ProxyType> typeButton;
    private EditBox hostBox;
    private EditBox portBox;
    private EditBox usernameBox;
    private EditBox passwordBox;
    private EditBox domainsBox;

    public ProxyConfigScreen(Screen parent) {
        super(Component.translatable("config.networkproxy.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.config = NetworkProxy.getConfig();

        int fieldX = fieldX();
        int y = START_Y;

        this.enabledButton = CycleButton.onOffBuilder(config.enabled)
                .create(fieldX, y, FIELD_WIDTH, FIELD_HEIGHT,
                        Component.translatable("config.networkproxy.enabled"),
                        (button, value) -> config.enabled = value);
        y += ROW_HEIGHT;

        this.useForDownloadsButton = CycleButton.onOffBuilder(config.useForDownloads)
                .create(fieldX, y, FIELD_WIDTH, FIELD_HEIGHT,
                        Component.translatable("config.networkproxy.use_for_downloads"),
                        (button, value) -> config.useForDownloads = value);
        y += ROW_HEIGHT;

        this.useFilterButton = CycleButton.onOffBuilder(config.useFilter)
                .create(fieldX, y, FIELD_WIDTH, FIELD_HEIGHT,
                        Component.translatable("config.networkproxy.use_filter"),
                        (button, value) -> config.useFilter = value);
        y += ROW_HEIGHT;

        this.typeButton = CycleButton.builder(type -> Component.literal(type.name()), config.type)
                .withValues(ProxyConfig.ProxyType.values())
                .create(fieldX, y, FIELD_WIDTH, FIELD_HEIGHT,
                        Component.translatable("config.networkproxy.type"),
                        (button, value) -> config.type = value);
        y += ROW_HEIGHT;

        this.hostBox = textField(fieldX, y, Component.translatable("config.networkproxy.host"),
                config.host, 255);
        y += ROW_HEIGHT;

        this.portBox = textField(fieldX, y, Component.translatable("config.networkproxy.port"),
                Integer.toString(config.port), 5);
        y += ROW_HEIGHT;

        this.usernameBox = textField(fieldX, y, Component.translatable("config.networkproxy.username"),
                config.username, 64);
        y += ROW_HEIGHT;

        this.passwordBox = textField(fieldX, y, Component.translatable("config.networkproxy.password"),
                config.password, 64);
        y += ROW_HEIGHT;

        this.domainsBox = textField(fieldX, y, Component.translatable("config.networkproxy.domain_list"),
                String.join(",", config.proxyDomains), 512);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"),
                        button -> saveAndClose())
                .bounds(this.width / 2 - 100, this.height - 30, 200, 20)
                .build());
    }

    private EditBox textField(int x, int y, Component label, String value, int maxLength) {
        EditBox box = new EditBox(this.font, x, y, FIELD_WIDTH, FIELD_HEIGHT, label);
        box.setMaxLength(maxLength);
        box.setValue(value);
        this.addRenderableWidget(box);
        return box;
    }

    private int fieldX() {
        return this.width / 2 + 10;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);

        int y = START_Y;
        y += ROW_HEIGHT * 3; // three toggle rows (their labels are the button messages)
        drawLabel(extractor, Component.translatable("config.networkproxy.type"), y);
        y += ROW_HEIGHT;
        drawLabel(extractor, Component.translatable("config.networkproxy.host"), y);
        y += ROW_HEIGHT;
        drawLabel(extractor, Component.translatable("config.networkproxy.port"), y);
        y += ROW_HEIGHT;
        drawLabel(extractor, Component.translatable("config.networkproxy.username"), y);
        y += ROW_HEIGHT;
        drawLabel(extractor, Component.translatable("config.networkproxy.password"), y);
        y += ROW_HEIGHT;
        drawLabel(extractor, Component.translatable("config.networkproxy.domain_list"), y);
    }

    private void drawLabel(GuiGraphicsExtractor extractor, Component label, int rowY) {
        int x = fieldX() - 8 - this.font.width(label);
        extractor.text(this.font, label, x, rowY + 6, COLOR_LABEL, true);
    }

    private void saveAndClose() {
        config.enabled = enabledButton.getValue();
        config.useForDownloads = useForDownloadsButton.getValue();
        config.useFilter = useFilterButton.getValue();
        config.type = typeButton.getValue();

        config.host = hostBox.getValue().trim();
        try {
            int port = Integer.parseInt(portBox.getValue().trim());
            if (port >= 0 && port <= 65535) {
                config.port = port;
            }
        } catch (NumberFormatException ignored) {
            // keep the previous value
        }
        config.username = usernameBox.getValue();
        config.password = passwordBox.getValue();

        List<String> domains = new ArrayList<>();
        for (String domain : domainsBox.getValue().split(",")) {
            String trimmed = domain.trim().toLowerCase();
            if (!trimmed.isEmpty() && !domains.contains(trimmed)) {
                domains.add(trimmed);
            }
        }
        config.proxyDomains = domains;

        NetworkProxy.saveConfig();
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    public void onClose() {
        // ESC: close without saving
        Minecraft.getInstance().setScreenAndShow(parent);
    }
}
