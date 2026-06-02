# LuminaMC — Launcher

A dark, modern Minecraft launcher (JavaFX) that pairs with the **Lumina Fabric mod**
in the repo root. The launcher manages instances, accounts, downloads and Java; the
mod provides the in-game client features (HUD, PVP, the Right-Shift overlay). The
launcher installs the mod into Fabric instances and writes its per-instance
`config/lumina.json` from the toggles you set under the **Performance** tab.

## Run it

```powershell
# from the repo root
.\launcher\gradlew.bat -p launcher run
```

or, from inside `launcher/`:

```bash
./gradlew run
```

Build a distributable:

```bash
./gradlew -p launcher build        # jar + start scripts + distZip under launcher/build/
```

- **Java:** 21 (toolchain enforced)
- **UI:** JavaFX 21
- **Data dir:** `~/.luminamc/` (instances, versions, libraries, assets, natives, logs)

## Microsoft sign-in

Login uses the OAuth 2.0 **device-code flow**. You must supply your own Azure AD
application client id (Personal Microsoft Accounts, public client, device-code flow
enabled, scope `XboxLive.signin offline_access`):

```powershell
$env:LUMINAMC_MS_CLIENT_ID = "<your-azure-app-client-id>"
.\launcher\gradlew.bat -p launcher run
```

or pass `-Dluminamc.msClientId=<id>`. Without it the sign-in dialog explains the
missing config and you can still launch in offline mode.

## Status of each subsystem

| Area | Status |
|------|--------|
| Instance CRUD, JSON storage in `~/.luminamc/instances/<id>/` | ✅ working |
| Mod manager (add/remove, enable/disable, drag & drop) | ✅ working |
| Java auto-detection + manual override | ✅ working |
| Multi-threaded downloader (SHA-1 verified, progress) | ✅ working |
| Mojang manifest + version/asset/library resolution | ✅ working (modern + legacy arg schemes) |
| Fabric loader install (meta.fabricmc.net) | ✅ working |
| Forge / NeoForge install | ⚠️ best-effort — runs the official installer headlessly (`--installClient`) and merges the produced profile; very old Forge predating `--installClient` needs a one-time GUI install |
| Microsoft OAuth (device code) | ✅ implemented; needs your Azure client id to exercise live |
| Feature toggles → `config/lumina.json` | ✅ working |
| Lumina mod install into Fabric instances | ✅ working, version-gated to the mod's declared MC dependency |
| Dark/violet UI, tabs, animated launch button, setup wizard | ✅ working (verified rendering) |
| Self-updater | ✅ checks a release feed; jar swap is left to the platform installer |
| Crash reporter + log viewer | ✅ working |

### Not exercised end-to-end here
A full play session (real account, ~hundreds of MB of game files, launching the JVM)
and the live OAuth chain compile and are wired correctly but were not run in this
environment. The app itself was launch-tested: it renders the UI and creates the data
tree cleanly.

## Package layout (`com.luminamc`)

```
config/     paths, JSON helper, launcher config
auth/       Account, AuthStore, MicrosoftAuth (device-code chain)
download/   Http, DownloadManager/Task, VersionManifest, MojangMeta, FabricMeta, ForgeLikeMeta
instance/   Instance, InstanceManager, ModLoader, Mod
features/   FeatureSettings, HudElement, Crosshair (→ lumina.json export)
javart/     JavaRuntime, JavaDetector
game/       ResolvedVersion, GameInstaller, GameLauncher, ArgumentBuilder, NativeExtractor, LuminaModInstaller
crash/      CrashReporter
update/     SelfUpdater
ui/         AppContext, LaunchService, MainWindow, Sidebar, panels/, components/, dialogs, SetupWizard, Theme
```
