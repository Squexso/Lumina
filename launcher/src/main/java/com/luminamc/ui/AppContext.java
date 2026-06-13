package com.luminamc.ui;

import com.luminamc.auth.AuthStore;
import com.luminamc.config.LauncherConfig;
import com.luminamc.config.LuminaPaths;
import com.luminamc.instance.Instance;
import com.luminamc.instance.InstanceManager;
import com.luminamc.javart.JavaDetector;
import com.luminamc.javart.JavaRuntime;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/** Shared, app-wide state and services injected into every UI component. */
public final class AppContext {

    public final LauncherConfig   config;
    public final AuthStore        auth;
    public final InstanceManager  instances;
    public final JavaDetector     javaDetector = new JavaDetector();
    public List<JavaRuntime>      runtimes;

    public final ObjectProperty<Instance> selectedInstance = new SimpleObjectProperty<>();

    public AppContext() {
        LuminaPaths.ensureBaseDirs();
        config = LauncherConfig.load();
        auth = AuthStore.load();
        instances = new InstanceManager();
        instances.loadAll();
        runtimes = javaDetector.detectAll();
        // Make stored client id visible to MicrosoftAuth.resolveCustomId()
        // which reads the system property.
        applyStoredClientId();
    }

    /** Applies the saved MS client id to the JVM property so MicrosoftAuth picks it up. */
    public void applyStoredClientId() {
        if (config.msClientId != null && !config.msClientId.isBlank()) {
            System.setProperty("luminamc.msClientId", config.msClientId.trim());
        }
    }

    /** Saves a new client id to config and immediately activates it. */
    public void setMsClientId(String id) {
        config.msClientId = id == null ? null : id.trim();
        applyStoredClientId();
        config.save();
    }

    /** Resolves the {@code java} executable to use for an instance, applying overrides. */
    public String resolveJavaExe(Instance inst) {
        JavaRuntime rt;
        if (inst != null && notBlank(inst.javaPathOverride)) {
            rt = javaDetector.probePath(inst.javaPathOverride);
            if (rt != null) return rt.executable.toString();
        }
        if (notBlank(config.defaultJavaPath)) {
            rt = javaDetector.probePath(config.defaultJavaPath);
            if (rt != null) return rt.executable.toString();
        }
        if (runtimes != null && !runtimes.isEmpty() && inst != null) {
            rt = javaDetector.bestFor(runtimes, inst.mcVersion);
            if (rt != null) return rt.executable.toString();
        }
        // Fall back to the JVM running the launcher.
        boolean win = System.getProperty("os.name").toLowerCase().contains("win");
        Path self = Paths.get(System.getProperty("java.home"), "bin", win ? "java.exe" : "java");
        return self.toString();
    }

    public void refreshRuntimes() {
        runtimes = javaDetector.detectAll();
    }

    /**
     * Chooses the JDK to launch with for a version's {@link com.luminamc.javart.JavaTarget}.
     * Honors an explicit per-instance override; otherwise uses the global default if the
     * target accepts it, else the best detected JDK. For a capped target (legacy Minecraft,
     * Java 8 only) a too-new global default is skipped and an exact match is required.
     * Returns null if nothing satisfies the target.
     */
    public JavaRuntime javaForLaunch(Instance inst, com.luminamc.javart.JavaTarget target) {
        if (inst != null && notBlank(inst.javaPathOverride)) {
            JavaRuntime r = javaDetector.probePath(inst.javaPathOverride);
            if (r != null) return r; // explicit choice is respected as-is
        }
        if (notBlank(config.defaultJavaPath)) {
            JavaRuntime r = javaDetector.probePath(config.defaultJavaPath);
            if (r != null && target.accepts(r.major)) return r;
        }
        return target.exact
                ? javaDetector.exactMajor(runtimes, target.major)
                : javaDetector.bestForMajor(runtimes, target.major);
    }

    /** e.g. "21, 17" — installed Java majors, for error messages. */
    public String installedMajors() {
        if (runtimes == null || runtimes.isEmpty()) return "none";
        return runtimes.stream().map(r -> String.valueOf(r.major)).distinct()
                .reduce((a, b) -> a + ", " + b).orElse("none");
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
