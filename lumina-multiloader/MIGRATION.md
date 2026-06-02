# LuminaMC → Multiloader (Fabric · Forge · NeoForge)

This folder is the **foundation** for porting the LuminaMC client mod (the
Right-Shift control panel + HUD overlays) from Fabric-only to **Fabric, Forge and
NeoForge**. It is based on the proven
[jaredlll08/MultiLoader-Template](https://github.com/jaredlll08/MultiLoader-Template)
(branch `1.21.11`), rebranded to `net.squxso.lumina` / mod id `lumina`.

> **Status: Step 1 done on ALL THREE loaders (Fabric · Forge · NeoForge).** The
> shared feature framework (`FeatureManager`, `Feature`, `HudFeature`) is ported to
> `common` (Mojmap), with the FPS + Coordinates HUD features and a minimal
> Right-Shift panel (`LuminaScreen`). Each loader's client adapter wires the
> keybind, tick, HUD render and lifecycle — all three **build real jars** with these
> features.
>
> Loader notes: NeoForge draws the HUD via `RenderGuiEvent.Post`. Forge 1.21.11 uses
> the rewritten **eventbus-7** model (`Event.BUS.addListener(...)`,
> `TickEvent.ClientTickEvent.Post.BUS`) and `AddGuiOverlayLayersEvent` +
> `ForgeLayeredDraw.add(Identifier, ForgeLayer)` for the HUD. In 1.21.11 the vanilla
> id class resolves as `net.minecraft.resources.Identifier` (not `ResourceLocation`).
>
> **Remaining:** the rest of the 60 files — more HUD features (#4), the themed GUI
> (#5), mixins (#6), zoom + config (#7), and wiring the launcher to install the jars
> (#8). See the steps below.
>
> Ported so far → `common/.../feature/*`, `common/.../client/*`,
> `{fabric,neoforge,forge}/.../Lumina*Client`.

---

## 1. Why this is a real port, not a recompile

The existing mod (`../src/client/java`) is written against **Yarn mappings** and the
**Fabric API**. Forge/NeoForge use **official (Mojmap) mappings** and their own
event systems. So every Minecraft reference and every loader hook has to change:

| Existing (Fabric / Yarn)                     | Multiloader (Mojmap)                         |
|----------------------------------------------|----------------------------------------------|
| `net.minecraft.client.MinecraftClient`       | `net.minecraft.client.Minecraft`             |
| `client.player` / `getInventory()`           | `minecraft.player` / `getInventory()`        |
| `DrawContext`                                 | `GuiGraphics`                                |
| `Screen.render(context, …)`                  | `Screen.render(GuiGraphics, …)`              |
| `HudRenderCallback` (Fabric API)             | per-loader HUD hook (see §4)                 |
| `KeyBindingHelper` (Fabric API)              | per-loader key registration (see §3)         |
| `ClientModInitializer`                       | per-loader client entry point                |
| `fabric.mod.json`                            | `fabric.mod.json` + `mods.toml` + `neoforge.mods.toml` |

Parchment (configured here) adds readable parameter names on top of Mojmap, so the
code reads closely to Yarn — but **class and method names differ** and must be
translated.

---

## 2. Build & verify the toolchain first

Before porting anything, confirm all three loaders build on this machine:

```powershell
cd lumina-multiloader
.\gradlew.bat build            # builds common + fabric + forge + neoforge
```

Outputs land in:
```
fabric/build/libs/lumina-fabric-1.21.11-1.1.8.jar
forge/build/libs/lumina-forge-1.21.11-1.1.8.jar
neoforge/build/libs/lumina-neoforge-1.21.11-1.1.8.jar
```

> First build downloads the Fabric/Forge/NeoForge dev environments (large, slow).
> If a loader version no longer resolves, bump it in `gradle.properties`
> (`fabric_version`, `forge_version`, `neoforge_version`, `parchment_version`).
> Run `.\gradlew.bat :fabric:runClient` to launch a dev client for that loader.

---

## 3. STEP 1 — Get Right-Shift working on all three loaders

This is the first feature to port and the proof the architecture works: one
key (Right-Shift) that opens a screen, on every loader.

**Common** — put the shared bits in `common` (the screen + an `openMenu()`):

```java
// common/.../client/LuminaKeys.java
package net.squxso.lumina.client;

import net.minecraft.client.Minecraft;

public final class LuminaKeys {
    // Translation key shown in Options → Controls.
    public static final String CATEGORY = "key.categories.lumina";
    public static final String OPEN_MENU = "key.lumina.open_menu";

    /** Called from each loader when the bound key is pressed. */
    public static void openMenu() {
        Minecraft mc = Minecraft.getInstance();
        // TODO: replace with the real ported LuminaGameMenuScreen
        mc.setScreen(new net.minecraft.client.gui.screens.Screen(
                net.minecraft.network.chat.Component.literal("LuminaMC")) {});
    }
}
```

**Fabric** — add a *client* entry point (`fabric.mod.json` → `entrypoints.client`):

```java
// fabric/.../LuminaFabricClient.java  (implements ClientModInitializer)
KeyMapping key = new KeyMapping(LuminaKeys.OPEN_MENU,
        InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, LuminaKeys.CATEGORY);
KeyBindingHelper.registerKeyBinding(key);
ClientTickEvents.END_CLIENT_TICK.register(mc -> { while (key.consumeClick()) LuminaKeys.openMenu(); });
```
```json
// fabric.mod.json
"entrypoints": { "main": [...], "client": ["net.squxso.lumina.LuminaFabricClient"] }
```

**NeoForge** — register on the *mod* event bus, tick on the *game* bus:

```java
// neoforge/.../LuminaNeoForgeClient.java
@EventBusSubscriber(modid = Constants.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
class KeyReg {
    static final KeyMapping KEY = new KeyMapping(LuminaKeys.OPEN_MENU,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, LuminaKeys.CATEGORY);
    @SubscribeEvent static void onReg(RegisterKeyMappingsEvent e) { e.register(KEY); }
}
@EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT)
class KeyTick {
    @SubscribeEvent static void onTick(ClientTickEvent.Post e) { while (KeyReg.KEY.consumeClick()) LuminaKeys.openMenu(); }
}
```

**Forge** — same idea, Forge event classes (`RegisterKeyMappingsEvent`,
`TickEvent.ClientTickEvent`), `@Mod.EventBusSubscriber(Dist.CLIENT)`.

Once this builds and Right-Shift opens an (empty) screen on Fabric **and** NeoForge
**and** Forge, the hard part — the cross-loader skeleton — is done.

---

## 4. STEP 2 — HUD overlay rendering

The HUD features (FPS, coordinates, keystrokes, CPS, ping, tool, armour/potions)
all draw each frame. Replace the Fabric-API `HudRenderCallback` with:

- **Fabric:** `HudLayerRegistrationCallback` / `HudRenderCallback` (Fabric API).
- **NeoForge:** `RegisterGuiLayersEvent` (client mod bus) → a `LayeredDraw.Layer`.
- **Forge:** `RegisterGuiOverlaysEvent` → `IGuiOverlay`.

Keep all the *drawing* code (positions, colours, text) in `common`, taking a
`GuiGraphics` — only the *registration* differs per loader.

---

## 5. STEP 3 — Mixins

The 9 mixins (`LuminaChatMixin`, `LuminaCrosshairMixin`, `LuminaZoomMixin`,
`LuminaFovMixin`, `LuminaGameMenuBgMixin`, `LuminaScreenBgMixin`,
`LuminaSplashMixin`, `LuminaButtonMixin`, `LuminaSodiumMixin`):

- Move loader-agnostic mixins into `common/.../mixin/` and list them in
  `common/src/main/resources/lumina.mixins.json` (already present, currently holds
  the example `MixinMinecraft`).
- **Retarget every `@Inject`/`@Redirect` to Mojmap names** (method descriptors
  change vs Yarn). Use the Parchment/Mojmap javadocs to find the new signatures.
- `LuminaSodiumMixin` is Sodium-specific → keep it in the Fabric module only (or
  guard with `required = false`), since Sodium's internals differ per loader.
- The `lumina.accesswidener` (Fabric) becomes: Fabric keeps the AW; for
  Forge/NeoForge use an **access transformer** (`META-INF/accesstransformer.cfg`)
  for the same fields/methods.

---

## 6. STEP 4 — Feature-by-feature checklist

Port these from `../src/client/java/net/squxso/lumina/feature/impl/` into `common`
(drawing/logic) + per-loader registration. Tick as you go:

- [ ] FpsHudFeature, CoordinatesHudFeature, DirectionHudFeature
- [ ] CpsHudFeature, KeystrokesHudFeature, PingHudFeature, ToolHudFeature
- [ ] ArmorPotionHudFeature
- [ ] CrosshairFeature (mixin), ZoomFeature (mixin + key), BrightnessBoostFeature
- [ ] DynamicLightsFeature (Fabric already uses LambDynamicLights — see launcher)
- [ ] ChatFeature (mixins), UiThemeFeature
- [ ] The GUI screens (`gui/Lumina*Screen.java`) — mostly portable once
      `MinecraftClient`→`Minecraft` and `DrawContext`→`GuiGraphics` are translated.
- [ ] LuminaConfig / LuminaKeybinds / LuminaLayout (pure logic — easiest, do early)

---

## 7. STEP 5 — Wire the launcher

Once the three jars build, make the launcher install the right one. In
`../launcher/.../game/LuminaModInstaller.java`:

- Replace the Fabric-only `findBuiltJar()` with a loader switch:
  - `FABRIC`  → `lumina-multiloader/fabric/build/libs/lumina-fabric-*.jar`
  - `FORGE`   → `…/forge/build/libs/lumina-forge-*.jar`
  - `NEOFORGE`→ `…/neoforge/build/libs/lumina-neoforge-*.jar`
- Drop the hard `SUPPORTED_MC == "1.21.10"` gate; instead check the jar's
  `minecraft_version` (it's in the filename) against `inst.mcVersion`.
- Until then, the current Fabric-only path keeps working — **do not change it
  before the multiloader jars exist**, or you'll break the working install.

---

## 8. Caveats

- **Forge 1.21.11** is `61.0.1` (very new / thin ecosystem). NeoForge is the
  primary modern Forge-family target; keep Forge building but expect to lean on
  Fabric + NeoForge.
- Each **new Minecraft version** needs its own `gradle.properties` version bump
  (and possibly mapping/mixin fixes). This template is pinned to **1.21.11**.
- This is a parallel project: the shipping Fabric mod at the repo root is
  untouched and keeps working until the multiloader build replaces it.
