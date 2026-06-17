package com.misaka10843.networkproxy.mixin.client;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.misaka10843.networkproxy.NetworkProxyClient;
import com.misaka10843.networkproxy.config.ProxyConfig;
import net.minecraft.util.HttpUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.*;
import java.nio.file.Path;
import java.util.Map;

@Mixin(HttpUtil.class)
public class HttpUtilMixin {
    @Inject(
            method = "downloadFile",
            at = @At(value = "INVOKE", target = "Ljava/net/URL;openConnection(Ljava/net/Proxy;)Ljava/net/URLConnection;")
    )
    private static void networkproxy$overrideProxy(
            Path targetDir,
            URL url,
            Map<String, String> headers,
            HashFunction hashFunction,
            HashCode requestedHash,
            int maxSize,
            Proxy originalProxy,
            HttpUtil.DownloadProgressListener listener,
            CallbackInfoReturnable<Path> cir,
            @Local(argsOnly = true, ordinal = 0) LocalRef<Proxy> proxy
    ) {
        ProxyConfig config = NetworkProxyClient.getConfig();
        if (!config.enabled || !config.useForDownloads || url == null || !NetworkProxyClient.shouldProxyHost(url.getHost())) {
            return;
        }

        try {
            URI uri = url.toURI();
            NetworkProxyClient.proxiedUrls.merge(uri, 1, Integer::sum);
            proxy.set(switch (config.type) {
                case HTTP -> new Proxy(Proxy.Type.HTTP, new InetSocketAddress(config.host, config.port));
                case SOCKS5 -> new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(config.host, config.port));
            });
        } catch (URISyntaxException ignored) {
        }
    }

    @Inject(method = "downloadFile", at = @At("RETURN"))
    private static void networkproxy$undoProxy(
            Path targetDir,
            URL url,
            Map<String, String> headers,
            HashFunction hashFunction,
            HashCode requestedHash,
            int maxSize,
            Proxy originalProxy,
            HttpUtil.DownloadProgressListener listener,
            CallbackInfoReturnable<Path> cir
    ) {
        if (url == null) {
            return;
        }

        try {
            URI uri = url.toURI();
            NetworkProxyClient.proxiedUrls.computeIfPresent(uri, (ignored, count) -> count <= 1 ? null : count - 1);
        } catch (URISyntaxException ignored) {
        }
    }
}
