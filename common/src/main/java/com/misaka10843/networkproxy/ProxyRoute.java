package com.misaka10843.networkproxy;

import com.misaka10843.networkproxy.config.ProxyConfig;
import com.misaka10843.networkproxy.config.ProxySettings;
import com.misaka10843.networkproxy.config.ServerProxySettings;

import java.net.InetSocketAddress;
import java.util.function.Supplier;

public final class ProxyRoute {
    private static final ScopedValue<ProxyRoute> CURRENT = ScopedValue.newInstance();

    private final ProxySettings settings;
    private final boolean enforced;
    private InetSocketAddress endpoint;

    private ProxyRoute(ProxySettings selected, boolean requireProxy) {
        settings = selected == null ? null : new ProxySettings();
        if (settings != null) {
            settings.copyFrom(selected);
        }
        enforced = requireProxy || (selected instanceof ServerProxySettings profile && profile.enforceProxyForProfile);
    }

    public static ProxyRoute select(ProxyConfig config, String host, int port, boolean inheritedEnforcement) {
        synchronized (config) {
            return new ProxyRoute(config.forServer(host, port), inheritedEnforcement || config.enforceProxy);
        }
    }

    public static ProxyRoute current() {
        return CURRENT.isBound() ? CURRENT.get() : null;
    }

    public <T> T call(Supplier<T> action) {
        return ScopedValue.where(CURRENT, this).call(action::get);
    }

    public void run(Runnable action) {
        ScopedValue.where(CURRENT, this).run(action);
    }

    public boolean enforced() {
        return enforced;
    }

    public ProxySettings settings() {
        return settings;
    }

    public synchronized InetSocketAddress enforcedEndpoint() {
        if (settings == null) {
            throw new IllegalStateException("Network Proxy enforcement requires an enabled proxy for this server");
        }
        if (endpoint == null) {
            InetSocketAddress address = settings.proxyAddress();
            if (address.isUnresolved()) {
                throw new IllegalStateException("Cannot resolve the configured proxy endpoint");
            }
            endpoint = address;
        }
        return endpoint;
    }

    public interface Holder {
        ProxyRoute networkProxy$route();

        void networkProxy$setRoute(ProxyRoute route);
    }

    public static ProxyRoute attachedTo(Object owner) {
        return owner instanceof Holder holder ? holder.networkProxy$route() : null;
    }

    public static void attach(Object owner, ProxyRoute route) {
        ((Holder) owner).networkProxy$setRoute(route);
    }
}
