package com.misaka10843.networkproxy.config;

public final class RealmsProxySettings extends ProxySettings {
    public Mode mode = Mode.DEFAULT;

    @Override
    void fillMissingValues() {
        super.fillMissingValues();
        if (mode == null) {
            mode = Mode.DEFAULT;
        }
    }

    public RealmsProxySettings copy() {
        var copy = new RealmsProxySettings();
        copy.copyFrom(this);
        copy.mode = mode;
        return copy;
    }

    public enum Mode {
        DEFAULT,
        DIRECT,
        CUSTOM
    }
}
