# Network Proxy

A client-side Minecraft mod for routing server connections through HTTP or SOCKS5 proxies, with optional domain filtering and resource-pack download proxying.

Turn on **SOCKS5 Remote DNS** in Network Proxy Settings to let the proxy resolve multiplayer server names for joins and server-list pings. The setting defaults to off and applies when the server uses the SOCKS5 proxy. With this setting enabled, Minecraft skips local DNS and SRV lookups for those addresses. For an SRV-only server, enter the target hostname and port advertised by its SRV record.

The repository targets Minecraft 26.3 and builds separate Fabric and NeoForge jars from shared code. Java 25 is required.

- Fabric: install Fabric Loader, Cloth Config and optionally Mod Menu to access the configuration screen.
- NeoForge: install NeoForge and Cloth Config; open the configuration screen from the mod list.

Build both variants with `./gradlew build`. The jars are generated in `fabric/build/libs` and `neoforge/build/libs`. Configure the Gradle JVM to use Java 25.

The shared source and resources are in `common/`; loader-specific entry points and metadata are in `fabric/` and `neoforge/`.

In IntelliJ IDEA, reload the Gradle project to generate the **Fabric Client**
run configuration. It runs the `:fabric:runClient` task; NeoForge has its own
client configuration. If the Fabric configuration is missing after syncing,
run `./gradlew :fabric:ideaSyncTask` and reload the project.
