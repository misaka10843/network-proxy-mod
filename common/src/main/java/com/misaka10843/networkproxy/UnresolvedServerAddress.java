package com.misaka10843.networkproxy;

import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress;

import java.net.InetAddress;
import java.net.InetSocketAddress;

public record UnresolvedServerAddress(InetSocketAddress target, InetAddress literal) implements ResolvedServerAddress {
    @Override
    public String getHostName() {
        return target.getHostString();
    }

    @Override
    public String getHostIp() {
        return literal == null ? "" : literal.getHostAddress();
    }

    @Override
    public int getPort() {
        return target.getPort();
    }

    @Override
    public InetSocketAddress asInetSocketAddress() {
        return target;
    }
}
