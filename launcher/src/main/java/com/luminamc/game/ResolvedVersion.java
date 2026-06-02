package com.luminamc.game;

import com.luminamc.download.DownloadTask;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A fully-parsed Minecraft version ready to install and launch: the files to
 * download, the resolved classpath, native jars to extract, argument templates
 * and the main class. Mod loaders merge their own entries into this object.
 */
public final class ResolvedVersion {

    public String id;
    public String mainClass;

    public final List<DownloadTask> downloads = new ArrayList<>();
    public final List<Path> classpath        = new ArrayList<>();
    public final List<Path> nativeJars       = new ArrayList<>();
    public final List<String> jvmArgs        = new ArrayList<>();
    public final List<String> gameArgs       = new ArrayList<>();

    public Path clientJar;
    public Path assetsRoot;
    public Path nativesDir;       // where native jars are extracted for this launch
    public String assetIndexId;
    public int javaMajor = 21;

    /** Legacy assets (pre-1.7) live as files; modern use the hashed object store. */
    public boolean legacyAssets = false;
}
