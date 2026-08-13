package com.misaka10843.networkproxy.mixin;

import com.misaka10843.networkproxy.ui.ProxyConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a "Network Proxy" button to the pause menu so the config screen is
 * reachable without ModMenu (or any other mod-menu library) on either loader.
 */
@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin {

    @Shadow
    protected abstract <T extends GuiEventListener & Renderable & NarratableEntry> T addRenderableWidget(T widget);

    @Inject(method = "init", at = @At("TAIL"))
    private void networkproxy$addConfigButton(CallbackInfo ci) {
        PauseScreen self = (PauseScreen) (Object) this;
        int x = self.width / 2 - 100;
        int y = self.height - 40;
        Button button = Button.builder(
                        Component.translatable("config.networkproxy.title"),
                        b -> Minecraft.getInstance().setScreenAndShow(new ProxyConfigScreen(self)))
                .bounds(x, y, 200, 20)
                .build();
        this.addRenderableWidget(button);
    }
}
