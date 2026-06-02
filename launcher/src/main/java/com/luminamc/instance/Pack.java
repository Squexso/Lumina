package com.luminamc.instance;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A resource pack or texture pack inside an instance's {@code resourcepacks/}
 * or {@code texturepacks/} folder. A pack is either a {@code .zip} archive or,
 * for resource packs, an unpacked folder.
 */
public final class Pack {

    private final Path file;

    public Pack(Path file) {
        this.file = file;
    }

    public Path path()       { return file; }
    public String fileName() { return file.getFileName().toString(); }

    public boolean isFolder() {
        return Files.isDirectory(file);
    }

    /** Human-friendly name without the {@code .zip} tail. */
    public String displayName() {
        String n = fileName();
        if (n.toLowerCase().endsWith(".zip")) n = n.substring(0, n.length() - 4);
        return n;
    }

    /** Size in bytes for archives; {@code -1} for unpacked folders. */
    public long sizeBytes() {
        if (isFolder()) return -1L;
        try {
            return Files.size(file);
        } catch (Exception e) {
            return 0L;
        }
    }
}
