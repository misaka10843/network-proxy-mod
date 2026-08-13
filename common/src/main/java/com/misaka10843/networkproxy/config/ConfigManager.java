package com.misaka10843.networkproxy.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.misaka10843.networkproxy.NetworkProxy;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Own Gson-based config persistence. Replaces AutoConfig's GsonConfigSerializer
 * so the mod no longer needs Cloth Config just to store a file.
 *
 * <p>The file lives at {@code <gameDir>/config/networkproxy.json}, the same
 * location AutoConfig used, so upgrades keep the user's existing settings.</p>
 */
public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ConfigManager() {
    }

    public static Path configPath() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("networkproxy.json");
    }

    public static ProxyConfig load() {
        Path path = configPath();
        if (Files.exists(path)) {
            try {
                ProxyConfig config = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), ProxyConfig.class);
                if (config != null) {
                    return config;
                }
            } catch (IOException | RuntimeException e) {
                NetworkProxy.LOGGER.warn("Failed to load networkproxy config, falling back to defaults", e);
            }
        }
        return new ProxyConfig();
    }

    public static void save(ProxyConfig config) {
        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(config), StandardCharsets.UTF_8);
        } catch (IOException e) {
            NetworkProxy.LOGGER.error("Failed to save networkproxy config", e);
        }
    }
}
