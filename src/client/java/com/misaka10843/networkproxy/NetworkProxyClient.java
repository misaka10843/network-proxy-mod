package com.misaka10843.networkproxy;

import com.misaka10843.networkproxy.config.ProxyConfig;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.*;
import java.util.concurrent.ConcurrentHashMap;

public class NetworkProxyClient implements ClientModInitializer {
    public static final String MOD_ID = "networkproxy";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static ProxyConfig getConfig() {
        return AutoConfig.getConfigHolder(ProxyConfig.class).getConfig();
    }
    @Override
    public void onInitializeClient() {
        AutoConfig.register(ProxyConfig.class, GsonConfigSerializer::new);

        LOGGER.info("NetworkProxy Client Initialized!");
        Authenticator.setDefault(new AuthenticatorWrapper(Authenticator.getDefault()));
    }

    public static ConcurrentHashMap<URI, Integer> proxiedUrls = new ConcurrentHashMap<>();

    public static class AuthenticatorWrapper extends Authenticator {

        private final Authenticator original;
        public AuthenticatorWrapper(Authenticator authenticator) {
            super();
            original = authenticator;
        }
        @Override
        public PasswordAuthentication requestPasswordAuthenticationInstance(String host,InetAddress addr,int port,String protocol,String prompt,String scheme,URL url,RequestorType reqType){
            try {
                ProxyConfig config = NetworkProxyClient.getConfig();
                if(proxiedUrls.getOrDefault(url.toURI(),0)>0 && (config.enabled&&config.useForDownloads)){
                    return super.requestPasswordAuthenticationInstance(host,addr,port,protocol,prompt,scheme,url,reqType);
                }else{
                    return original.requestPasswordAuthenticationInstance(host,addr,port,protocol,prompt,scheme,url,reqType);
                }
            }catch(URISyntaxException e){
                return original.requestPasswordAuthenticationInstance(host,addr,port,protocol,prompt,scheme,url,reqType);
            }
        }

        @Override
        protected PasswordAuthentication getPasswordAuthentication(){
            ProxyConfig config = NetworkProxyClient.getConfig();
            return new PasswordAuthentication(config.username, config.password.toCharArray());
        }
    }
}