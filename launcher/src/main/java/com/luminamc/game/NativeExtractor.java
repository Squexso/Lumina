package com.luminamc.game;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Extracts platform native libraries ({@code .dll/.so/.dylib}) from jars into a flat dir. */
public final class NativeExtractor {

    private NativeExtractor() {}

    public static void extract(List<Path> nativeJars, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        for (Path jar : nativeJars) {
            if (!Files.exists(jar)) continue;
            try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(jar))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.isDirectory()) continue;
                    String name = entry.getName();
                    if (name.startsWith("META-INF/")) continue;
                    String lower = name.toLowerCase();
                    if (!(lower.endsWith(".dll") || lower.endsWith(".so") || lower.endsWith(".dylib")
                            || lower.endsWith(".jnilib"))) {
                        continue;
                    }
                    Path out = targetDir.resolve(Paths.get(name).getFileName().toString());
                    // Same-size copy already there → skip (also avoids touching DLLs a
                    // running game has locked when the instance is launched a second time).
                    try {
                        if (entry.getSize() >= 0 && Files.exists(out)
                                && Files.size(out) == entry.getSize()) {
                            continue;
                        }
                        Files.copy(zis, out, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException copyError) {
                        boolean existingUsable = false;
                        try { existingUsable = Files.exists(out) && Files.size(out) > 0; }
                        catch (IOException ignored) {}
                        if (!existingUsable) throw copyError;   // locked-but-present → keep it
                    }
                }
            }
        }
    }
}
