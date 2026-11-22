# Network Proxy

A Minecraft 26.3 client mod for HTTP, HTTPS, and SOCKS5 proxies.

## Install

Network Proxy requires Java 25 and Cloth Config.

- **Fabric** requires Fabric Loader. Install **Mod Menu** to configure Network Proxy in-game.
- **NeoForge** users can open Network Proxy Settings from the mod list.

## Configure

### Server addresses

Choose **Add Server Address** in **Network Proxy Settings** to set the proxy type, host, port, and optional credentials. Use `example.org` to match any port on that hostname or `example.org:25566` to match a specific port.

**Use Proxy** requires the configured proxy for joins and pings to that address. Resource packs from that server use the same proxy. Server-directed transfers require a proxy for the new address. Turning it off makes that address direct unless the killswitch is on.

### Default proxy

- Unlisted servers use the default proxy for joins and pings when **Enable Default Proxy** and **Proxy Unlisted Servers** are both on.
- **Enable Default Proxy** also proxies web downloads. Resource packs use the game connection’s proxy when one is required.
- HTTP and HTTPS proxies resolve pack hostnames. For SOCKS5, turn on **DNS via Proxy** to skip local hostname lookups.
- **Proxy Enforcement / Killswitch** blocks joins and pings without an enabled proxy. It’s off by default.

### DNS and HTTPS

**DNS via Proxy** defaults to on for SOCKS5 and off for HTTP and HTTPS. When on, it sends the server hostname to the proxy for joins and pings, skipping local DNS and SRV lookups. There’s no local DNS fallback. For SRV-only servers, enter the hostname and port from the SRV record. The proxy must be able to resolve and reach the destination, including `.onion` or `.i2p` addresses.

SOCKS5 resolves pack hostnames through the proxy when **DNS via Proxy** is on, including after redirects. HTTPS downloads use TLS through that tunnel. With **DNS via Proxy** off, Minecraft’s downloader tries to resolve the pack hostname locally before opening a SOCKS5 tunnel. Proxy failures, HTTP errors, and invalid certificates also stop the download without a direct retry.

### Realms

Use the permanent **Realms** entry to set the game connection’s route.

- **Follow Default Settings** keeps the existing Realms routing.
- **Direct** connects without a proxy.
- **Use Custom Proxy** requires a dedicated proxy for the game connection and any resource packs the Realm requests over it. It also supports **DNS via Proxy**.

## Build

Run `./gradlew build` with Java 25. The JARs are in `fabric/build/libs` and `neoforge/build/libs`. Shared source and resources are in `common/`.

In IntelliJ IDEA, reload the Gradle project to generate the **Fabric Client** run configuration, which runs `:fabric:runClient`. NeoForge has a separate client configuration. If **Fabric Client** is missing, run `./gradlew :fabric:ideaSyncTask` and reload the project.

## Credits

- Original mod by [misaka10843](https://github.com/misaka10843).
- Proxy enforcement and remote DNS improvements by [FF69B4](https://github.com/FF69B4).
