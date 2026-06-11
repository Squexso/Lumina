# LuminaMC

A custom **Minecraft launcher** (JavaFX) paired with a **client mod** for
Minecraft **1.21.11** that works on **Fabric, Forge and NeoForge**.

The launcher manages instances, Microsoft accounts (multi-account), skins and a
3D wardrobe, plus an in-launcher cosmetics shop (capes & accessories) powered by
**Lumina Tokens** earned by playing. The bundled mod renders the equipped cape
in-game and adds a set of fair, quality-of-life HUD features.

## Download

Grab the latest Windows build from the **[Releases page](https://github.com/Squexso/Lumina/releases/latest)**:

- `LuminaMC-windows.zip` — unzip and run `LuminaMC.exe`.

The launcher auto-updates itself on launch.

## Building from source

```bash
# Launcher (JavaFX app)
./launcher/gradlew -p launcher run

# Client mods (Fabric / Forge / NeoForge)
cd lumina-multiloader && ./gradlew build
```

Requires JDK 21.

## Windows SmartScreen note

LuminaMC builds are **not code-signed yet**, so the first launch may show
*“Windows protected your PC”*. That's only because the project is new — to run it:

1. Click **More info**
2. Click **Run anyway**

This warning disappears on its own as more people download LuminaMC (SmartScreen
reputation is download-based). Code signing via the
[SignPath Foundation](https://signpath.org) is planned once the project has grown —
the release pipeline is already wired for it, see [`docs/SIGNING.md`](docs/SIGNING.md).

## License

LuminaMC is open source under the **[GNU GPL-3.0](LICENSE.txt)**.
