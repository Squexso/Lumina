# lumina-multiloader

Multiloader build (Fabric · Forge · NeoForge, Minecraft 1.21.11) for the **LuminaMC
client mod** — the Right-Shift control panel + HUD overlays.

Based on [jaredlll08/MultiLoader-Template](https://github.com/jaredlll08/MultiLoader-Template)
(`1.21.11`), rebranded to `net.squxso.lumina` / mod id `lumina`.

**This is a work-in-progress scaffold.** The cross-loader build is wired up; the
actual LuminaMC features are being ported from the Fabric-only mod at the repo root.
See **[MIGRATION.md](MIGRATION.md)** for the full status and step-by-step plan.

## Build

```powershell
cd lumina-multiloader
.\gradlew.bat build
# jars: {fabric,forge,neoforge}/build/libs/lumina-<loader>-1.21.11-1.1.8.jar
```

Run a dev client: `.\gradlew.bat :fabric:runClient` (or `:neoforge:runClient`, `:forge:runClient`).
