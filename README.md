# Network Proxy

A client-side Minecraft mod for routing server connections through HTTP, HTTPS or SOCKS5 proxies, with optional domain filtering and resource-pack download proxying.

In Network Proxy Settings, select **Add Server Address** to give a multiplayer server its own proxy type, host, port, credentials, and **Resolve hostnames through proxy** setting. **Enable Proxy for This Address** controls the entry independently of **Enable Default Proxy**. Use `example.org` for any port at that hostname or `example.org:25566` for a specific port. A port-specific entry takes precedence. Disable an entry to connect to that address directly. Unmatched addresses use the default proxy if it is enabled and **Only proxy configured addresses** is off. Imported domain filter entries match the domain and its subdomains.

When **Resolve hostnames through proxy** is enabled, joins and server-list pings send the original hostname to the HTTP, HTTPS or SOCKS5 proxy. Minecraft skips local DNS and SRV lookups. For an SRV-only server, enter the target hostname and port advertised by its SRV record. With the toggle off, Minecraft resolves the address locally and sends the resulting IP to the proxy.

Choose **HTTPS** for a proxy endpoint that accepts TLS connections. Java verifies the certificate and hostname of the proxy for both multiplayer and proxied resource-pack downloads. Enter the hostname covered by the certificate in **Proxy Host**.

The repository targets Minecraft 26.3 and builds separate Fabric and NeoForge jars from shared code. Java 25 is required.

- Fabric: install Fabric Loader, Cloth Config and optionally Mod Menu to access the configuration screen.
- NeoForge: install NeoForge and Cloth Config; open the configuration screen from the mod list.

Build both variants with `./gradlew build`. The jars are generated in `fabric/build/libs` and `neoforge/build/libs`. Configure the Gradle JVM to use Java 25.

The shared source and resources are in `common/`; loader-specific entry points and metadata are in `fabric/` and `neoforge/`.

In IntelliJ IDEA, reload the Gradle project to generate the **Fabric Client**
run configuration. It runs the `:fabric:runClient` task; NeoForge has its own
client configuration. If the Fabric configuration is missing after syncing,
run `./gradlew :fabric:ideaSyncTask` and reload the project.
