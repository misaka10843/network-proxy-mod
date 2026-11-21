package com.misaka10843.networkproxy.mixin.client;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misaka10843.networkproxy.NetworkProxyClient;
import net.minecraft.util.HttpUtil;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Path;
import java.util.Map;

@Mixin(HttpUtil.class)
public class HttpUtilMixin {
    @WrapMethod(method = "downloadFile")
    private static Path downloadThroughProxy(Path targetDir, URL url, Map<String, String> headers,
                                             HashFunction hashFunction, @Nullable HashCode requestedHash, int maxSize,
                                             Proxy proxy, HttpUtil.DownloadProgressListener listener, Operation<Path> original) {
        return NetworkProxyClient.withDownloadProxy(proxy, selected ->
                original.call(targetDir, url, headers, hashFunction, requestedHash, maxSize, selected, listener));
    }

    @WrapOperation(method = "downloadFile", at = @At(value = "INVOKE",
            target = "Ljava/net/URL;openConnection(Ljava/net/Proxy;)Ljava/net/URLConnection;"))
    private static URLConnection configureAuthentication(URL url, Proxy proxy, Operation<URLConnection> original) {
        URLConnection connection = original.call(url, proxy);
        NetworkProxyClient.configureDownloadAuthentication(connection);
        return connection;
    }
}
