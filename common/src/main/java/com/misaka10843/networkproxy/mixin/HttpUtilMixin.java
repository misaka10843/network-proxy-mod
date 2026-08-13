package com.misaka10843.networkproxy.mixin;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.misaka10843.networkproxy.NetworkProxy;
import com.misaka10843.networkproxy.config.ProxyConfig;
import net.minecraft.util.HttpUtil;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Path;
import java.util.Map;

/**
 * Routes web downloads (resource packs etc.) through the configured proxy.
 *
 * <p>Uses plain {@link Redirect} instead of MixinExtras' {@code @Local} so the
 * mod has no runtime dependency on MixinExtras on either loader.</p>
 */
@Mixin(HttpUtil.class)
public class HttpUtilMixin {

    @Redirect(method = "downloadFile",
            at = @At(value = "INVOKE", target = "Ljava/net/URL;openConnection(Ljava/net/Proxy;)Ljava/net/URLConnection;"))
    private static URLConnection networkproxy$overrideProxy(URL url, Proxy proxy) throws IOException {
        ProxyConfig config = NetworkProxy.getConfig();
        if (config.enabled && config.useForDownloads) {
            try {
                URI uri = url.toURI();
                NetworkProxy.proxiedUrls.put(uri, NetworkProxy.proxiedUrls.getOrDefault(uri, 0) + 1);
                proxy = switch (config.type) {
                    case HTTP -> new Proxy(Proxy.Type.HTTP, new InetSocketAddress(config.host, config.port));
                    case SOCKS5 -> new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(config.host, config.port));
                };
            } catch (URISyntaxException e) {
                // it's okay
            }
        }
        return url.openConnection(proxy);
    }

    @Inject(method = "downloadFile",
            at = @At(value = "INVOKE", target = "Lorg/apache/commons/io/IOUtils;closeQuietly(Ljava/io/InputStream;)V"))
    private static void networkproxy$undoProxy(Path targetDir, URL url, Map<String, String> headers,
                                               HashFunction hashFunction, @Nullable HashCode requestedHash, int maxSize,
                                               Proxy origProxy, HttpUtil.DownloadProgressListener listener,
                                               CallbackInfoReturnable<Path> cir) {
        try {
            URI uri = url.toURI();
            if (NetworkProxy.proxiedUrls.containsKey(uri)) {
                NetworkProxy.proxiedUrls.put(uri, Math.max(0, NetworkProxy.proxiedUrls.get(uri) - 1));
                if (NetworkProxy.proxiedUrls.get(uri) == 0) {
                    NetworkProxy.proxiedUrls.remove(uri);
                }
            }
        } catch (URISyntaxException e) {
            // it's okay
        }
    }
}
