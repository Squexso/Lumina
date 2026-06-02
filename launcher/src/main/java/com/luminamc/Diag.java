package com.luminamc;

import com.luminamc.download.FabricMeta;
import com.luminamc.download.MojangMeta;
import com.luminamc.download.VersionManifest;
import com.luminamc.game.ResolvedVersion;

/** Headless diagnostic for the launch pipeline: {@code gradlew diag --args="<mc> [fabricLoader]"}. */
public final class Diag {

    public static void main(String[] args) throws Exception {
        String version = args.length > 0 ? args[0] : "26.1.2";
        String fabricLoader = args.length > 1 ? args[1] : null;

        System.out.println("== Fetching manifest ==");
        VersionManifest vm = VersionManifest.fetch();
        System.out.println("latestRelease=" + vm.latestRelease + "  total=" + vm.versions.size());

        VersionManifest.Entry e = vm.find(version);
        System.out.println("entry for " + version + " = " + (e == null ? "NOT FOUND" : e.url));
        if (e == null) {
            System.out.println("first 12 manifest ids:");
            vm.versions.stream().limit(12).forEach(x -> System.out.println("   " + x.id + " (" + x.type + ")"));
            return;
        }

        System.out.println("== Resolving vanilla ==");
        ResolvedVersion rv = new MojangMeta().resolve(e.url);
        System.out.println("mainClass=" + rv.mainClass);
        System.out.println("javaMajor=" + rv.javaMajor);
        System.out.println("downloads=" + rv.downloads.size() + "  classpath=" + rv.classpath.size());
        System.out.println("jvmArgs=" + rv.jvmArgs.size() + "  gameArgs=" + rv.gameArgs.size());

        if (fabricLoader != null) {
            System.out.println("== Applying Fabric " + fabricLoader + " ==");
            new FabricMeta().applyTo(rv, version, fabricLoader);
            System.out.println("after-fabric mainClass=" + rv.mainClass);
            System.out.println("after-fabric downloads=" + rv.downloads.size() + "  classpath=" + rv.classpath.size());

            System.out.println("-- ASM entries BEFORE dedup --");
            rv.classpath.stream().filter(p -> p.toString().contains("asm"))
                    .forEach(p -> System.out.println("   " + p.getFileName()));

            var deduped = com.luminamc.game.LibraryDedup.dedupe(rv.classpath);
            System.out.println("classpath after dedup=" + deduped.size()
                    + "  (removed " + (rv.classpath.size() - deduped.size()) + ")");
            System.out.println("-- ASM entries AFTER dedup --");
            deduped.stream().filter(p -> p.toString().contains("asm"))
                    .forEach(p -> System.out.println("   " + p.getFileName()));
        }
        System.out.println("== OK ==");
    }
}
