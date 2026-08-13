# Network Proxy Mod

A Minecraft **client-side** mod with support for HTTP/SOCKS5 proxies and
per-server (per-domain) proxy configuration.

- **Minecraft:** 26.2
- **Loaders:** Fabric **and** NeoForge (single shared codebase)
- **Dependencies:** none beyond the loader itself — no Cloth Config, no ModMenu,
  no Fabric API. The config UI is hand-written with vanilla widgets and the
  config file is stored with Gson.

## Features

- Route all server connections through an HTTP or SOCKS5 proxy.
- Optional domain whitelist: only matching servers use the proxy
  (e.g. `hypixel.net`).
- Optional proxy use for web downloads (resource packs etc.).
- Optional username/password authentication.
- Config file: `<gameDir>/config/networkproxy.json` (same path AutoConfig used,
  so existing configs carry over).
- Config UI reachable from the **pause menu** ("Network Proxy Settings" button);
  on NeoForge also from the mods list "Config" button.

## Building

Requires JDK 25 (Minecraft 26.x requirement) and Gradle 9.7 (wrapper included).

```bash
./gradlew build
```

Artifacts:

- `fabric/build/libs/networkproxy-*-fabric.jar`
- `neoforge/build/libs/networkproxy-*-neoforge.jar`

Each jar bundles the two Netty modules Minecraft does not ship
(`netty-handler-proxy`, `netty-codec-socks`, version-matched to the bundled
Netty 4.2).

## Project layout

```
common/    loader-agnostic code: mixins, config, UI (compiled by both modules)
fabric/    Fabric module (fabric.mod.json + entrypoint)
neoforge/  NeoForge module (neoforge.mods.toml + entrypoint)
```

## License

MIT
