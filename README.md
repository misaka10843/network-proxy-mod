# Network Proxy

A client-side Minecraft mod for routing server connections through HTTP, HTTPS or SOCKS5 proxies, with optional domain filtering and resource-pack download proxying.

## Proxy settings

### Server addresses

Open **Network Proxy Settings** and select **Add Server Address** to set a proxy type, host, port, and optional credentials for a multiplayer server. Use `example.org` to match any port on that hostname or `example.org:25566` for a specific port. Port-specific profiles take precedence. Imported domain filters match the domain and its subdomains.

Turn on **Use Proxy** in an address profile to require that proxy for joins and server-list pings. If it is unavailable, the connection fails. Turning **Use Proxy** off makes that address direct unless the killswitch is on. Server resource packs use the session's proxy, and transfers from that session require a proxy route for the new address.

### Default proxy and killswitch

- **Enable Default Proxy** routes Minecraft web downloads through the default proxy. Server resource packs from enforced sessions use their session proxy.
- **Proxy Unlisted Servers** appears when the default proxy is enabled. It also uses the default proxy for joins and pings to addresses without a profile.
- **Proxy Enforcement / Killswitch** requires an enabled proxy route for every multiplayer join or ping. A missing route blocks the attempt, including an address with **Use Proxy** switched off. The killswitch is off by default.

### DNS and proxy types

For proxied multiplayer joins and pings, **DNS via Proxy** sends the original server hostname to the proxy. Minecraft skips local DNS and SRV lookups and does not retry with local DNS if the proxy fails. With the option off, Minecraft resolves the server address locally. For an SRV-only server, enter the hostname and port advertised by its SRV record if you use **DNS via Proxy**.

This is compatible with Tor and I2P proxies that can resolve and reach the destination. Server resource-pack downloads from an enforced SOCKS5 session with **DNS via Proxy** enabled are blocked because the download transport uses local DNS.

Choose **HTTPS** for a proxy endpoint that accepts TLS. Java verifies the proxy certificate and hostname for multiplayer connections and proxied resource-pack downloads. Enter the hostname covered by the certificate in **Proxy Host**.

### Realms

The permanent **Realms** entry controls the Realms game connection separately from the multiplayer killswitch.

- **Follow Default Settings** keeps the existing Realms routing.
- **Direct** connects the game session directly.
- **Use Custom Proxy** requires a dedicated proxy for joining and for server resource packs requested during the session. The profile can use **DNS via Proxy** for the Realms server hostname.

Realms menus and account services keep their existing routes.

## Installation and development

The repository targets Minecraft 26.3 and builds separate Fabric and NeoForge jars from shared code. Java 25 is required.

- For Fabric, install Fabric Loader and Cloth Config. **Mod Menu is required to configure Network Proxy in-game.**
- For NeoForge, install NeoForge and Cloth Config, then open the configuration screen from the mod list.

Build both variants with `./gradlew build`. The jars are generated in `fabric/build/libs` and `neoforge/build/libs`. Configure the Gradle JVM to use Java 25.

The shared source and resources are in `common/`. Loader-specific entry points and metadata are in `fabric/` and `neoforge/`.

In IntelliJ IDEA, reload the Gradle project to generate the **Fabric Client**
run configuration. It runs the `:fabric:runClient` task; NeoForge has its own
client configuration. If the Fabric configuration is missing after syncing,
run `./gradlew :fabric:ideaSyncTask` and reload the project.
