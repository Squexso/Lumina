package com.luminamc.ui;

import com.luminamc.config.LauncherConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Creates a Desktop shortcut to {@code LuminaMC.exe} the first time the packaged app
 * runs (Windows only). Runs once — tracked via {@link LauncherConfig#desktopShortcutCreated}
 * — so a user who later deletes the shortcut won't have it forced back.
 *
 * <p>No-op when running from a dev/Gradle classpath (no {@code LuminaMC.exe} present).
 */
public final class DesktopShortcut {

    private DesktopShortcut() {}

    public static void ensureOnFirstRun(LauncherConfig config) {
        if (config.desktopShortcutCreated) return;
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return;

        Path exe = locateExe();
        if (exe == null) return; // dev mode / not a packaged app-image

        Path dir = exe.getParent();
        String script = String.join("\n",
                "$ws = New-Object -ComObject WScript.Shell",
                "$desktop = [Environment]::GetFolderPath('Desktop')",
                "$lnk = Join-Path $desktop 'LuminaMC.lnk'",
                "$s = $ws.CreateShortcut($lnk)",
                "$s.TargetPath = '" + exe + "'",
                "$s.WorkingDirectory = '" + dir + "'",
                "$s.IconLocation = '" + exe + ",0'",
                "$s.Description = 'LuminaMC — Minecraft launcher'",
                "$s.Save()");
        try {
            new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", script)
                    .start();
            config.desktopShortcutCreated = true;
            config.save();
        } catch (Exception ignored) {
            // best-effort — never block startup over a shortcut
        }
    }

    /** Locates {@code LuminaMC.exe} relative to the running jar inside the app-image. */
    private static Path locateExe() {
        try {
            Path jar = Paths.get(DesktopShortcut.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            // .../LuminaMC/app/luminamc-launcher.jar  →  .../LuminaMC/LuminaMC.exe
            if (jar.getFileName().toString().endsWith(".jar") && jar.getParent() != null
                    && jar.getParent().getParent() != null) {
                Path exe = jar.getParent().getParent().resolve("LuminaMC.exe");
                if (Files.exists(exe)) return exe;
            }
        } catch (Exception ignored) {}
        return null;
    }
}
