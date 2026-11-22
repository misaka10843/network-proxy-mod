package com.misaka10843.networkproxy.config;

import com.google.common.net.HostAndPort;
import com.google.common.net.InetAddresses;
import io.netty.util.NetUtil;

import java.net.IDN;
import java.util.Locale;

public class ServerProxySettings extends ProxySettings {
    public String address = "";
    public boolean enabled = true;
    public boolean enforceProxyForProfile;
    public boolean legacyDomainMatch;

    public ServerProxySettings copy() {
        ServerProxySettings copy = new ServerProxySettings();
        copy.copyFrom(this);
        copy.address = address;
        copy.enabled = enabled;
        copy.enforceProxyForProfile = enforceProxyForProfile;
        copy.legacyDomainMatch = legacyDomainMatch;
        return copy;
    }

    static boolean isValidAddress(String value) {
        return parse(value) != null;
    }

    boolean hasSameAddress(String value) {
        HostAndPort existing = legacyDomainMatch ? null : parse(address);
        HostAndPort candidate = parse(value);
        return existing != null && existing.equals(candidate);
    }

    int match(String host, int port) {
        if (address == null) {
            return 0;
        }
        if (legacyDomainMatch) {
            String domain = normalizeHost(address.strip().replaceFirst("^\\.", ""));
            String hostname = normalizeHost(host);
            return !domain.isEmpty() && (hostname.equals(domain) || hostname.endsWith("." + domain)) ? 1 : 0;
        }
        HostAndPort target = parse(address);
        if (target == null || !target.getHost().equals(normalizeHost(host))) {
            return 0;
        }
        if (target.hasPort()) {
            return target.getPort() == port ? 3 : 0;
        }
        return 2;
    }

    private static HostAndPort parse(String value) {
        if (value == null) {
            return null;
        }
        try {
            HostAndPort target = HostAndPort.fromString(value.strip());
            String host = normalizeHost(target.getHost());
            if (host.isEmpty() || (target.hasPort() && target.getPort() == 0)) {
                return null;
            }
            return target.hasPort() ? HostAndPort.fromParts(host, target.getPort()) : HostAndPort.fromHost(host);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static String normalizeHost(String value) {
        if (value == null) {
            return "";
        }
        if (InetAddresses.isInetAddress(value) && !value.contains("%")) {
            return InetAddresses.toAddrString(InetAddresses.forString(value));
        }
        if (NetUtil.isValidIpV6Address(value)) {
            return value;
        }
        try {
            String ascii = IDN.toASCII(value, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
            return ascii.endsWith(".") ? ascii.substring(0, ascii.length() - 1) : ascii;
        } catch (IllegalArgumentException e) {
            return "";
        }
    }
}
