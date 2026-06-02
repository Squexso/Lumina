package com.luminamc.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Central resolver for every on-disk location LuminaMC uses. Everything lives
 * under {@code ~/.luminamc} as required by the spec.
 */
public final class LuminaPaths {

    private LuminaPaths() {}

    /** Root data directory: {@code ~/.luminamc}. */
    public static final Path ROOT = Paths.get(System.getProperty("user.home"), ".luminamc");

    public static Path root()              { return ROOT; }
    public static Path instances()         { return ROOT.resolve("instances"); }
    public static Path instance(String id) { return instances().resolve(id); }
    public static Path versions()          { return ROOT.resolve("versions"); }
    public static Path libraries()         { return ROOT.resolve("libraries"); }
    public static Path assets()            { return ROOT.resolve("assets"); }
    public static Path natives()           { return ROOT.resolve("natives"); }
    public static Path modCache()          { return ROOT.resolve("modcache"); }
    public static Path logs()              { return ROOT.resolve("logs"); }
    public static Path config()            { return ROOT.resolve("launcher.json"); }
    public static Path accounts()          { return ROOT.resolve("accounts.json"); }

    /** Per-instance subpaths. */
    public static Path instanceConfig(String id)   { return instance(id).resolve("instance.json"); }
    public static Path instanceGameDir(String id)  { return instance(id).resolve("minecraft"); }
    public static Path instanceMods(String id)      { return instanceGameDir(id).resolve("mods"); }
    public static Path instanceResourcePacks(String id) { return instanceGameDir(id).resolve("resourcepacks"); }
    public static Path instanceTexturePacks(String id)  { return instanceGameDir(id).resolve("texturepacks"); }
    public static Path instanceShaderPacks(String id)   { return instanceGameDir(id).resolve("shaderpacks"); }
    public static Path instanceScreenshots(String id) { return instanceGameDir(id).resolve("screenshots"); }
    public static Path instanceLuminaConfig(String id) { return instanceGameDir(id).resolve("config").resolve("lumina.json"); }

    /** Creates the shared base directories. Safe to call repeatedly. */
    public static void ensureBaseDirs() {
        for (Path p : new Path[]{ROOT, instances(), versions(), libraries(), assets(), natives(), modCache(), logs()}) {
            mkdirs(p);
        }
    }

    public static void mkdirs(Path p) {
        try {
            Files.createDirectories(p);
        } catch (IOException e) {
            throw new RuntimeException("Could not create directory: " + p, e);
        }
    }
}
