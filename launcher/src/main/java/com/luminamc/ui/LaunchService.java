package com.luminamc.ui;

import com.luminamc.auth.Account;
import com.luminamc.auth.MicrosoftAuth;
import com.luminamc.crash.CrashReporter;
import com.luminamc.download.VersionManifest;
import com.luminamc.game.GameInstaller;
import com.luminamc.game.GameLauncher;
import com.luminamc.game.LuminaModInstaller;
import com.luminamc.game.ResolvedVersion;
import com.luminamc.instance.Instance;

import javafx.application.Platform;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Drives a full play session on a background thread — token refresh, manifest
 * lookup, Lumina mod + feature-config install, multi-threaded game install,
 * launch, and crash capture — marshalling every update onto the FX thread.
 */
public final class LaunchService {

    public interface Callbacks {
        void phase(String text);
        void progress(double fraction, String detail);  // fraction < 0 => indeterminate
        void log(String line);
        void started(Process process);
        void finished(CrashReporter.Report report);
        void failed(Throwable error);
    }

    private final AppContext ctx;

    public LaunchService(AppContext ctx) {
        this.ctx = ctx;
    }

    public Thread launchAsync(Instance inst, Callbacks cb) {
        Thread t = new Thread(() -> run(inst, cb), "luminamc-launch-" + inst.id);
        t.setDaemon(true);
        t.start();
        return t;
    }

    private void run(Instance inst, Callbacks cb) {
        try {
            // 1. Account / token refresh.
            Account account = ctx.auth.active();
            if (account != null && account.isExpired() && account.msRefreshToken != null) {
                fx(cb, c -> c.phase("Refreshing Microsoft session…"));
                try {
                    String oldId = account.id;
                    Account refreshed = new MicrosoftAuth().refresh(account);
                    refreshed.id = oldId;
                    ctx.auth.addOrReplace(refreshed);
                    account = refreshed;
                } catch (Exception e) {
                    fx(cb, c -> c.log("Token refresh failed (" + e.getMessage() + "); launching with stored token."));
                }
            }
            if (account == null) {
                fx(cb, c -> c.log("Warning: no account signed in — launching in offline mode."));
            }

            // 2. Resolve the version entry from the live manifest.
            fx(cb, c -> c.phase("Fetching version manifest…"));
            VersionManifest manifest = VersionManifest.fetch();
            VersionManifest.Entry entry = manifest.find(inst.mcVersion);
            if (entry == null) {
                throw new IllegalStateException("Version " + inst.mcVersion + " not found in the Mojang manifest.");
            }

            // 3. Install the Lumina mod + write feature toggles.
            fx(cb, c -> c.phase("Preparing client features…"));
            String modStatus = new LuminaModInstaller().prepare(inst);
            fx(cb, c -> c.log(modStatus));

            // 3b. Fabric instances need the Fabric API for almost every mod
            //     (including the Lumina mod) — install it automatically.
            if (inst.loader == com.luminamc.instance.ModLoader.FABRIC) {
                fx(cb, c -> c.phase("Checking Fabric API…"));
                new com.luminamc.game.FabricApiInstaller().ensure(inst, msg -> fx(cb, c -> c.log(msg)));
            }

            // 3c. Performance-mod toggle on: drop in a maintained performance mod
            //     (Sodium / Embeddium) for the loader — real, cross-version FPS.
            if (inst.features != null && inst.features.performanceMod
                    && inst.loader != com.luminamc.instance.ModLoader.VANILLA) {
                fx(cb, c -> c.phase("Checking performance mod…"));
                new com.luminamc.game.PerformanceModInstaller().ensure(inst, msg -> fx(cb, c -> c.log(msg)));
            }

            // 3d. Dynamic lights — real, smooth lighting via LambDynamicLights (Fabric).
            if (inst.loader == com.luminamc.instance.ModLoader.FABRIC) {
                fx(cb, c -> c.phase("Checking dynamic lights…"));
                new com.luminamc.game.ModrinthModInstaller().ensure(inst,
                        "lambdynamiclights", "lambdynamiclights", "LambDynamicLights",
                        msg -> fx(cb, c -> c.log(msg)));
            }

            // 4. Resolve version + loader (cheap), then validate Java BEFORE downloading.
            final Account acc = account;
            GameInstaller installer = new GameInstaller();
            GameInstaller.Listener listener = new GameInstaller.Listener() {
                @Override public void phase(String description) {
                    fx(cb, c -> c.phase(description));
                }
                @Override public void update(long done, long total, int filesDone, int filesTotal, String current) {
                    double fraction = total > 0 ? (double) done / total
                            : filesTotal > 0 ? (double) filesDone / filesTotal : -1;
                    String detail = current + "  (" + filesDone + "/" + filesTotal + ")";
                    fx(cb, c -> c.progress(fraction, detail));
                }
            };
            String initialJava = ctx.resolveJavaExe(inst);
            ResolvedVersion rv = installer.resolve(inst, entry, initialJava, listener);

            com.luminamc.javart.JavaRuntime jr = ctx.javaForLaunch(inst, rv.javaMajor);
            String javaExe;
            if (jr != null && jr.major >= rv.javaMajor) {
                // A suitable local JDK/JRE is available.
                javaExe = jr.executable.toString();
                fx(cb, c -> c.log("Using Java " + jr.major + " at " + jr.executable));
            } else {
                // No adequate local Java — download the right one automatically.
                fx(cb, c -> {
                    c.phase("Preparing Java " + rv.javaMajor + "…");
                    c.log("No local Java " + rv.javaMajor + " found (have: " + ctx.installedMajors()
                            + "). Downloading it automatically — this happens only once.");
                });
                try {
                    java.nio.file.Path autoJava = new com.luminamc.javart.JreProvisioner()
                            .ensureJava(rv.javaMajor, msg -> fx(cb, c -> c.phase(msg)));
                    javaExe = autoJava.toString();
                    fx(cb, c -> c.log("Using auto-downloaded Java " + rv.javaMajor + " at " + autoJava));
                } catch (Exception dlErr) {
                    throw new IllegalStateException("Minecraft " + inst.mcVersion + " needs Java "
                            + rv.javaMajor + ", which isn't installed, and the automatic download failed:\n"
                            + dlErr.getMessage(), dlErr);
                }
            }

            // 5. Download everything, then launch.
            installer.fetchAll(inst, rv, Math.max(1, ctx.config.downloadThreads), listener);

            // Auto-install the matching LuminaMC client mod (Right-Shift panel) for modded instances.
            // Only the supported Minecraft version gets it; on any other version it's removed so an
            // incompatible jar can't stop the game from launching.
            if (com.luminamc.instance.LuminaClientInstaller.install(inst)) {
                fx(cb, c -> c.log("Installed LuminaMC client mod for " + inst.loader));
            } else if (inst.loader != com.luminamc.instance.ModLoader.VANILLA
                    && !com.luminamc.instance.LuminaClientInstaller.SUPPORTED_MC.equals(inst.mcVersion)) {
                fx(cb, c -> c.log("LuminaMC client mod targets Minecraft "
                        + com.luminamc.instance.LuminaClientInstaller.SUPPORTED_MC
                        + " — skipped (and removed) for " + inst.mcVersion + " so it launches cleanly."));
            }

            // Sync the equipped cape + accessory (from the shop) into the in-game mod config.
            com.luminamc.game.LuminaCosmetics.writeEquipped(inst, ctx.config.equippedCape, ctx.config.equippedAccessory);

            fx(cb, c -> { c.progress(1.0, "Ready"); c.phase("Launching Minecraft…"); });
            List<String> captured = Collections.synchronizedList(new CopyOnWriteArrayList<>());
            Process process = new GameLauncher().launch(inst, rv, acc, javaExe, line -> {
                captured.add(line);
                fx(cb, c -> c.log(line));
            });
            fx(cb, c -> c.started(process));

            long sessionStart = System.currentTimeMillis();
            inst.lastPlayed = sessionStart;
            ctx.instances.save(inst);

            int code = process.waitFor();

            // Accumulate total playtime across all sessions, and convert it into
            // Lumina Tokens (1 per minute) for the shop.
            long played = System.currentTimeMillis() - sessionStart;
            if (played > 0) {
                ctx.config.totalPlayMillis += played;
                ctx.config.save();
                inst.playMillis += played;             // per-instance playtime (shown on the card)
                ctx.instances.save(inst);
                new com.luminamc.shop.TokenEconomy(ctx.config).sync(ctx.config.totalPlayMillis);
            }

            CrashReporter.Report report = new CrashReporter().handleExit(inst, code, captured);
            fx(cb, c -> c.finished(report));

        } catch (Throwable t) {
            fx(cb, c -> c.failed(t));
        }
    }

    private void fx(Callbacks cb, java.util.function.Consumer<Callbacks> action) {
        Platform.runLater(() -> action.accept(cb));
    }
}
