# Verification Record — 26.2 Port + Fabric/NeoForge Multi-Loader + Self-Hosted UI

Date: 2026-08-13 (UTC+8) · Branch: 26.1.x · JDK: Zulu 25.0.4 (`D:\PL\.jdks\azul-25.0.4`)

## 1. 26.2 baseline (Fabric) — COMPLETE, compile-verified

- `gradle.properties` bumped: `minecraft=26.2`, `loader=0.19.3`, `loom=1.17.19`,
  `fabric-api=0.157.0+26.2`, `cloth-config=26.2.155`, `modmenu=20.0.1`.
- Gradle wrapper 9.4.1 → 9.7.0 (fabric-loom 1.17.19 requires Gradle plugin API 9.5.0).
- `./gradlew build` → BUILD SUCCESSFUL, `networkproxy-1.1.0.jar` produced.
- Version sources: Fabric meta API (game/loader), Modrinth API (fabric-api, modmenu,
  cloth-config), fabric-loom maven metadata.

## 2. Mixin targets vs. actual 26.2 jar — COMPLETE (javap on `minecraft-common-bfb32e66d2-26.2.jar`)

| Check | Result |
|---|---|
| `Connection#configurePacketHandler(ChannelPipeline)V` | ✅ present, param `pipeline` |
| `HttpUtil#downloadFile(Path,URL,Map,HashFunction,HashCode,int,Proxy,DownloadProgressListener)Path` | ✅ present |
| `downloadFile` param `proxy` (java/net/Proxy) — used by `@Local(argsOnly)` previously | ✅ present |
| bytecode `139: invokevirtual URL.openConnection:(Ljava/net/Proxy;)Ljava/net/URLConnection;` | ✅ present |

Conclusion: both original mixins port to 26.2 unchanged at the bytecode level.

## 3. GUI/API surface vs. 26.2 — COMPLETE

- `Component.translatable/literal` ✅ (only vanilla UI API the mod used).
- 26.2 GUI rework does not affect this mod (it never touched `Minecraft#screen`/`ChatFormatting`).

## 4–8. Multi-loader restructure — COMPLETE (both jars build)

- Layout: `common/` (shared sources+resources) + `fabric/` + `neoforge/`.
- Deobfuscation (26.1+) removed the need for remapping, so both loader modules
  compile the same `common` sources directly (no Architectury Loom remap chain).
- NeoForge build: ModDevGradle `2.0.143` with `enable { disableRecompilation = true }`
  (26.x ships deobfuscated; skips the NeoForm pipeline).
- Artifacts: `fabric/build/libs/networkproxy-fabric-2.0.0.jar`,
  `neoforge/build/libs/networkproxy-neoforge-2.0.0.jar` — BUILD SUCCESSFUL.
- Mixin config `networkproxy.common.mixins.json` registered in both
  `fabric.mod.json` and `neoforge.mods.toml` (`[[mixins]]`).
- Entrypoints: `NetworkProxyFabricClient` (ClientModInitializer) and
  `NetworkProxyNeoForge` (`@Mod` + `FMLClientSetupEvent`).
- Netty packaging (MC 26.2 bundles Netty 4.2 core but NOT the proxy modules —
  verified against `26.2.json` library list):
  - Fabric: `META-INF/jars/netty-{handler-proxy,codec-socks}-4.2.15.Final.jar` (jar-in-jar) ✅
  - NeoForge: `META-INF/jarjar/…` with `version.range [4.2,)` ✅
  - Both bumped 4.1.97 → **4.2.15.Final** to match Minecraft's Netty.

## 9–11. Self-hosted config (drop Cloth Config + ModMenu) — COMPLETE

- `ConfigManager` (Gson): reads/writes `<gameDir>/config/networkproxy.json` —
  same path AutoConfig used, existing configs load as-is (field names unchanged).
- `ProxyConfigScreen`: hand-written with vanilla `CycleButton`/`EditBox`/`Button`
  (26.2 API: `CycleButton.builder(fn, initial)`, `onOffBuilder`, `EditBox(Font,x,y,w,h,msg)`,
  labels drawn via `GuiGraphicsExtractor.text(Font,Component,x,y,color,shadow)`
  — `GuiGraphics` no longer exists in 26.2).
- Entries: pause-menu button (`PauseScreenMixin`, @Shadow `addRenderableWidget`) on both
  loaders + NeoForge native config button via `IConfigScreenFactory` extension point
  (`ModContainer.registerExtensionPoint`; note: `RegisterClientExtensionsEvent` moved to
  `net.neoforged.neoforge.client.extensions.common` in 26.2 and no longer handles screens).
- `HttpUtilMixin` rewritten from MixinExtras `@Local` to plain Mixin `@Redirect` →
  **zero dependency on MixinExtras** (previously pulled in transitively by Cloth Config).
- Fabric side now depends only on `fabric-loader` (+ bundled netty): **no Fabric API,
  no ModMenu, no Cloth Config, no MixinExtras**.
- Deviation from plan step 11: no keybind registered; the pause-menu button + NeoForge
  mods-list button cover the entry points with less code and no extra APIs.

## 12. Localization / convergence — COMPLETE

- `en_us.json` + `zh_cn.json` kept (dropped the ModMenu-only description key).
- CI workflow updated to upload `fabric/build/libs/*.jar` + `neoforge/build/libs/*.jar`.

## 13. Remaining runtime verification (needs a real game launch — user step)

Compile-time and bytecode checks above give high confidence, but these still need a
real client launch (headless verification is not possible here):

- [ ] Fabric: `./gradlew :fabric:runClient` — mixins apply, pause-menu button opens UI,
      HTTP proxy to a server works, SOCKS5 works, domain filter works, resource-pack
      download through proxy works, proxy auth works.
- [ ] NeoForge: `./gradlew :neoforge:runClient` — same four paths + mods-list "Config" button.
- [ ] Config round-trip: edit → Done → `config/networkproxy.json` written and reloaded.
- [ ] Upgrade path: place an old AutoConfig `config/networkproxy.json` and confirm it loads.

## Version-bump checklist (for future MC releases, e.g. 26.3)

1. Bump `minecraft_version`, `loader_version`, `loom_version` (Fabric meta API), `neoForge_version`
   (neoforge maven), `modDevGradle_version`, `netty_version` if MC's Netty changes.
2. `./gradlew build` — mixin compile is the first signal.
3. javap the new MC jar: `Connection#configurePacketHandler`,
   `HttpUtil#downloadFile` (param `proxy`, `URL.openConnection(Proxy)` bytecode).
4. Check GUI API drift: `Screen.extractRenderState(GuiGraphicsExtractor,…)`,
   `CycleButton.builder`, `EditBox` constructors, `Minecraft#setScreenAndShow`.
5. Check NeoForge API drift: `IConfigScreenFactory` extension point, `RegisterClientExtensionsEvent` package.
6. Run both clients, re-verify the four proxy paths.
