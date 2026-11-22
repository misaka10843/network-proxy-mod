package com.misaka10843.networkproxy.mixin.client;

import com.misaka10843.networkproxy.NetworkProxyClient;
import com.misaka10843.networkproxy.UnresolvedServerAddress;
import com.misaka10843.networkproxy.config.ProxySettings;
import io.netty.util.NetUtil;
import net.minecraft.client.multiplayer.resolver.AddressCheck;
import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerNameResolver;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Optional;

@Mixin(ServerNameResolver.class)
public class ServerNameResolverMixin {
    @Shadow @Final private AddressCheck addressCheck;

    @Inject(method = "resolveAddress", at = @At("HEAD"), cancellable = true)
    private void deferSocks5Resolution(ServerAddress address,
                                       CallbackInfoReturnable<Optional<ResolvedServerAddress>> cir) {
        String host = address.getHost();
        ProxySettings settings = NetworkProxyClient.getConfig().forServer(host, address.getPort());
        if (settings == null || !settings.usesRemoteDns()) {
            return;
        }
        if (host.isEmpty() || !addressCheck.isAllowed(address)) {
            cir.setReturnValue(Optional.empty());
            return;
        }

        // Preserve the entered host and port before vanilla can perform DNS or an SRV redirect.
        InetSocketAddress target = InetSocketAddress.createUnresolved(host, address.getPort());
        InetAddress literal = NetUtil.createInetAddressFromIpAddressString(host);
        ResolvedServerAddress unresolved = new UnresolvedServerAddress(target, literal);
        cir.setReturnValue(literal != null && !addressCheck.isAllowed(unresolved)
                ? Optional.empty() : Optional.of(unresolved));
    }
}
