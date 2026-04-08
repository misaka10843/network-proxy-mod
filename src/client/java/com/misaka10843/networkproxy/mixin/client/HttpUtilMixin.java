package com.misaka10843.networkproxy.mixin.client;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.misaka10843.networkproxy.NetworkProxyClient;
import com.misaka10843.networkproxy.config.ProxyConfig;
import net.minecraft.util.HttpUtil;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.*;
import java.nio.file.Path;
import java.util.Map;

//handles resourcepacks and probably other stuff
@Mixin(HttpUtil.class)
public class HttpUtilMixin {
    @Inject(method = "downloadFile", at = @At(value = "INVOKE", target="Ljava/net/URL;openConnection(Ljava/net/Proxy;)Ljava/net/URLConnection;"))
    private static void overrideProxy(Path targetDir,URL url,Map<String,String> headers,HashFunction hashFunction,@Nullable HashCode requestedHash,int maxSize,Proxy origProxy,HttpUtil.DownloadProgressListener listener,CallbackInfoReturnable<Path> cir, @Local(name="proxy", argsOnly=true) LocalRef<Proxy> mutProxy) {
        ProxyConfig config = NetworkProxyClient.getConfig();
        if(config.enabled&&config.useForDownloads){
            try {
                URI uri = url.toURI();
                NetworkProxyClient.proxiedUrls.put(uri,NetworkProxyClient.proxiedUrls.getOrDefault(uri,0)+1);
                mutProxy.set(switch(config.type){
                    case HTTP -> new Proxy(Proxy.Type.HTTP, new InetSocketAddress(config.host, config.port));
                    case SOCKS5 -> new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(config.host, config.port));
                });
            }catch(URISyntaxException e){
                //it's okay
            }
        }
    }

    @Inject(method = "downloadFile", at =@At(value="INVOKE", target="Lorg/apache/commons/io/IOUtils;closeQuietly(Ljava/io/InputStream;)V"))
    private static void undoProxy(Path targetDir,URL url,Map<String,String> headers,HashFunction hashFunction,@Nullable HashCode requestedHash,int maxSize,Proxy origProxy,HttpUtil.DownloadProgressListener listener,CallbackInfoReturnable<Path> cir, @Local(name="proxy", argsOnly=true) LocalRef<Proxy> mutProxy) {
        try {
            URI uri = url.toURI();
            if(NetworkProxyClient.proxiedUrls.contains(uri)){
                NetworkProxyClient.proxiedUrls.put(uri,Math.max(0,NetworkProxyClient.proxiedUrls.get(uri)-1));
                if(NetworkProxyClient.proxiedUrls.get(uri)==0){
                    NetworkProxyClient.proxiedUrls.remove(uri);
                }
            }
        }catch(URISyntaxException e){
            //it's okay
        }
    }
}
