package net.squxso.lumina;

/** Loader-agnostic bootstrap, called from each loader's mod constructor. */
public final class CommonClass {
    private CommonClass() {}

    public static void init() {
        Constants.LOG.info("LuminaMC common bootstrap");
    }
}
