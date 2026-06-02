package com.luminamc.instance;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A mod jar inside an instance's {@code mods/} folder. "Disabled" mods are
 * stored on disk with a {@code .disabled} suffix so the loader skips them.
 */
public final class Mod {

    public static final String DISABLED_SUFFIX = ".disabled";

    private final Path file;

    public Mod(Path file) {
        this.file = file;
    }

    public Path path()        { return file; }
    public String fileName()  { return file.getFileName().toString(); }

    public boolean isEnabled() {
        return !fileName().endsWith(DISABLED_SUFFIX);
    }

    /** Human-friendly name without the {@code .jar}/{@code .disabled} tail. */
    public String displayName() {
        String n = fileName();
        if (n.endsWith(DISABLED_SUFFIX)) n = n.substring(0, n.length() - DISABLED_SUFFIX.length());
        if (n.endsWith(".jar")) n = n.substring(0, n.length() - 4);
        return n;
    }

    public long sizeBytes() {
        try {
            return Files.size(file);
        } catch (Exception e) {
            return 0L;
        }
    }
}
