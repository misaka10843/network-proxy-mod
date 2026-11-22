package com.misaka10843.networkproxy.mixin.client;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.misaka10843.networkproxy.ProxyRoute;
import net.minecraft.server.packs.DownloadQueue;
import net.minecraft.util.HttpUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.net.Proxy;
import java.net.URL;
import java.nio.file.Path;
import java.util.Map;

@Mixin(DownloadQueue.class)
public class DownloadQueueMixin {
    @WrapOperation(method = "lambda$runDownload$0", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/HttpUtil;downloadFile(Ljava/nio/file/Path;Ljava/net/URL;Ljava/util/Map;Lcom/google/common/hash/HashFunction;Lcom/google/common/hash/HashCode;ILjava/net/Proxy;Lnet/minecraft/util/HttpUtil$DownloadProgressListener;)Ljava/nio/file/Path;"))
    private Path applyServerRoute(Path directory, URL url, Map<String, String> headers, HashFunction hashFunction,
                                  HashCode hash, int maxSize, Proxy proxy, HttpUtil.DownloadProgressListener listener,
                                  Operation<Path> original, @Local(argsOnly = true) DownloadQueue.BatchConfig config) {
        ProxyRoute route = ProxyRoute.attachedTo(config);
        if (route == null) {
            return original.call(directory, url, headers, hashFunction, hash, maxSize, proxy, listener);
        }
        return route.call(() -> original.call(directory, url, headers, hashFunction, hash, maxSize, proxy, listener));
    }
}
