# Verification Record — 26.2 Port + Fabric/NeoForge Multi-Loader + Self-Hosted UI

Date: 2026-08-13 (UTC+8) · Branch: 26.1.x · JDK: Zulu 25.0.4 (`D:\PL\.jdks\azul-25.0.4`)
Runtime-fix follow-up: 2026-08-18 (see §14).

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

## 3. GUI/API surface vs. 26.2 — COMPLETE (bytecode) / one runtime bug found 2026-08-18

- `Component.translatable/literal` ✅ (only vanilla UI API the mod used).
- 26.2 GUI rework does not affect the mod's proxy logic. **However** the
  pause-menu button mixin DID fail at runtime on both loaders — not because of
  the GUI rework, but because of Mixin 0.8.7's `@Shadow` resolution (see §14).

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
- Entries: pause-menu button (`ScreenConfigButtonMixin`, targets `Screen` — see §14)
  on both loaders + NeoForge native config button via `IConfigScreenFactory` extension point
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

## 13. Runtime verification — startup verified 2026-08-18, functional paths still user-step

Startup has now been verified on real client launches (runClient with the local
JDKs; assets downloaded once to `D:\PL.gradle\caches\neoformruntime\assets`):

- [x] Fabric `runClient`: mod list contains `networkproxy 2.0.0`, log shows
      `NetworkProxy initialized`, **no mixin error**, game reaches main menu.
- [x] NeoForge `runClient`: mod list contains `Network Proxy 2.0.0 (networkproxy)`
      (requires the `mods` block, see §14), **no mixin error**, game passes
      `Minecraft.<init>` and reaches main menu.
- [ ] HTTP proxy to a server works, SOCKS5 works, domain filter works,
      resource-pack download through proxy works, proxy auth works (interactive).
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

## 14. Runtime fixes (2026-08-18) �� two bugs found by actually launching the clients

### 14.1 `@Shadow` of an inherited method fails �� mixin retargeted to `Screen`

- Symptom (both loaders, Mixin 0.8.7): `InvalidMixinException: @Shadow method
  addRenderableWidget(...) was not located in the target class
  net.minecraft.client.gui.screens.PauseScreen. No refMap loaded.`
- Root cause: Mixin 0.8.7 resolves `@Shadow` **only against members declared in
  the target class itself** (see `TargetClassContext.findMethod` /
  `findAliasedMethod` �� they iterate `classNode.methods` and never walk the
  superclass chain). `addRenderableWidget`/`removeWidget` are declared in
  `Screen`, not in `PauseScreen`, so a `@Mixin(PauseScreen.class)` shadow can
  never resolve. It is unrelated to generics (raw-typed shadow failed identically).
- Fix: `PauseScreenMixin` �� `ScreenConfigButtonMixin`, targets `@Mixin(Screen.class)`
  (where the methods are declared), `@Inject` at `init(II)V` TAIL (runs after
  `PauseScreen.init()` on every `Gui.setScreen` call) and `rebuildWidgets` TAIL
  (covers window resize), guarded by `instanceof PauseScreen`, with a `@Unique`
  button field + `@Shadow removeWidget` to keep exactly one button.
- Verified: Fabric and NeoForge `runClient` both apply the mixin with no error.

### 14.2 NeoForge dev run did not load the mod �� missing `mods` block

- Symptom: `Mod List` in `runClient` showed only `minecraft` + `neoforge`; the
  mod's classes/mixins were never loaded, so NeoForge runs gave a false "no
  error" result.
- Fix: added `mods { networkproxy { sourceSet sourceSets.main } }` to
  `neoForge/build.gradle` (ModDevGradle requires this to put the developed mod
  on the dev-run classpath; FML logs `InDevFolderLocator ... from env` once set).
- Verified: `Mod List` now shows `Network Proxy 2.0.0 (networkproxy)`.

### 14.3 Local toolchain �� Java 21 needed by ModDevGradle asset download

- `:neoforge:downloadAssets` runs with a Java 21 toolchain; only Java 25 was
  registered. Added `D:/PL/.jdks/ms-21.0.12` to
  `org.gradle.java.installations.paths` (note: **comma**-separated, not `;`).

### 14.4 Config entry point moved from pause menu to the multiplayer screen

- Design decision (2026-08-18): a proxy is configured before connecting to a
  server, so the in-game config button belongs on the Join Multiplayer screen,
  not the pause menu. This is also the only loader-independent entry point for
  Fabric (which has no native mod-settings screen like NeoForge's
  IConfigScreenFactory "Config" button in the mods list).
- Change: ScreenConfigButtonMixin still targets @Mixin(Screen.class) (the
  @Shadow of addRenderableWidget/removeWidget must resolve against the class
  that declares them), but the guard is now instanceof JoinMultiplayerScreen.
  The button renders top-right at (width - 105, 8, 100, 20) with label key
  config.networkproxy.button (added to en_us.json + zh_cn.json).
- Compile-verified: :fabric:compileJava and :neoforge:compileJava both succeed.
  The @Shadow/@Mixin target are unchanged from 14.1, so the mixin still applies
  with no error; the instanceof change is a runtime check and cannot introduce
  an apply-time failure.
- Still needs a real client launch to confirm the button is visible and the
  config round-trips (see section 13).