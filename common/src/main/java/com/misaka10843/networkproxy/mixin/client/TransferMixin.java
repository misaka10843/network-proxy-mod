package com.misaka10843.networkproxy.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.misaka10843.networkproxy.ProxyRoute;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientCommonPacketListenerImpl.class)
public class TransferMixin {
    @Shadow @Final protected Connection connection;

    @ModifyExpressionValue(method = "handleTransfer", at = @At(value = "NEW",
            target = "net/minecraft/client/multiplayer/TransferState"))
    private TransferState inheritEnforcement(TransferState transfer) {
        ProxyRoute.attach(transfer, ProxyRoute.attachedTo(connection));
        return transfer;
    }
}
