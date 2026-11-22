# Network Proxy

A client-side Minecraft mod for routing server connections through HTTP or SOCKS5 proxies, with optional domain filtering and resource-pack download proxying.

In Network Proxy Settings, select **Add Server Address** to give a multiplayer server its own proxy type, host, port, credentials, and **SOCKS5 Remote DNS** setting. **Enable Proxy for This Address** controls the entry independently of **Enable Default Proxy**. Use `example.org` for any port at that hostname or `example.org:25566` for a specific port. A port-specific entry takes precedence. Disable an entry to connect to that address directly. Unmatched addresses use the default proxy if it is enabled and **Only proxy configured addresses** is off. Imported domain filter entries match the domain and its subdomains.

When **SOCKS5 Remote DNS** is enabled for an address, the proxy resolves its hostname for joins and server-list pings. Minecraft skips its local DNS and SRV lookups. For an SRV-only server, enter the target hostname and port advertised by its SRV record. With remote DNS off, Minecraft resolves the address and sends the resulting IP to the SOCKS5 proxy.

The repository targets Minecraft 26.3 and builds separate Fabric and NeoForge jars from shared code. Java 25 is required.

- Fabric: install Fabric Loader, Cloth Config and optionally Mod Menu to access the configuration screen.
- NeoForge: install NeoForge and Cloth Config; open the configuration screen from the mod list.

Build both variants with `./gradlew build`. The jars are generated in `fabric/build/libs` and `neoforge/build/libs`. Configure the Gradle JVM to use Java 25.

The shared source and resources are in `common/`; loader-specific entry points and metadata are in `fabric/` and `neoforge/`.

In IntelliJ IDEA, reload the Gradle project to generate the **Fabric Client**
run configuration. It runs the `:fabric:runClient` task; NeoForge has its own
client configuration. If the Fabric configuration is missing after syncing,
run `./gradlew :fabric:ideaSyncTask` and reload the project.
