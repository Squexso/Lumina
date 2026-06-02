package com.luminamc.javart;

import java.nio.file.Path;

/** A discovered JDK/JRE: the path to its {@code java} binary plus parsed version info. */
public final class JavaRuntime {

    public final Path    executable;
    public final String  version;   // e.g. "21.0.11"
    public final int     major;     // e.g. 21
    public final String  vendor;    // best-effort
    public final boolean is64Bit;

    public JavaRuntime(Path executable, String version, int major, String vendor, boolean is64Bit) {
        this.executable = executable;
        this.version = version;
        this.major = major;
        this.vendor = vendor;
        this.is64Bit = is64Bit;
    }

    public String label() {
        return "Java " + major + " (" + version + ") — " + vendor + (is64Bit ? " 64-bit" : " 32-bit");
    }

    @Override
    public String toString() {
        return label() + " @ " + executable;
    }
}
