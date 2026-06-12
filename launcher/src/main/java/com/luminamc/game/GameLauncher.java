package com.luminamc.game;

import com.luminamc.auth.Account;
import com.luminamc.config.LuminaPaths;
import com.luminamc.instance.Instance;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Assembles the JVM command line and launches Minecraft as a child process. */
public final class GameLauncher {

    /**
     * "RAM optimizer" — G1GC tuning applied when the per-instance toggle is on.
     * Includes the flags called out in the spec plus a well-known pause-target set.
     */
    public static final List<String> RAM_OPT_FLAGS = List.of(
            "-XX:+UseG1GC",
            "-XX:+UnlockExperimentalVMOptions",
            "-XX:+ParallelRefProcEnabled",
            "-XX:+DisableExplicitGC",
            "-XX:MaxGCPauseMillis=50",
            "-XX:G1NewSizePercent=30",
            "-XX:G1MaxNewSizePercent=40",
            "-XX:G1HeapRegionSize=16M",
            "-XX:G1ReservePercent=20",
            "-XX:InitiatingHeapOccupancyPercent=15",
            "-XX:SurvivorRatio=32",
            "-XX:+PerfDisableSharedMem",
            "-XX:MaxTenuringThreshold=1");

    /** Builds the full command without launching — useful for the UI's "command preview". */
    public List<String> buildCommand(Instance inst, ResolvedVersion rv, Account account, String javaExe) {
        Path gameDir = LuminaPaths.instanceGameDir(inst.id);
        ArgumentBuilder ab = new ArgumentBuilder(inst, rv, account, gameDir);

        List<String> cmd = new ArrayList<>();
        cmd.add(javaExe);
        cmd.add("-Xms" + inst.ramMinMb + "m");
        cmd.add("-Xmx" + inst.ramMaxMb + "m");
        if (inst.features.ramOptimizer) cmd.addAll(RAM_OPT_FLAGS);
        cmd.add("-Dminecraft.launcher.brand=LuminaMC");
        cmd.add("-Dminecraft.launcher.version=0.1.0");
        cmd.addAll(inst.extraJvmArgs);
        cmd.addAll(ab.resolveAll(rv.jvmArgs));
        cmd.add(rv.mainClass);
        cmd.addAll(ab.resolveAll(rv.gameArgs));
        return cmd;
    }

    /**
     * Writes a sensible default {@code options.txt} the first time an instance
     * runs: GUI Scale 2 (legible on high-DPI screens), view bobbing off, and a
     * quieter, balanced sound mix (master 75 %, music 5 %, everything else 50 %).
     * Only created when absent — the player's own later changes are never
     * overwritten.
     */
    private void ensureDefaultOptions(Path gameDir) {
        Path options = gameDir.resolve("options.txt");
        if (Files.exists(options)) return;
        String content = String.join("\n",
                "guiScale:2",
                "bobView:false",
                "soundCategory_master:0.75",
                "soundCategory_music:0.05",
                "soundCategory_record:0.5",
                "soundCategory_weather:0.5",
                "soundCategory_block:0.5",
                "soundCategory_hostile:0.5",
                "soundCategory_neutral:0.5",
                "soundCategory_player:0.5",
                "soundCategory_ambient:0.5",
                "soundCategory_voice:0.5",
                "soundCategory_ui:0.5") + "\n";
        try {
            Files.writeString(options, content, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // Non-fatal: the game will just use its own defaults.
        }
    }

    /**
     * Loader- and version-agnostic FPS boost: when the per-instance toggle is on,
     * merges performance-friendly values into {@code options.txt} before launch.
     * Because it edits the game's own options file (read by every Minecraft
     * version and every loader), this works on Fabric, Forge, NeoForge and vanilla
     * alike — no in-game mod required.
     *
     * <p>Only the FPS-related keys are touched; all other player settings are kept.
     */
    private void applyFpsBoost(Path gameDir, Instance inst) {
        if (inst.features == null || !inst.features.fpsBoost) return;
        Path options = gameDir.resolve("options.txt");

        java.util.LinkedHashMap<String, String> kv = new java.util.LinkedHashMap<>();
        try {
            if (Files.exists(options)) {
                for (String line : Files.readAllLines(options, StandardCharsets.UTF_8)) {
                    int i = line.indexOf(':');
                    if (i > 0) kv.put(line.substring(0, i), line.substring(i + 1));
                }
            }
        } catch (IOException ignored) {}

        // Performance-friendly overrides (safe, stable keys across MC versions).
        kv.put("graphicsMode", "0");        // Fast
        kv.put("renderDistance", "8");
        kv.put("simulationDistance", "6");
        kv.put("particles", "2");           // Minimal
        kv.put("entityShadows", "false");
        kv.put("mipmapLevels", "0");
        kv.put("biomeBlendRadius", "0");
        kv.put("bobView", "false");
        kv.put("entityDistanceScaling", "0.75");
        kv.put("renderClouds", "\"false\"");
        // Honour the per-instance frame-limit slider as the actual cap.
        int cap = inst.features.frameLimit > 0 ? inst.features.frameLimit : 260;
        kv.put("maxFps", String.valueOf(cap));

        StringBuilder sb = new StringBuilder();
        kv.forEach((k, v) -> sb.append(k).append(':').append(v).append('\n'));
        try {
            Files.writeString(options, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {}
    }

    /**
     * Launches the game. Each output line is delivered to {@code logSink} from a
     * daemon reader thread. Returns the live {@link Process}.
     */
    public Process launch(Instance inst, ResolvedVersion rv, Account account, String javaExe,
                          Consumer<String> logSink) throws IOException {
        return launch(inst, rv, account, javaExe, null, logSink);
    }

    /**
     * Launches the game and, when {@code joinServer} is set ({@code host[:port]}),
     * connects straight to that server (Quick Play on 1.20+, {@code --server} before).
     */
    public Process launch(Instance inst, ResolvedVersion rv, Account account, String javaExe,
                          String joinServer, Consumer<String> logSink) throws IOException {
        Path gameDir = LuminaPaths.instanceGameDir(inst.id);
        Files.createDirectories(gameDir);
        ensureDefaultOptions(gameDir);
        applyFpsBoost(gameDir, inst);

        List<String> cmd = buildCommand(inst, rv, account, javaExe);
        if (joinServer != null && !joinServer.isBlank()) {
            cmd.addAll(quickPlayArgs(inst, joinServer.trim()));
        }
        ProcessBuilder pb = new ProcessBuilder(cmd)
                .directory(gameDir.toFile())
                .redirectErrorStream(true);

        Process process = pb.start();

        Thread reader = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    logSink.accept(line);
                }
            } catch (IOException ignored) {
                // Process ended / stream closed.
            }
        }, "luminamc-gamelog-" + inst.id);
        reader.setDaemon(true);
        reader.start();

        return process;
    }

    /**
     * The join-on-launch arguments for {@code address} ({@code host[:port]}):
     * {@code --quickPlayMultiplayer} on 1.20+ (and the next-gen 26.x scheme),
     * the classic {@code --server}/{@code --port} pair on older versions.
     */
    private static List<String> quickPlayArgs(Instance inst, String address) {
        boolean modern = true;
        try {
            String[] parts = (inst.mcVersion == null ? "" : inst.mcVersion).split("\\.");
            int major = Integer.parseInt(parts[0].replaceAll("\\D", ""));
            if (major == 1 && parts.length > 1) {
                modern = Integer.parseInt(parts[1].replaceAll("\\D", "")) >= 20;
            }
        } catch (Exception ignored) { /* unparsable → assume modern */ }
        if (modern) return List.of("--quickPlayMultiplayer", address);

        String host = address;
        int port = 25565;
        int colon = address.lastIndexOf(':');
        if (colon > 0) {
            host = address.substring(0, colon);
            try { port = Integer.parseInt(address.substring(colon + 1)); } catch (NumberFormatException ignored) {}
        }
        return List.of("--server", host, "--port", String.valueOf(port));
    }
}
