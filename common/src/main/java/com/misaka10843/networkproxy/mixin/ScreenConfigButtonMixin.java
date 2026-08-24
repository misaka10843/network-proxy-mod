package com.misaka10843.networkproxy.mixin;

import com.misaka10843.networkproxy.ui.ProxyConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a "Proxy Settings" button to the multiplayer screen so the config screen
 * is reachable without ModMenu (or any other mod-menu library) on either loader.
 *
 * <p>The entry point lives on the {@code Join Multiplayer} screen rather than the
 * pause menu: a proxy is something you configure before connecting to a server,
 * not in the middle of a game.</p>
 *
 * <p>Mixin's {@code @Shadow} only resolves against members <em>declared in the
 * target class itself</em> (it never walks the superclass chain). The widgets
 * we need ({@code addRenderableWidget}, {@code removeWidget}) are declared in
 * {@link Screen}, not in {@link JoinMultiplayerScreen}, so this mixin targets
 * {@link Screen} and guards with {@code instanceof JoinMultiplayerScreen}.</p>
 */
@Mixin(Screen.class)
public abstract class ScreenConfigButtonMixin {

    @Shadow
    protected abstract GuiEventListener addRenderableWidget(GuiEventListener widget);

    @Shadow
    protected abstract void removeWidget(GuiEventListener widget);

    @Unique
    private Button networkproxy$configButton;

    /**
     * Fired by {@code Gui.setScreen} on every screen open (and again on window
     * resize, which re-runs {@code Screen.init(int, int)}). For the multiplayer
     * screen this runs after {@code JoinMultiplayerScreen.init()} has built its
     * header/footer layout, so our fixed-position button sits on top of it.
     */
    @Inject(method = "init(II)V", at = @At("TAIL"))
    private void networkproxy$addConfigButton(CallbackInfo ci) {
        this.networkproxy$ensureConfigButton();
    }

    /**
     * Some screens rebuild their widgets via {@code rebuildWidgets()}, which
     * would otherwise drop our button.
     */
    @Inject(method = "rebuildWidgets", at = @At("TAIL"))
    private void networkproxy$readdConfigButton(CallbackInfo ci) {
        this.networkproxy$ensureConfigButton();
    }

    @Unique
    private void networkproxy$ensureConfigButton() {
        if (!((Object) this instanceof JoinMultiplayerScreen)) {
            return;
        }
        if (this.networkproxy$configButton != null) {
            this.removeWidget(this.networkproxy$configButton);
        }
        Screen self = (Screen) (Object) this;
        this.networkproxy$configButton = Button.builder(
                        Component.translatable("config.networkproxy.button"),
                        b -> Minecraft.getInstance().setScreenAndShow(new ProxyConfigScreen(self)))
                .bounds(self.width - 105, 8, 100, 20)
                .build();
        this.addRenderableWidget(this.networkproxy$configButton);
    }
}
